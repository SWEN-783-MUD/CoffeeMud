package com.planet_ink.coffee_mud.Combat;

import java.util.Objects;

import com.planet_ink.coffee_mud.Combat.ValidationResult.Code;
import com.planet_ink.coffee_mud.core.CMLib;

/**
 * Resolves immediate actions through pre_use, execute, and post_use. Registered
 * room encounters must enter through EncounterLibrary, which holds their manager
 * lock and rechecks location. This resolver also permits enrolled NPC actors.
 */
public final class ActionResolver
{
	public ActionOutcome resolve(final ActionRequest request)
	{
		Objects.requireNonNull(request,"request");
		final GeneralEncounter encounter=request.getEncounter();
		synchronized(encounter)
		{
			if(!request.claim())
				return reject(Code.ALREADY_PROCESSED,"This action request was already processed.");
			if(!encounter.beginActionResolution())
				return reject(Code.RESOLUTION_IN_PROGRESS,"Another action is already resolving.");
			try
			{
				final EncounterParticipant participant=request.getParticipant();
				final EncounterAction action=request.getAction();
				final int cost=action.getActionCost();
				final ValidationResult validation=preUse(encounter,participant,action,cost);
				if(!validation.isValid())
					return ActionOutcome.rejected(validation);
				final int round=encounter.getRound();
				final ActionOutcome outcome=Objects.requireNonNull(action.execute(encounter,participant),"outcome");
				if(!outcome.isSuccessful())
					return outcome;
				if(!encounter.consumeActions(participant,cost,outcome.isAttack(),outcome.getDiagonalSteps()))
					throw new IllegalStateException("An action changed turn ownership or resources during execution.");
				try
				{
					action.postUse(encounter,participant,outcome);
				}
				finally
				{
					// Costs are already committed. A failed follow-up must not strand
					// an exhausted turn or allow this same request to run again.
					if((encounter.getState()==GeneralEncounter.State.ACTIVE)
					&&(encounter.getRound()==round)&&(encounter.getCurrentParticipant()==participant)
					&&(outcome.endsTurn()||(participant.getActionsRemaining()==0)))
						encounter.endTurn(participant);
				}
				return outcome;
			}
			finally
			{
				encounter.finishActionResolution();
			}
		}
	}

	private ValidationResult preUse(final GeneralEncounter encounter, final EncounterParticipant participant,
			final EncounterAction action, final int cost)
	{
		boolean enrolled=false;
		for(final EncounterParticipant member : encounter.getParticipants())
			if(member==participant)
				enrolled=true;
		if(!enrolled)
			return ValidationResult.rejected(Code.NOT_PARTICIPANT,"You are not participating in this encounter.");
		switch(encounter.getState())
		{
		case PENDING: return ValidationResult.rejected(Code.PENDING,"Wait until encounter setup finishes.");
		case AWAITING_RESCUE: return ValidationResult.rejected(Code.PAUSED,"Combat is paused awaiting rescue.");
		case COMPLETED: return ValidationResult.rejected(Code.COMPLETED,"This encounter has already ended.");
		default: break;
		}
		if(encounter.getCurrentParticipant()!=participant)
			return ValidationResult.rejected(Code.NOT_YOUR_TURN,"Wait for your turn.");
		if(!CMLib.flags().isAliveAwakeMobileUnbound(participant.getMob(),true))
			return ValidationResult.rejected(Code.UNABLE_TO_ACT,"You cannot act right now.");
		if(cost<0)
			return ValidationResult.rejected(Code.INVALID_ACTION,"The action cost cannot be negative.");
		if(cost>participant.getActionsRemaining())
			return ValidationResult.rejected(Code.INSUFFICIENT_ACTIONS,"You do not have enough actions remaining.");
		return Objects.requireNonNull(action.validate(encounter,participant),"validation");
	}

	private ActionOutcome reject(final Code code, final String reason)
	{
		return ActionOutcome.rejected(ValidationResult.rejected(code,reason));
	}
}

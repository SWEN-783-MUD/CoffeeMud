package com.planet_ink.coffee_mud.Combat;

/** Requests shared turn-end processing without consuming an action. */
public final class EndTurnAction implements EncounterAction
{
	@Override
	public int getActionCost() { return 0; }

	@Override
	public ValidationResult validate(final GeneralEncounter encounter, final EncounterParticipant participant)
	{
		return ValidationResult.valid();
	}

	@Override
	public ActionOutcome execute(final GeneralEncounter encounter, final EncounterParticipant participant)
	{
		return ActionOutcome.endTurn();
	}
}

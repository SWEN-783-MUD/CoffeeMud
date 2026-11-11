package com.planet_ink.coffee_mud.Combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An unstructured combat encounter initialized with its starting participants.
 */
public class GeneralEncounter implements Encounter
{
	public enum State
	{
		PENDING,
		ACTIVE,
		AWAITING_RESCUE,
		COMPLETED
	}

	private final List<EncounterParticipant> participants;
	private State state=State.PENDING;
	private int round=0;
	private int currentParticipantIndex=-1;

	/**
	 * Creates a pending encounter with a snapshot of the initial participants.
	 *
	 * @param initialParticipants the participants present at initiation
	 * @throws NullPointerException if the list or any participant is null
	 */
	public GeneralEncounter(final List<EncounterParticipant> initialParticipants)
	{
		final List<EncounterParticipant> copy=new ArrayList<EncounterParticipant>(initialParticipants);
		for(final EncounterParticipant participant : copy)
			Objects.requireNonNull(participant, "participant");
		participants=Collections.unmodifiableList(copy);
	}

	/**
	 * @return the encounter's current lifecycle state
	 */
	public synchronized State getState()
	{
		return state;
	}

	/**
	 * @return the current round, or zero before the encounter first activates
	 */
	public synchronized int getRound()
	{
		return round;
	}

	/**
	 * @return the participant taking the active turn, or null when no turn is active
	 */
	public synchronized EncounterParticipant getCurrentParticipant()
	{
		return ((state==State.ACTIVE)&&(currentParticipantIndex>=0))?participants.get(currentParticipantIndex):null;
	}

	/**
	 * Ends the active participant's turn and advances in the supplied order.
	 * Finishing the last participant's turn starts the next round.
	 *
	 * @param participant the participant requesting to end their current turn
	 * @return true if the active turn ended, false if the request is not permitted
	 */
	public synchronized boolean endTurn(final EncounterParticipant participant)
	{
		if((state!=State.ACTIVE)||(participant==null)||(participant!=getCurrentParticipant()))
			return false;
		participant.endTurn();
		currentParticipantIndex++;
		if(currentParticipantIndex==participants.size())
		{
			currentParticipantIndex=0;
			round++;
		}
		getCurrentParticipant().beginTurn();
		return true;
	}

	/**
	 * Spends actions for an already-validated activity without attack or movement
	 * usage. Spending the final action ends the turn.
	 *
	 * @param participant the active participant spending actions
	 * @param cost a positive action cost
	 * @return whether the resources were spent
	 */
	public synchronized boolean spendActions(final EncounterParticipant participant, final int cost)
	{
		return spendActions(participant,cost,false,0);
	}

	/**
	 * Records resources for an already-validated activity atomically, before any
	 * turn advancement. Action implementations remain responsible for validating
	 * the activity itself; a legal attack that misses still records an attack.
	 *
	 * @param participant the active participant spending actions
	 * @param cost a positive action cost
	 * @param attack whether this activity counts as one attack
	 * @param diagonalSteps the nonnegative number of actual diagonal steps
	 * @return false without changing resources if the request is invalid
	 */
	public synchronized boolean spendActions(final EncounterParticipant participant, final int cost,
			final boolean attack, final int diagonalSteps)
	{
		if((state!=State.ACTIVE)||(participant==null)||(participant!=getCurrentParticipant())
		||(cost<=0)||(diagonalSteps<0)||(cost>participant.getActionsRemaining()))
			return false;
		participant.spendActions(cost,attack,diagonalSteps);
		if(participant.getActionsRemaining()==0)
			endTurn(participant);
		return true;
	}

	/**
	 * Activates the encounter at round one once setup is complete.
	 *
	 * @return true if the encounter moved from pending to active
	 */
	public synchronized boolean activate()
	{
		if(state!=State.PENDING)
			return false;
		round=1;
		// Setup supplies the participant order; initiative rolling is separate.
		currentParticipantIndex=participants.isEmpty()?-1:0;
		state=State.ACTIVE;
		if(getCurrentParticipant()!=null)
			getCurrentParticipant().beginTurn();
		return true;
	}

	/**
	 * Pauses an active encounter while another party member may rescue it.
	 * The caller decides eligibility and owns any rescue deadline.
	 *
	 * @return true if the encounter moved from active to awaiting rescue
	 */
	public synchronized boolean awaitRescue()
	{
		if(state!=State.ACTIVE)
			return false;
		state=State.AWAITING_RESCUE;
		return true;
	}

	/**
	 * Resumes an encounter after its caller accepts a rescuer.
	 *
	 * @return true if the encounter moved from awaiting rescue to active
	 */
	public synchronized boolean resume()
	{
		if(state!=State.AWAITING_RESCUE)
			return false;
		state=State.ACTIVE;
		return true;
	}

	/**
	 * Completes an active encounter or one whose rescue opportunity has ended.
	 * Completion is terminal; pending encounters must first finish setup.
	 *
	 * @return true if the encounter moved to completed
	 */
	public synchronized boolean complete()
	{
		if((state!=State.ACTIVE)&&(state!=State.AWAITING_RESCUE))
			return false;
		if(currentParticipantIndex>=0)
			participants.get(currentParticipantIndex).endTurn();
		currentParticipantIndex=-1;
		state=State.COMPLETED;
		return true;
	}

	@Override
	public List<EncounterParticipant> getParticipants()
	{
		return participants;
	}
}

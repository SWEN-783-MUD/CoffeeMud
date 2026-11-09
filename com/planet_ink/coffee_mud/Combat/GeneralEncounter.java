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
	 * Activates the encounter once setup is complete.
	 *
	 * @return true if the encounter moved from pending to active
	 */
	public synchronized boolean activate()
	{
		if(state!=State.PENDING)
			return false;
		state=State.ACTIVE;
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
		state=State.COMPLETED;
		return true;
	}

	@Override
	public List<EncounterParticipant> getParticipants()
	{
		return participants;
	}
}

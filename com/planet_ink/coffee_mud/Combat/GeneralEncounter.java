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
	/** Phases owned by the room encounter. */
	public enum State
	{
		/** The participants have been selected and setup is in progress. */
		PENDING,
		/** The encounter has finished setup. */
		ACTIVE,
		/** Reserved for the encounter owner's rescue rules. */
		AWAITING_RESCUE,
		/** The encounter has ended. */
		COMPLETED
	}

	/** Fixed roster captured when the room opens the encounter. */
	private final List<EncounterParticipant> participants;
	/** Phase reads must be safe while the party library holds its character lock. */
	private volatile State state=State.PENDING;

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
	public State getState()
	{
		return state;
	}

	/**
	 * Activates the encounter once setup is complete.
	 *
	 * @return true if the encounter moved from pending to active
	 */
	synchronized boolean activate()
	{
		if(state!=State.PENDING)
			return false;
		state=State.ACTIVE;
		return true;
	}

	/**
	 * Marks an active encounter as completed.
	 *
	 * @return true if the encounter moved from active to completed
	 */
	synchronized boolean complete()
	{
		if(state!=State.ACTIVE)
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

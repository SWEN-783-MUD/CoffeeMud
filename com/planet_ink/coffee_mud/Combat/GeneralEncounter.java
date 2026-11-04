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
		return State.PENDING;
	}

	@Override
	public List<EncounterParticipant> getParticipants()
	{
		return participants;
	}
}

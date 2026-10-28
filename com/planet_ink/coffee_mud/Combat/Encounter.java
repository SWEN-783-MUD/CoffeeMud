package com.planet_ink.coffee_mud.Combat;

import java.util.List;

/**
 * Represents an encounter managed by an {@link EncounterManager}.
 * Provides a common type for different encounter implementations, such as
 * a general encounter. Shared encounter behavior can be defined here as
 * those implementations are introduced.
 */
public interface Encounter
{
	/**
	 * Returns the participants representing players and NPCs in this encounter.
	 * Implementations must return a non-null, unmodifiable list. Participant
	 * membership is managed by the encounter rather than through this list.
	 *
	 * @return an unmodifiable list of encounter participants, empty if there are none
	 */
	public List<EncounterParticipant> getParticipants();
}

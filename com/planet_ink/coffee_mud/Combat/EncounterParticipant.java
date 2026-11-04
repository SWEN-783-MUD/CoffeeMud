package com.planet_ink.coffee_mud.Combat;

import java.util.Objects;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

/**
 * Represents a player or NPC participating in an encounter.
 * Encounter-specific participant state can be added here.
 */
public class EncounterParticipant
{
	/** The player or NPC represented by this participant. */
	private final MOB mob;

	/**
	 * Creates a participant for the given player or NPC.
	 *
	 * @param mob the player or NPC participating in the encounter
	 * @throws NullPointerException if mob is null
	 */
	public EncounterParticipant(final MOB mob)
	{
		this.mob=Objects.requireNonNull(mob, "mob");
	}

	/**
	 * Returns the player or NPC represented by this participant.
	 *
	 * @return the participating player or NPC
	 */
	public MOB getMob()
	{
		return mob;
	}
}

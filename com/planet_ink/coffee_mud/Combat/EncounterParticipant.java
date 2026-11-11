package com.planet_ink.coffee_mud.Combat;

import java.util.Objects;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.party.ExplorationContext;

/**
 * Represents a player or NPC participating in an encounter.
 * Encounter-specific participant state can be added here.
 */
public class EncounterParticipant
{
	/** The player or NPC represented by this participant. */
	private final MOB mob;

	private final ExplorationContext approach;

	/**
	 * Creates a participant for the given player or NPC.
	 *
	 * @param mob the player or NPC participating in the encounter
	 * @throws NullPointerException if mob is null
	 */
	public EncounterParticipant(final MOB mob)
	{
		this(mob,null);
	}

	/**
	 * Captures a participant and the approach used for encounter setup.
	 *
	 * @param mob the character participating
	 * @param approach the saved room entry, or null if none was supplied
	 */
	public EncounterParticipant(final MOB mob, final ExplorationContext approach)
	{
		this.mob=Objects.requireNonNull(mob, "mob");
		this.approach=approach;
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

	/**
	 * Reads the approach recorded at setup. Later activity changes leave it intact.
	 *
	 * @return the saved approach, or null when there was none
	 */
	public ExplorationContext getApproach()
	{
		return approach;
	}
}

package com.planet_ink.coffee_mud.Combat;

import java.util.Objects;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.party.ExplorationContext;

/**
 * Represents a player or NPC participating in an encounter.
 * Holds the saved exploration approach and resources for the current turn.
 * Resource mutation methods are public for CoffeeMud's separate class loaders.
 * Callers should use GeneralEncounter to validate and spend turn resources.
 */
public class EncounterParticipant
{
	/** The player or NPC represented by this participant. */
	private final MOB mob;
	private int actionsRemaining;
	private int attackCount;
	private int diagonalCount;

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

	/** @return the actions remaining in this participant's turn */
	public synchronized int getActionsRemaining()
	{
		return actionsRemaining;
	}

	/** @return the number of attacks made during this participant's turn */
	public synchronized int getAttackCount()
	{
		return attackCount;
	}

	/** @return the diagonal steps taken during this participant's turn */
	public synchronized int getDiagonalCount()
	{
		return diagonalCount;
	}

	/** Called by the encounter when this participant's turn begins. */
	public synchronized void beginTurn()
	{
		actionsRemaining=3;
		attackCount=0;
		diagonalCount=0;
	}

	/** Called by the encounter to discard unused turn actions. */
	public synchronized void endTurn()
	{
		actionsRemaining=0;
	}

	/** Called by the encounter after validating the activity's resource request. */
	public synchronized void spendActions(final int cost, final boolean attack, final int diagonalSteps)
	{
		actionsRemaining-=cost;
		if(attack)
			attackCount++;
		diagonalCount+=diagonalSteps;
	}
}

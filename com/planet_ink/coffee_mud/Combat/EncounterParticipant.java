package com.planet_ink.coffee_mud.Combat;

import java.util.Objects;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

/**
 * Represents a player or NPC participating in an encounter.
 * Holds the resources and usage counts for that participant's current turn.
 */
public class EncounterParticipant
{
	/** The player or NPC represented by this participant. */
	private final MOB mob;
	private int actionsRemaining;
	private int attackCount;
	private int diagonalCount;

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

	/** Only the encounter begins turns and changes action resources. */
	synchronized void beginTurn()
	{
		actionsRemaining=3;
		attackCount=0;
		diagonalCount=0;
	}

	synchronized void endTurn()
	{
		actionsRemaining=0;
	}

	synchronized void spendActions(final int cost, final boolean attack, final int diagonalSteps)
	{
		actionsRemaining-=cost;
		if(attack)
			attackCount++;
		diagonalCount+=diagonalSteps;
	}
}

package com.planet_ink.coffee_mud.Libraries.interfaces;

import com.planet_ink.coffee_mud.Combat.GeneralEncounter;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

/** Shared entry point for player commands interacting with room encounters. */
public interface EncounterLibrary extends CMLibrary
{
	/** Outcome of a player's request to end their current turn. */
	public enum EndTurnResult
	{
		ENDED, NOT_PLAYER, NO_ENCOUNTER, NOT_PARTICIPANT,
		PENDING, PAUSED, COMPLETED, NOT_YOUR_TURN, UNABLE_TO_ACT
	}

	/**
	 * Finds the general encounter enrolling this character in their current room.
	 * This room-scoped lookup does not establish cross-room enrollment tracking.
	 * @param actor the character to look up
	 * @return the enrolling encounter, or null
	 */
	public GeneralEncounter getEncounter(MOB actor);

	/**
	 * Validates and immediately ends the player's active turn under the room
	 * manager and encounter locks. No request is queued or retained for replay.
	 * @param actor the player requesting to end their turn
	 * @return the result, for the command to translate into player feedback
	 */
	public EndTurnResult endTurn(MOB actor);
}

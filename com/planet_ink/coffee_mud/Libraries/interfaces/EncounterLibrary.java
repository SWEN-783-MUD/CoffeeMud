package com.planet_ink.coffee_mud.Libraries.interfaces;

import com.planet_ink.coffee_mud.Combat.GeneralEncounter;
import com.planet_ink.coffee_mud.Combat.EncounterAction;
import com.planet_ink.coffee_mud.Combat.ActionOutcome;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

/** Shared entry point for player commands interacting with room encounters. */
public interface EncounterLibrary extends CMLibrary
{
	/** Outcome of a player's request to end their current turn. */
	public enum EndTurnResult
	{
		ENDED, NOT_PLAYER, NO_ENCOUNTER, NOT_PARTICIPANT,
		PENDING, PAUSED, COMPLETED, NOT_YOUR_TURN, UNABLE_TO_ACT,
		RESOLUTION_IN_PROGRESS
	}

	/**
	 * Finds the general encounter enrolling this character in their current room.
	 * This room-scoped lookup does not establish cross-room enrollment tracking.
	 * @param actor the character to look up
	 * @return the enrolling encounter, or null
	 */
	public GeneralEncounter getEncounter(MOB actor);

	/**
	 * Resolves an immediate player action under the room and encounter locks.
	 * Common and action-specific prerequisites are checked before execution.
	 * No request is queued or retained for replay.
	 * @param actor the player submitting the action
	 * @param action the action and its parameters
	 * @return the resolution outcome or a rejection with its reason
	 */
	public ActionOutcome submit(MOB actor, EncounterAction action);

	/**
	 * Convenience entry point submitting EndTurnAction through the shared resolver.
	 * @param actor the player requesting to end their turn
	 * @return the result, for the command to translate into player feedback
	 */
	public EndTurnResult endTurn(MOB actor);
}

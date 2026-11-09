package com.planet_ink.coffee_mud.Libraries.interfaces;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.party.PartyStore.PartySnapshot;

/** Connects CoffeeMud's player commands to party state for the current server run. */
public interface PartyLibrary extends CMLibrary
{
	/** The outcome of an attempt to create a party. */
	public enum CreateResult
	{
		/** The character created and now leads a party. */
		CREATED,
		/** The character already belongs to a party. */
		ALREADY_MEMBER,
		/** The actor is not a player character. */
		NOT_PLAYER,
		/** The actor must wait for combat to end. */
		IN_COMBAT
	}

	/**
	 * Recognizes a player character even between sessions.
	 *
	 * @param character the actor to check
	 * @return whether the actor has player stats
	 */
	public boolean isPlayerCharacter(MOB character);

	/**
	 * Creates a party with the player as leader and first member.
	 *
	 * @param character the player creating the party
	 * @return the result of the creation attempt
	 */
	public CreateResult createParty(MOB character);

	/**
	 * Looks up the player's current party.
	 *
	 * @param character the player to look up
	 * @return an immutable roster, or {@code null} if there is no party
	 */
	public PartySnapshot findParty(MOB character);

	/**
	 * Removes the player from their party, including during combat.
	 *
	 * @param character the player leaving the party
	 * @return whether the player left a party
	 */
	public boolean leaveParty(MOB character);

	/**
	 * Looks up an online player by exact character name and attempts to invite them.
	 * Reports the result to the sender and delivers a new invitation to its recipient.
	 *
	 * @param sender the player sending the invitation
	 * @param recipientName the character name supplied by the sender
	 */
	public void sendInvitation(MOB sender, String recipientName);

	/**
	 * Shows the player's live invitations, deadlines, and commands for accepting them.
	 *
	 * @param character the player checking their invitations
	 */
	public void showPendingInvitations(MOB character);

	/**
	 * Attempts to join using an invitation and reports the result to the player.
	 *
	 * @param character the player accepting an invitation
	 * @param invitationId the ID supplied by the player
	 */
	public void acceptInvitation(MOB character, long invitationId);
}

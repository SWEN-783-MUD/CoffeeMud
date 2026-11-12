package com.planet_ink.coffee_mud.Libraries.interfaces;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.planet_ink.coffee_mud.Common.interfaces.Session;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.party.ExplorationContext;
import com.planet_ink.coffee_mud.core.party.ExplorationContext.Activity;
import com.planet_ink.coffee_mud.core.party.PartyStore.PartySnapshot;
import com.planet_ink.coffee_mud.core.party.PartyTravel.Arrival;
import com.planet_ink.coffee_mud.core.party.PartyTravel.Step;

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
		IN_COMBAT,
		/** The store cannot allocate another party ID. */
		ID_LIMIT
	}

	/**
	 * Recognizes a player character even between sessions.
	 *
	 * @param character the actor to check
	 * @return whether the actor has player stats
	 */
	public boolean isPlayerCharacter(MOB character);

	/**
	 * Checks native combat and enrollment in the character's room encounter.
	 *
	 * @param player the actor attempting a player command
	 * @return whether the command's combat restrictions apply
	 */
	public boolean isCombatRestricted(MOB player);

	/**
	 * Publishes an encounter change under the same lock as party commands and player travel.
	 * The change may read party state, but must not send room messages.
	 *
	 * @param change the room manager's checked state change
	 * @return whether the change succeeded
	 */
	public boolean commitEncounterChange(BooleanSupplier change);

	/**
	 * Looks up a character's other online party members from the current roster.
	 *
	 * @param player the character whose roster to read
	 * @return a fixed list of online teammates at the time of the lookup
	 */
	public List<MOB> onlinePartyMembers(MOB player);

	/** The result of choosing an exploration activity. */
	public enum ActivitySelection
	{
		/** The choice is now active. */
		SELECTED,
		/** The character must wait until combat ends. */
		IN_COMBAT,
		/** The character is unavailable for a selection. */
		UNAVAILABLE
	}

	/**
	 * Reads a character's exploration context without changing it.
	 *
	 * @param player the character whose context is needed
	 * @return a fixed snapshot, or null if the character is unavailable
	 */
	public ExplorationContext explorationContext(MOB player);

	/**
	 * Selects an activity for one character. Combat blocks the change while
	 * leaving the current selection available for inspection.
	 *
	 * @param player the character making the choice
	 * @param activity the new activity
	 * @return the outcome of the selection
	 */
	public ActivitySelection selectExplorationActivity(MOB player, Activity activity);

	/**
	 * Resets one character to Normal while retaining their entry into the current room.
	 * The encounter owner calls this for each participant whose encounter ended.
	 *
	 * @param player the participant whose activity should reset
	 */
	public void resetExplorationActivity(MOB player);

	/** Reasons a player-to-player follow can or cannot go ahead. */
	public enum FollowResult
	{
		/** The link can be made. */
		ALLOWED(null),
		/** The player named their own character. */
		SELF("You cannot follow yourself. Use UNFOLLOW to stop following."),
		/** An existing link already points to this target. */
		ALREADY_FOLLOWING("You are already following that player."),
		/** The link changed while a detach command was being checked. */
		LINK_CHANGED("Your follow target changed. Try again."),
		/** Either character is leaving or has no active session. */
		OFFLINE("Both players must be online to start following."),
		/** The characters are no longer together. */
		NOT_HERE("You must be in the same room to follow that player."),
		/** The target and follower do not share a roster. */
		NOT_PARTY("You can only follow players in your party."),
		/** The follower is in combat. */
		IN_COMBAT("Wait until combat ends before changing who you follow."),
		/** The target or their follow leader has NOFOLLOW enabled. */
		REFUSED("That player or their group leader is not accepting followers."),
		/** The target cannot add another branch under CoffeeMud's follower limit. */
		FULL("That player cannot accept any more followers."),
		/** The proposed link would lead back to the follower or into a cycle. */
		CYCLE("That would create a follow loop.");

		private final String message;

		private FollowResult(final String message)
		{
			this.message = message;
		}

		/**
		 * Gives the caller the reason a link was refused.
		 *
		 * @return the feedback, or {@code null} if the action was allowed
		 */
		public String getMessage()
		{
			return message;
		}
	}

	/**
	 * Checks a player-to-player follow before sending it through room rules.
	 * The final write checks the same rules again.
	 *
	 * @param follower the character starting to follow
	 * @param target the player they want to follow
	 * @return whether the proposed link is allowed
	 */
	public FollowResult checkPlayerFollow(MOB follower, MOB target);

	/**
	 * Checks and writes a player's native link under the same lock as membership.
	 * The write must only update the native link and follower list.
	 *
	 * @param follower the character whose outgoing link changes
	 * @param target the new target, or {@code null} to detach
	 * @param write the native link update, returning false if its expected old link has changed
	 * @return the result of the attempted change
	 */
	public FollowResult changePlayerFollow(MOB follower, MOB target, BooleanSupplier write);

	/**
	 * Publishes a player's location under the same lock as follow validation.
	 * Room and directory callbacks run outside this operation.
	 *
	 * @param player the character being placed
	 * @param write the location field update, which returns the previous room
	 * @return the previous room
	 */
	public Room changePlayerLocation(MOB player, Supplier<Room> write);

	/**
	 * Places a player from a native directional exit outside a coordinated batch.
	 * The native movement caller still adds the character to the room and sends entry messages.
	 *
	 * @param player the character entering the room
	 * @param destination the actual room entered
	 * @param entrySide the known return direction, or {@link ExplorationContext#UNKNOWN_ENTRY}
	 */
	public void placePlayerFromExit(MOB player, Room destination, int entrySide);

	/**
	 * Captures an independent step and its current party branches.
	 *
	 * @param player the character entering the travel command
	 * @return the root step, or null if the character cannot start
	 */
	public Step captureTravel(MOB player);

	/**
	 * Checks that a captured character is still ready to leave the source room.
	 *
	 * @param step the captured character and follow link
	 * @param destination the intended room
	 * @return whether the character is still eligible to take the step
	 */
	public boolean canTravel(Step step, Room destination);

	/**
	 * Rechecks the step and removes the character from the source room before native leave messages.
	 *
	 * @param step the character about to leave
	 * @param destination the intended room
	 * @return whether the character was removed
	 */
	public boolean departTravel(Step step, Room destination);

	/**
	 * Rechecks the step after native leave messages and places the character.
	 * Independent movement clears any outgoing party link only on a room change.
	 *
	 * @param step the character about to enter
	 * @param destination the room approved by the native move
	 * @param entrySide the known return direction, or {@link ExplorationContext#UNKNOWN_ENTRY}
	 * @return the arrival captured at placement, or null if the step was refused
	 */
	public Arrival arriveTravel(Step step, Room destination, int entrySide);

	/**
	 * Rechecks a completed arrival before the encounter uses it.
	 *
	 * @param arrival the placement captured by the travel batch
	 * @return whether the same session still occupies that placement
	 */
	public boolean isCurrentArrival(Arrival arrival);

	/**
	 * Restores source room membership after an interrupted step if the same
	 * session is still there and has not moved.
	 *
	 * @param step the interrupted character
	 */
	public void restoreTravelSource(Step step);

	/**
	 * Drops a blocked follower's original outgoing link if it is still current.
	 *
	 * @param step the blocked follower and their captured link
	 * @return whether this call cleared the link
	 */
	public boolean detachBlockedFollower(Step step);

	/**
	 * Clears the departing player's direct party links and exploration choice.
	 * Blocks new links and activity changes until login.
	 *
	 * @param player the departing character
	 * @param session the session ending for that character
	 */
	public void playerSessionEnding(MOB player, Session session);

	/**
	 * Allows new links and starts a fresh exploration context when login finishes.
	 *
	 * @param player the character entering the game
	 * @param session the current session
	 */
	public void playerSessionStarted(MOB player, Session session);

	/**
	 * Commits a character rename and updates the live name and party state together.
	 * The directory supplies the checked database write, which runs while party
	 * commands wait. A failed write leaves the character and party state alone.
	 *
	 * @param character the character being renamed
	 * @param oldName the expected current name
	 * @param newName the new character name
	 * @param save the directory's name validation and database write
	 * @return whether the rename committed
	 */
	public boolean renameCharacter(MOB character, String oldName, String newName, BooleanSupplier save);

	/**
	 * Commits a character deletion and retires their party state before another
	 * character can use the same name in party commands.
	 *
	 * @param character the character being deleted
	 * @param remove the directory's checked database write, given the current name
	 * @return the result of the deletion attempt
	 */
	public DeleteResult deleteCharacter(MOB character, Predicate<String> remove);

	/** The result of deleting a character and retiring their party state. */
	public enum DeleteResult
	{
		/** The character row was removed and its party state retired. */
		DELETED,
		/** This object no longer represents an available player character. */
		UNAVAILABLE,
		/** The character row could not be removed. */
		SAVE_FAILED
	}

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
	 * Lets the current leader name another member as leader. A member can take
	 * over even if they are offline, since their membership is still active.
	 *
	 * @param leader the player giving up leadership
	 * @param memberName the exact character name of the member taking over
	 */
	public void transferLeadership(MOB leader, String memberName);

	/**
	 * Looks up an online player by exact character name and attempts to invite them.
	 * Reports the result to the sender and delivers a new invitation to its recipient.
	 *
	 * @param sender the player sending the invitation
	 * @param recipientName the character name supplied by the sender
	 */
	public void sendInvitation(MOB sender, String recipientName);

	/**
	 * Asks to join a party by its online leader's exact character name.
	 * Reports the result to the candidate and delivers a new request to the leader.
	 *
	 * @param candidate the player asking to join
	 * @param leaderName the character name supplied by the candidate
	 */
	public void requestJoin(MOB candidate, String leaderName);

	/**
	 * Shows the player's incoming and outgoing invitations and requests.
	 * Each entry includes its remaining time and available commands.
	 *
	 * @param character the player checking their pending entries
	 */
	public void showPendingEntries(MOB character);

	/**
	 * Attempts to join using an invitation and reports the result to the player.
	 *
	 * @param character the player accepting an invitation
	 * @param invitationId the ID supplied by the player
	 */
	public void acceptInvitation(MOB character, long invitationId);

	/**
	 * Lets the current leader approve a request and tells the candidate if online.
	 *
	 * @param leader the player approving the request
	 * @param requestId the ID supplied by the leader
	 */
	public void approveRequest(MOB leader, long requestId);

	/**
	 * Turns down an invitation or request if the player is allowed to respond.
	 *
	 * @param actor the player declining the entry
	 * @param entryId the ID supplied by the player
	 */
	public void declineEntry(MOB actor, long entryId);

	/**
	 * Withdraws an invitation or request if the player controls the sending side.
	 *
	 * @param actor the player cancelling the entry
	 * @param entryId the ID supplied by the player
	 */
	public void cancelEntry(MOB actor, long entryId);
}

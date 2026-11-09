package com.planet_ink.coffee_mud.core.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores party membership and pending invitations by character key.
 * Each change checks its rules and updates the roster in one operation.
 */
public interface PartyStore
{
	/**
	 * Creates a party led by the character and clears their pending invitations.
	 *
	 * @param characterKey the character creating the party
	 * @return the new roster, or {@code null} if the character already has a party
	 */
	PartySnapshot create(String characterKey);

	/**
	 * Looks up the party a character currently belongs to.
	 *
	 * @param characterKey the character to look up
	 * @return the immutable roster, or {@code null} if the character has no party
	 */
	PartySnapshot findByMember(String characterKey);

	/**
	 * Removes a member. When the leader leaves, the earliest remaining member
	 * becomes leader. When the last member leaves, the party and its invites are removed.
	 *
	 * @param characterKey the character leaving the party
	 * @return whether the character left a party
	 */
	boolean leave(String characterKey);

	/**
	 * Lets a leader invite a character who has no party. A repeat returns the
	 * existing invitation with its original deadline.
	 *
	 * @param leaderKey the character sending the invitation
	 * @param recipientKey the character being invited
	 * @return the outcome and the invitation when one is available
	 */
	InviteResult invite(String leaderKey, String recipientKey);

	/**
	 * Lists the character's live invitations in the order they were sent.
	 *
	 * @param characterKey the invited character
	 * @return an immutable list of invitations, which may be empty
	 */
	List<PartyInvitation> pendingInvitations(String characterKey);

	/**
	 * Checks the invitation and adds its recipient to the party in one operation.
	 * A successful join clears the character's other pending invitations.
	 *
	 * @param characterKey the character accepting the invitation
	 * @param invitationId the ID of the invitation to accept
	 * @return the outcome of the acceptance attempt
	 */
	AcceptResult acceptInvitation(String characterKey, long invitationId);

	/** The outcome of an attempt to send a party invitation. */
	enum InviteStatus
	{
		/** The store created a new invitation. */
		CREATED,
		/** An invitation to this party is already pending for the recipient. */
		ALREADY_PENDING,
		/** The sender does not lead a party. */
		NOT_LEADER,
		/** The recipient already belongs to a party. */
		ALREADY_MEMBER
	}

	/** The outcome of an attempt to accept a party invitation. */
	enum AcceptResult
	{
		/** The recipient joined the party. */
		JOINED,
		/** The character already belongs to a party. */
		ALREADY_MEMBER,
		/** The ID does not identify an invitation available to this character. */
		INVITATION_UNAVAILABLE,
		/** The invitation expired before acceptance. */
		EXPIRED,
		/** The party is full. The recipient can retry before the deadline. */
		PARTY_FULL
	}

	/** Pairs the invite outcome with the record needed for player feedback. */
	final class InviteResult
	{
		/** The outcome of the operation. */
		private final InviteStatus status;

		/** The invitation created or found by the operation, when available. */
		private final PartyInvitation invitation;

		/**
		 * Records the result of an invite attempt.
		 *
		 * @param status the operation's outcome
		 * @param invitation the created or existing invitation, or {@code null} on rejection
		 */
		public InviteResult(final InviteStatus status, final PartyInvitation invitation)
		{
			this.status = status;
			this.invitation = invitation;
		}

		/**
		 * Returns the outcome for choosing a player message.
		 *
		 * @return the invite status
		 */
		public InviteStatus getStatus()
		{
			return status;
		}

		/**
		 * Returns the invite details for a new or repeated invitation.
		 *
		 * @return the invitation, or {@code null} on rejection
		 */
		public PartyInvitation getInvitation()
		{
			return invitation;
		}
	}

	/**
	 * An immutable snapshot of a party, including its ID, leader, and members
	 * in join order. Callers can keep the snapshot after the store releases its lock.
	 */
	final class PartySnapshot
	{
		/** The unique ID of the party. */
		private final long id;

		/** The character key of the party leader. */
		private final String leader;

		/** An unmodifiable list of member keys in join order. */
		private final List<String> members;

		/**
		 * Copies the member list so the roster remains fixed when membership changes.
		 *
		 * @param id the unique ID of the party
		 * @param leader the character key of the party leader
		 * @param members the member keys in join order
		 */
		public PartySnapshot(final long id, final String leader, final List<String> members)
		{
			this.id = id;
			this.leader = leader;

			// Own a copy so changes to the caller's list cannot change this roster.
			this.members = Collections.unmodifiableList(new ArrayList<String>(members));
		}

		/**
		 * Identifies the party across roster updates.
		 *
		 * @return the party ID
		 */
		public long getId()
		{
			return id;
		}

		/**
		 * Identifies the leader at the time this roster was captured.
		 *
		 * @return the leader's character key
		 */
		public String getLeader()
		{
			return leader;
		}

		/**
		 * Lists the members as they were when this roster was captured.
		 *
		 * @return the unmodifiable list of member keys in join order
		 */
		public List<String> getMembers()
		{
			return members;
		}
	}
}

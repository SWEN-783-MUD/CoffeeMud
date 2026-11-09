package com.planet_ink.coffee_mud.core.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores party membership and pending entries by character key.
 * Each change checks its rules and updates the roster in one operation.
 */
public interface PartyStore
{
	/**
	 * Creates a party led by the character and clears their pending attempts to join elsewhere.
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
	 * becomes leader. When the last member leaves, the party and its pending entries are removed.
	 *
	 * @param characterKey the character leaving the party
	 * @return whether the character left a party
	 */
	boolean leave(String characterKey);

	/**
	 * Lets a leader invite a character who has no party. Returns any live
	 * invitation or request already open for this candidate and party.
	 *
	 * @param leaderKey the character sending the invitation
	 * @param recipientKey the character being invited
	 * @return the outcome and any new or existing entry
	 */
	PendingResult invite(String leaderKey, String recipientKey);

	/**
	 * Lets a character ask to join a party by naming its current leader.
	 * Keeps the original type, ID, and deadline if an entry is already open.
	 *
	 * @param candidateKey the character who wants to join
	 * @param leaderKey the named party leader
	 * @return the outcome and any new or existing entry
	 */
	PendingResult requestJoin(String candidateKey, String leaderKey);

	/**
	 * Lists live invitations the character can accept and live requests they
	 * can approve as leader, in the order the entries were opened.
	 *
	 * @param characterKey the character checking their pending entries
	 * @return an immutable list, which may be empty
	 */
	List<PartyPendingEntry> pendingEntries(String characterKey);

	/**
	 * Checks the invitation and adds its recipient to the party in one operation.
	 * A successful join clears the character's other pending entries.
	 *
	 * @param characterKey the character accepting the invitation
	 * @param invitationId the ID of the invitation to accept
	 * @return the outcome of the acceptance attempt
	 */
	JoinResult acceptInvitation(String characterKey, long invitationId);

	/**
	 * Lets the current leader approve a request and add the candidate in one
	 * operation. A successful join clears the candidate's other pending entries.
	 *
	 * @param leaderKey the character approving the request
	 * @param requestId the ID of the request to approve
	 * @return the outcome of the approval attempt
	 */
	JoinResult approveRequest(String leaderKey, long requestId);

	/** The outcome of an attempt to open a pending entry. */
	enum PendingStatus
	{
		/** The store opened a new entry. */
		CREATED,
		/** This party and candidate already have an open invitation or request. */
		ALREADY_PENDING,
		/** The named leader does not currently lead a party. */
		NOT_LEADER,
		/** The candidate already belongs to a party. */
		ALREADY_MEMBER
	}

	/** The outcome of an acceptance or approval. */
	enum JoinStatus
	{
		/** The candidate joined the party. */
		JOINED,
		/** The candidate already belongs to a party. */
		ALREADY_MEMBER,
		/** The ID does not identify an entry of the right type that this actor can use. */
		ENTRY_UNAVAILABLE,
		/** The entry reached its deadline before acceptance or approval. */
		EXPIRED,
		/** The party is full. The actor can retry before the deadline. */
		PARTY_FULL
	}

	/** Pairs the opening outcome with the entry needed for player feedback. */
	final class PendingResult
	{
		/** The outcome of the operation. */
		private final PendingStatus status;

		/** The entry created or found by the operation, when available. */
		private final PartyPendingEntry entry;

		/**
		 * Records the result of opening an invitation or request.
		 *
		 * @param status the operation's outcome
		 * @param entry the new or existing entry, or {@code null} on rejection
		 */
		public PendingResult(final PendingStatus status, final PartyPendingEntry entry)
		{
			this.status = status;
			this.entry = entry;
		}

		/**
		 * Returns the outcome for choosing a player message.
		 *
		 * @return the opening status
		 */
		public PendingStatus getStatus()
		{
			return status;
		}

		/**
		 * Returns the entry, which may have been opened by the other side.
		 *
		 * @return the entry, or {@code null} on rejection
		 */
		public PartyPendingEntry getEntry()
		{
			return entry;
		}
	}

	/** Reports an admission attempt and identifies the member added on success. */
	final class JoinResult
	{
		/** The outcome of the operation. */
		private final JoinStatus status;

		/** The character added to the party, when the join succeeds. */
		private final String joinedCandidateKey;

		/**
		 * Records a join outcome so the caller can report it after the store releases its lock.
		 *
		 * @param status the operation's outcome
		 * @param joinedCandidateKey the member added to the roster, or {@code null} on failure
		 */
		public JoinResult(final JoinStatus status, final String joinedCandidateKey)
		{
			this.status = status;
			this.joinedCandidateKey = joinedCandidateKey;
		}

		/**
		 * Returns the outcome for choosing a player message.
		 *
		 * @return the join status
		 */
		public JoinStatus getStatus()
		{
			return status;
		}

		/**
		 * Identifies the candidate for an online notification after approval.
		 *
		 * @return the new member's key, or {@code null} if no one joined
		 */
		public String getJoinedCandidateKey()
		{
			return joinedCandidateKey;
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

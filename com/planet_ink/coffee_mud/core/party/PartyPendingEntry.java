package com.planet_ink.coffee_mud.core.party;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable invitation or request for a character to join a party.
 * The party owns the entry, so a change of leader does not replace it.
 */
public final class PartyPendingEntry
{
	/** How this entry started and which side can complete the join. */
	public enum Type
	{
		/** The leader invited a character, who can accept. */
		INVITATION,
		/** A character asked to join, and the current leader can approve. */
		REQUEST
	}

	/** The ID used to act on this entry. */
	private final long id;

	/** The party the candidate would join. */
	private final long partyId;

	/** The kind of entry originally opened. */
	private final Type type;

	/** The character who sent the invitation or request. */
	private final String senderKey;

	/** The character who would join if this entry succeeds. */
	private final String candidateKey;

	/** The first instant at which the entry is expired. */
	private final Instant expiresAt;

	/**
	 * Creates an entry with a deadline that stays fixed for its lifetime.
	 *
	 * @param id the ID assigned by the store
	 * @param partyId the party that owns the entry
	 * @param type whether the leader or candidate started the entry
	 * @param senderKey the original sender's character key
	 * @param candidateKey the character who would join
	 * @param expiresAt the first instant at which joining is too late
	 */
	public PartyPendingEntry(final long id, final long partyId, final Type type,
			final String senderKey, final String candidateKey, final Instant expiresAt)
	{
		this.id = id;
		this.partyId = partyId;
		this.type = Objects.requireNonNull(type);
		this.senderKey = Objects.requireNonNull(senderKey);
		this.candidateKey = Objects.requireNonNull(candidateKey);
		this.expiresAt = Objects.requireNonNull(expiresAt);
	}

	/**
	 * Returns the ID shown with the entry.
	 *
	 * @return the pending entry ID
	 */
	public long getId()
	{
		return id;
	}

	/**
	 * Identifies the party even if its leader changes.
	 *
	 * @return the party ID
	 */
	public long getPartyId()
	{
		return partyId;
	}

	/**
	 * Identifies the action that opened this entry.
	 *
	 * @return the original entry type
	 */
	public Type getType()
	{
		return type;
	}

	/**
	 * Identifies the original sender for player messages.
	 *
	 * @return the sender's character key
	 */
	public String getSenderKey()
	{
		return senderKey;
	}

	/**
	 * Identifies the character seeking membership.
	 *
	 * @return the candidate's character key
	 */
	public String getCandidateKey()
	{
		return candidateKey;
	}

	/**
	 * Returns the deadline so callers can show the time remaining.
	 *
	 * @return the expiration instant
	 */
	public Instant getExpiresAt()
	{
		return expiresAt;
	}

	/**
	 * Checks whether the given time has reached or passed the deadline.
	 *
	 * @param now the current time from the store's clock
	 * @return whether the entry is expired
	 */
	boolean isExpired(final Instant now)
	{
		return !now.isBefore(expiresAt);
	}
}

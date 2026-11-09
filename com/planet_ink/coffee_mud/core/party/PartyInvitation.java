package com.planet_ink.coffee_mud.core.party;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable invitation from a party leader to another character.
 * The party owns the invitation, so a new leader can take over while it is pending.
 */
public final class PartyInvitation
{
	/** The invitation ID used by the recipient to accept. */
	private final long id;

	/** The party the recipient would join. */
	private final long partyId;

	/** The character who sent the invitation. */
	private final String inviterKey;

	/** The character allowed to accept it. */
	private final String recipientKey;

	/** The first instant at which the invitation is expired. */
	private final Instant expiresAt;

	/**
	 * Creates an invitation with a deadline that stays fixed for its lifetime.
	 *
	 * @param id the ID assigned by the store
	 * @param partyId the party that owns this invitation
	 * @param inviterKey the original sender's character key
	 * @param recipientKey the invited character's key
	 * @param expiresAt the first instant at which acceptance is too late
	 */
	public PartyInvitation(final long id, final long partyId, final String inviterKey,
			final String recipientKey, final Instant expiresAt)
	{
		this.id = id;
		this.partyId = partyId;
		this.inviterKey = Objects.requireNonNull(inviterKey);
		this.recipientKey = Objects.requireNonNull(recipientKey);
		this.expiresAt = Objects.requireNonNull(expiresAt);
	}

	/**
	 * Returns the ID shown with the invitation.
	 *
	 * @return the invitation ID
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
	 * Identifies the original sender for the invitation message.
	 *
	 * @return the inviter's character key
	 */
	public String getInviterKey()
	{
		return inviterKey;
	}

	/**
	 * Identifies the character the invitation was sent to.
	 *
	 * @return the recipient's character key
	 */
	public String getRecipientKey()
	{
		return recipientKey;
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
	 * @return whether the invitation is expired
	 */
	boolean isExpired(final Instant now)
	{
		return !now.isBefore(expiresAt);
	}
}

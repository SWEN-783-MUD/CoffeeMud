package com.planet_ink.coffee_mud.core.party;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Keeps party rosters and invitations for the current server run.
 * One store lock protects the roster, member lookup, and invitation state.
 */
public final class InMemoryPartyStore implements PartyStore
{
	/** The usual time allowed to accept an invitation. */
	private static final Duration DEFAULT_INVITATION_LIFETIME = Duration.ofMinutes(2);

	/** The usual number of members, including the leader. */
	private static final int DEFAULT_MEMBER_LIMIT = 6;

	/** The clock used to issue and check invitation deadlines. */
	private final Supplier<Instant> clock;

	/** How long a new invitation stays open. */
	private final Duration invitationLifetime;

	/** The maximum number of members in a party. */
	private final int memberLimit;

	/** The current immutable roster for each party ID. */
	private final Map<Long, PartySnapshot> partiesById = new HashMap<Long, PartySnapshot>();

	/** The party ID for each character who belongs to a party. */
	private final Map<String, Long> partyIdsByMember = new HashMap<String, Long>();

	/** Invitations that have not been retired, in the order they were sent. */
	private final Map<Long, PartyInvitation> invitationsById = new LinkedHashMap<Long, PartyInvitation>();

	/** The next party ID to assign. A recreated party gets a new ID. */
	private long nextPartyId = 1;

	/** The next invitation ID. Retiring an invite does not free its ID for reuse. */
	private long nextInvitationId = 1;

	/** Creates a store with the server clock and the default deadline and capacity. */
	public InMemoryPartyStore()
	{
		this(Instant::now, DEFAULT_INVITATION_LIFETIME, DEFAULT_MEMBER_LIMIT);
	}

	/**
	 * Creates an empty store with the given invitation lifetime and party size.
	 * Tests can supply a clock they control to check deadlines without waiting.
	 *
	 * @param clock supplies the current time while the store lock is held
	 * @param invitationLifetime how long each new invitation stays open
	 * @param memberLimit the maximum number of members, including the leader
	 * @throws IllegalArgumentException if the lifetime or member limit is not positive
	 * @throws NullPointerException if the clock or lifetime is null
	 */
	public InMemoryPartyStore(final Supplier<Instant> clock, final Duration invitationLifetime,
			final int memberLimit)
	{
		this.clock = Objects.requireNonNull(clock);
		this.invitationLifetime = Objects.requireNonNull(invitationLifetime);

		// Every invitation needs a deadline and every party needs room for its leader.
		if(invitationLifetime.isZero() || invitationLifetime.isNegative())
			throw new IllegalArgumentException("Invitation lifetime must be positive.");
		if(memberLimit < 1)
			throw new IllegalArgumentException("Party member limit must be positive.");
		this.memberLimit = memberLimit;
	}

	/**
	 * Creates a one-member party and makes the character its leader.
	 * Checks membership and updates both lookups under the store lock.
	 *
	 * @param characterKey the stable character key
	 * @return the immutable roster, or {@code null} if the character already has a party
	 * @throws NullPointerException if the character key is null
	 */
	@Override
	public synchronized PartySnapshot create(final String characterKey)
	{
		Objects.requireNonNull(characterKey);
		if(partyIdsByMember.containsKey(characterKey))
			return null;

		// Store the roster once and link the creator to it by ID.
		final Long partyId = Long.valueOf(nextPartyId++);
		final PartySnapshot party = new PartySnapshot(partyId.longValue(), characterKey,
				Collections.singletonList(characterKey));
		partiesById.put(partyId, party);
		partyIdsByMember.put(characterKey, partyId);
		clearIncomingInvitations(characterKey);
		return party;
	}

	/**
	 * Finds the current roster using the character's party ID.
	 * The caller can read the immutable roster after the store lock is released.
	 *
	 * @param characterKey the stable character key
	 * @return the current roster, or {@code null} if the character has no party
	 * @throws NullPointerException if the character key is null
	 */
	@Override
	public synchronized PartySnapshot findByMember(final String characterKey)
	{
		final Long partyId = partyIdsByMember.get(Objects.requireNonNull(characterKey));
		return partyId == null ? null : partiesById.get(partyId);
	}

	/**
	 * Removes the character and updates the roster under the store lock.
	 * An empty party is removed along with its invitations. A party that stays
	 * open keeps its invitations, even if another member becomes leader.
	 *
	 * @param characterKey the stable character key
	 * @return {@code true} if the character left a party
	 * @throws NullPointerException if the character key is null
	 */
	@Override
	public synchronized boolean leave(final String characterKey)
	{
		final Long partyId = partyIdsByMember.get(Objects.requireNonNull(characterKey));
		if(partyId == null)
			return false;

		final PartySnapshot party = partiesById.get(partyId);
		final List<String> remaining = new ArrayList<String>(party.getMembers());
		remaining.remove(characterKey);

		if(remaining.isEmpty())
		{
			// Retire the party and every invite that could send someone to it.
			partiesById.remove(partyId);
			invitationsById.values().removeIf(invite -> invite.getPartyId() == partyId.longValue());
		}
		else
		{
			// Keep join order so the first remaining member can take over.
			final String leader = characterKey.equals(party.getLeader()) ? remaining.get(0) : party.getLeader();
			partiesById.put(partyId, new PartySnapshot(party.getId(), leader, remaining));
		}

		partyIdsByMember.remove(characterKey);
		return true;
	}

	/**
	 * Checks the sender's authority and creates or finds an invitation under one lock.
	 * An invitation can be sent while the party is full because seats are checked at acceptance.
	 *
	 * @param leaderKey the character sending the invitation
	 * @param recipientKey the character being invited
	 * @return the outcome and any new or existing invitation
	 * @throws NullPointerException if either character key is null
	 */
	@Override
	public synchronized InviteResult invite(final String leaderKey, final String recipientKey)
	{
		Objects.requireNonNull(recipientKey);
		final PartySnapshot party = findByMember(leaderKey);
		if((party == null) || !leaderKey.equals(party.getLeader()))
			return new InviteResult(InviteStatus.NOT_LEADER, null);
		if(partyIdsByMember.containsKey(recipientKey))
			return new InviteResult(InviteStatus.ALREADY_MEMBER, null);

		// Keep the first live invite for this party and recipient.
		final Instant now = clock.get();
		removeExpiredInvitations(now);
		for(final PartyInvitation invite : invitationsById.values())
		{
			if((invite.getPartyId() == party.getId()) && recipientKey.equals(invite.getRecipientKey()))
				return new InviteResult(InviteStatus.ALREADY_PENDING, invite);
		}

		final long id = nextInvitationId++;
		final PartyInvitation invite = new PartyInvitation(id, party.getId(), leaderKey, recipientKey,
				now.plus(invitationLifetime));
		invitationsById.put(Long.valueOf(id), invite);
		return new InviteResult(InviteStatus.CREATED, invite);
	}

	/**
	 * Removes expired records and copies the character's remaining invitations.
	 * The returned list can be read after the store lock is released.
	 *
	 * @param characterKey the invited character
	 * @return an immutable list in invitation order
	 * @throws NullPointerException if the character key is null
	 */
	@Override
	public synchronized List<PartyInvitation> pendingInvitations(final String characterKey)
	{
		Objects.requireNonNull(characterKey);
		removeExpiredInvitations(clock.get());

		final List<PartyInvitation> pending = new ArrayList<PartyInvitation>();
		for(final PartyInvitation invite : invitationsById.values())
		{
			if(characterKey.equals(invite.getRecipientKey()))
				pending.add(invite);
		}
		return Collections.unmodifiableList(pending);
	}

	/**
	 * Checks membership, recipient, deadline, and capacity under one lock.
	 * On success the roster and member lookup are updated together and the
	 * character's pending invitations are cleared. A full party leaves the invite open.
	 *
	 * @param characterKey the character accepting the invitation
	 * @param invitationId the invitation to accept
	 * @return the outcome of the attempt
	 * @throws NullPointerException if the character key is null
	 */
	@Override
	public synchronized AcceptResult acceptInvitation(final String characterKey, final long invitationId)
	{
		Objects.requireNonNull(characterKey);
		if(partyIdsByMember.containsKey(characterKey))
			return AcceptResult.ALREADY_MEMBER;

		// Check the recipient before changing the invitation, even if it has expired.
		final Long inviteId = Long.valueOf(invitationId);
		final PartyInvitation invite = invitationsById.get(inviteId);
		if((invite == null) || !characterKey.equals(invite.getRecipientKey()))
			return AcceptResult.INVITATION_UNAVAILABLE;
		if(invite.isExpired(clock.get()))
		{
			invitationsById.remove(inviteId);
			return AcceptResult.EXPIRED;
		}

		// Check the current roster so competing acceptances cannot overfill it.
		final Long partyId = Long.valueOf(invite.getPartyId());
		final PartySnapshot party = partiesById.get(partyId);
		if(party == null)
			return AcceptResult.INVITATION_UNAVAILABLE;
		if(party.getMembers().size() >= memberLimit)
			return AcceptResult.PARTY_FULL;

		// Publish the new roster and retire every invite this character could use.
		final List<String> members = new ArrayList<String>(party.getMembers());
		members.add(characterKey);
		partiesById.put(partyId, new PartySnapshot(party.getId(), party.getLeader(), members));
		partyIdsByMember.put(characterKey, partyId);
		clearIncomingInvitations(characterKey);
		return AcceptResult.JOINED;
	}

	/**
	 * Retires every invitation with a deadline at or before the given time.
	 * Call this while holding the store lock.
	 *
	 * @param now the current time for this operation
	 */
	private void removeExpiredInvitations(final Instant now)
	{
		invitationsById.values().removeIf(invite -> invite.isExpired(now));
	}

	/**
	 * Retires a character's invitations after they create or join a party.
	 * Call this while holding the store lock.
	 *
	 * @param characterKey the character whose invites are no longer usable
	 */
	private void clearIncomingInvitations(final String characterKey)
	{
		invitationsById.values().removeIf(invite -> characterKey.equals(invite.getRecipientKey()));
	}
}

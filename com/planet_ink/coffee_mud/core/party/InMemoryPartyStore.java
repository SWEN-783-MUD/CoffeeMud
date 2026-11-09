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

import com.planet_ink.coffee_mud.core.party.PartyPendingEntry.Type;

/**
 * Keeps party rosters and pending entries for the current server run.
 * One store lock protects the roster, member lookup, and pending state.
 */
public final class InMemoryPartyStore implements PartyStore
{
	/** The usual time allowed to accept an invitation or approve a request. */
	public static final Duration DEFAULT_PENDING_LIFETIME = Duration.ofMinutes(2);

	/** The usual number of members, including the leader. */
	public static final int DEFAULT_MEMBER_LIMIT = 6;

	/** The clock used to issue and check deadlines. */
	private final Supplier<Instant> clock;

	/** How long a new pending entry stays open. */
	private final Duration pendingLifetime;

	/** The maximum number of members in a party. */
	private final int memberLimit;

	/** The current immutable roster for each party ID. */
	private final Map<Long, PartySnapshot> partiesById = new HashMap<Long, PartySnapshot>();

	/** The party ID for each character who belongs to a party. */
	private final Map<String, Long> partyIdsByMember = new HashMap<String, Long>();

	/** Entries that have not been retired, in the order they were opened. */
	private final Map<Long, PartyPendingEntry> entriesById = new LinkedHashMap<Long, PartyPendingEntry>();

	/** The next party ID to assign. A recreated party gets a new ID. */
	private long nextPartyId = 1;

	/** The next pending ID. Retiring an entry does not free its ID for reuse. */
	private long nextEntryId = 1;

	/** Creates a store with the server clock and the default deadline and capacity. */
	public InMemoryPartyStore()
	{
		this(Instant::now, DEFAULT_PENDING_LIFETIME, DEFAULT_MEMBER_LIMIT);
	}

	/**
	 * Creates an empty store with the given pending lifetime and party size.
	 * Tests can supply a clock they control to check deadlines without waiting.
	 *
	 * @param clock supplies the current time while the store lock is held
	 * @param pendingLifetime how long each new entry stays open
	 * @param memberLimit the maximum number of members, including the leader
	 * @throws IllegalArgumentException if the lifetime or member limit is not positive
	 * @throws NullPointerException if the clock or lifetime is null
	 */
	public InMemoryPartyStore(final Supplier<Instant> clock, final Duration pendingLifetime,
			final int memberLimit)
	{
		this.clock = Objects.requireNonNull(clock);
		this.pendingLifetime = Objects.requireNonNull(pendingLifetime);

		// Every entry needs a deadline and every party needs room for its leader.
		if(pendingLifetime.isZero() || pendingLifetime.isNegative())
			throw new IllegalArgumentException("Pending lifetime must be positive.");
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
		clearCandidateEntries(characterKey);
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
	 * An empty party is removed along with its pending entries. A party that
	 * stays open keeps its entries, even if another member becomes leader.
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
			// Retire the party and every entry that could send someone to it.
			partiesById.remove(partyId);
			entriesById.values().removeIf(entry -> entry.getPartyId() == partyId.longValue());
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

	/** {@inheritDoc} */
	@Override
	public synchronized void renameCharacter(final String oldKey, final String newKey)
	{
		Objects.requireNonNull(oldKey);
		Objects.requireNonNull(newKey);
		if(oldKey.equals(newKey))
			return;

		// Replace the member in place so join order and the party ID stay intact.
		final Long partyId = partyIdsByMember.remove(oldKey);
		if(partyId != null)
		{
			final PartySnapshot party = partiesById.get(partyId);
			final List<String> members = new ArrayList<String>(party.getMembers());
			members.set(members.indexOf(oldKey), newKey);
			final String leader = oldKey.equals(party.getLeader()) ? newKey : party.getLeader();
			partiesById.put(partyId, new PartySnapshot(party.getId(), leader, members));
			partyIdsByMember.put(newKey, partyId);
		}

		// Change the names on the original IDs without reopening or moving entries.
		for(final Map.Entry<Long, PartyPendingEntry> record : entriesById.entrySet())
		{
			final PartyPendingEntry entry = record.getValue();
			final String sender = oldKey.equals(entry.getSenderKey()) ? newKey : entry.getSenderKey();
			final String candidate = oldKey.equals(entry.getCandidateKey()) ? newKey : entry.getCandidateKey();
			if(!sender.equals(entry.getSenderKey()) || !candidate.equals(entry.getCandidateKey()))
				record.setValue(entry.withCharacters(sender, candidate));
		}
	}

	/** {@inheritDoc} */
	@Override
	public synchronized void deleteCharacter(final String characterKey)
	{
		// Use ordinary succession, then retire every attempt to admit this character.
		leave(characterKey);
		clearCandidateEntries(characterKey);

		// Surviving invites now name the current leader so a reused name cannot inherit their authorship.
		for(final Map.Entry<Long, PartyPendingEntry> record : entriesById.entrySet())
		{
			final PartyPendingEntry entry = record.getValue();
			if(characterKey.equals(entry.getSenderKey()))
			{
				final PartySnapshot party = partiesById.get(Long.valueOf(entry.getPartyId()));
				record.setValue(entry.withCharacters(party.getLeader(), entry.getCandidateKey()));
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * Checks both members and publishes the new leader under the store lock.
	 *
	 * @throws NullPointerException if either character key is null
	 */
	@Override
	public synchronized TransferResult transferLeadership(final String leaderKey, final String memberKey)
	{
		Objects.requireNonNull(memberKey);
		final PartySnapshot party = findByMember(leaderKey);
		if((party == null) || !leaderKey.equals(party.getLeader()))
			return TransferResult.NOT_LEADER;
		if(leaderKey.equals(memberKey))
			return TransferResult.ALREADY_LEADER;
		if(!party.getMembers().contains(memberKey))
			return TransferResult.NOT_MEMBER;

		// Keep the same party ID and member order so pending entries carry over.
		final long id = party.getId();
		partiesById.put(Long.valueOf(id), new PartySnapshot(id, memberKey, party.getMembers()));
		return TransferResult.TRANSFERRED;
	}

	/**
	 * {@inheritDoc}
	 * Checks and opens the entry under the store lock. A full party can still receive pending entries.
	 *
	 * @throws NullPointerException if either character key is null
	 */
	@Override
	public synchronized PendingResult invite(final String leaderKey, final String recipientKey)
	{
		return openEntry(leaderKey, recipientKey, Type.INVITATION);
	}

	/**
	 * {@inheritDoc}
	 * Uses the same store lock and deadline rules as invitations.
	 *
	 * @throws NullPointerException if either character key is null
	 */
	@Override
	public synchronized PendingResult requestJoin(final String candidateKey, final String leaderKey)
	{
		return openEntry(leaderKey, candidateKey, Type.REQUEST);
	}

	/**
	 * Checks the party and candidate before opening a pending entry.
	 * Call this while holding the store lock so two starts cannot create separate entries.
	 *
	 * @param leaderKey the leader supplied by the caller
	 * @param candidateKey the character who would join
	 * @param type whether the leader or candidate started the entry
	 * @return the outcome and any new or existing entry
	 * @throws NullPointerException if either character key is null
	 */
	private PendingResult openEntry(final String leaderKey, final String candidateKey, final Type type)
	{
		Objects.requireNonNull(candidateKey);
		final PartySnapshot party = findByMember(leaderKey);
		if((party == null) || !leaderKey.equals(party.getLeader()))
			return new PendingResult(PendingStatus.NOT_LEADER, null);
		if(partyIdsByMember.containsKey(candidateKey))
			return new PendingResult(PendingStatus.ALREADY_MEMBER, null);

		// Keep the first live entry even when the two sides used different commands.
		final Instant now = clock.get();
		removeExpiredEntries(now);
		for(final PartyPendingEntry entry : entriesById.values())
		{
			if((entry.getPartyId() == party.getId()) && candidateKey.equals(entry.getCandidateKey()))
				return new PendingResult(PendingStatus.ALREADY_PENDING, entry);
		}

		final String senderKey = type == Type.INVITATION ? leaderKey : candidateKey;
		final PartyPendingEntry entry = new PartyPendingEntry(nextEntryId++, party.getId(), type,
				senderKey, candidateKey, now.plus(pendingLifetime));
		entriesById.put(Long.valueOf(entry.getId()), entry);
		return new PendingResult(PendingStatus.CREATED, entry);
	}

	/**
	 * Removes expired records and copies the entries this character can act on.
	 * The returned list can be read after the store lock is released.
	 *
	 * @param characterKey the character looking up their entries
	 * @return an immutable list in creation order
	 * @throws NullPointerException if the character key is null
	 */
	@Override
	public synchronized List<PendingView> pendingEntries(final String characterKey)
	{
		Objects.requireNonNull(characterKey);
		removeExpiredEntries(clock.get());

		final List<PendingView> pending = new ArrayList<PendingView>();
		for(final PartyPendingEntry entry : entriesById.values())
		{
			final PartySnapshot party = partiesById.get(Long.valueOf(entry.getPartyId()));
			if((party != null) && (characterKey.equals(entry.getCandidateKey()) || characterKey.equals(party.getLeader())))
				pending.add(new PendingView(entry, party.getLeader()));
		}
		return Collections.unmodifiableList(pending);
	}

	/**
	 * {@inheritDoc}
	 * The current membership check also covers retries after a successful acceptance.
	 *
	 * @throws NullPointerException if the character key is null
	 */
	@Override
	public synchronized JoinResult acceptInvitation(final String characterKey, final long invitationId)
	{
		Objects.requireNonNull(characterKey);
		if(partyIdsByMember.containsKey(characterKey))
			return new JoinResult(JoinStatus.ALREADY_MEMBER, null);
		return join(characterKey, invitationId, Type.INVITATION);
	}

	/**
	 * {@inheritDoc}
	 * Checks authority against the current roster under the store lock.
	 *
	 * @throws NullPointerException if the leader key is null
	 */
	@Override
	public synchronized JoinResult approveRequest(final String leaderKey, final long requestId)
	{
		return join(Objects.requireNonNull(leaderKey), requestId, Type.REQUEST);
	}

	/**
	 * Validates the entry and updates both membership lookups under the store lock.
	 * On success all competing entries are retired. A full party leaves the entry open.
	 *
	 * @param actorKey the character acting on the entry
	 * @param entryId the ID supplied by the actor
	 * @param type the type accepted by the command
	 * @return the outcome and the new member key on success
	 */
	private JoinResult join(final String actorKey, final long entryId, final Type type)
	{
		// Check authority before inspecting expiry so someone else's ID reveals no details.
		final Long id = Long.valueOf(entryId);
		final PartyPendingEntry entry = entriesById.get(id);
		if((entry == null) || (entry.getType() != type))
			return new JoinResult(JoinStatus.ENTRY_UNAVAILABLE, null);

		final Long partyId = Long.valueOf(entry.getPartyId());
		final PartySnapshot party = partiesById.get(partyId);
		if((party == null) || !actorKey.equals(responderKey(entry, party)))
			return new JoinResult(JoinStatus.ENTRY_UNAVAILABLE, null);
		if(entry.isExpired(clock.get()))
		{
			entriesById.remove(id);
			return new JoinResult(JoinStatus.EXPIRED, null);
		}

		// Check the current roster so competing acceptances cannot overfill it.
		final String candidateKey = entry.getCandidateKey();
		if(partyIdsByMember.containsKey(candidateKey))
			return new JoinResult(JoinStatus.ALREADY_MEMBER, null);
		if(party.getMembers().size() >= memberLimit)
			return new JoinResult(JoinStatus.PARTY_FULL, null);

		// Publish the new roster and retire every other attempt by the new member.
		final List<String> members = new ArrayList<String>(party.getMembers());
		members.add(candidateKey);
		partiesById.put(partyId, new PartySnapshot(party.getId(), party.getLeader(), members));
		partyIdsByMember.put(candidateKey, partyId);
		clearCandidateEntries(candidateKey);
		return new JoinResult(JoinStatus.JOINED, candidateKey);
	}

	/** {@inheritDoc} */
	@Override
	public synchronized PendingView decline(final String actorKey, final long entryId)
	{
		return closeEntry(actorKey, entryId, true);
	}

	/** {@inheritDoc} */
	@Override
	public synchronized PendingView cancel(final String actorKey, final long entryId)
	{
		return closeEntry(actorKey, entryId, false);
	}

	/**
	 * Retires a live entry while holding the same lock used for acceptance and approval.
	 * An unavailable result gives no details about another character's entry.
	 *
	 * @param actorKey the character acting on the entry
	 * @param entryId the ID supplied by the actor
	 * @param decline whether the actor is responding or withdrawing
	 * @return the closed entry, or {@code null} if the actor cannot close it
	 */
	private PendingView closeEntry(final String actorKey, final long entryId, final boolean decline)
	{
		Objects.requireNonNull(actorKey);
		final Long id = Long.valueOf(entryId);
		final PartyPendingEntry entry = entriesById.get(id);
		if(entry == null)
			return null;

		final PartySnapshot party = partiesById.get(Long.valueOf(entry.getPartyId()));
		if(party == null)
			return null;

		// A party owns its invitations, so the current leader can withdraw them.
		final String initiator = entry.getType() == Type.INVITATION ? party.getLeader() : entry.getCandidateKey();
		final String allowedActor = decline ? responderKey(entry, party) : initiator;
		if(!actorKey.equals(allowedActor))
			return null;

		final boolean expired = entry.isExpired(clock.get());
		entriesById.remove(id);
		return expired ? null : new PendingView(entry, party.getLeader());
	}

	/**
	 * Identifies who can accept, approve, or decline the entry.
	 *
	 * @param entry the invitation or request
	 * @param party the current roster of the party that owns the entry
	 * @return the candidate for an invitation or the current leader for a request
	 */
	private String responderKey(final PartyPendingEntry entry, final PartySnapshot party)
	{
		return entry.getType() == Type.INVITATION ? entry.getCandidateKey() : party.getLeader();
	}

	/**
	 * Retires every entry with a deadline at or before the given time.
	 * Call this while holding the store lock.
	 *
	 * @param now the current time for this operation
	 */
	private void removeExpiredEntries(final Instant now)
	{
		entriesById.values().removeIf(entry -> entry.isExpired(now));
	}

	/**
	 * Retires a candidate's entries after they create or join a party.
	 * Call this while holding the store lock.
	 *
	 * @param characterKey the candidate whose entries are no longer usable
	 */
	private void clearCandidateEntries(final String characterKey)
	{
		entriesById.values().removeIf(entry -> characterKey.equals(entry.getCandidateKey()));
	}
}

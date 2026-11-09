package com.planet_ink.coffee_mud.core.party;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps party rosters and membership for the current server run.
 * Reads and writes use the same store lock so the two lookups agree.
 */
public final class InMemoryPartyStore implements PartyStore
{
	/** The current immutable roster for each party ID. */
	private final Map<Long, PartySnapshot> partiesById = new HashMap<Long, PartySnapshot>();

	/** The party ID for each character who belongs to a party. */
	private final Map<String, Long> partyIdsByMember = new HashMap<String, Long>();

	/** The next party ID to assign. A recreated party gets a new ID. */
	private long nextPartyId = 1;

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
	 * Removes the character and their now-empty party under the store lock.
	 * Party creation is currently the only way to become a member.
	 *
	 * @param characterKey the stable character key
	 * @return {@code true} if the character left a party
	 * @throws NullPointerException if the character key is null
	 */
	@Override
	public synchronized boolean leave(final String characterKey)
	{
		final Long partyId = partyIdsByMember.remove(Objects.requireNonNull(characterKey));
		if(partyId == null)
			return false;

		partiesById.remove(partyId);
		return true;
	}
}

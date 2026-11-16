package com.planet_ink.coffee_mud.Tests;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.party.InMemoryPartyStore;
import com.planet_ink.coffee_mud.core.party.PartyPendingEntry;
import com.planet_ink.coffee_mud.core.party.PartyStore;
import com.planet_ink.coffee_mud.core.party.PartyStore.JoinResult;
import com.planet_ink.coffee_mud.core.party.PartyStore.JoinStatus;

/** Checks exact-name admission, ambiguous senders, and mixed name/ID races. */
public class PartySelectorTest extends StdTest
{
	/** {@inheritDoc} */
	@Override
	public String ID()
	{
		return "PartySelectorTest";
	}

	/** {@inheritDoc} */
	@Override
	public String[] getTestGroups()
	{
		return new String[] {"all", "party"};
	}

	/** {@inheritDoc} */
	@Override
	public String doTest(final MOB mob, final int metaFlags, final String what, final List<String> commands)
	{
		try
		{
			runCases();
			return null;
		}
		catch(final InterruptedException error)
		{
			Thread.currentThread().interrupt();
			return "Party selector test interrupted.";
		}
		catch(final AssertionError | Exception error)
		{
			return error.toString();
		}
	}

	/**
	 * Runs name selection against the store without starting a server.
	 *
	 * @param args unused command line arguments
	 * @throws Exception if a check or competing worker fails
	 */
	public static void main(final String[] args) throws Exception
	{
		runCases();
		System.out.println("PASS: Party selector fixture.");
	}

	private static void runCases() throws Exception
	{
		exactNamesKeepAuthorityAndCapacity();
		leadershipCanMakeTheSenderAmbiguous();
		expiredMatchesDoNotHideALiveInvitation();
		namesAndIdsShareTheAdmissionLock();
		renameAndDeletionUpdateTheNamesPlayersCanUse();
	}

	private static void exactNamesKeepAuthorityAndCapacity()
	{
		final PartyStore store = new InMemoryPartyStore(() -> Instant.EPOCH, Duration.ofMinutes(2), 2);
		store.create("Alice");
		store.invite("Alice", "Bob");
		store.requestJoin("Carol", "Alice");
		check(store.acceptInvitation("Dave", "Alice").getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"A sender name must not expose someone else's invitation.");
		check(store.acceptInvitation("Bob", "Ali").getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"A partial name must not pick an invitation.");
		check(store.acceptInvitation("Carol", "Alice").getStatus() == JoinStatus.ENTRY_UNAVAILABLE
				&& store.approveRequest("Alice", "Bob").getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"Names must not change whether an invitation or request needs acceptance or approval.");
		check(store.approveRequest("Dave", "Carol").getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"Knowing a candidate name must not grant approval authority.");
		check(store.approveRequest("Alice", "cArOl").getStatus() == JoinStatus.JOINED,
				"The leader should approve an exact name without requiring matching capitalization.");
		check(store.acceptInvitation("Bob", "aLiCe").getStatus() == JoinStatus.PARTY_FULL
				&& store.pendingEntries("Bob").size() == 1, "A full party must keep the invitation open.");
		store.leave("Carol");
		check(store.acceptInvitation("Bob", "ALICE").getStatus() == JoinStatus.JOINED,
				"The same named invitation should work once a seat opens.");
	}

	private static void leadershipCanMakeTheSenderAmbiguous()
	{
		final PartyStore store = new InMemoryPartyStore();
		store.create("Alice");
		final long bob = store.invite("Alice", "Bob").getEntry().getId();
		store.acceptInvitation("Bob", bob);
		final PartyPendingEntry first = store.invite("Alice", "Carol").getEntry();
		store.transferLeadership("Alice", "Bob");
		store.leave("Alice");
		store.create("Alice");
		final PartyPendingEntry second = store.invite("Alice", "Carol").getEntry();

		final JoinResult result = store.acceptInvitation("Carol", "alice");
		check(result.getStatus() == JoinStatus.AMBIGUOUS && store.findByMember("Carol") == null,
				"Two parties invited by the same sender must require an ID without joining either.");
		check(result.getMatchingEntries().size() == 2
				&& result.getMatchingEntries().get(0).getEntry() == first
				&& result.getMatchingEntries().get(1).getEntry() == second
				&& result.getMatchingEntries().get(0).getLeaderKey().equals("Bob"),
				"The choices should preserve each original ID and identify the current leaders.");
		try
		{
			result.getMatchingEntries().clear();
			throw new AssertionError("Ambiguous choices must be read-only.");
		}
		catch(final UnsupportedOperationException expected)
		{
			// A caller cannot change the store's captured choices.
		}
		check(store.acceptInvitation("Carol", first.getId()).getStatus() == JoinStatus.JOINED
				&& store.findByMember("Carol").getLeader().equals("Bob"), "The ID must still choose the original party.");
		check(store.pendingEntries("Carol").isEmpty(), "Joining must retire the competing invitation too.");
	}

	private static void expiredMatchesDoNotHideALiveInvitation()
	{
		final AtomicReference<Instant> now = new AtomicReference<Instant>(Instant.EPOCH);
		final PartyStore store = new InMemoryPartyStore(now::get, Duration.ofSeconds(10), 6);
		store.create("Alice");
		store.acceptInvitation("Bob", store.invite("Alice", "Bob").getEntry().getId());
		store.invite("Alice", "Carol");
		store.invite("Alice", "Dave");
		store.transferLeadership("Alice", "Bob");
		store.leave("Alice");
		store.create("Alice");
		now.set(Instant.EPOCH.plusSeconds(6));
		store.invite("Alice", "Carol");

		now.set(Instant.EPOCH.plusSeconds(10));
		check(store.acceptInvitation("Dave", "Alice").getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"A name must not accept an invitation at its expiry deadline.");
		check(store.acceptInvitation("Carol", "Alice").getStatus() == JoinStatus.JOINED
				&& store.findByMember("Carol").getLeader().equals("Alice"),
				"An expired match must not make the remaining live invitation ambiguous.");
	}

	private static void namesAndIdsShareTheAdmissionLock() throws Exception
	{
		final PartyStore store = new InMemoryPartyStore();
		store.create("Alice");
		final long id = store.invite("Alice", "Bob").getEntry().getId();
		final List<JoinStatus> results = PartyInvitationTest.runTogether(
				() -> store.acceptInvitation("Bob", "Alice").getStatus(),
				() -> store.acceptInvitation("Bob", id).getStatus());
		check(Collections.frequency(results, JoinStatus.JOINED) == 1
				&& Collections.frequency(results, JoinStatus.ALREADY_MEMBER) == 1
				&& store.findByMember("Bob").getMembers().size() == 2,
				"Competing name and ID acceptance must add Bob only once.");
	}

	private static void renameAndDeletionUpdateTheNamesPlayersCanUse()
	{
		final PartyStore store = new InMemoryPartyStore();
		store.create("Alice");
		store.invite("Alice", "Bob");
		store.renameCharacter("Alice", "Alicia");
		check(store.acceptInvitation("Bob", "Alice").getStatus() == JoinStatus.ENTRY_UNAVAILABLE
				&& store.acceptInvitation("Bob", "Alicia").getStatus() == JoinStatus.JOINED,
				"A saved rename should update the sender name used for acceptance.");
		store.requestJoin("Carol", "Alicia");
		store.renameCharacter("Carol", "Carly");
		check(store.approveRequest("Alicia", "Carol").getStatus() == JoinStatus.ENTRY_UNAVAILABLE
				&& store.approveRequest("Alicia", "Carly").getStatus() == JoinStatus.JOINED,
				"A saved candidate rename should update the approval selector.");

		store.invite("Alicia", "Dave");
		store.deleteCharacter("Alicia");
		check(store.acceptInvitation("Dave", "Alicia").getStatus() == JoinStatus.ENTRY_UNAVAILABLE
				&& store.acceptInvitation("Dave", "Bob").getStatus() == JoinStatus.JOINED,
				"A deleted sender's invitation should use the surviving leader's name.");
	}

	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}
}

package com.planet_ink.coffee_mud.Tests;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.party.InMemoryPartyStore;
import com.planet_ink.coffee_mud.core.party.PartyPendingEntry;
import com.planet_ink.coffee_mud.core.party.PartyStore;
import com.planet_ink.coffee_mud.core.party.PartyStore.JoinStatus;
import com.planet_ink.coffee_mud.core.party.PartyStore.PartySnapshot;
import com.planet_ink.coffee_mud.core.party.PartyStore.PendingResult;
import com.planet_ink.coffee_mud.core.party.PartyStore.PendingStatus;
import com.planet_ink.coffee_mud.core.party.PartyStore.PendingView;

/**
 * Checks invitation and roster rules through the party store API.
 * Each case uses a fresh store. Deadline checks advance a test clock, and
 * race checks release two workers together.
 */
public class PartyInvitationTest extends StdTest
{
	/**
	 * Identifies this test for in-game and launcher selection.
	 *
	 * @return the test ID
	 */
	@Override
	public String ID()
	{
		return "PartyInvitationTest";
	}

	/**
	 * Includes the cases in the party suite and CoffeeMud's all group.
	 *
	 * @return the groups that include this test
	 */
	@Override
	public String[] getTestGroups()
	{
		return new String[] {"all", "party"};
	}

	/**
	 * Runs the store cases for CoffeeMud's test command and host runner.
	 *
	 * @param mob the test actor, unused by these store cases
	 * @param metaFlags the command flags, unused by these store cases
	 * @param what the ID or group used to select the test
	 * @param commands extra command arguments, unused by these store cases
	 * @return a failure message, or {@code null} if the cases passed
	 */
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
			return "Party invitation test interrupted.";
		}
		catch(final AssertionError | Exception error)
		{
			return error.toString();
		}
	}

	/**
	 * Runs the same cases from a Java launch in the IDE.
	 *
	 * @param args unused command line arguments
	 * @throws Exception if a worker fails, times out, or is interrupted
	 */
	public static void main(final String[] args) throws Exception
	{
		runCases();
		System.out.println("PASS: Party invitation fixture.");
	}

	/**
	 * Runs each invitation, leave, and race case with its own starting state.
	 *
	 * @throws Exception if a worker fails, times out, or is interrupted
	 */
	private static void runCases() throws Exception
	{
		invitationRequiresLeaderAndRecipient();
		invitationExpiresAtItsDeadline();
		fullPartyKeepsInviteOpen();
		leaderDepartureKeepsPartyAndInvites();
		removedPartyRetiresInvites();
		joiningOrCreatingClearsOtherInvites();
		competingAcceptancesRespectCapacity();
		competingPartiesAdmitCharacterOnce();
	}

	/** Checks who can invite, view, and accept, including a repeat invitation. */
	private static void invitationRequiresLeaderAndRecipient()
	{
		final AtomicReference<Instant> now = new AtomicReference<Instant>(Instant.EPOCH);
		final PartyStore store = new InMemoryPartyStore(now::get, Duration.ofMinutes(2), 6);
		final PartySnapshot original = store.create("Alice");
		check(store.invite("Carol", "Bob").getStatus() == PendingStatus.NOT_LEADER,
				"A character without a party cannot invite someone.");

		// Sending an invite gives Bob a choice and leaves the roster alone.
		final PartyPendingEntry invite = sendInvite(store, "Alice", "Bob");
		check(store.findByMember("Bob") == null, "An invite alone must not join Bob.");
		check(store.pendingEntries("Carol").isEmpty(), "Carol must not see Bob's invite.");
		check(store.acceptInvitation("Carol", invite.getId()).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"Carol must not be able to accept Bob's invite.");

		// Repeating the invite keeps the same ID and deadline.
		now.set(Instant.EPOCH.plusSeconds(30));
		final PendingResult repeat = store.invite("Alice", "Bob");
		check(repeat.getStatus() == PendingStatus.ALREADY_PENDING, "A duplicate should find the first invite.");
		check(repeat.getEntry().getId() == invite.getId(), "A duplicate must keep the original ID.");
		check(repeat.getEntry().getExpiresAt().equals(invite.getExpiresAt()),
				"A duplicate must keep the original deadline.");
		check(store.pendingEntries("Bob").size() == 1, "Bob should have one invite to the party.");

		// The new roster is shared and earlier snapshots remain readable.
		check(store.acceptInvitation("Bob", invite.getId()).getStatus() == JoinStatus.JOINED, "Bob can accept his invite.");
		final PartySnapshot joined = store.findByMember("Bob");
		check(joined.getId() == original.getId(), "Bob should join Alice's existing party.");
		check(joined.getMembers().equals(Arrays.asList("Alice", "Bob")), "Bob should be appended in join order.");
		check(store.findByMember("Alice").getMembers().equals(joined.getMembers()), "Both members should see one roster.");
		check(original.getMembers().equals(Collections.singletonList("Alice")), "The earlier roster should stay fixed.");
		check(store.invite("Bob", "Carol").getStatus() == PendingStatus.NOT_LEADER,
				"A member who is not leader cannot invite someone.");
		check(store.invite("Alice", "Bob").getStatus() == PendingStatus.ALREADY_MEMBER,
				"An existing member does not need another invitation.");
	}

	/** Checks the configured deadline just before, exactly at, and just after expiry. */
	private static void invitationExpiresAtItsDeadline()
	{
		final AtomicReference<Instant> now = new AtomicReference<Instant>(Instant.EPOCH);
		final PartyStore store = new InMemoryPartyStore(now::get, Duration.ofSeconds(10), 6);
		store.create("Alice");
		final PartyPendingEntry bob = sendInvite(store, "Alice", "Bob");
		final PartyPendingEntry carol = sendInvite(store, "Alice", "Carol");
		final PartyPendingEntry dave = sendInvite(store, "Alice", "Dave");
		final Instant deadline = Instant.EPOCH.plusSeconds(10);
		check(bob.getExpiresAt().equals(deadline), "The store should use the configured invite lifetime.");

		// Advance time without waiting or changing the deadlines already issued.
		now.set(deadline.minusNanos(1));
		check(store.acceptInvitation("Bob", bob.getId()).getStatus() == JoinStatus.JOINED, "Bob can accept before the deadline.");
		now.set(deadline);
		check(store.acceptInvitation("Carol", carol.getId()).getStatus() == JoinStatus.EXPIRED, "The deadline itself is expired.");
		now.set(deadline.plusNanos(1));
		check(store.acceptInvitation("Dave", dave.getId()).getStatus() == JoinStatus.EXPIRED, "An overdue invite must fail.");
		check(store.findByMember("Carol") == null && store.findByMember("Dave") == null,
				"Expired invites must leave both characters without membership.");

		// A new invite has a fresh ID, and the old one cannot be used again.
		final PartyPendingEntry replacement = sendInvite(store, "Alice", "Carol");
		check(replacement.getId() != carol.getId(), "A new invite must get a new ID.");
		check(store.acceptInvitation("Carol", carol.getId()).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"The expired ID must not select the replacement invite.");
		now.set(replacement.getExpiresAt());
		check(store.pendingEntries("Carol").isEmpty(), "Pending should hide an invite at its deadline.");
	}

	/** Checks the default size limit and a retry after a member leaves. */
	private static void fullPartyKeepsInviteOpen()
	{
		final PartyStore store = new InMemoryPartyStore();
		store.create("Alice");

		// Hold the seventh player's invite while the other seats fill up.
		final PartyPendingEntry waitlisted = sendInvite(store, "Alice", "Bob");
		for(int seat = 2; seat <= 6; seat++)
		{
			final String member = "Member" + seat;
			final PartyPendingEntry invite = sendInvite(store, "Alice", member);
			check(store.acceptInvitation(member, invite.getId()).getStatus() == JoinStatus.JOINED, "An open seat should be available.");
		}
		check(store.findByMember("Alice").getMembers().size() == 6, "The limit should count the leader and five members.");
		check(store.acceptInvitation("Bob", waitlisted.getId()).getStatus() == JoinStatus.PARTY_FULL, "The seventh player must wait.");
		check(store.findByMember("Bob") == null, "A full party must leave Bob without membership.");

		// A full-party rejection leaves the original invitation available.
		final List<PartyPendingEntry> pending = visibleEntries(store, "Bob");
		check(pending.size() == 1 && pending.get(0).getExpiresAt().equals(waitlisted.getExpiresAt()),
				"A full-party rejection must keep the original deadline.");
		check(store.leave("Member2"), "A member should be able to free a seat.");
		check(store.acceptInvitation("Bob", waitlisted.getId()).getStatus() == JoinStatus.JOINED,
				"Bob can use the same invite once a seat opens.");
	}

	/** Checks join order, succession, and party-owned invites when a leader leaves. */
	private static void leaderDepartureKeepsPartyAndInvites()
	{
		final PartyStore store = new InMemoryPartyStore();
		final PartySnapshot original = store.create("Alice");
		final PartyPendingEntry bob = sendInvite(store, "Alice", "Bob");
		final PartyPendingEntry carol = sendInvite(store, "Alice", "Carol");
		final PartyPendingEntry dave = sendInvite(store, "Alice", "Dave");
		check(store.acceptInvitation("Bob", bob.getId()).getStatus() == JoinStatus.JOINED, "Bob joins first.");
		check(store.acceptInvitation("Carol", carol.getId()).getStatus() == JoinStatus.JOINED, "Carol joins second.");

		// The earliest remaining member takes over the existing party.
		check(store.leave("Alice"), "Alice should be able to leave the party.");
		check(store.findByMember("Alice") == null, "Leaving clears Alice's membership.");
		final PartySnapshot remaining = store.findByMember("Carol");
		check(remaining.getId() == original.getId(), "Succession should keep the same party ID.");
		check("Bob".equals(remaining.getLeader()), "Bob joined first and should become leader.");
		check(remaining.getMembers().equals(Arrays.asList("Bob", "Carol")), "The roster should keep the surviving join order.");

		// A repeat invite from the new leader should keep the original offer.
		final PendingResult repeat = store.invite("Bob", "Dave");
		check(repeat.getStatus() == PendingStatus.ALREADY_PENDING, "Dave's invitation should survive succession.");
		check(repeat.getEntry().getId() == dave.getId()
				&& repeat.getEntry().getExpiresAt().equals(dave.getExpiresAt()),
				"The new leader should see the original invite ID and deadline.");
		check("Alice".equals(repeat.getEntry().getSenderKey()), "The invite should still identify its original sender.");
		check(store.acceptInvitation("Dave", dave.getId()).getStatus() == JoinStatus.JOINED,
				"Dave can accept an invite that the old leader sent.");
		check(store.leave("Carol") && "Bob".equals(store.findByMember("Dave").getLeader()),
				"A non-leader leaving should keep the current leader.");
	}

	/** Checks that deleting and recreating a party cannot revive its invites. */
	private static void removedPartyRetiresInvites()
	{
		final PartyStore store = new InMemoryPartyStore();
		final PartySnapshot original = store.create("Alice");
		final PartyPendingEntry oldInvite = sendInvite(store, "Alice", "Bob");
		check(store.leave("Alice"), "The only member should be able to leave.");
		check(store.pendingEntries("Bob").isEmpty(), "Removing the party should retire its invite.");

		final PartySnapshot replacement = store.create("Alice");
		final PartyPendingEntry newInvite = sendInvite(store, "Alice", "Bob");
		check(replacement.getId() != original.getId(), "A recreated party should get a new ID.");
		check(newInvite.getId() != oldInvite.getId(), "An invite ID should never be reused in this store.");
		check(store.acceptInvitation("Bob", oldInvite.getId()).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"The old ID cannot admit Bob to the recreated party.");
		check(store.acceptInvitation("Bob", newInvite.getId()).getStatus() == JoinStatus.JOINED, "The new invite should work.");
	}

	/** Checks that membership cancels other invites even after the member later leaves. */
	private static void joiningOrCreatingClearsOtherInvites()
	{
		final PartyStore store = new InMemoryPartyStore();
		store.create("Alice");
		store.create("Carol");
		final PartyPendingEntry first = sendInvite(store, "Alice", "Bob");
		final PartyPendingEntry competing = sendInvite(store, "Carol", "Bob");
		check(visibleEntries(store, "Bob").equals(Arrays.asList(first, competing)),
				"Bob should see both invites in the order they were sent.");

		check(store.acceptInvitation("Bob", first.getId()).getStatus() == JoinStatus.JOINED, "Bob can choose Alice's party.");
		check(store.pendingEntries("Bob").isEmpty(), "Joining should clear both incoming invites.");
		check(store.leave("Bob"), "Bob should be able to leave Alice's party.");
		check(store.acceptInvitation("Bob", competing.getId()).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"Leaving must not restore Carol's invite.");

		// Creating a party must also retire invitations from other parties.
		final PartyPendingEntry beforeCreate = sendInvite(store, "Alice", "Bob");
		check(store.create("Bob") != null, "Bob can create a party while invited elsewhere.");
		check(store.pendingEntries("Bob").isEmpty(), "Creating should clear incoming invites.");
		check(store.leave("Bob"), "Bob should be able to leave his own party.");
		check(store.acceptInvitation("Bob", beforeCreate.getId()).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"Leaving must not restore an invite retired by creation.");
	}

	/**
	 * Extracts the underlying entries so cases can check their identity and order.
	 *
	 * @param store the case's store
	 * @param characterKey the character viewing the list
	 * @return the visible entries in the store's display order
	 */
	static List<PartyPendingEntry> visibleEntries(final PartyStore store, final String characterKey)
	{
		return store.pendingEntries(characterKey).stream().map(PendingView::getEntry).collect(Collectors.toList());
	}

	/**
	 * Checks that only one recipient can take the last seat.
	 *
	 * @throws Exception if a worker fails, times out, or is interrupted
	 */
	private static void competingAcceptancesRespectCapacity() throws Exception
	{
		final PartyStore store = new InMemoryPartyStore(() -> Instant.EPOCH, Duration.ofMinutes(2), 2);
		store.create("Alice");
		final PartyPendingEntry bob = sendInvite(store, "Alice", "Bob");
		final PartyPendingEntry carol = sendInvite(store, "Alice", "Carol");

		final List<JoinStatus> results = acceptTogether(store, bob, carol);
		check(Collections.frequency(results, JoinStatus.JOINED) == 1, "Only one character can take the last seat.");
		check(Collections.frequency(results, JoinStatus.PARTY_FULL) == 1, "The other character should see a full party.");
		check(store.findByMember("Alice").getMembers().size() == 2, "The party must stay within its limit.");
		check((store.findByMember("Bob") == null) != (store.findByMember("Carol") == null),
				"The member lookup must include only the winner.");
	}

	/**
	 * Checks that one character cannot accept invitations to two parties together.
	 *
	 * @throws Exception if a worker fails, times out, or is interrupted
	 */
	private static void competingPartiesAdmitCharacterOnce() throws Exception
	{
		final PartyStore store = new InMemoryPartyStore(() -> Instant.EPOCH, Duration.ofMinutes(2), 6);
		store.create("Alice");
		store.create("Carol");
		final PartyPendingEntry first = sendInvite(store, "Alice", "Bob");
		final PartyPendingEntry second = sendInvite(store, "Carol", "Bob");

		final List<JoinStatus> results = acceptTogether(store, first, second);
		check(Collections.frequency(results, JoinStatus.JOINED) == 1, "Bob must join exactly one party.");
		check(Collections.frequency(results, JoinStatus.ALREADY_MEMBER) == 1, "Bob must have one membership.");
		final int totalMembers = store.findByMember("Alice").getMembers().size()
				+ store.findByMember("Carol").getMembers().size();
		check(totalMembers == 3, "Only one roster should gain Bob.");
		check(store.pendingEntries("Bob").isEmpty(), "The successful join should retire both invites.");
	}

	/**
	 * Sends an invitation that the case expects to succeed.
	 *
	 * @param store the store used by the case
	 * @param leader the sender
	 * @param recipient the invited character
	 * @return the invitation created by the store
	 */
	private static PartyPendingEntry sendInvite(final PartyStore store, final String leader, final String recipient)
	{
		final PendingResult result = store.invite(leader, recipient);
		check(result.getStatus() == PendingStatus.CREATED && result.getEntry() != null,
				"Expected " + leader + " to invite " + recipient + ", got " + result.getStatus() + ".");
		return result.getEntry();
	}

	/**
	 * Releases two acceptance attempts together and waits for both outcomes.
	 *
	 * @param store the store shared by both workers
	 * @param first the first invitation to accept
	 * @param second the second invitation to accept
	 * @return outcomes in the same order as the invitations
	 * @throws Exception if a worker fails, times out, or is interrupted
	 */
	private static List<JoinStatus> acceptTogether(final PartyStore store, final PartyPendingEntry first,
			final PartyPendingEntry second) throws Exception
	{
		return runTogether(
				() -> store.acceptInvitation(first.getCandidateKey(), first.getId()).getStatus(),
				() -> store.acceptInvitation(second.getCandidateKey(), second.getId()).getStatus());
	}

	/**
	 * Releases two party store operations together. Each worker must reach the barrier
	 * before either can try its operation. Both the barrier and results have time limits.
	 *
	 * @param <T> the result type shared by the operations
	 * @param first the operation for the first worker
	 * @param second the operation for the second worker
	 * @return outcomes in the same order as the operations
	 * @throws Exception if a worker fails, times out, or is interrupted
	 */
	static <T> List<T> runTogether(final Callable<T> first, final Callable<T> second) throws Exception
	{
		final ExecutorService workers = Executors.newFixedThreadPool(2);
		final CyclicBarrier start = new CyclicBarrier(2);
		try
		{
			final Future<T> firstResult = workers.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return first.call();
			});
			final Future<T> secondResult = workers.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return second.call();
			});
			return Arrays.asList(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
		}
		finally
		{
			workers.shutdownNow();
		}
	}

	/**
	 * Fails a case with a message that says which rule broke.
	 *
	 * @param condition whether the expected state was reached
	 * @param message what the case expected
	 */
	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}
}

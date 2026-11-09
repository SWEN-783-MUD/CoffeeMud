package com.planet_ink.coffee_mud.Tests;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.party.InMemoryPartyStore;
import com.planet_ink.coffee_mud.core.party.PartyInvitation;
import com.planet_ink.coffee_mud.core.party.PartyStore;
import com.planet_ink.coffee_mud.core.party.PartyStore.AcceptResult;
import com.planet_ink.coffee_mud.core.party.PartyStore.InviteResult;
import com.planet_ink.coffee_mud.core.party.PartyStore.InviteStatus;
import com.planet_ink.coffee_mud.core.party.PartyStore.PartySnapshot;

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
		check(store.invite("Carol", "Bob").getStatus() == InviteStatus.NOT_LEADER,
				"A character without a party cannot invite someone.");

		// Sending an invite gives Bob a choice and leaves the roster alone.
		final PartyInvitation invite = sendInvite(store, "Alice", "Bob");
		check(store.findByMember("Bob") == null, "An invite alone must not join Bob.");
		check(store.pendingInvitations("Carol").isEmpty(), "Carol must not see Bob's invite.");
		check(store.acceptInvitation("Carol", invite.getId()) == AcceptResult.INVITATION_UNAVAILABLE,
				"Carol must not be able to accept Bob's invite.");

		// Repeating the invite keeps the same ID and deadline.
		now.set(Instant.EPOCH.plusSeconds(30));
		final InviteResult repeat = store.invite("Alice", "Bob");
		check(repeat.getStatus() == InviteStatus.ALREADY_PENDING, "A duplicate should find the first invite.");
		check(repeat.getInvitation().getId() == invite.getId(), "A duplicate must keep the original ID.");
		check(repeat.getInvitation().getExpiresAt().equals(invite.getExpiresAt()),
				"A duplicate must keep the original deadline.");
		check(store.pendingInvitations("Bob").size() == 1, "Bob should have one invite to the party.");

		// The new roster is shared and earlier snapshots remain readable.
		check(store.acceptInvitation("Bob", invite.getId()) == AcceptResult.JOINED, "Bob can accept his invite.");
		final PartySnapshot joined = store.findByMember("Bob");
		check(joined.getId() == original.getId(), "Bob should join Alice's existing party.");
		check(joined.getMembers().equals(Arrays.asList("Alice", "Bob")), "Bob should be appended in join order.");
		check(store.findByMember("Alice").getMembers().equals(joined.getMembers()), "Both members should see one roster.");
		check(original.getMembers().equals(Collections.singletonList("Alice")), "The earlier roster should stay fixed.");
		check(store.invite("Bob", "Carol").getStatus() == InviteStatus.NOT_LEADER,
				"A member who is not leader cannot invite someone.");
		check(store.invite("Alice", "Bob").getStatus() == InviteStatus.ALREADY_MEMBER,
				"An existing member does not need another invitation.");
	}

	/** Checks the configured deadline just before, exactly at, and just after expiry. */
	private static void invitationExpiresAtItsDeadline()
	{
		final AtomicReference<Instant> now = new AtomicReference<Instant>(Instant.EPOCH);
		final PartyStore store = new InMemoryPartyStore(now::get, Duration.ofSeconds(10), 6);
		store.create("Alice");
		final PartyInvitation bob = sendInvite(store, "Alice", "Bob");
		final PartyInvitation carol = sendInvite(store, "Alice", "Carol");
		final PartyInvitation dave = sendInvite(store, "Alice", "Dave");
		final Instant deadline = Instant.EPOCH.plusSeconds(10);
		check(bob.getExpiresAt().equals(deadline), "The store should use the configured invite lifetime.");

		// Advance time without waiting or changing the deadlines already issued.
		now.set(deadline.minusNanos(1));
		check(store.acceptInvitation("Bob", bob.getId()) == AcceptResult.JOINED, "Bob can accept before the deadline.");
		now.set(deadline);
		check(store.acceptInvitation("Carol", carol.getId()) == AcceptResult.EXPIRED, "The deadline itself is expired.");
		now.set(deadline.plusNanos(1));
		check(store.acceptInvitation("Dave", dave.getId()) == AcceptResult.EXPIRED, "An overdue invite must fail.");
		check(store.findByMember("Carol") == null && store.findByMember("Dave") == null,
				"Expired invites must leave both characters without membership.");

		// A new invite has a fresh ID, and the old one cannot be used again.
		final PartyInvitation replacement = sendInvite(store, "Alice", "Carol");
		check(replacement.getId() != carol.getId(), "A new invite must get a new ID.");
		check(store.acceptInvitation("Carol", carol.getId()) == AcceptResult.INVITATION_UNAVAILABLE,
				"The expired ID must not select the replacement invite.");
		now.set(replacement.getExpiresAt());
		check(store.pendingInvitations("Carol").isEmpty(), "Pending should hide an invite at its deadline.");
	}

	/** Checks the default size limit and a retry after a member leaves. */
	private static void fullPartyKeepsInviteOpen()
	{
		final PartyStore store = new InMemoryPartyStore();
		store.create("Alice");

		// Hold the seventh player's invite while the other seats fill up.
		final PartyInvitation waitlisted = sendInvite(store, "Alice", "Bob");
		for(int seat = 2; seat <= 6; seat++)
		{
			final String member = "Member" + seat;
			final PartyInvitation invite = sendInvite(store, "Alice", member);
			check(store.acceptInvitation(member, invite.getId()) == AcceptResult.JOINED, "An open seat should be available.");
		}
		check(store.findByMember("Alice").getMembers().size() == 6, "The limit should count the leader and five members.");
		check(store.acceptInvitation("Bob", waitlisted.getId()) == AcceptResult.PARTY_FULL, "The seventh player must wait.");
		check(store.findByMember("Bob") == null, "A full party must leave Bob without membership.");

		// A full-party rejection leaves the original invitation available.
		final List<PartyInvitation> pending = store.pendingInvitations("Bob");
		check(pending.size() == 1 && pending.get(0).getExpiresAt().equals(waitlisted.getExpiresAt()),
				"A full-party rejection must keep the original deadline.");
		check(store.leave("Member2"), "A member should be able to free a seat.");
		check(store.acceptInvitation("Bob", waitlisted.getId()) == AcceptResult.JOINED,
				"Bob can use the same invite once a seat opens.");
	}

	/** Checks join order, succession, and party-owned invites when a leader leaves. */
	private static void leaderDepartureKeepsPartyAndInvites()
	{
		final PartyStore store = new InMemoryPartyStore();
		final PartySnapshot original = store.create("Alice");
		final PartyInvitation bob = sendInvite(store, "Alice", "Bob");
		final PartyInvitation carol = sendInvite(store, "Alice", "Carol");
		final PartyInvitation dave = sendInvite(store, "Alice", "Dave");
		check(store.acceptInvitation("Bob", bob.getId()) == AcceptResult.JOINED, "Bob joins first.");
		check(store.acceptInvitation("Carol", carol.getId()) == AcceptResult.JOINED, "Carol joins second.");

		// The earliest remaining member takes over the existing party.
		check(store.leave("Alice"), "Alice should be able to leave the party.");
		check(store.findByMember("Alice") == null, "Leaving clears Alice's membership.");
		final PartySnapshot remaining = store.findByMember("Carol");
		check(remaining.getId() == original.getId(), "Succession should keep the same party ID.");
		check("Bob".equals(remaining.getLeader()), "Bob joined first and should become leader.");
		check(remaining.getMembers().equals(Arrays.asList("Bob", "Carol")), "The roster should keep the surviving join order.");

		// A repeat invite from the new leader should keep the original offer.
		final InviteResult repeat = store.invite("Bob", "Dave");
		check(repeat.getStatus() == InviteStatus.ALREADY_PENDING, "Dave's invitation should survive succession.");
		check(repeat.getInvitation().getId() == dave.getId()
				&& repeat.getInvitation().getExpiresAt().equals(dave.getExpiresAt()),
				"The new leader should see the original invite ID and deadline.");
		check("Alice".equals(repeat.getInvitation().getInviterKey()), "The invite should still identify its original sender.");
		check(store.acceptInvitation("Dave", dave.getId()) == AcceptResult.JOINED,
				"Dave can accept an invite that the old leader sent.");
		check(store.leave("Carol") && "Bob".equals(store.findByMember("Dave").getLeader()),
				"A non-leader leaving should keep the current leader.");
	}

	/** Checks that deleting and recreating a party cannot revive its invites. */
	private static void removedPartyRetiresInvites()
	{
		final PartyStore store = new InMemoryPartyStore();
		final PartySnapshot original = store.create("Alice");
		final PartyInvitation oldInvite = sendInvite(store, "Alice", "Bob");
		check(store.leave("Alice"), "The only member should be able to leave.");
		check(store.pendingInvitations("Bob").isEmpty(), "Removing the party should retire its invite.");

		final PartySnapshot replacement = store.create("Alice");
		final PartyInvitation newInvite = sendInvite(store, "Alice", "Bob");
		check(replacement.getId() != original.getId(), "A recreated party should get a new ID.");
		check(newInvite.getId() != oldInvite.getId(), "An invite ID should never be reused in this store.");
		check(store.acceptInvitation("Bob", oldInvite.getId()) == AcceptResult.INVITATION_UNAVAILABLE,
				"The old ID cannot admit Bob to the recreated party.");
		check(store.acceptInvitation("Bob", newInvite.getId()) == AcceptResult.JOINED, "The new invite should work.");
	}

	/** Checks that membership cancels other invites even after the member later leaves. */
	private static void joiningOrCreatingClearsOtherInvites()
	{
		final PartyStore store = new InMemoryPartyStore();
		store.create("Alice");
		store.create("Carol");
		final PartyInvitation first = sendInvite(store, "Alice", "Bob");
		final PartyInvitation competing = sendInvite(store, "Carol", "Bob");
		check(store.pendingInvitations("Bob").equals(Arrays.asList(first, competing)),
				"Bob should see both invites in the order they were sent.");

		check(store.acceptInvitation("Bob", first.getId()) == AcceptResult.JOINED, "Bob can choose Alice's party.");
		check(store.pendingInvitations("Bob").isEmpty(), "Joining should clear both incoming invites.");
		check(store.leave("Bob"), "Bob should be able to leave Alice's party.");
		check(store.acceptInvitation("Bob", competing.getId()) == AcceptResult.INVITATION_UNAVAILABLE,
				"Leaving must not restore Carol's invite.");

		// Creating a party must also retire invitations from other parties.
		final PartyInvitation beforeCreate = sendInvite(store, "Alice", "Bob");
		check(store.create("Bob") != null, "Bob can create a party while invited elsewhere.");
		check(store.pendingInvitations("Bob").isEmpty(), "Creating should clear incoming invites.");
		check(store.leave("Bob"), "Bob should be able to leave his own party.");
		check(store.acceptInvitation("Bob", beforeCreate.getId()) == AcceptResult.INVITATION_UNAVAILABLE,
				"Leaving must not restore an invite retired by creation.");
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
		final PartyInvitation bob = sendInvite(store, "Alice", "Bob");
		final PartyInvitation carol = sendInvite(store, "Alice", "Carol");

		final List<AcceptResult> results = acceptTogether(store, bob, carol);
		check(Collections.frequency(results, AcceptResult.JOINED) == 1, "Only one character can take the last seat.");
		check(Collections.frequency(results, AcceptResult.PARTY_FULL) == 1, "The other character should see a full party.");
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
		final PartyInvitation first = sendInvite(store, "Alice", "Bob");
		final PartyInvitation second = sendInvite(store, "Carol", "Bob");

		final List<AcceptResult> results = acceptTogether(store, first, second);
		check(Collections.frequency(results, AcceptResult.JOINED) == 1, "Bob must join exactly one party.");
		check(Collections.frequency(results, AcceptResult.ALREADY_MEMBER) == 1, "Bob must have one membership.");
		final int totalMembers = store.findByMember("Alice").getMembers().size()
				+ store.findByMember("Carol").getMembers().size();
		check(totalMembers == 3, "Only one roster should gain Bob.");
		check(store.pendingInvitations("Bob").isEmpty(), "The successful join should retire both invites.");
	}

	/**
	 * Sends an invitation that the case expects to succeed.
	 *
	 * @param store the store used by the case
	 * @param leader the sender
	 * @param recipient the invited character
	 * @return the invitation created by the store
	 */
	private static PartyInvitation sendInvite(final PartyStore store, final String leader, final String recipient)
	{
		final InviteResult result = store.invite(leader, recipient);
		check(result.getStatus() == InviteStatus.CREATED && result.getInvitation() != null,
				"Expected " + leader + " to invite " + recipient + ", got " + result.getStatus() + ".");
		return result.getInvitation();
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
	private static List<AcceptResult> acceptTogether(final PartyStore store, final PartyInvitation first,
			final PartyInvitation second) throws Exception
	{
		final ExecutorService workers = Executors.newFixedThreadPool(2);
		final CyclicBarrier start = new CyclicBarrier(2);
		try
		{
			// Both workers must arrive before either attempts to join.
			final Future<AcceptResult> firstResult = workers.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return store.acceptInvitation(first.getRecipientKey(), first.getId());
			});
			final Future<AcceptResult> secondResult = workers.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return store.acceptInvitation(second.getRecipientKey(), second.getId());
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

package com.planet_ink.coffee_mud.Tests;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.party.InMemoryPartyStore;
import com.planet_ink.coffee_mud.core.party.PartyPendingEntry;
import com.planet_ink.coffee_mud.core.party.PartyPendingEntry.Type;
import com.planet_ink.coffee_mud.core.party.PartyStore;
import com.planet_ink.coffee_mud.core.party.PartyStore.JoinStatus;
import com.planet_ink.coffee_mud.core.party.PartyStore.PendingResult;
import com.planet_ink.coffee_mud.core.party.PartyStore.PendingStatus;

/** Checks request authority, deadlines, and races through the same store as invitations. */
public class PartyRequestTest extends StdTest
{
	/** {@inheritDoc} */
	@Override
	public String ID()
	{
		return "PartyRequestTest";
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
			return "Party request test interrupted.";
		}
		catch(final AssertionError | Exception error)
		{
			return error.toString();
		}
	}

	/**
	 * Runs the store cases without starting CoffeeMud.
	 *
	 * @param args unused command line arguments
	 * @throws Exception if a worker fails, times out, or is interrupted
	 */
	public static void main(final String[] args) throws Exception
	{
		runCases();
		System.out.println("PASS: Party request fixture.");
	}

	/**
	 * Starts a fresh store for each request scenario.
	 *
	 * @throws Exception if a worker fails, times out, or is interrupted
	 */
	private static void runCases() throws Exception
	{
		originalEntryStaysOpen();
		crossedStartsShareOneEntry();
		currentLeaderOwnsApproval();
		expiredAndRemovedRequestsStayRetired();
		competingApprovalsRespectCapacity();
		competingPartiesAdmitCandidateOnce();
	}

	/** Checks both command orders, a repeat request, and replacement at the exact deadline. */
	private static void originalEntryStaysOpen()
	{
		final AtomicReference<Instant> now = new AtomicReference<Instant>(Instant.EPOCH);
		final PartyStore store = new InMemoryPartyStore(now::get, Duration.ofMinutes(2), 6);
		store.create("Alice");
		final PartyPendingEntry invite = store.invite("Alice", "Bob").getEntry();
		final PartyPendingEntry request = sendRequest(store, "Carol", "Alice");

		now.set(Instant.EPOCH.plusSeconds(20));
		checkSameEntry(store.requestJoin("Bob", "Alice"), invite, Type.INVITATION);
		checkSameEntry(store.invite("Alice", "Carol"), request, Type.REQUEST);
		checkSameEntry(store.requestJoin("Carol", "Alice"), request, Type.REQUEST);
		check(store.findByMember("Bob") == null && store.findByMember("Carol") == null,
				"Crossed and repeated starts must leave both candidates without membership.");

		// Approve cannot consume an invitation, and accept cannot consume a request.
		check(store.approveRequest("Alice", invite.getId()).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"An invitation still needs the candidate's acceptance.");
		check(store.acceptInvitation("Carol", request.getId()).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"A request still needs the leader's approval.");

		now.set(invite.getExpiresAt());
		final PartyPendingEntry replacement = sendRequest(store, "Bob", "Alice");
		check(replacement.getId() != invite.getId() && replacement.getType() == Type.REQUEST,
				"An expired invitation must give way to a new request with a fresh ID.");
		check(store.acceptInvitation("Bob", invite.getId()).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"The retired invitation ID must not act on the replacement.");
	}

	/**
	 * Releases an invitation and request for the same candidate at the same time.
	 *
	 * @throws Exception if a worker fails, times out, or is interrupted
	 */
	private static void crossedStartsShareOneEntry() throws Exception
	{
		final PartyStore store = new InMemoryPartyStore(() -> Instant.EPOCH, Duration.ofMinutes(2), 6);
		store.create("Alice");
		final List<PendingResult> results = PartyInvitationTest.runTogether(
				() -> store.invite("Alice", "Bob"), () -> store.requestJoin("Bob", "Alice"));
		final PendingResult first = results.get(0);
		final PendingResult second = results.get(1);

		// Either worker can win. The other must get back the winner's entry.
		final PendingResult winner = first.getStatus() == PendingStatus.CREATED ? first : second;
		final PendingResult other = winner == first ? second : first;
		check(winner.getStatus() == PendingStatus.CREATED, "One worker should open an entry.");
		final PartyPendingEntry entry = winner.getEntry();
		checkSameEntry(other, entry, entry.getType());
		final String reviewer = entry.getType() == Type.INVITATION ? "Bob" : "Alice";
		check(store.pendingEntries(reviewer).equals(Collections.singletonList(entry)),
				"Only the winning entry should be actionable.");
		check(store.findByMember("Bob") == null, "Opening either entry must leave the seat free.");
	}

	/** Checks who can see and approve a request before and after the leader leaves. */
	private static void currentLeaderOwnsApproval()
	{
		final PartyStore store = new InMemoryPartyStore();
		store.create("Alice");
		final PartyPendingEntry invite = store.invite("Alice", "Bob").getEntry();
		check(store.acceptInvitation("Bob", invite.getId()).getStatus() == JoinStatus.JOINED, "Bob should join first.");
		check(store.requestJoin("Carol", "Bob").getStatus() == PendingStatus.NOT_LEADER,
				"A request must name a current party leader.");

		final PartyPendingEntry request = sendRequest(store, "Carol", "Alice");
		check(store.pendingEntries("Alice").equals(Collections.singletonList(request)),
				"Alice should see the incoming request.");
		check(store.pendingEntries("Bob").isEmpty() && store.pendingEntries("Carol").isEmpty(),
				"Only the current leader should see requests to approve.");
		check(store.approveRequest("Bob", request.getId()).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"An ordinary member cannot approve a request.");

		// Succession keeps the same request and shifts approval to Bob.
		check(store.leave("Alice"), "Alice should be able to leave.");
		check(store.pendingEntries("Bob").equals(Collections.singletonList(request)),
				"Bob should inherit the same request and deadline.");
		check(store.approveRequest("Alice", request.getId()).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"The former leader cannot approve with an old request ID.");
		check("Carol".equals(store.approveRequest("Bob", request.getId()).getJoinedCandidateKey()),
				"The successful approval should identify Carol for the online notice.");
		check(store.findByMember("Bob").getMembers().equals(Arrays.asList("Bob", "Carol")),
				"Carol should be added once at the end of the roster.");
	}

	/** Checks expiry at approval time and retirement when the party disappears. */
	private static void expiredAndRemovedRequestsStayRetired()
	{
		final AtomicReference<Instant> now = new AtomicReference<Instant>(Instant.EPOCH);
		final PartyStore store = new InMemoryPartyStore(now::get, Duration.ofMinutes(2), 6);
		store.create("Alice");
		final PartyPendingEntry expired = sendRequest(store, "Bob", "Alice");
		now.set(expired.getExpiresAt());
		check(store.approveRequest("Alice", expired.getId()).getStatus() == JoinStatus.EXPIRED,
				"Approval must fail at the deadline even without listing pending entries first.");

		final PartyPendingEntry old = sendRequest(store, "Bob", "Alice");
		store.leave("Alice");
		store.create("Alice");
		final PartyPendingEntry current = sendRequest(store, "Bob", "Alice");
		check(old.getPartyId() != current.getPartyId() && old.getId() != current.getId(),
				"A recreated party and its new request should have fresh IDs.");
		check(store.approveRequest("Alice", old.getId()).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"The retired request cannot join the recreated party.");
		check(store.pendingEntries("Alice").equals(Collections.singletonList(current)),
				"Only the new request should remain available.");
	}

	/**
	 * Checks that only one request can take the last seat, and that the other can be retried.
	 *
	 * @throws Exception if a worker fails, times out, or is interrupted
	 */
	private static void competingApprovalsRespectCapacity() throws Exception
	{
		final PartyStore store = new InMemoryPartyStore(() -> Instant.EPOCH, Duration.ofMinutes(2), 2);
		store.create("Alice");
		final PartyPendingEntry bob = sendRequest(store, "Bob", "Alice");
		final PartyPendingEntry carol = sendRequest(store, "Carol", "Alice");

		final List<JoinStatus> results = PartyInvitationTest.runTogether(
				() -> store.approveRequest("Alice", bob.getId()).getStatus(),
				() -> store.approveRequest("Alice", carol.getId()).getStatus());
		check(Collections.frequency(results, JoinStatus.JOINED) == 1, "Only one approval can take the last seat.");
		check(Collections.frequency(results, JoinStatus.PARTY_FULL) == 1, "The second approval should find a full party.");

		final boolean bobJoined = results.get(0) == JoinStatus.JOINED;
		final PartyPendingEntry remaining = bobJoined ? carol : bob;
		check(store.pendingEntries("Alice").equals(Collections.singletonList(remaining)),
				"The original request should remain available with the same deadline.");
		store.leave(bobJoined ? "Bob" : "Carol");
		check(store.approveRequest("Alice", remaining.getId()).getStatus() == JoinStatus.JOINED,
				"The leader can approve the original request once a seat opens.");
	}

	/**
	 * Checks that a join retires competing entries of both types for the candidate.
	 *
	 * @throws Exception if a worker fails, times out, or is interrupted
	 */
	private static void competingPartiesAdmitCandidateOnce() throws Exception
	{
		final PartyStore store = new InMemoryPartyStore();
		store.create("Alice");
		store.create("Carol");
		store.create("Dave");
		final PartyPendingEntry first = sendRequest(store, "Bob", "Alice");
		final PartyPendingEntry second = sendRequest(store, "Bob", "Carol");
		store.invite("Dave", "Bob");

		final List<JoinStatus> results = PartyInvitationTest.runTogether(
				() -> store.approveRequest("Alice", first.getId()).getStatus(),
				() -> store.approveRequest("Carol", second.getId()).getStatus());
		check(Collections.frequency(results, JoinStatus.JOINED) == 1, "Only one party can admit Bob.");
		check(Collections.frequency(results, JoinStatus.ENTRY_UNAVAILABLE) == 1, "The other request should be retired.");
		check(store.pendingEntries("Alice").isEmpty() && store.pendingEntries("Carol").isEmpty()
				&& store.pendingEntries("Bob").isEmpty(), "Joining must retire all of Bob's pending entries.");
	}

	/**
	 * Opens a request in a case that expects a new entry.
	 *
	 * @param store the case's store
	 * @param candidate the character asking to join
	 * @param leader the leader they name
	 * @return the new request
	 */
	private static PartyPendingEntry sendRequest(final PartyStore store, final String candidate, final String leader)
	{
		final PendingResult result = store.requestJoin(candidate, leader);
		check(result.getStatus() == PendingStatus.CREATED, "Expected a new join request, got " + result.getStatus());
		return result.getEntry();
	}

	/**
	 * Checks that a crossed or repeated command leaves the original entry intact.
	 *
	 * @param repeat the result of the second command
	 * @param original the first entry
	 * @param type the first entry's expected type
	 */
	private static void checkSameEntry(final PendingResult repeat, final PartyPendingEntry original, final Type type)
	{
		check(repeat.getStatus() == PendingStatus.ALREADY_PENDING, "The second command should find the first entry.");
		final PartyPendingEntry entry = repeat.getEntry();
		check(entry.getType() == type && entry.getId() == original.getId()
				&& entry.getExpiresAt().equals(original.getExpiresAt()), "The type, ID, and deadline must stay the same.");
	}

	/**
	 * Stops a case with the reason it failed.
	 *
	 * @param condition whether the expected state was reached
	 * @param message the result the case expected
	 */
	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}
}

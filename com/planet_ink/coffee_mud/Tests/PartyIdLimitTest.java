package com.planet_ink.coffee_mud.Tests;

import java.lang.reflect.Field;
import java.util.List;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.party.InMemoryPartyStore;
import com.planet_ink.coffee_mud.core.party.PartyPendingEntry;
import com.planet_ink.coffee_mud.core.party.PartyStore.IdLimitException;
import com.planet_ink.coffee_mud.core.party.PartyStore.JoinStatus;
import com.planet_ink.coffee_mud.core.party.PartyStore.PendingStatus;

/** Checks that exhausted counters preserve existing parties and never publish wrapped IDs. */
public class PartyIdLimitTest extends StdTest
{
	/** {@inheritDoc} */
	@Override
	public String ID()
	{
		return "PartyIdLimitTest";
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
		catch(final AssertionError | Exception error)
		{
			return error.toString();
		}
	}

	/**
	 * Runs the counter boundary checks without starting a server.
	 *
	 * @param args unused command line arguments
	 * @throws Exception if a boundary check fails
	 */
	public static void main(final String[] args) throws Exception
	{
		runCases();
		System.out.println("PASS: Party ID limit fixture.");
	}

	private static void runCases() throws Exception
	{
		partyLimitPreservesPendingInvitations();
		entryLimitPreservesExistingActions();
	}

	private static void partyLimitPreservesPendingInvitations() throws Exception
	{
		final InMemoryPartyStore store = new InMemoryPartyStore();
		setCounter(store, "nextPartyId", Long.MAX_VALUE - 1);
		check(store.create("Alice").getId() == Long.MAX_VALUE - 1, "The last allocatable party ID should remain positive.");
		final PartyPendingEntry invite = store.invite("Alice", "Bob").getEntry();

		check(store.create("Alice") == null, "Duplicate creation should still return the normal membership result.");
		expectLimit(() -> store.create("Bob"));
		check(store.findByMember("Bob") == null && store.pendingEntries("Bob").size() == 1,
				"A failed creation must preserve Bob's invitation and leave him without a party.");
		check(store.acceptInvitation("Bob", invite.getId()).getStatus() == JoinStatus.JOINED,
				"An existing party must remain usable after the counter reaches its limit.");
		expectLimit(() -> store.create("Carol"));
	}

	private static void entryLimitPreservesExistingActions() throws Exception
	{
		final InMemoryPartyStore store = new InMemoryPartyStore();
		store.create("Alice");
		setCounter(store, "nextEntryId", Long.MAX_VALUE - 1);
		final PartyPendingEntry invite = store.invite("Alice", "Bob").getEntry();
		check(invite.getId() == Long.MAX_VALUE - 1, "The last allocatable entry ID should remain positive.");
		check(store.invite("Alice", "Bob").getStatus() == PendingStatus.ALREADY_PENDING,
				"A duplicate should still find its original entry without allocating an ID.");

		expectLimit(() -> store.requestJoin("Carol", "Alice"));
		check(store.pendingEntries("Alice").size() == 1 && store.pendingEntries("Carol").isEmpty(),
				"A failed allocation must not create a request or replace the existing invitation.");
		check(store.acceptInvitation("Bob", invite.getId()).getStatus() == JoinStatus.JOINED,
				"An existing invitation must still work after new IDs run out.");
		expectLimit(() -> store.invite("Alice", "Carol"));
	}

	private static void setCounter(final InMemoryPartyStore store, final String name, final long value) throws Exception
	{
		// Reach the boundary without adding production constructors just for tests.
		final Field counter = InMemoryPartyStore.class.getDeclaredField(name);
		counter.setAccessible(true);
		counter.setLong(store, value);
	}

	private static void expectLimit(final Runnable operation)
	{
		try
		{
			operation.run();
			throw new AssertionError("An exhausted counter must refuse a new ID.");
		}
		catch(final IdLimitException expected)
		{
			// Existing entries remain available to inspect, accept, decline, or cancel.
		}
	}

	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}
}

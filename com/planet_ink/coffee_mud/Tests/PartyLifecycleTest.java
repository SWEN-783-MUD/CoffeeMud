package com.planet_ink.coffee_mud.Tests;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.planet_ink.coffee_mud.Common.interfaces.PlayerStats;
import com.planet_ink.coffee_mud.Libraries.interfaces.PartyLibrary.CreateResult;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMClass;
import com.planet_ink.coffee_mud.core.CMLib;
import com.planet_ink.coffee_mud.core.party.InMemoryPartyStore;
import com.planet_ink.coffee_mud.core.party.PartyPendingEntry;
import com.planet_ink.coffee_mud.core.party.PartyStore;
import com.planet_ink.coffee_mud.core.party.PartyStore.JoinStatus;
import com.planet_ink.coffee_mud.core.party.PartyStore.PartySnapshot;

/** Checks party state during character changes and through the real player directory. */
public class PartyLifecycleTest extends StdTest
{
	/** {@inheritDoc} */
	@Override
	public String ID()
	{
		return "PartyLifecycleTest";
	}

	/**
	 * Limits the database cases to the party suite, which uses a disposable host.
	 *
	 * @return the group that runs the fixture
	 */
	@Override
	public String[] getTestGroups()
	{
		return new String[] {"party"};
	}

	/** {@inheritDoc} */
	@Override
	public String doTest(final MOB mob, final int metaFlags, final String what, final List<String> commands)
	{
		try
		{
			runStoreCases();
			checkPlayerDirectory();
			return null;
		}
		catch(final InterruptedException error)
		{
			Thread.currentThread().interrupt();
			return "Party lifecycle test interrupted.";
		}
		catch(final AssertionError | Exception error)
		{
			return error.toString();
		}
	}

	/**
	 * Runs the store cases without booting a server.
	 *
	 * @param args unused command-line arguments
	 * @throws Exception if either race worker fails or times out
	 */
	public static void main(final String[] args) throws Exception
	{
		runStoreCases();
		System.out.println("PASS: Party lifecycle store cases.");
	}

	/**
	 * Gives each party-state scenario a fresh store.
	 *
	 * @throws Exception if either race worker fails or times out
	 */
	private static void runStoreCases() throws Exception
	{
		renameKeepsPartyAndPendingEntries();
		deletingLeaderKeepsPartyEntries();
		deletionRacingApprovalClearsReusedName();
	}

	/** Checks roster order, invitation sender, and request candidate after rename. */
	private static void renameKeepsPartyAndPendingEntries()
	{
		final PartyStore store = new InMemoryPartyStore(() -> Instant.EPOCH, Duration.ofMinutes(2), 6);
		final PartySnapshot original = store.create("Alice");
		final long bobInvite = store.invite("Alice", "Bob").getEntry().getId();
		check(store.acceptInvitation("Bob", bobInvite).getStatus() == JoinStatus.JOINED, "Bob should join Alice.");
		final PartyPendingEntry request = store.requestJoin("Carol", "Alice").getEntry();
		final PartyPendingEntry invitation = store.invite("Alice", "Dara").getEntry();

		store.renameCharacter("Alice", "Alicia");
		store.renameCharacter("Bob", "Robert");
		store.renameCharacter("Carol", "Caroline");
		store.renameCharacter("Dara", "Daria");

		final PartySnapshot renamed = store.findByMember("Alicia");
		check(renamed.getId() == original.getId() && "Alicia".equals(renamed.getLeader())
				&& renamed.getMembers().equals(Arrays.asList("Alicia", "Robert")), "Rename should keep the roster in join order.");
		check(store.findByMember("Alice") == null && store.findByMember("Bob") == null,
				"The old member keys should be free.");
		final List<PartyPendingEntry> entries = PartyInvitationTest.visibleEntries(store, "Alicia");
		check(entries.size() == 2, "The renamed leader should still see both entries.");
		checkSameEntry(entries.get(0), request, "Caroline", "Caroline");
		checkSameEntry(entries.get(1), invitation, "Alicia", "Daria");

		check(store.approveRequest("Alice", request.getId()).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"The old leader name cannot approve the request.");
		check(store.approveRequest("Alicia", request.getId()).getStatus() == JoinStatus.JOINED
				&& store.findByMember("Caroline").getId() == original.getId(), "The original request should admit Caroline.");
		check(store.acceptInvitation("Dara", invitation.getId()).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"The old candidate name cannot accept the invite.");
		check(store.acceptInvitation("Daria", invitation.getId()).getStatus() == JoinStatus.JOINED,
				"The renamed candidate should still be able to accept.");
	}

	/** Checks succession and keeps an old sender name from carrying over to a replacement. */
	private static void deletingLeaderKeepsPartyEntries()
	{
		final PartyStore store = new InMemoryPartyStore();
		final PartySnapshot original = store.create("Alice");
		final long bobInvite = store.invite("Alice", "Bob").getEntry().getId();
		check(store.acceptInvitation("Bob", bobInvite).getStatus() == JoinStatus.JOINED, "Bob should join Alice.");
		final PartyPendingEntry invitation = store.invite("Alice", "Carol").getEntry();
		final PartyPendingEntry request = store.requestJoin("Dara", "Alice").getEntry();

		store.deleteCharacter("Alice");
		check(store.findByMember("Alice") == null && store.findByMember("Bob").getId() == original.getId()
				&& "Bob".equals(store.findByMember("Bob").getLeader()), "Deletion should apply ordinary succession.");
		final List<PartyPendingEntry> surviving = PartyInvitationTest.visibleEntries(store, "Bob");
		check(surviving.size() == 2, "Both party-owned entries should survive.");
		checkSameEntry(surviving.get(0), invitation, "Bob", "Carol");
		checkSameEntry(surviving.get(1), request, "Dara", "Dara");

		check(store.create("Alice") != null && store.pendingEntries("Alice").isEmpty(),
				"A replacement named Alice should start without any old party state.");
		check(store.approveRequest("Alice", request.getId()).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"The replacement must not inherit approval authority.");
		check(store.approveRequest("Bob", request.getId()).getStatus() == JoinStatus.JOINED,
				"Bob can approve the original request.");

		final long doomedInvite = store.invite("Alice", "Eve").getEntry().getId();
		store.deleteCharacter("Alice");
		check(store.acceptInvitation("Eve", doomedInvite).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"Deleting the last member should retire that party's entries.");
	}

	/**
	 * Releases an approval and candidate deletion together, then reuses the name.
	 *
	 * @throws Exception if either race worker fails or times out
	 */
	private static void deletionRacingApprovalClearsReusedName() throws Exception
	{
		final PartyStore store = new InMemoryPartyStore();
		store.create("Alice");
		store.create("Dara");
		final long oldRequest = store.requestJoin("Bob", "Alice").getEntry().getId();
		final long oldInvite = store.invite("Dara", "Bob").getEntry().getId();
		final List<JoinStatus> outcomes = PartyInvitationTest.runTogether(
				() -> store.approveRequest("Alice", oldRequest).getStatus(),
				() -> {
					store.deleteCharacter("Bob");
					return JoinStatus.ENTRY_UNAVAILABLE;
				});
		check(outcomes.get(0) == JoinStatus.JOINED || outcomes.get(0) == JoinStatus.ENTRY_UNAVAILABLE,
				"Approval may win first or find the request already retired.");
		check(store.findByMember("Bob") == null && store.pendingEntries("Bob").isEmpty(),
				"Either ordering must leave the deleted character's key empty.");
		final long replacement = store.requestJoin("Bob", "Alice").getEntry().getId();
		check(replacement != oldRequest && store.approveRequest("Alice", oldRequest).getStatus() == JoinStatus.ENTRY_UNAVAILABLE
				&& store.acceptInvitation("Bob", oldInvite).getStatus() == JoinStatus.ENTRY_UNAVAILABLE,
				"Old pending IDs cannot admit a replacement character.");
	}

	/**
	 * Runs successful and unsuccessful changes through the native directory against
	 * the disposable database. The unsaved actor tests a write that changes zero rows.
	 */
	private static void checkPlayerDirectory()
	{
		final String suffix = UUID.randomUUID().toString().substring(0, 8);
		final String original = "Life" + suffix;
		final String renamed = "Renamed" + suffix;
		final String unsavedName = "Unsaved" + suffix;
		final MOB player = createFixturePlayer(original);
		final MOB unsaved = createFixturePlayer(unsavedName);
		final MOB replacement = createFixturePlayer(renamed);
		try
		{
			CMLib.database().DBCreateCharacter(player);
			CMLib.players().addPlayer(unsaved);
			check(CMLib.parties().createParty(player) == CreateResult.CREATED, "The saved character should create a party.");
			final long partyId = CMLib.parties().findParty(player).getId();
			check(CMLib.parties().createParty(unsaved) == CreateResult.CREATED, "Set up party state for a rejected rename.");

			check(!CMLib.players().renamePlayer(unsaved, unsavedName, "Missing" + suffix)
					&& unsavedName.equals(unsaved.Name()) && unsavedName.equals(CMLib.parties().findParty(unsaved).getLeader()),
					"A failed database rename should preserve the live name and party.");
			check(!CMLib.players().obliteratePlayer(unsaved, false, true) && !unsaved.amDestroyed()
					&& CMLib.parties().findParty(unsaved) != null, "A failed database delete should preserve party state.");
			check(!CMLib.players().renamePlayer(player, original, unsavedName) && original.equals(player.Name()),
					"An occupied name must be rejected before changing either character.");
			check(CMLib.players().renamePlayer(player, original, renamed), "The saved character should be renamed.");
			check(CMLib.database().DBUserSearch(original) == null && CMLib.database().DBUserSearch(renamed) != null,
					"Only the renamed character should be in the database.");
			check(CMLib.parties().findParty(player).getId() == partyId
					&& CMLib.parties().findParty(player).getLeader().equals(renamed), "The live directory should migrate the party.");

			check(CMLib.players().obliteratePlayer(player, false, true) && player.amDestroyed()
					&& CMLib.database().DBUserSearch(renamed) == null,
					"The native deletion should remove the saved character.");
			CMLib.database().DBCreateCharacter(replacement);
			check(CMLib.database().DBUserSearch(renamed) != null, "The same name should be available for a new character.");
			check(CMLib.parties().findParty(replacement) == null
					&& CMLib.parties().createParty(replacement) == CreateResult.CREATED,
					"A new character with the same name should have no membership to inherit.");
			check(!CMLib.parties().leaveParty(player) && CMLib.parties().findParty(replacement) != null,
					"A delayed command from the old object must not change the replacement's party.");
		}
		finally
		{
			if(!player.amDestroyed())
				CMLib.players().obliteratePlayer(player, false, true);
			CMLib.parties().leaveParty(unsaved);
			CMLib.players().delPlayer(unsaved);
			unsaved.destroy();
			if(player.amDestroyed() && CMLib.database().DBUserSearch(renamed) != null)
				CMLib.players().obliteratePlayer(replacement, false, true);
			else
				replacement.destroy();
		}
	}

	/**
	 * Creates a player object that can be saved to the disposable database.
	 *
	 * @param name a unique fixture name
	 * @return the player object, without a session
	 */
	private static MOB createFixturePlayer(final String name)
	{
		final MOB player = CMClass.getMOB("StdMOB");
		player.setName(name);
		player.setPlayerStats((PlayerStats)CMClass.getCommon("DefaultPlayerStats"));
		player.baseCharStats().setMyRace(CMClass.getRace("Human"));
		player.baseCharStats().setCurrentClass(CMClass.getCharClass("Fighter"));
		player.baseCharStats().setCurrentClassLevel(1);
		return player;
	}

	/**
	 * Checks the parts of an entry that must survive a lifecycle change.
	 *
	 * @param current the entry after the change
	 * @param original the entry before the change
	 * @param sender the expected sender after the change
	 * @param candidate the expected candidate after the change
	 */
	private static void checkSameEntry(final PartyPendingEntry current, final PartyPendingEntry original,
			final String sender, final String candidate)
	{
		check(current.getId() == original.getId() && current.getPartyId() == original.getPartyId()
				&& current.getType() == original.getType() && current.getExpiresAt().equals(original.getExpiresAt())
				&& sender.equals(current.getSenderKey()) && candidate.equals(current.getCandidateKey()),
				"The original entry and deadline should survive with the right names.");
	}

	/**
	 * Stops a case with its expected result.
	 *
	 * @param condition whether the expected state was reached
	 * @param message the expected result
	 */
	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}
}

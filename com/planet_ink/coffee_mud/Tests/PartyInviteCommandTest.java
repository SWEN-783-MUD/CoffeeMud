package com.planet_ink.coffee_mud.Tests;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Common.FakeSession;
import com.planet_ink.coffee_mud.Common.interfaces.PlayerStats;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMClass;
import com.planet_ink.coffee_mud.core.CMLib;
import com.planet_ink.coffee_mud.core.Directions;
import com.planet_ink.coffee_mud.core.party.PartyStore.PartySnapshot;

/** Runs both ways of joining through the live party library and command registry. */
public class PartyInviteCommandTest extends StdTest
{
	private static final Pattern ACCEPT_COMMAND = Pattern.compile("PARTY ACCEPT ([0-9]+)");

	private static final Pattern APPROVE_COMMAND = Pattern.compile("PARTY APPROVE ([0-9]+)");

	private static final Pattern SENT_ID = Pattern.compile("(?:Invitation|Request) ID is ([0-9]+)");

	/**
	 * Identifies this fixture for CoffeeMud's test command.
	 *
	 * @return the test ID
	 */
	@Override
	public String ID()
	{
		return "PartyInviteCommandTest";
	}

	/**
	 * Registers the fixture with the party tests that run in a booted host.
	 *
	 * @return the groups that include this fixture
	 */
	@Override
	public String[] getTestGroups()
	{
		return new String[] {"all", "party"};
	}

	/**
	 * Gives test players rooms and online sessions, then runs the commands they would type.
	 *
	 * @param mob the test runner's actor, separate from the players in this fixture
	 * @param metaFlags CoffeeMud's test command flags
	 * @param what the ID or group used to select this fixture
	 * @param commands extra test arguments
	 * @return a failure message, or {@code null} if the walkthrough passed
	 */
	@Override
	public String doTest(final MOB mob, final int metaFlags, final String what, final List<String> commands)
	{
		final String suffix = UUID.randomUUID().toString().substring(0, 8);
		final Area area = CMClass.getAreaType("StdArea");
		final Room hall = CMClass.getLocale("StdRoom");
		final Room passage = CMClass.getLocale("StdRoom");
		try
		{
			// Connect the rooms through the normal movement path.
			area.setName("PartyTest" + suffix);
			hall.setRoomID(area.Name() + "#1");
			passage.setRoomID(area.Name() + "#2");
			hall.setArea(area);
			passage.setArea(area);
			hall.setRawExit(Directions.NORTH, CMClass.getExit("Open"));
			passage.setRawExit(Directions.SOUTH, CMClass.getExit("Open"));
			hall.rawDoors()[Directions.NORTH] = passage;
			passage.rawDoors()[Directions.SOUTH] = hall;

			try(TestPlayer alice = new TestPlayer("Alice" + suffix, hall);
				TestPlayer bob = new TestPlayer("Bob" + suffix, passage);
				TestPlayer carol = new TestPlayer("Carol" + suffix, passage))
			{
				runWalkthrough(alice, bob, hall, passage);
				runClosureWalkthrough(alice, bob, carol);
				runRequestWalkthrough(alice, bob, carol);
			}
			return null;
		}
		catch(final AssertionError | Exception error)
		{
			return error.toString();
		}
		finally
		{
			hall.destroy();
			passage.destroy();
			area.destroy();
		}
	}

	private static void runClosureWalkthrough(final TestPlayer alice, final TestPlayer bob, final TestPlayer carol)
	{
		final String aliceName = alice.mob.Name();
		final String carolName = carol.mob.Name();
		final String inviteId = sentId(alice.command("PARTY", "INVITE", carolName));
		check(alice.command("PARTY", "PENDING").contains("PARTY CANCEL " + inviteId),
				"The leader should be able to find an invitation to cancel.");
		check(carol.command("PARTY", "PENDING").contains("PARTY DECLINE " + inviteId),
				"The invite recipient should see how to decline.");
		check(bob.command("PARTY", "PENDING").contains("no pending party invitations or requests"),
				"An ordinary member should not see the party's outgoing invites.");
		check(bob.command("PARTY", "CANCEL", inviteId).contains("unavailable"),
				"Knowing the ID should not let a member cancel the invite.");
		check(carol.command("PARTY", "DECLINE", inviteId).contains("You declined the party invitation"),
				"Carol should be able to turn down her invitation.");
		check(alice.output().contains(carolName + " declined your party invitation"), "Alice should see the reply.");
		check(carol.command("PARTY", "ACCEPT", inviteId).contains("unavailable"),
				"The declined invitation should no longer be usable.");

		final String requestId = sentId(carol.command("PARTY", "REQUEST", aliceName));
		final String outgoing = carol.command("PARTY", "PENDING");
		check(outgoing.contains(aliceName) && outgoing.contains("PARTY CANCEL " + requestId),
				"The requester should see the party leader and the command to withdraw.");
		check(alice.command("PARTY", "CANCEL", requestId).contains("unavailable"),
				"The leader can decline a request but cannot withdraw it on Carol's behalf.");
		checkCombatRestriction(carol, "CANCEL", requestId);
		check(carol.command("PARTY", "CANCEL", requestId).contains("You cancelled the join request"),
				"Carol should be able to withdraw after combat.");
		check(alice.output().contains(carolName + " cancelled the join request"), "Alice should see the withdrawal.");
		check(alice.command("PARTY", "APPROVE", requestId).contains("unavailable"),
				"The leader should not be able to approve a withdrawn request.");
	}

	/**
	 * Reads the ID from the sender's confirmation.
	 *
	 * @param output the output of opening a new invitation or request
	 * @return the ID as the player would enter it
	 */
	static String sentId(final String output)
	{
		final Matcher match = SENT_ID.matcher(output);
		check(match.find(), "The sender should see the new pending ID. Output was: " + output);
		return match.group(1);
	}

	private static void runWalkthrough(final TestPlayer alice, final TestPlayer bob,
			final Room hall, final Room passage)
	{
		final String aliceName = alice.mob.Name();
		final String bobName = bob.mob.Name();
		check(alice.command("PARTY", "CREATE").contains("You created a party"), "Alice should create a party.");
		check(alice.command("HELP", "PARTY").contains("PARTY PENDING"), "Party help should list the new commands.");

		// Only an exact online name should reach the recipient.
		check(alice.command("PARTY", "INVITE", bobName.substring(0, bobName.length() - 1))
				.contains("No online player has the character name"), "A partial name must not create an invite.");
		check(alice.command("PARTY", "INVITE", bobName).contains("You invited " + bobName),
				"Alice should be able to invite Bob across rooms.");
		final String notification = bob.output();
		check(notification.contains(aliceName + " invited you"), "Bob should receive Alice's invitation.");
		final Matcher accept = ACCEPT_COMMAND.matcher(notification);
		check(accept.find(), "The notification should show the acceptance command.");
		final String id = accept.group(1);
		check(CMLib.parties().findParty(bob.mob) == null, "An invitation alone must leave Bob without a party.");
		// Repeating the invite gives Alice the same ID without another notice for Bob.
		final String repeat = alice.command("PARTY", "INVITE", bobName);
		check(repeat.contains("already pending") && repeat.contains("ID is " + id), "A repeat must report the original ID.");
		check(notification.equals(bob.output()), "A repeat invite must not send another notice.");

		check(bob.command("PARTY", "REQUEST", aliceName).contains("PARTY ACCEPT " + id),
				"A request that crosses an invite should show the original acceptance command.");
		check(CMLib.parties().findParty(bob.mob) == null, "A crossed request must not admit Bob.");

		final String pending = bob.command("PARTY", "PENDING");
		check(pending.contains(aliceName) && pending.contains("seconds left") && pending.contains("PARTY ACCEPT " + id),
				"Pending should show the sender, time remaining, and the original acceptance command.");
		check(bob.command("PARTY", "ACCEPT", "not-an-id").contains("Use PARTY ACCEPT"),
				"An invalid ID should give the player usage help.");

		checkCombatRestriction(bob, "ACCEPT", id);
		check(CMLib.parties().findParty(bob.mob) == null, "A blocked acceptance should leave membership empty.");

		// Joining updates the roster while the players stay in their own rooms.
		check(bob.command("PARTY", "ACCEPT", id).contains("You joined the party"), "Bob should join after combat.");
		final PartySnapshot joined = CMLib.parties().findParty(bob.mob);
		check(joined != null && joined.getMembers().equals(Arrays.asList(aliceName, bobName)),
				"The roster should include Alice and Bob in join order.");
		check(CMLib.parties().findParty(alice.mob).getId() == joined.getId(), "Both players should find the same party.");
		check(alice.command("PARTY").contains(bobName) && bob.command("PARTY").contains(aliceName),
				"Both players should see the shared roster through PARTY.");
		check(alice.mob.location() == hall && bob.mob.location() == passage && bob.mob.amFollowing() == null,
				"Joining should keep both locations and leave Bob free to move independently.");

		// Move through a real exit and confirm membership after the rooms separate again.
		bob.command("SOUTH");
		check(bob.mob.location() == hall, "Bob should be able to walk to Alice.");
		bob.command("NORTH");
		check(bob.mob.location() == passage && alice.mob.location() == hall, "Bob should be able to walk away alone.");
		check(CMLib.parties().findParty(bob.mob).getId() == joined.getId(), "Independent movement should keep membership.");
	}

	private static void runRequestWalkthrough(final TestPlayer alice, final TestPlayer bob, final TestPlayer carol)
	{
		final String aliceName = alice.mob.Name();
		final String bobName = bob.mob.Name();
		final String carolName = carol.mob.Name();
		check(carol.command("PARTY", "REQUEST", "Nobody" + carolName).contains("No online player"),
				"A request to an offline name should fail clearly.");
		check(carol.command("PARTY", "REQUEST", bobName).contains("does not lead a party"),
				"A request should name the leader, not another member.");
		check(carol.command("PARTY", "REQUEST", aliceName).contains("You asked to join " + aliceName),
				"Carol should be able to ask Alice to join.");

		// An invite that crosses the request should show Alice how to approve the first entry.
		final String notice = alice.output();
		final int start = notice.lastIndexOf(carolName + " asked to join");
		check(start >= 0, "Alice should be told who asked to join.");
		final Matcher approve = APPROVE_COMMAND.matcher(notice.substring(start));
		check(approve.find(),
				"The leader should get the requester and approval command.");
		final String id = approve.group(1);
		check(carol.command("PARTY", "REQUEST", aliceName).contains("Request ID is " + id),
				"A repeat should show Carol the original ID.");
		check(alice.output().equals(notice), "A repeat request should not notify the leader again.");
		check(alice.command("PARTY", "INVITE", carolName).contains("PARTY APPROVE " + id),
				"An invite that crosses a request should show the original approval command.");
		check(CMLib.parties().findParty(carol.mob) == null, "Carol still needs explicit approval.");
		check(bob.command("PARTY", "APPROVE", id).contains("That request is unavailable"),
				"Bob cannot approve while Alice is leader.");

		// Transfer keeps Alice in the party and moves approval authority to Bob.
		check(bob.command("PARTY", "LEADER", aliceName).contains("Only the current party leader"),
				"An ordinary member cannot pass leadership.");
		check(alice.command("PARTY", "LEADER", carolName).contains("is not in your party"),
				"Alice cannot transfer to a character with just a pending request.");
		check(alice.command("PARTY", "LEADER", bobName.toUpperCase(Locale.ROOT)).contains("passed party leadership to " + bobName),
				"Alice can pass leadership across rooms using the exact name in a different case.");
		check(bob.output().contains(aliceName + " passed party leadership to you"), "Bob should see the transfer notice.");
		final PartySnapshot transferred = CMLib.parties().findParty(alice.mob);
		check(bobName.equals(transferred.getLeader()) && transferred.getMembers().equals(Arrays.asList(aliceName, bobName)),
				"Transfer should keep Alice and preserve the join order.");
		final String pending = bob.command("PARTY", "PENDING");
		check(pending.contains(carolName) && pending.contains("seconds left") && pending.contains("PARTY APPROVE " + id),
				"The new leader should see Carol's original request and remaining time.");
		check(alice.command("PARTY", "APPROVE", id).contains("That request is unavailable"),
				"Alice should lose the ability to approve.");
		check(carol.command("PARTY", "PENDING").contains("You asked to join " + bobName),
				"Carol's outgoing request should name the new leader.");
		checkCombatRestriction(bob, "APPROVE", id);
		check(CMLib.parties().findParty(carol.mob) == null, "A blocked approval should leave membership empty.");

		// Approval sends feedback to both sides and keeps their positions and follow links.
		final Room carolRoom = carol.mob.location();
		check(bob.command("PARTY", "APPROVE", id).contains(carolName + " joined your party"),
				"The current leader should be able to approve after combat.");
		check(carol.output().contains(bobName + " approved your request"), "Carol should be notified of approval.");
		check(carol.command("PARTY").contains("Party leader: " + bobName),
				"Carol should see the new roster after approval.");
		check(carol.mob.location() == carolRoom && carol.mob.amFollowing() == null,
				"Approval should neither move Carol nor start following.");
		check(bob.command("PARTY", "LEAVE").contains("You left the party"), "Bob should be able to leave.");
		check(carol.command("PARTY").contains("Party leader: " + aliceName),
				"Alice should take over again because she was first to join.");
	}

	private static void checkCombatRestriction(final TestPlayer player, final String action, final String id)
	{
		final Room room = player.mob.location();
		final MOB opponent = CMClass.getMOB("StdMOB");
		try
		{
			// Put the opponent in the room so CoffeeMud recognizes the fight.
			opponent.setLocation(room);
			room.addInhabitant(opponent);
			player.mob.setVictim(opponent);
			check(player.mob.isInCombat(), "The fixture should put the player in combat.");
			check(player.command("PARTY", "PENDING").contains("PARTY " + action + " " + id),
					"Combat should allow pending reads.");
			check(player.command("PARTY", action, id).contains("Wait until combat ends"),
					"Combat should block " + action + ".");
		}
		finally
		{
			player.mob.makePeace(true);
			opponent.destroy();
		}
	}

	/** Supplies a real player object with a session that captures command output. */
	static final class TestPlayer implements AutoCloseable
	{
		private final MOB mob = CMClass.getMOB("StdMOB");

		private final RecordingSession session = new RecordingSession();

		/**
		 * Places an ordinary player in a room and registers the session with the host.
		 *
		 * @param name the unique test character name
		 * @param room the character's starting room
		 */
		TestPlayer(final String name, final Room room)
		{
			mob.setName(name);
			mob.setPlayerStats((PlayerStats)CMClass.getCommon("DefaultPlayerStats"));
			mob.baseCharStats().setMyRace(CMClass.getRace("Human"));
			mob.recoverCharStats();
			mob.recoverPhyStats();
			mob.recoverMaxState();
			mob.resetToMaxState();
			mob.setStartRoom(room);
			mob.setLocation(room);
			room.addInhabitant(mob);

			// Make this character visible to the same online lookup used in play.
			session.initialize(null, "MEMORY");
			session.setMob(mob);
			mob.setSession(session);
			CMLib.sessions().add(session);
		}

		/**
		 * Gives other party command fixtures the character they are testing.
		 *
		 * @return the fixture character
		 */
		MOB getMob()
		{
			return mob;
		}

		/**
		 * Runs a command through CoffeeMud and returns the output added by that command.
		 *
		 * @param words the command words as the client would submit them
		 * @return the new output for this player
		 */
		String command(final String... words)
		{
			final int before = output().length();
			mob.doCommand(Arrays.asList(words), 0);
			return output().substring(before);
		}

		/**
		 * Reads all messages saved by the recording session.
		 *
		 * @return the output captured so far
		 */
		String output()
		{
			return session.getAfkMessage();
		}

		/** Removes party state, unregisters the session, and destroys the player. */
		@Override
		public void close()
		{
			CMLib.parties().leaveParty(mob);
			CMLib.sessions().remove(session);
			mob.setSession(null);
			session.setMob(null);
			mob.destroy();
		}
	}

	/** Captures output in memory while remaining visible to the player's session getter. */
	private static final class RecordingSession extends FakeSession
	{
		/**
		 * Keeps the test session visible to CoffeeMud's online lookup.
		 *
		 * @return {@code false} so the player's session getter exposes it
		 */
		@Override
		public boolean isFake()
		{
			return false;
		}
	}

	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}
}

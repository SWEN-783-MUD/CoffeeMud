package com.planet_ink.coffee_mud.Tests;

import java.util.Arrays;
import java.util.List;
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

/** Runs the invitation walkthrough through the live party library and command registry. */
public class PartyInviteCommandTest extends StdTest
{
	/** The acceptance command shown to a recipient, including the invitation ID. */
	private static final Pattern ACCEPT_COMMAND = Pattern.compile("PARTY ACCEPT ([0-9]+)");

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
	 * Gives two test players rooms and online sessions, then runs the commands they would type.
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
				TestPlayer bob = new TestPlayer("Bob" + suffix, passage))
			{
				runWalkthrough(alice, bob, hall, passage);
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

	/**
	 * Checks cross-room joining, independent movement, and leadership through player commands.
	 *
	 * @param alice the party creator
	 * @param bob the player receiving an invitation
	 * @param hall Alice's room
	 * @param passage Bob's room
	 */
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
		final String pending = bob.command("PARTY", "PENDING");
		check(pending.contains(aliceName) && pending.contains("seconds left") && pending.contains("PARTY ACCEPT " + id),
				"Pending should show the sender, time remaining, and the original acceptance command.");
		check(bob.command("PARTY", "ACCEPT", "not-an-id").contains("Use PARTY ACCEPT"),
				"An invalid ID should give the player usage help.");

		checkCombatAcceptance(bob, passage, id);

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

		check(alice.command("PARTY", "LEAVE").contains("You left the party"), "Alice should be able to leave.");
		check(CMLib.parties().findParty(alice.mob) == null, "Leaving should remove Alice's membership.");
		check(bob.command("PARTY").contains("Your party leader is " + bobName), "Bob should become leader.");
	}

	/**
	 * Checks that a player in combat can read an invite and accept it after the fight.
	 *
	 * @param bob the recipient to put in combat
	 * @param room Bob's current room
	 * @param id the ID from the invitation notice
	 */
	private static void checkCombatAcceptance(final TestPlayer bob, final Room room, final String id)
	{
		final MOB opponent = CMClass.getMOB("StdMOB");
		try
		{
			// Put the opponent in the room so CoffeeMud recognizes the fight.
			opponent.setLocation(room);
			room.addInhabitant(opponent);
			bob.mob.setVictim(opponent);
			check(bob.mob.isInCombat(), "The fixture should put Bob in combat.");
			check(bob.command("PARTY", "PENDING").contains("PARTY ACCEPT " + id), "Combat should allow pending reads.");
			check(bob.command("PARTY", "ACCEPT", id).contains("Wait until combat ends"), "Combat should block acceptance.");
			check(CMLib.parties().findParty(bob.mob) == null, "A blocked acceptance should leave membership empty.");
		}
		finally
		{
			bob.mob.makePeace(true);
			opponent.destroy();
		}
	}

	/** Supplies a real player object with a session that captures command output. */
	private static final class TestPlayer implements AutoCloseable
	{
		/** The character that enters the commands. */
		private final MOB mob = CMClass.getMOB("StdMOB");

		/** The session registered for online lookup and used to read player messages. */
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

	/**
	 * Stops the walkthrough with the reason a check failed.
	 *
	 * @param condition whether the expected result occurred
	 * @param message the result the walkthrough expected
	 */
	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}
}

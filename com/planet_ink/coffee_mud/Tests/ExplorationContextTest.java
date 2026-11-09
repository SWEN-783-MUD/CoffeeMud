package com.planet_ink.coffee_mud.Tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Common.interfaces.Session;
import com.planet_ink.coffee_mud.Libraries.interfaces.PartyLibrary;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Tests.PartyInviteCommandTest.TestPlayer;
import com.planet_ink.coffee_mud.core.CMClass;
import com.planet_ink.coffee_mud.core.CMLib;
import com.planet_ink.coffee_mud.core.Directions;
import com.planet_ink.coffee_mud.core.party.ExplorationContext;
import com.planet_ink.coffee_mud.core.party.ExplorationContext.Activity;
import com.planet_ink.coffee_mud.core.party.PartyTravel.Arrival;
import com.planet_ink.coffee_mud.core.party.PartyTravel.Completed;

/** Checks the activity command and the context an encounter receives from actual arrivals. */
public class ExplorationContextTest extends StdTest
{
	/** {@inheritDoc} */
	@Override
	public String ID()
	{
		return "ExplorationContextTest";
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
		final String suffix = UUID.randomUUID().toString().substring(0,8);
		final Area area = CMClass.getAreaType("StdArea");
		area.setName("Exploration" + suffix);
		final Room hall = createRoom(area,"Hall");
		final Room guardroom = createRoom(area,"Guardroom");
		final Room alcove = createRoom(area,"Alcove");
		final List<Completed> completed = new ArrayList<Completed>();

		// The first exit has an asymmetric return. The alcove has no return exit.
		connect(hall,Directions.NORTH,guardroom);
		connect(guardroom,Directions.WEST,hall);
		connect(hall,Directions.EAST,alcove);
		try(AutoCloseable subscription = CMLib.tracking().onPartyTravelCompleted(batch -> {
				if((batch.source == hall) || (batch.source == guardroom))
					completed.add(batch);
			});
			TestPlayer alice = new TestPlayer("Alice" + suffix,hall);
			TestPlayer bob = new TestPlayer("Bob" + suffix,hall))
		{
			checkChoices(bob);
			final Arrival bobArrival = checkPartyEntry(alice,bob,guardroom,completed);
			checkResets(alice,bob,bobArrival);
			checkOtherPlacement(bob,hall,guardroom,alcove,completed);
			return null;
		}
		catch(final AssertionError | Exception error)
		{
			return error.toString();
		}
		finally
		{
			hall.destroy();
			guardroom.destroy();
			alcove.destroy();
			area.destroy();
		}
	}

	/**
	 * Makes a room suitable for native movement and inspection.
	 *
	 * @param area the fixture area
	 * @param name the room name
	 * @return the room
	 */
	private static Room createRoom(final Area area, final String name)
	{
		final Room room = CMClass.getLocale("StdRoom");
		room.setArea(area);
		room.setRoomID(area.Name() + "#" + name);
		room.setDisplayText(name);
		return room;
	}

	/**
	 * Sets up one direction of an exit so the return side can be tested separately.
	 *
	 * @param source the room containing the exit
	 * @param direction the direction out
	 * @param destination the room reached
	 */
	private static void connect(final Room source, final int direction, final Room destination)
	{
		source.setRawExit(direction,CMClass.getExit("Open"));
		source.rawDoors()[direction] = destination;
	}

	/**
	 * Checks the command and makes sure inspection and activity choice leave native visibility alone.
	 *
	 * @param player the character who enters the commands
	 */
	private static void checkChoices(final TestPlayer player)
	{
		final ExplorationContext initial = context(player);
		final String status = player.command("EXPLORE");
		check(initial.activity == Activity.NORMAL && initial.entrySide == ExplorationContext.UNKNOWN_ENTRY,
				"A character starts on Normal with an unknown approach.");
		check(status.contains("activity is Normal") && status.contains("EXPLORE NORMAL")
				&& status.contains("EXPLORE AVOID NOTICE"),"EXPLORE should show the current activity and both choices.");
		check(player.command("HELP","EXPLORE").contains("EXPLORE AVOID NOTICE"),"HELP EXPLORE should explain the choices.");

		final MOB character = player.getMob();
		final int disposition = character.phyStats().disposition();
		final int effects = character.numEffects();
		check(player.command("EXPLORE","avoid","NOTICE").contains("now Avoid Notice"),"Activity words are case insensitive.");
		final ExplorationContext selected = context(player);
		check(selected.activity == Activity.AVOID_NOTICE && selected.revision == initial.revision + 1,
				"Selecting Avoid Notice should record one activity change.");

		final String invalid = player.command("EXPLORE","sneak");
		check(invalid.contains("EXPLORE NORMAL") && invalid.contains("EXPLORE AVOID NOTICE"),
				"An unknown choice should show the supported commands.");
		check(player.command("LOOK").contains("Hall"),"Native LOOK should still show the room.");
		player.command("EXAMINE","room");
		player.command("EXITS");
		check(context(player).revision == selected.revision && context(player).activity == Activity.AVOID_NOTICE,
				"Invalid choices and native inspection should leave exploration context alone.");
		check(character.phyStats().disposition() == disposition && character.numEffects() == effects,
				"Activity and inspection commands should not apply stealth effects.");
	}

	/**
	 * Moves two party members with different activities and checks the encounter handoff.
	 *
	 * @param alice the independent mover
	 * @param bob the follower with Avoid Notice selected
	 * @param guardroom the actual destination
	 * @param completed the consumer's recorded travel
	 * @return Bob's arrival for later snapshot checks
	 */
	private static Arrival checkPartyEntry(final TestPlayer alice, final TestPlayer bob, final Room guardroom,
			final List<Completed> completed)
	{
		check(alice.command("PARTY","CREATE").contains("You created a party"),"Alice should create a party.");
		final String id = PartyInviteCommandTest.sentId(alice.command("PARTY","INVITE",bob.getMob().Name()));
		check(bob.command("PARTY","ACCEPT",id).contains("You joined the party"),"Bob should join the party.");
		bob.command("FOLLOW",alice.getMob().Name());
		check(bob.getMob().amFollowing() == alice.getMob(),"Bob should follow Alice.");

		final long beforeEntry = context(bob).revision;
		alice.command("NORTH");
		check(completed.size() == 1 && completed.get(0).arrivals.size() == 2,
				"One completed travel report should contain both characters.");
		final Arrival leader = completed.get(0).arrivals.get(0);
		final Arrival follower = completed.get(0).arrivals.get(1);
		check(leader.player == alice.getMob() && leader.exploration.activity == Activity.NORMAL
				&& follower.player == bob.getMob() && follower.exploration.activity == Activity.AVOID_NOTICE,
				"The arrival report must carry each player's own activity.");
		check(leader.exploration.room == guardroom && follower.exploration.room == guardroom
				&& leader.exploration.entrySide == Directions.WEST && follower.exploration.entrySide == Directions.WEST,
				"Both approaches should use the real west entry into the guardroom.");
		check(follower.exploration.revision == beforeEntry + 1 && context(bob).revision == follower.exploration.revision,
				"One room entry should produce one new exploration revision.");

		bob.command("NORTH");
		check(completed.size() == 1 && context(bob).revision == follower.exploration.revision
				&& context(bob).entrySide == Directions.WEST,"An unsuccessful exit should preserve the last approach.");
		return follower;
	}

	/**
	 * Checks combat restrictions, participant-only reset, and a fresh session.
	 *
	 * @param alice another party member, used to detect a roster-wide reset
	 * @param bob the character whose activity gets reset
	 * @param arrival the snapshot already handed to an encounter consumer
	 */
	private static void checkResets(final TestPlayer alice, final TestPlayer bob, final Arrival arrival)
	{
		bob.getMob().setVictim(alice.getMob());
		try
		{
			check(bob.command("EXPLORE").contains("activity is Avoid Notice"),"Status should stay available during combat.");
			check(bob.command("EXPLORE","NORMAL").contains("Wait until combat ends"),
					"Combat should block a change to Normal.");
			check(context(bob).revision == arrival.exploration.revision,"The blocked change should leave the context alone.");
		}
		finally
		{
			bob.getMob().makePeace(true);
		}

		alice.command("EXPLORE","AVOID","NOTICE");
		final PartyLibrary parties = CMLib.parties();
		parties.resetExplorationActivity(bob.getMob());
		final ExplorationContext afterEncounter = context(bob);
		check(afterEncounter.activity == Activity.NORMAL && afterEncounter.room == arrival.exploration.room
				&& afterEncounter.entrySide == Directions.WEST,"Encounter completion should reset activity and keep the entry.");
		check(context(alice).activity == Activity.AVOID_NOTICE && arrival.exploration.activity == Activity.AVOID_NOTICE,
				"Resetting Bob should leave Alice's selection and Bob's earlier arrival report alone.");

		bob.command("EXPLORE","AVOID","NOTICE");
		final Session session = bob.getMob().session();
		parties.playerSessionEnding(bob.getMob(),session);
		parties.playerSessionStarted(bob.getMob(),session);
		check(context(bob).activity == Activity.NORMAL && context(bob).entrySide == ExplorationContext.UNKNOWN_ENTRY,
				"A fresh session should start on Normal with an unknown entry.");
		check(bob.command("EXPLORE","AVOID","NOTICE").contains("now Avoid Notice"),
				"The new session should be able to select an activity again.");
	}

	/**
	 * Distinguishes a system placement, a forced exit, and a one-way player exit.
	 *
	 * @param player the traveler
	 * @param hall the start of both fixture exits
	 * @param guardroom the destination with a return connection
	 * @param alcove the one-way destination
	 * @param completed the consumer's recorded travel
	 */
	private static void checkOtherPlacement(final TestPlayer player, final Room hall, final Room guardroom,
			final Room alcove, final List<Completed> completed)
	{
		hall.bringMobHere(player.getMob(),false);
		check(context(player).room == hall && context(player).entrySide == ExplorationContext.UNKNOWN_ENTRY
				&& context(player).activity == Activity.AVOID_NOTICE,"A direct placement should clear only the approach.");

		CMLib.commands().forceStandardCommand(player.getMob(),"North",new ArrayList<String>(Arrays.asList("NORTH")));
		check(context(player).room == guardroom && context(player).entrySide == Directions.WEST
				&& completed.size() == 1,"A forced exit should record its approach without publishing a command batch.");
		hall.bringMobHere(player.getMob(),false);
		check(context(player).entrySide == ExplorationContext.UNKNOWN_ENTRY,"Placement should clear a previous known entry.");
		check(player.command("EXPLORE","NORMAL").contains("now Normal"),"EXPLORE NORMAL should confirm the new choice.");

		player.command("EAST");
		check(completed.size() == 2 && completed.get(1).arrivals.size() == 1,"The solo exit should publish one arrival.");
		final ExplorationContext approach = completed.get(1).arrivals.get(0).exploration;
		check(approach.room == alcove && approach.entrySide == ExplorationContext.UNKNOWN_ENTRY
				&& approach.activity == Activity.NORMAL,"A one-way arrival should report an unknown entry and the current activity.");
	}

	/**
	 * Reads the current context from the same library the command uses.
	 *
	 * @param player the character under test
	 * @return the current context snapshot
	 */
	private static ExplorationContext context(final TestPlayer player)
	{
		return CMLib.parties().explorationContext(player.getMob());
	}

	/**
	 * Fails the fixture with the expected behavior when a check fails.
	 *
	 * @param condition whether the expected result occurred
	 * @param message the expected result
	 */
	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}
}

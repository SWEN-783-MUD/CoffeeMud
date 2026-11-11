package com.planet_ink.coffee_mud.Tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Common.interfaces.CharStats;
import com.planet_ink.coffee_mud.Locales.StdRoom;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Tests.PartyInviteCommandTest.TestPlayer;
import com.planet_ink.coffee_mud.core.CMClass;
import com.planet_ink.coffee_mud.core.CMLib;
import com.planet_ink.coffee_mud.core.Directions;
import com.planet_ink.coffee_mud.core.interfaces.Environmental;
import com.planet_ink.coffee_mud.core.party.PartyTravel.Completed;
import com.planet_ink.coffee_mud.core.party.PartyTravel.FailureReason;

/** Runs party travel through registered commands, native room events, and a recording consumer. */
public class PartyTravelTest extends StdTest
{
	/** {@inheritDoc} */
	@Override
	public String ID()
	{
		return "PartyTravelTest";
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
		final String suffix = UUID.randomUUID().toString().substring(0, 8);
		final Area area = CMClass.getAreaType("StdArea");
		final TravelRoom hall = new TravelRoom();
		final TravelRoom guardroom = new TravelRoom();
		final TravelRoom ledge = new TravelRoom();
		final MOB pet = CMClass.getMOB("StdMOB");
		final List<Completed> completed = new ArrayList<Completed>();
		try
		{
			area.setName("PartyTravel" + suffix);
			setUpRoom(hall, area, "Hall");
			setUpRoom(guardroom, area, "Guardroom");
			setUpRoom(ledge, area, "Ledge");
			connect(hall, Directions.NORTH, guardroom);
			connect(guardroom, Directions.WEST, hall);
			connect(hall, Directions.EAST, ledge);
			hall.bringMobHere(pet,false);

			try(AutoCloseable subscription = CMLib.tracking().onPartyTravelCompleted(batch -> {
					if(batch.source == hall)
						completed.add(batch);
				});
				TestPlayer alice = new TestPlayer("Alice" + suffix, hall);
				TestPlayer bob = new TestPlayer("Bob" + suffix, hall);
				TestPlayer carol = new TestPlayer("Carol" + suffix, hall);
				TestPlayer dara = new TestPlayer("Dara" + suffix, hall);
				TestPlayer eve = new TestPlayer("Eve" + suffix, hall))
			{
				makeParty(alice,bob,carol,dara,eve);
				pet.setFollowing(alice.getMob());
				checkCompletedArrivals(alice,bob,carol,dara,eve,hall,guardroom,completed);
				check(pet.location() == guardroom, "Native NPC followers should still travel.");
				pet.setFollowing(null);
				checkBlockedBranch(alice,bob,carol,dara,hall,guardroom,completed);
				checkIndependentTravel(alice,bob,carol,hall,guardroom,ledge,completed);
				checkDepartureDuringTravel(alice,bob,carol,dara,hall,guardroom,completed,false);
				checkDepartureDuringTravel(alice,bob,carol,dara,hall,guardroom,completed,true);
			}
			return null;
		}
		catch(final AssertionError | Exception error)
		{
			return error.toString();
		}
		finally
		{
			pet.destroy();
			hall.destroy();
			guardroom.destroy();
			ledge.destroy();
			area.destroy();
		}
	}

	private static void setUpRoom(final Room room, final Area area, final String name)
	{
		room.setArea(area);
		room.setRoomID(area.Name() + "#" + name);
		room.setDisplayText(name);
	}

	private static void connect(final Room source, final int direction, final Room destination)
	{
		source.setRawExit(direction, CMClass.getExit("Open"));
		source.rawDoors()[direction] = destination;
	}

	private static void makeParty(final TestPlayer leader, final TestPlayer... members)
	{
		check(leader.command("PARTY", "CREATE").contains("You created a party"), "Alice should create a party.");
		leader.getMob().baseCharStats().setStat(CharStats.STAT_CHARISMA, 24);
		leader.getMob().recoverCharStats();
		for(final TestPlayer member : members)
		{
			final String id = PartyInviteCommandTest.sentId(leader.command("PARTY", "INVITE", member.getMob().Name()));
			check(member.command("PARTY", "ACCEPT", id).contains("You joined the party"), "The player should join Alice.");
		}
	}

	private static void checkCompletedArrivals(final TestPlayer alice, final TestPlayer bob, final TestPlayer carol,
			final TestPlayer dara, final TestPlayer eve, final TravelRoom hall, final TravelRoom guardroom,
			final List<Completed> completed)
	{
		follow(bob,alice);
		follow(carol,bob);
		follow(dara,alice);
		alice.command("NORTH");
		final Completed batch = onlyBatch(completed);
		checkArrivals(batch,alice,bob,carol,dara);
		check(batch.source == hall && batch.destination == guardroom && batch.direction == Directions.NORTH,
				"The batch must describe the actual exit.");
		check(batch.arrivals.stream().allMatch(arrival -> arrival.entrySide == Directions.WEST),
				"The real return exit is west even though the party traveled north.");
		check(batch.failures.isEmpty() && eve.getMob().location() == hall && guardroom.isInhabitant(carol.getMob()),
				"An unrelated party member must not be moved or reported.");
		check(guardroom.entries.equals(Arrays.asList(alice.getMob(),bob.getMob(),carol.getMob(),dara.getMob())),
				"Native entry messages must be marked for deferral and observed in arrival order.");
		check(bob.getMob().amFollowing() == alice.getMob() && carol.getMob().amFollowing() == bob.getMob(),
				"Automatic steps must retain their links.");
	}

	private static void checkBlockedBranch(final TestPlayer alice, final TestPlayer bob, final TestPlayer carol,
			final TestPlayer dara, final TravelRoom hall, final TravelRoom guardroom, final List<Completed> completed)
	{
		reset(hall,guardroom,completed,alice,bob,carol,dara);
		guardroom.blocked = bob.getMob();
		final int before = bob.output().length();
		alice.command("GO","NORTH");
		final Completed batch = onlyBatch(completed);
		checkArrivals(batch,alice,dara);
		check(bob.getMob().location() == hall && carol.getMob().location() == hall && bob.getMob().amFollowing() == null,
				"The blocked player must stay behind and lose the link to Alice.");
		check(carol.getMob().amFollowing() == bob.getMob(), "The descendant must keep its own link.");
		check(bob.output().substring(before).contains("stopped following"), "The blocked follower should get an explanation.");
		check(batch.failures.size() == 2 && batch.failures.get(0).player == bob.getMob()
				&& batch.failures.get(0).reason == FailureReason.BLOCKED
				&& batch.failures.get(1).player == carol.getMob()
				&& batch.failures.get(1).reason == FailureReason.PARENT_STAYED, "The consumer should see why each step stayed behind.");
		guardroom.blocked = null;
	}

	private static void checkIndependentTravel(final TestPlayer alice, final TestPlayer bob, final TestPlayer carol,
			final TravelRoom hall, final TravelRoom guardroom, final TravelRoom ledge, final List<Completed> completed)
	{
		reset(hall,guardroom,completed,alice,bob,carol);
		follow(bob,alice);
		bob.command("WEST");
		check(bob.getMob().amFollowing() == alice.getMob() && completed.isEmpty(),
				"A failed independent command must keep the link and publish nothing.");
		bob.command("RUN","NORTH");
		checkArrivals(onlyBatch(completed),bob,carol);
		check(bob.getMob().amFollowing() == null && carol.getMob().amFollowing() == bob.getMob()
				&& alice.getMob().location() == hall, "A successful independent step detaches only its old outgoing link.");

		reset(hall,guardroom,completed,alice,bob,carol);
		follow(bob,alice);
		CMLib.commands().forceStandardCommand(bob.getMob(),"North",new ArrayList<String>(Arrays.asList("NORTH")));
		check(bob.getMob().location() == guardroom && bob.getMob().amFollowing() == alice.getMob()
				&& completed.isEmpty() && guardroom.entries.isEmpty(),
				"A forced command uses native travel without an independent detach or completion.");

		reset(hall,guardroom,completed,alice,bob,carol);
		bob.command("EAST");
		checkArrivals(onlyBatch(completed),bob,carol);
		check(completed.get(0).destination == ledge && completed.get(0).arrivals.get(0).entrySide == -1,
				"A one-way entry must report an unknown return side.");
	}

	private static void checkDepartureDuringTravel(final TestPlayer alice, final TestPlayer bob, final TestPlayer carol,
			final TestPlayer dara, final TravelRoom hall, final TravelRoom guardroom,
			final List<Completed> completed, final boolean disconnect)
	{
		reset(hall,guardroom,completed,alice,bob,carol,dara);
		if(CMLib.parties().findParty(bob.getMob()) == null)
		{
			final String id = PartyInviteCommandTest.sentId(alice.command("PARTY", "INVITE", bob.getMob().Name()));
			check(bob.command("PARTY", "ACCEPT", id).contains("You joined the party"), "Bob should rejoin.");
		}
		follow(bob,alice);
		follow(carol,bob);
		final ExecutorService worker = Executors.newSingleThreadExecutor();
		try
		{
			hall.interruptLeaveFor = bob.getMob();
			hall.duringLeave = () -> {
				try
				{
					worker.submit(() -> {
						if(disconnect)
							CMLib.parties().playerSessionEnding(bob.getMob(),bob.getMob().session());
						else
							CMLib.parties().leaveParty(bob.getMob());
					}).get(5,TimeUnit.SECONDS);
				}
				catch(final Exception error)
				{
					throw new IllegalStateException("The room callback could not update party state.",error);
				}
			};
			alice.command("NORTH");
			checkArrivals(onlyBatch(completed),alice,dara);
			check(!guardroom.isInhabitant(bob.getMob()) && hall.isInhabitant(bob.getMob()) == !disconnect
					&& carol.getMob().location() == hall, "A late departure must stop the branch without restoring a closed session.");
			check(completed.get(0).failures.get(0).reason == FailureReason.NO_LONGER_ELIGIBLE,
					"The failed step should report that its captured state expired.");
		}
		finally
		{
			worker.shutdownNow();
		}
	}

	private static void reset(final Room source, final TravelRoom destination, final List<Completed> completed,
			final TestPlayer... players)
	{
		for(final TestPlayer player : players)
		{
			if(player.getMob().location() != source)
				source.bringMobHere(player.getMob(),false);
			player.getMob().resetToMaxState();
		}
		destination.entries.clear();
		completed.clear();
	}

	private static void follow(final TestPlayer follower, final TestPlayer target)
	{
		if(follower.getMob().amFollowing() != target.getMob())
			follower.command("FOLLOW",target.getMob().Name());
		check(follower.getMob().amFollowing() == target.getMob(), "The follow link should be set before travel.");
	}

	private static Completed onlyBatch(final List<Completed> completed)
	{
		check(completed.size() == 1, "Exactly one completion should be published. Received " + completed.size());
		return completed.get(0);
	}

	private static void checkArrivals(final Completed batch, final TestPlayer... players)
	{
		final List<MOB> expected = Arrays.stream(players).map(TestPlayer::getMob).collect(Collectors.toList());
		final List<MOB> actual = batch.arrivals.stream().map(arrival -> arrival.player).collect(Collectors.toList());
		check(actual.equals(expected), "The consumer must receive the successful steps in order. Received " + actual);
	}

	/** Records native entry messages and controls one veto or concurrent departure. */
	private static final class TravelRoom extends StdRoom
	{
		private final List<MOB> entries = new ArrayList<MOB>();
		private MOB blocked;
		private MOB interruptLeaveFor;
		/** A one-time state change during native leave handling. */
		private Runnable duringLeave;

		/** {@inheritDoc} */
		@Override
		public boolean okMessage(final Environmental host, final CMMsg msg)
		{
			if((msg.sourceMinor() == CMMsg.TYP_ENTER) && (msg.source() == blocked))
				return false;
			return super.okMessage(host,msg);
		}

		/** {@inheritDoc} */
		@Override
		public void executeMsg(final Environmental host, final CMMsg msg)
		{
			super.executeMsg(host,msg);
			if((msg.sourceMinor() == CMMsg.TYP_ENTER) && CMLib.tracking().isPartyTravelArrival(msg))
				entries.add(msg.source());
			if((msg.sourceMinor() == CMMsg.TYP_LEAVE) && (msg.source() == interruptLeaveFor) && (duringLeave != null))
			{
				final Runnable callback = duringLeave;
				duringLeave = null;
				callback.run();
			}
		}
	}

	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}
}

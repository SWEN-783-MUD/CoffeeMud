package com.planet_ink.coffee_mud.Tests;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Combat.EncounterManager;
import com.planet_ink.coffee_mud.Combat.GeneralEncounter;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Common.interfaces.Session;
import com.planet_ink.coffee_mud.Locales.StdRoom;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Tests.PartyInviteCommandTest.TestPlayer;
import com.planet_ink.coffee_mud.core.CMClass;
import com.planet_ink.coffee_mud.core.CMLib;
import com.planet_ink.coffee_mud.core.Directions;
import com.planet_ink.coffee_mud.core.interfaces.Environmental;
import com.planet_ink.coffee_mud.core.party.ExplorationContext.Activity;
import com.planet_ink.coffee_mud.core.party.PartyTravel.Completed;

/** Exercises the Epic 3 room manager through player commands and native room events. */
public class PartyEncounterTest extends StdTest
{
	/** {@inheritDoc} */
	@Override
	public String ID()
	{
		return "PartyEncounterTest";
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
		area.setName("Encounter" + suffix);
		final Room hall = CMClass.getLocale("StdRoom");
		final EntryRoom room = new EntryRoom();
		final MOB guard = CMClass.getMOB("StdMOB");
		final ExecutorService combatThread = Executors.newSingleThreadExecutor();
		try
		{
			setUpRoom(hall,area,"Hall");
			setUpRoom(room,area,"Guardroom");
			hall.setRawExit(Directions.EAST,CMClass.getExit("Open"));
			hall.rawDoors()[Directions.EAST] = room;
			room.setRawExit(Directions.WEST,CMClass.getExit("Open"));
			room.rawDoors()[Directions.WEST] = hall;
			guard.setName("Guard");
			room.bringMobHere(guard,false);
			room.guard = guard;
			room.combatThread = combatThread;

			try(TestPlayer alice = new TestPlayer("Alice" + suffix,hall);
				TestPlayer bob = new TestPlayer("Bob" + suffix,hall);
				TestPlayer carol = new TestPlayer("Carol" + suffix,hall))
			{
				check(alice.command("PARTY","CREATE").contains("You created a party"),"Alice should create a party.");
				join(alice,bob,invite(alice,bob));
				bob.command("FOLLOW",alice.getMob().Name());
				bob.command("EXPLORE","AVOID","NOTICE");

				final AtomicReference<Completed> offered = new AtomicReference<Completed>();
				final EncounterManager manager = room.getEncounterManager();
				manager.setEntryTrigger(batch -> {
					check(offered.getAndSet(batch) == null,"The entry rule should run once for this move.");
					check(room.isInhabitant(bob.getMob()),"Bob should be placed before the entry rule runs.");
					// Redeliver while the first call is still evaluating its rule.
					onWorker(combatThread,() -> manager.onPartyTravelCompleted(batch));
					return guard;
				});

				final String carolInvite = invite(alice,carol);
				alice.command("EAST");
				final GeneralEncounter first = checkSetup(manager,alice,bob,guard,Activity.AVOID_NOTICE);
				check(room.attackChecked && !guard.isInCombat(),"Native aggression should wait for all branches.");
				check(!CMLib.tracking().isPartyTravelInProgress(alice.getMob()),"Travel should clear after completion.");
				checkRestrictions(manager,first,alice,bob,carol,carolInvite);
				check(manager.completeEncounter(first),"Completing the active encounter should release the slot.");
				check(!manager.completeEncounter(first) && manager.getEncounter() == null,"Completion should be safe to retry.");
				check(bob.command("EXPLORE").contains("activity is Normal")
						&& carol.command("EXPLORE").contains("activity is Avoid Notice"),
						"Completion should reset the participants only.");
				check(first.getParticipants().get(1).getApproach().activity == Activity.AVOID_NOTICE,
						"Completion should preserve the saved setup.");
				manager.onPartyTravelCompleted(offered.getAndSet(null));
				check(manager.getEncounter() == null,"A repeated handoff must not reopen a completed encounter.");

				// Start a new trip. Carol follows Bob but the guardroom refuses her step.
				hall.bringMobHere(alice.getMob(),false);
				hall.bringMobHere(bob.getMob(),false);
				join(alice,bob,invite(alice,bob));
				join(alice,carol,invite(alice,carol));
				bob.command("FOLLOW",alice.getMob().Name());
				carol.command("FOLLOW",bob.getMob().Name());
				check(carol.getMob().amFollowing() == bob.getMob(),"Carol should follow Bob for the blocked branch.");
				check(bob.command("EXPLORE","NORMAL").contains("now Normal"),"Bob should confirm the new choice.");
				room.blocked = carol.getMob();
				alice.command("EAST");
				final GeneralEncounter second = checkSetup(manager,alice,bob,guard,Activity.NORMAL);
				check(carol.getMob().location() == hall && carol.getMob().amFollowing() == null,
						"A refused follower should stay outside the encounter and lose the failed link.");
				check(offered.get().failures.size() == 1 && manager.outsidePartyMembers(second).contains(carol.getMob()),
						"The blocked teammate should remain available to notification lookup.");
				check(manager.activateEncounter(second) && manager.completeEncounter(second),"The second encounter should close.");

				// Bob reaches the room but his session ends before setup can enroll him.
				hall.bringMobHere(alice.getMob(),false);
				hall.bringMobHere(bob.getMob(),false);
				final Session bobSession = bob.getMob().session();
				manager.setEntryTrigger(batch -> {
					check(batch.arrivals.size() == 2,"Both players should have arrived before this disconnect.");
					onWorker(combatThread,() -> CMLib.parties().playerSessionEnding(bob.getMob(),bobSession));
					return guard;
				});
				alice.command("EAST");
				final GeneralEncounter interrupted = (GeneralEncounter)manager.getEncounter();
				check(interrupted != null && interrupted.getParticipants().size() == 2
						&& interrupted.getParticipants().get(1).getMob() == guard && room.isInhabitant(bob.getMob()),
						"A departing session should not be enrolled even if the player is still in the room.");
				CMLib.parties().playerSessionStarted(bob.getMob(),bobSession);
				check(manager.activateEncounter(interrupted) && manager.completeEncounter(interrupted),
						"The encounter should still finish with the opener.");

				// Existing manual initiation uses the same manager and captures the opener's current approach.
				manager.setEntryTrigger(null);
				final CMMsg nativeAttack = CMClass.getMsg(guard,alice.getMob(),null,CMMsg.MSG_WEAPONATTACK,null);
				check(room.okMessage(guard,nativeAttack),"Native attack permission should return after the encounter ends.");
				check(alice.command("KILL","Guard").contains("is pending"),"The team's KILL trigger should still open an encounter.");
				final GeneralEncounter manual = (GeneralEncounter)manager.getEncounter();
				check(manual.getParticipants().size() == 2 && manual.getParticipants().get(0).getMob() == alice.getMob()
						&& manual.getParticipants().get(0).getApproach().entrySide == Directions.WEST,
						"Manual initiation should record the opener's approach.");
				check(manager.activateEncounter(manual) && manager.completeEncounter(manual),"The manual encounter should close.");
			}
			return null;
		}
		catch(final AssertionError | Exception error)
		{
			return error.toString();
		}
		finally
		{
			combatThread.shutdownNow();
			guard.destroy();
			room.destroy();
			hall.destroy();
			area.destroy();
		}
	}

	/**
	 * Runs a competing action while the setup callback is still open.
	 *
	 * @param worker the separate test thread
	 * @param action the action that must finish before setup can continue
	 */
	private static void onWorker(final ExecutorService worker, final Runnable action)
	{
		try
		{
			worker.submit(action).get(5,TimeUnit.SECONDS);
		}
		catch(final Exception error)
		{
			throw new AssertionError("The competing action did not return cleanly.",error);
		}
	}

	/**
	 * Verifies the participants and their fixed setup information.
	 *
	 * @param manager the room's actual encounter manager
	 * @param alice the first arrival
	 * @param bob the second arrival
	 * @param guard the encounter NPC
	 * @param activity Bob's choice at entry
	 * @return the pending encounter
	 */
	private static GeneralEncounter checkSetup(final EncounterManager manager, final TestPlayer alice,
			final TestPlayer bob, final MOB guard, final Activity activity)
	{
		final GeneralEncounter encounter = (GeneralEncounter)manager.getEncounter();
		check(encounter != null && encounter.getState() == GeneralEncounter.State.PENDING,
				"The completed movement should open one pending encounter.");
		check(encounter.getParticipants().size() == 3 && encounter.getParticipants().get(0).getMob() == alice.getMob()
				&& encounter.getParticipants().get(1).getMob() == bob.getMob()
				&& encounter.getParticipants().get(2).getMob() == guard,
				"Only the two successful arrivals and the guard should be enrolled.");
		check(encounter.getParticipants().get(0).getApproach().activity == Activity.NORMAL
				&& encounter.getParticipants().get(1).getApproach().activity == activity
				&& encounter.getParticipants().get(1).getApproach().entrySide == Directions.WEST,
				"The encounter should get the individual approach and activity from placement.");
		return encounter;
	}

	/**
	 * Exercises commands and notification lookup across pending and active phases.
	 *
	 * @param manager the room's encounter manager
	 * @param encounter the first encounter
	 * @param alice the enrolled leader
	 * @param bob the enrolled follower
	 * @param carol the player in the hall
	 * @param carolInvite the entry Carol can accept during the encounter
	 */
	private static void checkRestrictions(final EncounterManager manager, final GeneralEncounter encounter,
			final TestPlayer alice, final TestPlayer bob, final TestPlayer carol, final String carolInvite)
	{
		check(bob.command("EXPLORE","NORMAL").contains("Wait until combat ends"),"Pending setup should protect the activity.");
		check(!manager.completeEncounter(encounter) && manager.activateEncounter(encounter)
				&& !manager.activateEncounter(encounter),"Only a pending encounter should activate.");
		check(alice.command("PARTY").contains("Your party leader"),"Active participants can inspect their party.");
		check(alice.command("PARTY","PENDING").contains("You can act on pending entries after combat"),
				"Active participants can inspect pending entries.");
		check(alice.command("PARTY","CANCEL",carolInvite).contains("Wait until combat ends"),
				"A blocked party change should leave the invite open.");

		join(alice,carol,carolInvite);
		check(manager.outsidePartyMembers(encounter).equals(Collections.singletonList(carol.getMob()))
				&& !manager.restricts(carol.getMob()),"Joining the roster should not enroll an outside teammate.");
		check(carol.command("EXPLORE","AVOID","NOTICE").contains("now Avoid Notice"),"Outside teammates can change activity.");
		carol.command("PARTY","LEAVE");
		check(manager.outsidePartyMembers(encounter).isEmpty(),"A fresh lookup must exclude former party members.");

		final Room room = bob.getMob().location();
		final int hp = bob.getMob().curState().getHitPoints();
		bob.command("PARTY","LEAVE");
		check(bob.getMob().location() == room && bob.getMob().curState().getHitPoints() == hp
				&& manager.getEncounter() == encounter && manager.restricts(bob.getMob()),
				"Leaving the party should leave position, HP, and enrollment under encounter control.");
		check(bob.command("PARTY","CREATE").contains("Wait until combat ends"),"Leaving should not lift combat restrictions.");
	}

	/**
	 * Adds a room to the fixture's encounter area.
	 *
	 * @param room the room being prepared
	 * @param area the opt-in area
	 * @param name the room's local name
	 */
	private static void setUpRoom(final Room room, final Area area, final String name)
	{
		room.setArea(area);
		room.setRoomID(area.Name() + "#" + name);
		room.setDisplayText(name);
	}

	/**
	 * Opens a real invitation and returns its ID.
	 *
	 * @param leader the inviting player
	 * @param member the invited player
	 * @return the invitation ID from the command output
	 */
	private static String invite(final TestPlayer leader, final TestPlayer member)
	{
		return PartyInviteCommandTest.sentId(leader.command("PARTY","INVITE",member.getMob().Name()));
	}

	/**
	 * Accepts an invitation and checks that the leader and member now share a party.
	 *
	 * @param leader the party leader
	 * @param member the player joining
	 * @param id the invitation ID
	 */
	private static void join(final TestPlayer leader, final TestPlayer member, final String id)
	{
		check(member.command("PARTY","ACCEPT",id).contains("You joined the party")
				&& CMLib.parties().findParty(member.getMob()).getId() == CMLib.parties().findParty(leader.getMob()).getId(),
				"The invited player should join the party.");
	}

	/** A room that attempts a native attack on a separate thread during entry. */
	private static final class EntryRoom extends StdRoom
	{
		/** Enables this one fixture room without changing the server's encounter settings. */
		private final EncounterManager manager = new EncounterManager(this)
		{
			/** {@inheritDoc} */
			@Override
			public boolean isEnabled()
			{
				return true;
			}
		};

		/** The NPC attempting the attack. */
		private MOB guard;
		/** The worker that acts while the entry callback is paused. */
		private ExecutorService combatThread;
		/** Whether the first native entry has already checked the guard. */
		private boolean attackChecked;
		/** An optional follower whose entry is refused by the room. */
		private MOB blocked;

		/** {@inheritDoc} */
		@Override
		public EncounterManager getEncounterManager()
		{
			return manager;
		}

		/** {@inheritDoc} */
		@Override
		public boolean okMessage(final Environmental host, final CMMsg msg)
		{
			return !((blocked != null) && (msg.source() == blocked) && (msg.target() == this)
					&& (msg.sourceMinor() == CMMsg.TYP_ENTER)) && super.okMessage(host,msg);
		}

		/** {@inheritDoc} */
		@Override
		public void executeMsg(final Environmental host, final CMMsg msg)
		{
			super.executeMsg(host,msg);
			if(attackChecked || (msg.target() != this) || (msg.sourceMinor() != CMMsg.TYP_ENTER))
				return;

			attackChecked = true;
			final MOB entrant = msg.source();
			check(getEncounterManager().getEncounter() == null,"An individual entry must not open the encounter.");
			check(guard.mayPhysicallyAttack(entrant),"The guard should otherwise be able to attack the traveler.");
			try
			{
				final boolean attacked = combatThread.submit(() -> CMLib.combat().postAttack(guard,entrant,null))
						.get(5,TimeUnit.SECONDS).booleanValue();
				check(!attacked,"A guard's native attack must not bypass open travel on another thread.");
			}
			catch(final Exception error)
			{
				throw new AssertionError("Could not check the guard attack.",error);
			}
		}
	}

	/**
	 * Stops the fixture with the behavior that failed.
	 *
	 * @param condition whether the expected behavior occurred
	 * @param message the expected behavior
	 */
	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}
}

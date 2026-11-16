package com.planet_ink.coffee_mud.Tests;

import java.util.List;
import java.util.UUID;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Common.interfaces.CharStats;
import com.planet_ink.coffee_mud.Common.interfaces.Session;
import com.planet_ink.coffee_mud.Locales.StdRoom;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Tests.PartyInviteCommandTest.TestPlayer;
import com.planet_ink.coffee_mud.core.CMClass;
import com.planet_ink.coffee_mud.core.CMLib;
import com.planet_ink.coffee_mud.core.Directions;
import com.planet_ink.coffee_mud.core.interfaces.Environmental;

/** Exercises party follow commands, native links, and cleanup in a booted host. */
public class PartyFollowTest extends StdTest
{
	/** {@inheritDoc} */
	@Override
	public String ID()
	{
		return "PartyFollowTest";
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
		final FollowRoom hall = new FollowRoom();
		final Room side = CMClass.getLocale("StdRoom");
		final MOB pet = CMClass.getMOB("StdMOB");
		try
		{
			// Give the players a real exit for the unfollow travel check.
			area.setName("PartyFollow" + suffix);
			hall.setArea(area);
			side.setArea(area);
			hall.setRoomID(area.Name() + "#1");
			side.setRoomID(area.Name() + "#2");
			hall.setRawExit(Directions.NORTH, CMClass.getExit("Open"));
			side.setRawExit(Directions.SOUTH, CMClass.getExit("Open"));
			hall.rawDoors()[Directions.NORTH] = side;
			side.rawDoors()[Directions.SOUTH] = hall;
			pet.setName("Partypet" + suffix);
			hall.bringMobHere(pet, false);

			try(TestPlayer alice = new TestPlayer("Alice" + suffix, hall);
				TestPlayer bob = new TestPlayer("Bob" + suffix, hall);
				TestPlayer carol = new TestPlayer("Carol" + suffix, hall);
				TestPlayer dara = new TestPlayer("Dara" + suffix, hall);
				TestPlayer outsider = new TestPlayer("Other" + suffix, hall))
			{
				giveRoomForFollowers(alice.getMob());
				giveRoomForFollowers(bob.getMob());
				giveRoomForFollowers(carol.getMob());
				check(alice.command("PARTY", "CREATE").contains("You created a party"), "Alice should create a party.");
				join(alice, bob);
				join(alice, carol);
				join(alice, dara);
				outsider.command("PARTY", "CREATE");

				checkFollowRules(alice, bob, carol, dara, outsider, hall, side);
				checkUnfollowAndDismissal(alice, bob, carol, hall, side, pet);
				checkCleanup(alice, bob, carol, dara, pet);
				checkLateRejection(alice, bob, carol, hall);
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
			side.destroy();
			area.destroy();
		}
	}

	private static void giveRoomForFollowers(final MOB character)
	{
		character.baseCharStats().setStat(CharStats.STAT_CHARISMA, 24);
		character.recoverCharStats();
	}

	private static void join(final TestPlayer leader, final TestPlayer candidate)
	{
		final String id = PartyInviteCommandTest.sentId(leader.command("PARTY", "INVITE", candidate.getMob().Name()));
		check(candidate.command("PARTY", "ACCEPT", id).contains("You joined the party"), "The invite should join the candidate.");
	}

	private static void checkFollowRules(final TestPlayer alice, final TestPlayer bob, final TestPlayer carol,
			final TestPlayer dara, final TestPlayer outsider, final FollowRoom hall, final Room side)
	{
		follow(bob, alice, "FOLLOW");
		follow(carol, bob, "FOL");
		follow(dara, carol, "FO");
		check(alice.command("F", dara.getMob().Name()).contains("follow loop"), "A longer cycle must be rejected.");
		check(bob.command("FOL", carol.getMob().Name()).contains("follow loop"), "A two-player cycle must be rejected.");
		check(bob.command("FO", "me").contains("cannot follow yourself"), "FOLLOW ME must reject self-follow.");
		check(bob.command("F", "self").contains("cannot follow yourself"), "FOLLOW SELF must reject self-follow.");
		check(bob.command("FOLLOW", outsider.getMob().Name()).contains("only follow players in your party"),
				"The same room is not enough when a target is in a different party.");
		bob.getMob().setFollowing(outsider.getMob());
		check(bob.getMob().amFollowing() == alice.getMob(), "Direct writes cannot bypass party checks.");

		// Use the internal entry point with a target that the room command cannot see.
		side.bringMobHere(dara.getMob(), false);
		CMLib.commands().postFollow(bob.getMob(), dara.getMob(), true);
		check(bob.getMob().amFollowing() == alice.getMob(), "An internal cross-room attempt must preserve the old link.");
		hall.bringMobHere(dara.getMob(), false);
		check(alice.command("NOFOLLOW").contains("no longer accepting"), "Bare NOFOLLOW should toggle acceptance.");
		check(carol.command("F", alice.getMob().Name()).contains("not accepting followers"),
				"An alias must respect NOFOLLOW.");
		check(carol.getMob().amFollowing() == bob.getMob(), "A refused replacement must keep the old link.");
		check(alice.command("NOFOLLOW").contains("now accepting"), "A second toggle should allow new links.");

		carol.getMob().setVictim(outsider.getMob());
		try
		{
			check(carol.command("F", alice.getMob().Name()).contains("Wait until combat ends")
					&& carol.getMob().amFollowing() == bob.getMob(), "Combat must keep the old link in place.");
		}
		finally
		{
			carol.getMob().makePeace(true);
		}

		// A native room veto must leave the prior link in place as well.
		hall.vetoNextFollow = true;
		carol.command("FOLLOW", alice.getMob().Name());
		check(carol.getMob().amFollowing() == bob.getMob(), "A veto must keep the old target.");
		follow(carol, alice, "F");
		check(!bob.getMob().isFollowedBy(carol.getMob()) && dara.getMob().amFollowing() == carol.getMob()
				&& carol.getMob().location() == hall, "A valid switch should only change the outgoing link.");
		follow(carol, bob, "FOL");
		check(alice.command("GROUP", "LEADER", bob.getMob().Name()).contains("Use PARTY LEADER")
				&& bob.getMob().amFollowing() == alice.getMob(), "GROUP LEADER must not rewire player links.");
	}

	private static void checkUnfollowAndDismissal(final TestPlayer alice, final TestPlayer bob, final TestPlayer carol,
			final Room hall, final Room side, final MOB pet)
	{
		check(bob.command("UNFOLLOW").contains("stop"), "UNFOLLOW should report that the outgoing link stopped.");
		check(bob.getMob().amFollowing() == null && carol.getMob().amFollowing() == bob.getMob(),
				"UNFOLLOW must preserve incoming links.");
		alice.command("NORTH");
		check(alice.getMob().location() == side && bob.getMob().location() == hall && carol.getMob().location() == hall,
				"Bob and Carol should stay behind after Bob unfollows.");
		check(CMLib.parties().findParty(bob.getMob()).getMembers().contains(alice.getMob().Name()),
				"Stopping automatic travel must keep membership.");
		alice.command("SOUTH");

		follow(bob, alice, "FOLLOW");
		alice.command("NOFOLLOW", bob.getMob().Name());
		check(bob.getMob().amFollowing() == null && carol.getMob().amFollowing() == bob.getMob(),
				"Named NOFOLLOW should dismiss just the named follower.");
		follow(bob, alice, "FOLLOW");
		CMLib.commands().postFollow(pet, alice.getMob(), true);
		check(pet.amFollowing() == alice.getMob(), "Native NPC following must still work.");
		alice.command("NOFOLLOW", "ALL");
		check(bob.getMob().amFollowing() == null && pet.amFollowing() == null
				&& carol.getMob().amFollowing() == bob.getMob(), "NOFOLLOW ALL must dismiss both player and NPC followers.");
	}

	private static void checkCleanup(final TestPlayer alice, final TestPlayer bob, final TestPlayer carol,
			final TestPlayer dara, final MOB pet)
	{
		follow(bob, alice, "FOLLOW");
		CMLib.commands().postFollow(pet, bob.getMob(), true);
		check(bob.command("PARTY", "LEAVE").contains("You left"), "Bob should be able to leave.");
		check(bob.getMob().amFollowing() == null && carol.getMob().amFollowing() == null
				&& dara.getMob().amFollowing() == carol.getMob() && pet.amFollowing() == bob.getMob(),
				"Leaving should clear Bob's two party links and keep the descendant and pet links.");

		join(alice, bob);
		follow(bob, alice, "FOLLOW");
		follow(carol, bob, "FOLLOW");
		final Session session = bob.getMob().session();
		CMLib.parties().playerSessionEnding(bob.getMob(), session);
		CMLib.parties().playerSessionEnding(bob.getMob(), session);
		check(bob.getMob().amFollowing() == null && carol.getMob().amFollowing() == null
				&& dara.getMob().amFollowing() == carol.getMob() && pet.amFollowing() == bob.getMob(),
				"Session cleanup should be repeatable and limited to direct party links.");
		check(CMLib.parties().findParty(bob.getMob()) != null, "Disconnecting must preserve membership.");
		check(carol.command("FOLLOW", bob.getMob().Name()).contains("Both players must be online"),
				"A link cannot be restored while the target is logging out.");
		CMLib.parties().playerSessionStarted(bob.getMob(), session);
		check(bob.getMob().amFollowing() == null && carol.getMob().amFollowing() == null,
				"Login should allow new links without restoring old ones.");
	}

	private static void checkLateRejection(final TestPlayer alice, final TestPlayer bob, final TestPlayer carol,
			final FollowRoom hall)
	{
		follow(bob, carol, "FOLLOW");
		hall.beforeNextFollow = () -> CMLib.parties().leaveParty(alice.getMob());
		check(bob.command("FOLLOW", alice.getMob().Name()).contains("only follow players in your party"),
				"The native link update must recheck a target that leaves after preflight.");
		check(bob.getMob().amFollowing() == carol.getMob(), "Late rejection should keep the old link.");

		final CMMsg stale = CMClass.getMsg(bob.getMob(), alice.getMob(), null, CMMsg.MSG_NOFOLLOW, "Stale dismissal");
		hall.send(bob.getMob(), stale);
		check(bob.getMob().amFollowing() == carol.getMob(), "An old dismissal must not clear a newer target.");
	}

	private static void follow(final TestPlayer follower, final TestPlayer target, final String word)
	{
		final String output = follower.command(word, target.getMob().Name());
		check(follower.getMob().amFollowing() == target.getMob(), word + " should link the players. Output was: " + output);
	}

	/** Runs a one-time veto or state change during a native room check. */
	private static final class FollowRoom extends StdRoom
	{
		private boolean vetoNextFollow;

		private Runnable beforeNextFollow;

		/** {@inheritDoc} */
		@Override
		public boolean okMessage(final Environmental host, final CMMsg msg)
		{
			if(!super.okMessage(host, msg))
				return false;
			if(msg.sourceMinor() == CMMsg.TYP_FOLLOW)
			{
				if(vetoNextFollow)
				{
					vetoNextFollow = false;
					return false;
				}
				final Runnable callback = beforeNextFollow;
				beforeNextFollow = null;
				if(callback != null)
					callback.run();
			}
			return true;
		}
	}

	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}
}

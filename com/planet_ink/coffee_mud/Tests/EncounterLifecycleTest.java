package com.planet_ink.coffee_mud.Tests;

import java.util.Collections;
import java.util.List;

import com.planet_ink.coffee_mud.Combat.EncounterParticipant;
import com.planet_ink.coffee_mud.Combat.EncounterManager;
import com.planet_ink.coffee_mud.Locales.StdRoom;
import com.planet_ink.coffee_mud.Combat.GeneralEncounter;
import com.planet_ink.coffee_mud.Combat.GeneralEncounter.State;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

/** Exercises encounter lifecycle requests independently of combat policy. */
public class EncounterLifecycleTest extends StdTest
{
	@Override
	public String ID()
	{
		return "EncounterLifecycleTest";
	}

	@Override
	public String[] getTestGroups()
	{
		return new String[] {"all", "encounter", "exec_integration"};
	}

	@Override
	public String doTest(final MOB mob, final int metaFlags, final String what, final List<String> commands)
	{
		try
		{
			runCases();
			return null;
		}
		catch(final AssertionError error)
		{
			return error.getMessage();
		}
	}

	private static void runCases()
	{
		try(Fixture rescued=new Fixture();
			Fixture defeated=new Fixture();
			Fixture victory=new Fixture())
		{
			check(rescued.getState()==State.PENDING,"Encounters must begin pending.");
			check(rescued.getRound()==0,"Pending encounters must not have started round one.");
			check(!rescued.awaitRescue()&&!rescued.resume()&&!rescued.complete(),
					"Rescue and completion requests must not bypass pending setup.");
			check(rescued.getState()==State.PENDING,"Rejected requests must preserve pending setup.");
			check(rescued.getRound()==0,"Rejected pending requests must not start a round.");
			check(rescued.activate(),"Finished setup must allow activation.");
			check(rescued.getRound()==1,"Activation must start round one.");
			check(!rescued.activate()&&!rescued.resume(),"Repeated setup and unsolicited rescue must be rejected.");
			check(rescued.getState()==State.ACTIVE,"Rejected requests must preserve active combat.");
			check(rescued.getRound()==1,"Duplicate activation must not advance the round.");
			check(rescued.awaitRescue(),"Active combat must be able to await rescue.");
			check(rescued.getState()==State.AWAITING_RESCUE,"Combat must remain paused awaiting rescue.");
			check(!rescued.awaitRescue()&&!rescued.activate(),"Duplicate pause or setup must not resume combat.");
			check(rescued.getState()==State.AWAITING_RESCUE,"Duplicate requests must preserve the rescue window.");
			check(rescued.getRound()==1,"Awaiting rescue must retain the current round.");
			check(rescued.resume(),"An accepted rescue must resume combat.");
			check(!rescued.resume(),"A duplicate rescue must not produce another transition.");
			check(rescued.getState()==State.ACTIVE,"Rescued encounters must be active again.");
			check(rescued.awaitRescue()&&rescued.resume(),"A rescued fight may later need rescue again.");
			check(rescued.getRound()==1,"Resuming alone must not start a new round.");
			check(rescued.complete(),"A rescued fight must still be able to finish normally.");
			checkTerminal(rescued);

			check(defeated.activate()&&defeated.awaitRescue(),"The defeat fixture must reach its rescue window.");
			check(defeated.complete(),"An unsuccessful rescue window must allow completion directly.");
			checkTerminal(defeated);

			check(victory.activate()&&victory.complete(),"Normal completion must not require a rescue window.");
			checkTerminal(victory);
		}
	}

	private static void checkTerminal(final Fixture encounter)
	{
		check(encounter.getState()==State.COMPLETED,"Finished encounters must be completed.");
		check(!encounter.activate()&&!encounter.awaitRescue()&&!encounter.resume()&&!encounter.complete(),
				"Late or duplicate lifecycle requests must not reopen a completed encounter.");
		check(encounter.getState()==State.COMPLETED,"Completion must be terminal.");
		check(encounter.getRound()==1,"Completion and rejected reactivation must retain the final round.");
	}

	/** Exercise transitions through the room owner, including stale-owner rejection. */
	private static final class Fixture implements AutoCloseable
	{
		private final GeneralEncounter encounter=new GeneralEncounter(Collections.<EncounterParticipant>emptyList());
		private final EncounterManager manager=new EncounterManager(new StdRoom());
		private final GeneralEncounter unrelated=new GeneralEncounter(Collections.<EncounterParticipant>emptyList());

		private Fixture()
		{
			check(manager.trySetEncounter(encounter),"The fixture must own the room slot.");
			check(!manager.activateEncounter(unrelated) && !manager.awaitRescue(unrelated)
					&& !manager.resumeEncounter(unrelated) && !manager.completeEncounter(unrelated),
					"Transitions must reject an encounter outside the room slot.");
			check(!manager.activateEncounter(null) && !manager.awaitRescue(null)
					&& !manager.resumeEncounter(null) && !manager.completeEncounter(null),
					"Transitions must reject null encounters.");
		}

		@Override
		public void close()
		{
			manager.getRoom().destroy();
		}

		private State getState() { return encounter.getState(); }
		private int getRound() { return encounter.getRound(); }
		private boolean activate() { return manager.activateEncounter(encounter); }
		private boolean awaitRescue() { return manager.awaitRescue(encounter); }
		private boolean resume() { return manager.resumeEncounter(encounter); }
		private boolean complete()
		{
			final boolean completed=manager.completeEncounter(encounter);
			if(completed)
				check(manager.getEncounter()==null,"Completion must release the room slot.");
			return completed;
		}
	}

	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}
}

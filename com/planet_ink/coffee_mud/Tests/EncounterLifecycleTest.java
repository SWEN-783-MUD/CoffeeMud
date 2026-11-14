package com.planet_ink.coffee_mud.Tests;

import java.util.Collections;
import java.util.List;

import com.planet_ink.coffee_mud.Combat.EncounterParticipant;
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

	public static void main(final String[] args)
	{
		runCases();
		System.out.println("PASS: Encounter lifecycle transitions.");
	}

	private static void runCases()
	{
		final GeneralEncounter rescued=newEncounter();
		check(rescued.getState()==State.PENDING,"Encounters must begin pending.");
		check(rescued.getRound()==0,"Pending encounters must not have started round one.");
		check(!rescued.awaitRescue()&&!rescued.resume()&&!rescued.complete(),
				"Rescue and completion requests must not bypass pending setup.");
		check(rescued.getState()==State.PENDING,"Rejected requests must preserve pending setup.");
		check(rescued.getRound()==0,"Rejected pending requests must not start a round.");
		check(rescued.activate(),"Finished setup must allow activation.");
		check(rescued.getRound()==1,"Activation must start round one.");
		check(!rescued.activate()&&!rescued.resume(),"Repeated setup and unsolicited rescue must be rejected.");
		check(rescued.getRound()==1,"Duplicate activation must not advance the round.");
		check(rescued.getState()==State.ACTIVE,"Rejected requests must preserve active combat.");
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

		final GeneralEncounter defeated=newEncounter();
		check(defeated.activate()&&defeated.awaitRescue(),"The defeat fixture must reach its rescue window.");
		check(defeated.complete(),"An unsuccessful rescue window must allow completion directly.");
		checkTerminal(defeated);

		final GeneralEncounter victory=newEncounter();
		check(victory.activate()&&victory.complete(),"Normal completion must not require a rescue window.");
		checkTerminal(victory);
	}

	private static GeneralEncounter newEncounter()
	{
		return new GeneralEncounter(Collections.<EncounterParticipant>emptyList());
	}

	private static void checkTerminal(final GeneralEncounter encounter)
	{
		check(encounter.getState()==State.COMPLETED,"Finished encounters must be completed.");
		check(!encounter.activate()&&!encounter.awaitRescue()&&!encounter.resume()&&!encounter.complete(),
				"Late or duplicate lifecycle requests must not reopen a completed encounter.");
		check(encounter.getState()==State.COMPLETED,"Completion must be terminal.");
		check(encounter.getRound()==1,"Completion and rejected reactivation must retain the final round.");
	}

	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}
}

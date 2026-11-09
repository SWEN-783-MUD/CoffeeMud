package com.planet_ink.coffee_mud.Tests;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.planet_ink.coffee_mud.Combat.EncounterParticipant;
import com.planet_ink.coffee_mud.Combat.GeneralEncounter;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMProps;

/** Verifies participant turn order, round progression, and lifecycle guards. */
public class EncounterTurnTest extends StdTest
{
	@Override
	public String ID()
	{
		return "EncounterTurnTest";
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
		// MOB interface initialization needs tick configuration in standalone runs.
		new CMProps();
		runCases();
		System.out.println("PASS: Encounter turn progression.");
	}

	private static void runCases()
	{
		participantLifecycle();
		roundProgression();
		singleParticipantRounds();
	}

	private static void participantLifecycle()
	{
		final EncounterParticipant first=participant();
		final GeneralEncounter encounter=new GeneralEncounter(Arrays.asList(first,participant()));
		checkNoTurn(encounter);
		check(encounter.activate(),"The encounter must activate.");
		check(encounter.getCurrentParticipant()==first,"Activation must select the first supplied participant.");
		check(!encounter.activate(),"Repeated activation must be rejected.");
		check(encounter.getCurrentParticipant()==first,"Rejected activation must preserve the current participant.");
		check(encounter.awaitRescue(),"The encounter must pause for rescue.");
		checkNoTurn(encounter);
		check(encounter.resume(),"The encounter must resume after rescue.");
		check(encounter.getCurrentParticipant()==first,"Resuming must preserve the tracked participant.");
		check(encounter.complete(),"The encounter must complete.");
		checkNoTurn(encounter);

		final GeneralEncounter empty=new GeneralEncounter(Collections.<EncounterParticipant>emptyList());
		check(empty.activate(),"Empty lifecycle fixtures must remain supported.");
		checkNoTurn(empty);
		check(!empty.endTurn(first)&&!empty.endTurn(null),"An empty encounter cannot advance a turn.");
		check(empty.getRound()==1,"Rejected empty turn requests must not advance the round.");
	}

	private static void roundProgression()
	{
		final EncounterParticipant first=participant();
		final EncounterParticipant second=participant();
		final EncounterParticipant third=participant();
		final GeneralEncounter encounter=new GeneralEncounter(Arrays.asList(first,second,third));
		check(!encounter.endTurn(first),"Pending encounters must reject turn advancement.");
		checkTurn(encounter,null,0);
		check(encounter.activate(),"The encounter must activate.");
		checkTurn(encounter,first,1);
		check(!encounter.endTurn(second)&&!encounter.endTurn(participant())&&!encounter.endTurn(null),
				"Only the active participant can end a turn.");
		checkTurn(encounter,first,1);
		check(encounter.endTurn(first),"The active participant must be able to end their turn.");
		checkTurn(encounter,second,1);
		check(!encounter.endTurn(first),"Repeating the previous participant's request must not skip another turn.");
		checkTurn(encounter,second,1);
		check(encounter.endTurn(second),"The next participant must be able to end their turn.");
		checkTurn(encounter,third,1);
		check(encounter.awaitRescue(),"The encounter must pause for rescue.");
		check(!encounter.endTurn(third)&&!encounter.endTurn(first),"Awaiting rescue must block advancement.");
		checkTurn(encounter,null,1);
		check(encounter.resume(),"The encounter must resume.");
		checkTurn(encounter,third,1);
		check(encounter.endTurn(third),"The final participant must be able to end their turn.");
		checkTurn(encounter,first,2);
		check(encounter.endTurn(first),"The first participant must be able to act again in round two.");
		checkTurn(encounter,second,2);
		check(encounter.complete(),"The encounter must complete.");
		check(!encounter.endTurn(second)&&!encounter.endTurn(first),"Completed encounters must block advancement.");
		checkTurn(encounter,null,2);
	}

	private static void singleParticipantRounds()
	{
		final EncounterParticipant only=participant();
		final GeneralEncounter encounter=new GeneralEncounter(Collections.singletonList(only));
		check(encounter.activate()&&encounter.endTurn(only),"A single participant must be able to finish a round.");
		checkTurn(encounter,only,2);
	}

	private static void checkTurn(final GeneralEncounter encounter, final EncounterParticipant participant, final int round)
	{
		check(encounter.getCurrentParticipant()==participant&&encounter.getRound()==round,
				"The current participant and round must match the expected progression (round "+round+").");
	}

	private static EncounterParticipant participant()
	{
		final MOB mob=(MOB)Proxy.newProxyInstance(MOB.class.getClassLoader(),new Class<?>[] {MOB.class},
				(proxy,method,args) -> { throw new UnsupportedOperationException(method.getName()); });
		return new EncounterParticipant(mob);
	}

	private static void checkNoTurn(final GeneralEncounter encounter)
	{
		check(encounter.getCurrentParticipant()==null,
				"An encounter without an active turn must not expose a current participant.");
	}

	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}
}

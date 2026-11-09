package com.planet_ink.coffee_mud.Tests;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.planet_ink.coffee_mud.Combat.EncounterParticipant;
import com.planet_ink.coffee_mud.Combat.GeneralEncounter;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMProps;

/** Verifies current participant tracking across encounter lifecycle changes. */
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
		System.out.println("PASS: Encounter current participant.");
	}

	private static void runCases()
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

package com.planet_ink.coffee_mud.Tests;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Combat.EncounterManager;
import com.planet_ink.coffee_mud.Combat.EncounterParticipant;
import com.planet_ink.coffee_mud.Combat.GeneralEncounter;
import com.planet_ink.coffee_mud.Commands.EndTurn;
import com.planet_ink.coffee_mud.Common.interfaces.PhyStats;
import com.planet_ink.coffee_mud.Libraries.interfaces.EncounterLibrary;
import com.planet_ink.coffee_mud.Libraries.interfaces.EncounterLibrary.EndTurnResult;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Tests.PartyInviteCommandTest.TestPlayer;
import com.planet_ink.coffee_mud.core.CMClass;
import com.planet_ink.coffee_mud.core.CMLib;

/** Exercises the registered command through the encounter library and room owner. */
public class EncounterCommandTest extends StdTest
{
	@Override
	public String ID() { return "EncounterCommandTest"; }

	@Override
	public String[] getTestGroups() { return new String[] {"all", "encounter", "exec_integration"}; }

	@Override
	public String doTest(final MOB mob, final int metaFlags, final String what, final List<String> commands)
	{
		final String suffix=UUID.randomUUID().toString().substring(0,8);
		final Area area=CMClass.getAreaType("StdArea");
		final Room room=CMClass.getLocale("StdRoom");
		final Room hall=CMClass.getLocale("StdRoom");
		final MOB npc=CMClass.getMOB("StdMOB");
		area.setName("EncounterCommands"+suffix);
		room.setArea(area);
		hall.setArea(area);
		try(TestPlayer alice=new TestPlayer("Alice"+suffix,room);
			TestPlayer bob=new TestPlayer("Bob"+suffix,room);
			TestPlayer outsider=new TestPlayer("Outsider"+suffix,room))
		{
			final EncounterLibrary service=CMLib.encounters();
			check(service!=null && service.ID().equals("CMEncounters"),"The encounter library must be registered.");
			check(service.endTurn(null)==EndTurnResult.NOT_PLAYER && service.endTurn(npc)==EndTurnResult.NOT_PLAYER,
					"The player command API must reject missing actors and NPCs.");
			check(service.getEncounter(null)==null && service.getEncounter(alice.getMob())==null,
					"Lookup must handle actors without encounters.");
			check(alice.command("ENDTURN").contains("not in an encounter"),"The registered command must explain missing enrollment.");
			final EncounterParticipant first=new EncounterParticipant(alice.getMob());
			final EncounterParticipant second=new EncounterParticipant(bob.getMob());
			final GeneralEncounter encounter=new GeneralEncounter(Arrays.asList(first,second));
			final EncounterManager manager=room.getEncounterManager();
			check(manager.trySetEncounter(encounter),"The fixture must register the room encounter.");
			check(service.getEncounter(alice.getMob())==encounter && service.getEncounter(outsider.getMob())==null,
					"Lookup must find enrolled players only.");
			check(outsider.command("ENDTURN").contains("not participating"),"An outsider must not control encounter turns.");
			check(alice.command("ENDTURN").contains("setup finishes") && encounter.getRound()==0,
					"Pending setup must reject End Turn.");
			check(manager.activateEncounter(encounter),"The manager must activate the fixture.");
			check(alice.command("ENDTURN","extra").contains("Usage: ENDTURN") && first.getActionsRemaining()==3,
					"Invalid syntax must preserve the current turn.");
			check(bob.command("ENDTURN").contains("Wait for your turn") && encounter.getCurrentParticipant()==first,
					"Another participant must not end Alice's turn.");
			alice.getMob().basePhyStats().setDisposition(PhyStats.IS_SLEEPING);
			alice.getMob().recoverPhyStats();
			check(service.endTurn(alice.getMob())==EndTurnResult.UNABLE_TO_ACT && first.getActionsRemaining()==3,
					"An actor unable to act must not submit a voluntary turn end.");
			alice.getMob().basePhyStats().setDisposition(0);
			alice.getMob().recoverPhyStats();
			check(manager.awaitRescue(encounter) && alice.command("ENDTURN").contains("paused awaiting rescue")
					&& first.getActionsRemaining()==3,"Paused combat must reject End Turn without spending resources.");
			check(manager.resumeEncounter(encounter),"The manager must resume the fixture.");
			final double nativeActions=alice.getMob().actions();
			check(alice.command("ENDTURN").contains("You ended your turn")
					&& encounter.getCurrentParticipant()==second && first.getActionsRemaining()==0
					&& second.getActionsRemaining()==3 && alice.getMob().actions()==nativeActions,
					"End Turn must discard unused encounter actions without spending native action credits.");
			check(alice.command("ENDTURN").contains("Wait for your turn") && encounter.getCurrentParticipant()==second,
					"Repeating Alice's command must not end Bob's turn.");
			check(bob.command("ENDTURN").contains("You ended your turn") && encounter.getRound()==2
					&& encounter.getCurrentParticipant()==first,"The second participant must finish the round through the same library.");
			hall.bringMobHere(alice.getMob(),false);
			check(service.getEncounter(alice.getMob())==null && service.endTurn(alice.getMob())==EndTurnResult.NO_ENCOUNTER,
					"The current lookup is room-scoped; a displaced actor must not control the old room.");
			room.bringMobHere(alice.getMob(),false);
			check(manager.completeEncounter(encounter) && alice.command("ENDTURN").contains("not in an encounter"),
					"Completion must release the command's encounter lookup.");
			check(new EndTurn().actionsCost(alice.getMob(),Arrays.asList("ENDTURN"))==0.0
					&& new EndTurn().combatActionsCost(alice.getMob(),Arrays.asList("ENDTURN"))==0.0
					&& !new EndTurn().canBeOrdered(),"End Turn must be free of native action costs and refuse orders.");
			return null;
		}
		catch(final AssertionError | Exception error)
		{
			return error.toString();
		}
		finally
		{
			npc.destroy();
			room.destroy();
			hall.destroy();
			area.destroy();
		}
	}

	private static void check(final boolean condition, final String message)
	{
		if(!condition)
			throw new AssertionError(message);
	}
}

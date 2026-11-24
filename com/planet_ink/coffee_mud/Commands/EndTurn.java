package com.planet_ink.coffee_mud.Commands;

import java.util.List;
import com.planet_ink.coffee_mud.Combat.ActionOutcome;
import com.planet_ink.coffee_mud.Combat.EndTurnAction;
import com.planet_ink.coffee_mud.Libraries.interfaces.EncounterLibrary;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMLib;

/** Parses ENDTURN and delegates encounter rules to the shared library. */
public class EndTurn extends StdCommand
{
	private final String[] access=I(new String[] {"ENDTURN"});

	@Override
	public String[] getAccessWords() { return access; }

	/** Allows command fixtures to supply an isolated encounter service. */
	protected EncounterLibrary encounters() { return CMLib.encounters(); }

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
	{
		if(commands.size()!=1)
		{
			mob.tell(L("Usage: ENDTURN"));
			return false;
		}
		final ActionOutcome outcome=encounters().submit(mob,new EndTurnAction());
		switch(outcome.getValidation().getCode())
		{
		case VALID: mob.tell(L("You ended your turn.")); break;
		case NOT_PLAYER: mob.tell(L("Only player characters can use ENDTURN.")); break;
		case NO_ENCOUNTER: mob.tell(L("You are not in an encounter here.")); break;
		case NOT_PARTICIPANT: mob.tell(L("You are not participating in this encounter.")); break;
		case PENDING: mob.tell(L("Wait until encounter setup finishes.")); break;
		case PAUSED: mob.tell(L("Combat is paused awaiting rescue.")); break;
		case COMPLETED: mob.tell(L("This encounter has already ended.")); break;
		case NOT_YOUR_TURN: mob.tell(L("Wait for your turn.")); break;
		case UNABLE_TO_ACT: mob.tell(L("You cannot act right now.")); break;
		default: mob.tell(outcome.getValidation().getReason()); break;
		}
		return false;
	}

	@Override
	public double actionsCost(final MOB mob, final List<String> commands) { return 0.0; }

	@Override
	public double combatActionsCost(final MOB mob, final List<String> commands) { return 0.0; }

	@Override
	public boolean canBeOrdered() { return false; }
}

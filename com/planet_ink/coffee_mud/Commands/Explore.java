package com.planet_ink.coffee_mud.Commands;

import java.util.List;

import com.planet_ink.coffee_mud.Libraries.interfaces.PartyLibrary;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMLib;
import com.planet_ink.coffee_mud.core.CMParms;
import com.planet_ink.coffee_mud.core.party.ExplorationContext;
import com.planet_ink.coffee_mud.core.party.ExplorationContext.Activity;

/** Lets a player check or change their own exploration activity. */
public class Explore extends StdCommand
{
	private final String[] access = I(new String[] {"EXPLORE"});

	/** {@inheritDoc} */
	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * Shows the current activity or asks the party library to change it.
	 * A player can still check their choice during combat.
	 *
	 * @param mob the character using the command
	 * @param commands the command word and optional activity
	 * @param metaFlags the CoffeeMud flags for this command
	 * @return false because the command needs no further handling
	 */
	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
	{
		final PartyLibrary parties = CMLib.parties();
		final ExplorationContext context = parties.explorationContext(mob);
		if(context == null)
		{
			mob.tell(L("Exploration activities are only available to players."));
			return false;
		}

		if(commands.size() == 1)
		{
			mob.tell(L("Your exploration activity is @x1.",L(context.activity.label)));
			showChoices(mob);
			return false;
		}

		final Activity activity = Activity.fromCommand(CMParms.combine(commands,1));
		if(activity == null)
		{
			showChoices(mob);
			return false;
		}

		switch(parties.selectExplorationActivity(mob,activity))
		{
		case SELECTED:
			mob.tell(L("Your exploration activity is now @x1.",L(activity.label)));
			break;
		case IN_COMBAT:
			mob.tell(L("Wait until combat ends before changing your exploration activity."));
			break;
		case UNAVAILABLE:
			mob.tell(L("You can't change your exploration activity right now."));
			break;
		}
		return false;
	}

	private void showChoices(final MOB mob)
	{
		mob.tell(L("Use EXPLORE NORMAL or EXPLORE AVOID NOTICE."));
	}

	/** {@inheritDoc} */
	@Override
	public double actionsCost(final MOB mob, final List<String> commands)
	{
		return 0.0;
	}

	/** {@inheritDoc} */
	@Override
	public double combatActionsCost(final MOB mob, final List<String> commands)
	{
		return 0.0;
	}

	/** {@inheritDoc} */
	@Override
	public boolean canBeOrdered()
	{
		return false;
	}
}

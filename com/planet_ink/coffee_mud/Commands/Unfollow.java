package com.planet_ink.coffee_mud.Commands;

import java.util.List;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMLib;

/** Stops the caller from following while leaving their own followers and party in place. */
public class Unfollow extends StdCommand
{
	/** The standalone command word, separate from NOFOLLOW. */
	private final String[] access = I(new String[] {"UNFOLLOW"});

	/** {@inheritDoc} */
	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/** {@inheritDoc} */
	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
	{
		if(commands.size() != 1)
		{
			mob.tell(L("Use UNFOLLOW to stop following."));
			return false;
		}

		final MOB target = mob.amFollowing();
		if(mob.isPlayer() && (target != null) && target.isPlayer() && mob.isInCombat())
			mob.tell(L("Wait until combat ends before changing who you follow."));
		else
			CMLib.commands().postFollow(mob,null,false);
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public boolean canBeOrdered()
	{
		return false;
	}
}

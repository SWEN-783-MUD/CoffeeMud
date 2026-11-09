package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.PartyLibrary.FollowResult;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.util.*;

/*
   Copyright 2004-2026 Bo Zimmerman

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

	   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
public class Follow extends StdCommand
{
	public Follow()
	{
	}

	private final String[]	access	= I(new String[] { "FOLLOW", "FOL", "FO", "F" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	private final static Class<?>[][] internalParameters=new Class<?>[][]{{MOB.class,Boolean.class}};

	public boolean nofollow(final MOB mob, final boolean errorsOk, final boolean quiet)
	{
		if(mob==null)
			return false;
		final Room R=mob.location();
		if(R==null)
			return false;
		final MOB target = mob.amFollowing();
		if(target!=null)
		{
			final CMMsg msg=CMClass.getMsg(mob,target,null,CMMsg.MSG_NOFOLLOW,quiet?null:L("<S-NAME> stop(s) following <T-NAMESELF>."));
			// no room OKaffects, since the damn leader may not be here.
			if(mob.okMessage(mob,msg))
				R.send(mob,msg);
			else
				return false;
		}
		else
		if(errorsOk)
			mob.tell(L("You aren't following anyone!"));
		return true;
	}

	public void unfollow(final MOB mob, final boolean quiet)
	{
		nofollow(mob,false,quiet);
		final List<MOB> V=new ArrayList<MOB>();
		for(int f=0;f<mob.numFollowers();f++)
		{
			final MOB F=mob.fetchFollower(f);
			if(F!=null)
				V.add(F);
		}
		for(int v=0;v<V.size();v++)
		{
			final MOB F=V.get(v);
			nofollow(F,false,quiet);
		}
	}

	/**
	 * Handles a follow attempt from a command or an internal caller. Player
	 * switches are checked before the old link changes.
	 *
	 * @param mob the follower
	 * @param tofollow the new target, or {@code null} to detach
	 * @param quiet whether to suppress follow messages
	 * @return whether the action went ahead
	 */
	public boolean processFollow(final MOB mob, final MOB tofollow, final boolean quiet)
	{
		if(mob==null)
			return false;
		final Room R=mob.location();
		if(R==null)
			return false;
		if(tofollow!=null)
		{
			if(mob.isPlayer() && tofollow.isPlayer())
				return followPartyMember(mob,tofollow,R,quiet);
			if(tofollow==mob)
			{
				return nofollow(mob,true,false);
			}
			if(mob.getGroupMembers(new HashSet<MOB>()).contains(tofollow))
			{
				if(!quiet)
					mob.tell(L("You are already a member of @x1's group!",tofollow.name()));
				return false;
			}
			if(nofollow(mob,false,false))
			{
				final CMMsg msg=CMClass.getMsg(mob,tofollow,null,CMMsg.MSG_FOLLOW,quiet?null:L("<S-NAME> follow(s) <T-NAMESELF>."));
				if(R.okMessage(mob,msg))
					R.send(mob,msg);
				else
					return false;
			}
			else
				return false;
		}
		else
			return nofollow(mob,!quiet,quiet);
		return true;
	}

	/**
	 * Checks party and native room rules without removing an existing link.
	 * The native message applies the link and rechecks party state at that point.
	 *
	 * @param follower the player starting to follow
	 * @param target the party member they want to follow
	 * @param room the room handling this attempt
	 * @param quiet whether to suppress follow messages
	 * @return whether the native link changed to the target
	 */
	private boolean followPartyMember(final MOB follower, final MOB target, final Room room, final boolean quiet)
	{
		final FollowResult result = CMLib.parties().checkPlayerFollow(follower,target);
		if(result != FollowResult.ALLOWED)
		{
			if(!quiet)
				follower.tell(L(result.getMessage()));
			return false;
		}

		// Keep visibility and any native effects that prevent detaching from the old target.
		if(!CMLib.flags().canBeSeenBy(target,follower))
		{
			if(!quiet)
				follower.tell(L("I don't see them here."));
			return false;
		}
		final MOB previous = follower.amFollowing();
		if((previous != null)
		&& !follower.okMessage(follower,CMClass.getMsg(follower,previous,null,CMMsg.MSG_NOFOLLOW,null)))
			return false;

		final CMMsg msg = CMClass.getMsg(follower,target,null,CMMsg.MSG_FOLLOW,
				quiet ? null : L("<S-NAME> follow(s) <T-NAMESELF>."));
		if(!room.okMessage(follower,msg))
			return false;
		if((follower.location() != room) || (target.location() != room))
		{
			if(!quiet)
				follower.tell(L(FollowResult.NOT_HERE.getMessage()));
			return false;
		}
		room.send(follower,msg);
		return follower.amFollowing() == target;
	}

	/** {@inheritDoc} */
	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		boolean quiet=false;
		if(mob==null)
			return false;
		final Vector<String> origCmds=new XVector<String>(commands);
		final Room R=mob.location();
		if(R==null)
			return false;

		if((commands.size()>2)
		&&(commands.get(commands.size()-1).equalsIgnoreCase("UNOBTRUSIVELY")))
		{
			commands.remove(commands.size()-1);
			quiet=true;
		}

		if(commands.size()<2)
		{
			CMLib.commands().postCommandFail(mob,origCmds,L("Follow whom?"));
			return false;
		}

		final String whomToFollow=CMParms.combine(commands,1);
		if(whomToFollow.equalsIgnoreCase("self") || whomToFollow.equalsIgnoreCase("me")
		|| (!mob.isPlayer() && mob.name().toUpperCase().startsWith(whomToFollow)))
		{
			if(mob.isPlayer())
			{
				if(!quiet)
					mob.tell(L(FollowResult.SELF.getMessage()));
			}
			else
				nofollow(mob,true,quiet);
			return false;
		}
		final MOB target=R.fetchInhabitant(whomToFollow);
		if((target==null)||(!CMLib.flags().canBeSeenBy(target,mob)))
		{
			CMLib.commands().postCommandFail(mob,origCmds,L("I don't see them here."));
			return false;
		}
		if(mob.isPlayer() && target.isPlayer())
		{
			processFollow(mob,target,quiet);
			return false;
		}
		if((target.isMonster())&&(!mob.isMonster()))
		{
			CMLib.commands().postCommandFail(mob,origCmds,L("You cannot follow '@x1'.",target.name(mob)));
			return false;
		}
		if(target.isAttributeSet(MOB.Attrib.NOFOLLOW))
		{
			CMLib.commands().postCommandFail(mob,origCmds,L("@x1 is not accepting followers.",target.name(mob)));
			return false;
		}
		if(target.getGroupLeader().isAttributeSet(MOB.Attrib.NOFOLLOW))
		{
			CMLib.commands().postCommandFail(mob,origCmds,L("@x1 is not accepting followers.",target.getGroupLeader().name()));
			return false;
		}
		processFollow(mob,target,quiet);
		return false;
	}

	@Override
	public Object executeInternal(final MOB mob, final int metaFlags, final Object... args) throws java.io.IOException
	{
		if(!super.checkArguments(internalParameters, args))
			return Boolean.FALSE;
		final MOB target=(MOB)args[0];
		final Boolean quiet=(Boolean)args[1];
		return Boolean.valueOf(processFollow(mob,target,quiet.booleanValue()));
	}

	@Override
	public double combatActionsCost(final MOB mob, final List<String> cmds)
	{
		return CMProps.getCommandCombatActionCost(ID());
	}

	@Override
	public double actionsCost(final MOB mob, final List<String> cmds)
	{
		return CMProps.getCommandActionCost(ID());
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

}

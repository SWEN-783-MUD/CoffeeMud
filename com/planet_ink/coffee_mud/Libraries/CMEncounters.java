package com.planet_ink.coffee_mud.Libraries;

import com.planet_ink.coffee_mud.Combat.Encounter;
import com.planet_ink.coffee_mud.Combat.EncounterManager;
import com.planet_ink.coffee_mud.Combat.EncounterParticipant;
import com.planet_ink.coffee_mud.Combat.GeneralEncounter;
import com.planet_ink.coffee_mud.Libraries.interfaces.EncounterLibrary;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMLib;

/** Coordinates command access while room managers and encounters own their state. */
public class CMEncounters extends StdLibrary implements EncounterLibrary
{
	@Override
	public String ID()
	{
		return "CMEncounters";
	}

	private EncounterParticipant participant(final GeneralEncounter encounter, final MOB actor)
	{
		for(final EncounterParticipant member : encounter.getParticipants())
		{
			if(member.getMob()==actor)
				return member;
		}
		return null;
	}

	@Override
	public GeneralEncounter getEncounter(final MOB actor)
	{
		final Room room=actor==null?null:actor.location();
		if(room==null)
			return null;
		final Encounter current=room.getEncounterManager().getEncounter();
		if(!(current instanceof GeneralEncounter) || !room.isInhabitant(actor))
			return null;
		final GeneralEncounter encounter=(GeneralEncounter)current;
		return participant(encounter,actor)==null?null:encounter;
	}

	@Override
	public EndTurnResult endTurn(final MOB actor)
	{
		if((actor==null) || actor.isMonster() || (actor.playerStats()==null))
			return EndTurnResult.NOT_PLAYER;
		final Room room=actor.location();
		if(room==null)
			return EndTurnResult.NO_ENCOUNTER;
		final EncounterManager manager=room.getEncounterManager();
		synchronized(manager)
		{
			final Encounter current=manager.getEncounter();
			if(!(current instanceof GeneralEncounter) || (actor.location()!=room) || !room.isInhabitant(actor))
				return EndTurnResult.NO_ENCOUNTER;
			final GeneralEncounter encounter=(GeneralEncounter)current;
			synchronized(encounter)
			{
				final EncounterParticipant member=participant(encounter,actor);
				if(member==null)
					return EndTurnResult.NOT_PARTICIPANT;
				switch(encounter.getState())
				{
				case PENDING: return EndTurnResult.PENDING;
				case AWAITING_RESCUE: return EndTurnResult.PAUSED;
				case COMPLETED: return EndTurnResult.COMPLETED;
				default: break;
				}
				if(encounter.getCurrentParticipant()!=member)
					return EndTurnResult.NOT_YOUR_TURN;
				if(!CMLib.flags().isAliveAwakeMobileUnbound(actor,true))
					return EndTurnResult.UNABLE_TO_ACT;
				return encounter.endTurn(member)?EndTurnResult.ENDED:EndTurnResult.NOT_YOUR_TURN;
			}
		}
	}
}

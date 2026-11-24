package com.planet_ink.coffee_mud.Libraries;

import com.planet_ink.coffee_mud.Combat.Encounter;
import com.planet_ink.coffee_mud.Combat.EncounterManager;
import com.planet_ink.coffee_mud.Combat.EncounterParticipant;
import com.planet_ink.coffee_mud.Combat.GeneralEncounter;
import com.planet_ink.coffee_mud.Combat.ActionOutcome;
import com.planet_ink.coffee_mud.Combat.ActionRequest;
import com.planet_ink.coffee_mud.Combat.ActionResolver;
import com.planet_ink.coffee_mud.Combat.EncounterAction;
import com.planet_ink.coffee_mud.Combat.EndTurnAction;
import com.planet_ink.coffee_mud.Combat.ValidationResult;
import com.planet_ink.coffee_mud.Combat.ValidationResult.Code;
import com.planet_ink.coffee_mud.Libraries.interfaces.EncounterLibrary;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

/** Coordinates command access while room managers and encounters own their state. */
public class CMEncounters extends StdLibrary implements EncounterLibrary
{
	private final ActionResolver resolver=new ActionResolver();

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
		final EncounterManager manager=room.getEncounterManager();
		if(manager==null)
			return null;
		final Encounter current=manager.getEncounter();
		if(!(current instanceof GeneralEncounter) || !room.isInhabitant(actor))
			return null;
		final GeneralEncounter encounter=(GeneralEncounter)current;
		return participant(encounter,actor)==null?null:encounter;
	}

	@Override
	public EndTurnResult endTurn(final MOB actor)
	{
		final Code code=submit(actor,new EndTurnAction()).getValidation().getCode();
		return code==Code.VALID?EndTurnResult.ENDED:EndTurnResult.valueOf(code.name());
	}

	private ActionOutcome reject(final Code code, final String reason)
	{
		return ActionOutcome.rejected(ValidationResult.rejected(code,reason));
	}

	@Override
	public ActionOutcome submit(final MOB actor, final EncounterAction action)
	{
		if((actor==null) || actor.isMonster() || (actor.playerStats()==null))
			return reject(Code.NOT_PLAYER,"Only player characters can submit encounter commands.");
		if(action==null)
			return reject(Code.INVALID_ACTION,"No encounter action was supplied.");
		final Room room=actor.location();
		if(room==null)
			return reject(Code.NO_ENCOUNTER,"You are not in an encounter here.");
		final EncounterManager manager=room.getEncounterManager();
		if(manager==null)
			return reject(Code.NO_ENCOUNTER,"You are not in an encounter here.");
		synchronized(manager)
		{
			final Encounter current=manager.getEncounter();
			if(!(current instanceof GeneralEncounter) || (actor.location()!=room) || !room.isInhabitant(actor))
				return reject(Code.NO_ENCOUNTER,"You are not in an encounter here.");
			final GeneralEncounter encounter=(GeneralEncounter)current;
			synchronized(encounter)
			{
				final EncounterParticipant member=participant(encounter,actor);
				if(member==null)
					return reject(Code.NOT_PARTICIPANT,"You are not participating in this encounter.");
				return resolver.resolve(new ActionRequest(encounter,member,action));
			}
		}
	}
}

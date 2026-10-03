package com.planet_ink.coffee_mud.Combat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;

public class EncounterManager
{
	private final List<Encounter> encounters = new ArrayList<Encounter>();
	private final Room room;

	public EncounterManager(final Room room)
	{
		this.room = room;
	}

	public List<Encounter> getEncounters()
	{
		return encounters;
	}

	public Encounter startEncounter(final Collection<MOB> mobs)
	{
		final Encounter encounter = new Encounter(mobs);
		encounters.add(encounter);
		return encounter;
	}

	public boolean endEncounter(final Encounter encounter)
	{
		return encounters.remove(encounter);
	}
}

package com.planet_ink.coffee_mud.Combat;

import java.util.Collection;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

public class Encounter
{
	private final Collection<MOB> mobs;

	public Encounter(final Collection<MOB> mobs)
	{
		this.mobs = mobs;
	}
}

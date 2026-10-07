package com.planet_ink.coffee_mud.Combat;

import com.planet_ink.coffee_mud.Common.interfaces.CharStats;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

public class EncounterParticipant
{
	private final MOB mob;
	private final int perception;
	private final int initiative;

	public EncounterParticipant(final MOB mob)
	{
		this.mob = mob;
		this.perception = mob.charStats().getStat(CharStats.STAT_WISDOM);
		this.initiative = perception;
	}

	public MOB getMob()
	{
		return mob;
	}

	public int getPerception()
	{
		return perception;
	}

	public int getInitiative()
	{
		return initiative;
	}
}

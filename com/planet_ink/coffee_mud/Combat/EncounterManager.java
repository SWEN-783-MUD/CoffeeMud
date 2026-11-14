package com.planet_ink.coffee_mud.Combat;

import java.util.Arrays;
import java.util.Objects;

import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMLib;

/**
 * Manages the current combat encounter for a single room.
 */
public class EncounterManager
{
	/** The room associated with this manager. */
	private final Room room;
	private Encounter encounter;

	/**
	 * Creates an encounter manager for the given room.
	 *
	 * @param room the room associated with this manager
	 * @throws NullPointerException if room is null
	 */
	public EncounterManager(final Room room)
	{
		this.room=Objects.requireNonNull(room, "room");
	}

	/**
	 * Returns the room associated with this manager.
	 *
	 * @return the associated room
	 */
	public Room getRoom()
	{
		return room;
	}

	/**
	 * @return whether the room is in the area selected by coffeemud.encounterArea
	 */
	public boolean isEnabled()
	{
		final String areaName=System.getProperty("coffeemud.encounterArea", "");
		return (!areaName.isEmpty())&&(room.getArea()!=null)
			&&areaName.equalsIgnoreCase(room.getArea().Name());
	}

	/**
	 * Checks whether the player can open an encounter against the NPC.
	 */
	public synchronized boolean canStartEncounter(final MOB player, final MOB target)
	{
		return (encounter==null)
			&&isEnabled()
			&&(player!=null)
			&&(!player.isMonster())
			&&(target!=null)
			&&target.isMonster()
			&&(player.location()==room)
			&&(target.location()==room)
			&&room.isInhabitant(player)
			&&room.isInhabitant(target)
			&&(!player.isInCombat())
			&&(!target.isInCombat())
			&&(!target.amDead())
			&&CMLib.flags().isAliveAwakeMobileUnbound(player,true)
			&&player.mayPhysicallyAttack(target);
	}

	/**
	 * Registers a pending encounter after rechecking the opening request.
	 *
	 * @return whether a new encounter was registered
	 */
	public synchronized boolean startEncounter(final MOB player, final MOB target)
	{
		if(!canStartEncounter(player,target))
			return false;
		return trySetEncounter(new GeneralEncounter(Arrays.asList(
			new EncounterParticipant(player),new EncounterParticipant(target))));
	}

	/**
	 * @return the current encounter, or null if the room has none
	 */
	public synchronized Encounter getEncounter()
	{
		return encounter;
	}

	/**
	 * Registers an encounter if the room's slot is empty.
	 *
	 * @param newEncounter the encounter to register
	 * @return true if the encounter was registered
	 * @throws NullPointerException if newEncounter is null
	 */
	public synchronized boolean trySetEncounter(final Encounter newEncounter)
	{
		Objects.requireNonNull(newEncounter, "newEncounter");
		if(encounter!=null)
			return false;
		encounter=newEncounter;
		return true;
	}

	/**
	 * Clears the slot only if it still holds the given encounter.
	 *
	 * @param finishedEncounter the encounter to clear
	 * @return true if the encounter was cleared
	 * @throws NullPointerException if finishedEncounter is null
	 */
	public synchronized boolean clearEncounter(final Encounter finishedEncounter)
	{
		Objects.requireNonNull(finishedEncounter, "finishedEncounter");
		if(encounter!=finishedEncounter)
			return false;
		encounter=null;
		return true;
	}
}

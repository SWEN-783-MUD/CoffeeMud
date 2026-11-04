package com.planet_ink.coffee_mud.Combat;

import java.util.Objects;

import com.planet_ink.coffee_mud.Locales.interfaces.Room;

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

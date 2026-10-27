package com.planet_ink.coffee_mud.Combat;

import java.util.Objects;

import com.planet_ink.coffee_mud.Locales.interfaces.Room;

/**
 * Manages combat encounters associated with a single room.
 */
public class EncounterManager
{
	/** The room associated with this manager. */
	private final Room room;

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
}

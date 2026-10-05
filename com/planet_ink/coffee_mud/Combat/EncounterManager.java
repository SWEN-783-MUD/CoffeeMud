package com.planet_ink.coffee_mud.Combat;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;

public class EncounterManager
{   
    private final EncounterDirectory directory; // The EncounterDirectory instance that manages encounters
    private final Set<Encounter> encounters = new LinkedHashSet<Encounter>();
    private final Room room;

    public EncounterManager(final Room room,final EncounterDirectory directory)
    {
        this.room = room;
        this.directory = Objects.requireNonNull(directory, "Encounter directory cannot be null");
    }

    public synchronized Set<Encounter> getEncounters()
    {
        return Collections.unmodifiableSet(new LinkedHashSet<Encounter>(encounters));
    }
    
    /**
     * Starts a new encounter with the specified collection of MOBs.
     * <p> This method attempts to register the MOBs in the encounter using the EncounterDirectory.
     * If registration fails (e.g., if any MOB is already registered in another encounter), it returns null.
     * <p> This method is synchronized to ensure thread safety when accessing the encounters list and the EncounterDirectory.
     * @param mobs the collection of MOBs involved in the encounter
     * @return the newly created Encounter instance if successful, or null if registration fails
     */
    public synchronized Encounter startEncounter(final Collection<MOB> mobs)
    {
        final Encounter encounter = new Encounter(mobs);
        // Attempt to register the MOBs in the encounter using the EncounterDirectory
        if (!directory.registerMobs(encounter))
        {
            return null; // If registration fails, return null to indicate that the encounter could not be started
        }
        encounters.add(encounter);
        return encounter;
    }

    public synchronized boolean endEncounter(final Encounter encounter)
    {
        if (encounter == null || !encounters.contains(encounter))
        {
            return false; // If the encounter is null or not found in the encounters list, return false to indicate that the encounter could not be ended
        }
        directory.unregisterEncounter(encounter); // Unregister the encounter from the EncounterDirectory
        return encounters.remove(encounter);
    }
}

package com.planet_ink.coffee_mud.Combat;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

/**
 * The EncounterDirectory class maintains a mapping of MOBs to their corresponding Encounter instances.
 * It allows for efficient retrieval of the Encounter associated with a specific MOB.
 */
public class EncounterDirectory
{
    // Using IdentityHashMap to ensure that the mapping is based on object identity rather than equals().
    // This is important where two Mobs could be functionally equal (Same name, race, stats, etc. ) but are different instances, and we want to treat them as distinct.
    private final Map<MOB, Encounter> memberships = new IdentityHashMap<MOB, Encounter>(); 
    
    /**
     * Returns the Encounter associated with the given MOB, or null if the MOB is not registered in any Encounter.
     * <p> This method is synchronized to ensure thread safety when accessing the memberships map, since
     * multiple threads may be accessing or modifying the map concurrently to check if a MOB is in an encounter or to add/remove MOBs from encounters.
     * @param The MOB for which to retrieve the Encounter.
     * @return The Encounter associated with the given MOB, or null if not found.
     * 
     */
    public synchronized Encounter getEncounter(final MOB mob)
    {
        return memberships.get(mob);
    }
    
    /**
     * Attempts to register all the Mobs in an Encounter to said Encounter.
     * <p> This can only be successful if every supplied MOB is not currently
     * registered in an encounter. If even one MOB is unable to be registered, 
     * none of them will be and this method will return false. true is returned only if 
     * all mobs have been registered. 
     * @param The List of Mobs to be registerd
     * @param The Encounter each Mob is to be registered to
     * @return ture or false based on the success of the registration
     */
    public synchronized boolean registerMobs(final Encounter encounter)
    {   
        // Reject null encounters
        if (encounter == null) {
            return false;
        }
        
        // Get the collection of Mobs from the encounter
        final Collection<MOB> mobs = encounter.getMobs();
        // Check that each Mob is not currently Registered
        boolean canRegister = mobs.stream().allMatch((mob) -> {
          return mob != null && !memberships.containsKey(mob);
        });
        // Registers each mob if canRegister is true
        if (canRegister) {
            mobs.forEach((mob) -> {
                memberships.put(mob, encounter);
            });
        }
        return canRegister;     
    }
    
    
}

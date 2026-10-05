package com.planet_ink.coffee_mud.Combat;

import java.util.Collection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

/**
 * Represents a combat encounter involving a collection of MOBs in the game.
 */
public class Encounter
{
    private final UUID id; // A unique identifier for the encounter
    private final List<MOB> mobs; // A list of MOBs involved in the encounter
    
    /**
     * Constructs a new Encounter with the specified collection of MOBs.
     * The provided collections of MOBs must not be null, empty, contain null elements, or contain duplicates
     * (two references to the same MOB object). 
     * <p> Note: Currently, the encounter requires at least one MOB to be present. This restriction may be relaxed in the future.
     * @param mobs the collection of MOBs involved in the encounter
     */
    public Encounter(final Collection<MOB> mobs)
    {   
        // Validate that the provided collection of MOBs is not null or empty, does not contain null elements, and does not contain duplicates
        if (mobs == null) {
            throw new IllegalArgumentException("MOB collection cannot be null");
        }
        
        final List<MOB> mobList = new ArrayList<>(mobs);
        if (mobList.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("MOB collection cannot contain null elements");
        }
        // Check for duplicates using an IdentityHashMap to ensure that the same MOB object is not added more than once
        final Map<MOB, Boolean> seen = new IdentityHashMap<MOB, Boolean>();
        mobList.forEach(mob -> {
            if (seen.put(mob, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("MOB collection cannot contain duplicate MOBs");
            }
        });
        if (mobList.isEmpty()) {
            throw new IllegalArgumentException("MOB collection cannot be empty");
        }
        
        this.id = UUID.randomUUID();
        this.mobs = mobList;
    }
    
    /**
     * Returns the unique identifier for this encounter.
     * @return the UUID of the encounter
     */
    public UUID getId()
    {
        return id;
    }
    
    /**
     * Returns an unmodifiable collection of the MOBs involved in this encounter.
     * @return an unmodifiable collection of MOBs
     */
    public Collection<MOB> getMobs()
    {
        return Collections.unmodifiableList(mobs);
    }
}

package com.planet_ink.coffee_mud.Combat;

import java.util.Collection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

public class Encounter
{
    private final UUID id; // A unique identifier for the encounter
    private final List<MOB> mobs; // A list of MOBs involved in the encounter

    public Encounter(final Collection<MOB> mobs)
    {
        this.id = UUID.randomUUID();
        this.mobs = new ArrayList<>(mobs);
    }

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

package com.planet_ink.coffee_mud.Combat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;

public class EncounterManager
{   
    private final EncounterDirectory directory; // The EncounterDirectory instance that manages encounters
    private final List<Encounter> encounters = new ArrayList<Encounter>();
    private final Room room;

    public EncounterManager(final Room room,final EncounterDirectory directory)
    {
        this.room = room;
        this.directory = Objects.requireNonNull(directory, "Encounter directory cannot be null");
    }

    public List<Encounter> getEncounters()
    {
        return Collections.unmodifiableList(encounters);
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

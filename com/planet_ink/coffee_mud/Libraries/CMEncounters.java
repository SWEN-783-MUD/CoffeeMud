package com.planet_ink.coffee_mud.Libraries;

import com.planet_ink.coffee_mud.Combat.EncounterDirectory;
import com.planet_ink.coffee_mud.Libraries.interfaces.EncounterLibrary;

/**
 * The CMEncounters class is a library that provides access to the EncounterDirectory,
 * which manages encounters in the game. It implements the EncounterLibrary interface.
 * 
 */
public class CMEncounters extends StdLibrary implements EncounterLibrary {
    
    /**
     * The EncounterDirectory instance that manages encounters.
     */
    private final EncounterDirectory directory = new EncounterDirectory(); 
    
    /**
     * Returns the unique identifier for this library.
     * @return the string "CMEncounters"
     */
    @Override
    public String ID() {
        return "CMEncounters";
    }
    
    /**
     * Returns the EncounterDirectory instance that manages encounters.
     * @return the EncounterDirectory instance
     */
    @Override
    public EncounterDirectory getDirectory() {
        return directory;
    }

}

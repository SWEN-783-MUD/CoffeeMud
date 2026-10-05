package com.planet_ink.coffee_mud.Libraries.interfaces;

import com.planet_ink.coffee_mud.Combat.EncounterDirectory;

/**
 * The EncounterLibrary interface defines the contract for libraries that manage encounters in the game.
 * It provides access to the EncounterDirectory, which maintains a mapping of MOBs to their corresponding Encounter instances.\
 */
public interface EncounterLibrary extends CMLibrary
{
    EncounterDirectory getDirectory();
}
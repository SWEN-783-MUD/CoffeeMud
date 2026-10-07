package com.planet_ink.coffee_mud.Commands;

import java.util.List;

import com.planet_ink.coffee_mud.Combat.Encounter;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMLib;
import com.planet_ink.coffee_mud.Combat.EncounterManager;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;

/**
 * Provides encounter controls for combat.
 * STATUS displays the caller's encounter ID, state, and participants.
 *  END performs manual cleanup and ends the caller's encounter in the test room.
 */
public class EncounterControl extends StdCommand
{   
    /**
     * The access words that can be used to invoke the ENCOUNTER command.
     * ENCOUNTER: The main command to control encounters.
     */
    private final String[] access = I(new String[]{"ENCOUNTER"});
    
    /**
     * Returns the access words for the ENCOUNTER command.
     * @return an array of access words for the command
     */
    @Override
    public String[] getAccessWords()
    {
        return access;
    }
    
    /**
     * Executes the ENCOUNTER command with the specified subcommand.
     * @param mob the MOB executing the command
     * @param commands the command words, including ENCOUNTER and its subcommand
     * @param metaFlags command execution flags supplied by CoffeeMud
     * @return always false, following CoffeeMud's command convention
     * @throws java.io.IOException if an I/O error occurs
     */
    @Override
    public boolean execute(final MOB mob,
                           final List<String> commands,
                           final int metaFlags)
            throws java.io.IOException
    {
        // Check if the command has the correct number of arguments and if the subcommand is provided.
        if (commands == null || commands.size() != 2 || commands.get(1) == null || commands.get(1).trim().isEmpty())
        {
            mob.tell(L("Usage: ENCOUNTER STATUS or ENCOUNTER END."));
            return false;
        }
        // Check if the provided subcommand is valid (either STATUS or END).
        final String subcommand = commands.get(1);
        if (!subcommand.equalsIgnoreCase("STATUS")
                && !subcommand.equalsIgnoreCase("END"))
        {
            mob.tell(L("Invalid subcommand. Use STATUS or END."));
            return false;
        }

        // Look up the caller's encounter.
        final Encounter encounter = CMLib.encounters().getDirectory().getEncounter(mob);
        
        if (encounter == null) {
            mob.tell(L("You are not currently in an encounter."));
            return false;
        }
        
        // Handle the subcommand based on the provided argument.
        switch (subcommand.toUpperCase(java.util.Locale.ROOT)) {
            case "STATUS":
                // Display the current status, id, and participants of the encounter.
                mob.tell(L("Encounter ID: @x1", encounter.getId().toString()));
                mob.tell(L("Encounter State: @x1", encounter.getState().toString()));
                mob.tell(L("Participants:"));
                for (final MOB participant : encounter.getMobs()) {
                    mob.tell(L(" - @x1", participant.Name()));
                }
                break;
            case "END":
            {   
                final Room room = mob.location();
                // Only allow ending encounters in the test room "New Area#0".
                if (room == null || !"New Area#0".equals(room.roomID()))
                {
                    mob.tell(L("For this spike, end encounters in the test room."));
                    return false;
                }
                
                final EncounterManager manager = room.getEncounterManager();
                // Ensure that the encounter is managed by the room's EncounterManager.
                if (manager == null || !manager.getEncounters().contains(encounter))
                {
                    mob.tell(L("This room does not manage your encounter."));
                    return false;
                }
                // Attempt to end the encounter, handling any exceptions that may occur.
                try
                {
                    // If the encounter is active, begin the ending process before attempting to end it.
                    if (encounter.getState() == Encounter.State.ACTIVE
                            && !manager.beginEndingEncounter(encounter))
                    {
                        mob.tell(L("Could not begin ending your encounter."));
                        return false;
                    }
                    // Attempt to end the encounter and inform the user of the result.
                    if (manager.endEncounter(encounter))
                    {
                        mob.tell(L("Encounter ended. ID: @x1",
                                encounter.getId().toString()));
                    }
                    else
                    {
                        mob.tell(L("Encounter was not ended. Check ENCOUNTER STATUS."));
                    }
                }
                catch (final RuntimeException ex)
                {
                    mob.tell(L("Encounter cleanup failed. Check STATUS and retry END."));
                }
                break;
            }
            default:
                mob.tell(L("Unknown subcommand. Valid subcommands are: STATUS or END."));
                return false;
        }

        return false;
    }
    
    @Override
    public boolean canBeOrdered()
    {
        return false;
    }
}
package com.planet_ink.coffee_mud.Combat;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.core.Log;

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
            logEncounterEvent("encounter-start-rejected", encounter,
                    "reason=membership-conflict registered=false");
            return null; // If registration fails, return null to indicate that the encounter could not be started
        }
        encounters.add(encounter);
        logEncounterEvent("encounter-started", encounter, "");
        return encounter;
    }

    /**
     * Ends the specified encounter and unregisters its MOBs from the EncounterDirectory.
     * <p> This method checks if the specified encounter is valid and present in the encounters list.
     * If valid, it unregisters the encounter from the EncounterDirectory and removes it from the encounters list.
     * <p> This method is synchronized to ensure thread safety when accessing the encounters list and the EncounterDirectory.
     * @param encounter the Encounter instance to be ended
     * @return true if the encounter was successfully ended and removed, false otherwise
     */
    public synchronized boolean endEncounter(final Encounter encounter)
    {
        // Check if the encounter is null or not found in the encounters list
        if (encounter == null || !encounters.contains(encounter))
        {
            return false; // If the encounter is null or not found in the encounters list, return false to indicate that the encounter could not be ended
        }
        // Check if the encounter is in the ENDING state before proceeding to finish ending it
        if (encounter.getState() != Encounter.State.ENDING)
        {
            return false;
        }
        // Clear the legacy combat state for all MOBs in the encounter before finishing the ending process
        clearLegacyCombatState(encounter);

        if (!encounter.finishEnding())
        {
            return false;
        }

        directory.unregisterEncounter(encounter);
        final boolean removed = encounters.remove(encounter);
        if (removed)
        {
            logEncounterEvent("encounter-ended", encounter, "");
        }
        return removed;
    }

    /**
     * Begins the process of ending an encounter.
     * <p> This method checks if the specified encounter is valid and present in the encounters list.
     * If valid, it calls the beginEnding() method on the encounter to initiate the ending process.
     * <p> This method is synchronized to ensure thread safety when accessing the encounters list.
     * @param encounter the Encounter instance to be ended
     * @return true if the encounter is valid and the ending process has begun, false otherwise
     */
    public synchronized boolean beginEndingEncounter(final Encounter encounter) {

        if (encounter == null || !encounters.contains(encounter))
        {
            return false; // If the encounter is null or not found in the encounters list, return false to indicate that the encounter could not be ended
        }
        final boolean beganEnding = encounter.beginEnding();
        if (beganEnding)
        {
            logEncounterEvent("encounter-ending", encounter, "");
        }
        return beganEnding;
    }

    /**
     * Clears the legacy combat state for all MOBs in the specified encounter.
     * <p> Clears each participant's legacy target, pending commands, and scheduling credit.
     * Cleanup failures are logged and rethrown so that membership remains registered.
     *
     * @param encounter the Encounter instance whose MOBs' legacy combat state should be cleared
     */
    private void clearLegacyCombatState(final Encounter encounter) {
        encounter.getMobs().forEach( mob -> {
            logCleanupSnapshot("cleanup-before", encounter, mob);
            String step = "clear-victim";
            try
            {
                mob.setVictim(null);
                step = "clear-command-queue";
                mob.clearCommandQueue();
                step = "reset-action-credit";
                mob.setActions(0.0);
            }
            catch (final RuntimeException ex)
            {
                logEncounterException("cleanup-failed", encounter, mob, "step=" + step, ex);
                throw ex;
            }
            logCleanupSnapshot("cleanup-after", encounter, mob);
        });
    }

    /** Returns common context for correlating spike events in the server log. */
    private String encounterLogContext(final Encounter encounter)
    {
        return "encounter=" + encounter.getId()
                + " room=" + quoteLogValue(room == null ? "<none>" : room.roomID())
                + " state=" + encounter.getState()
                + " participants=" + encounter.getMobs().size();
    }

    private void logEncounterEvent(final String event, final Encounter encounter, final String details)
    {
        try
        {
            Log.sysOut("EncounterSpike", "event=" + event + " " + encounterLogContext(encounter)
                    + (details.isEmpty() ? "" : " " + details));
        }
        catch (final RuntimeException ex)
        {
            logEncounterException("telemetry-failed", encounter, null, "originalEvent=" + event, ex);
        }
    }

    /** Snapshot reads are observational; they do not form an atomic view of MOB state. */
    private void logCleanupSnapshot(final String event, final Encounter encounter, final MOB mob)
    {
        try
        {
            // getVictim() and isInCombat() can mutate combat state, so do not read them here.
            final String details = "mob=" + quoteLogValue(mob.Name())
                    + " queuedCommands=" + mob.commandQueSize()
                    + " legacyActionCredit=" + mob.actions()
                    + " hp=" + mob.curState().getHitPoints();
            logEncounterEvent(event, encounter, details);
        }
        catch (final RuntimeException ex)
        {
            logEncounterException("telemetry-failed", encounter, mob, "originalEvent=" + event, ex);
        }
    }

    /** Telemetry must not replace a cleanup exception or change lifecycle results. */
    private void logEncounterException(final String event, final Encounter encounter, final MOB mob,
            final String details, final RuntimeException exception)
    {
        try
        {
            Log.errOut("EncounterSpike", exception, "event=" + event + " " + encounterLogContext(encounter)
                    + (mob == null ? "" : " mob=" + quoteLogValue(mob.Name()))
                    + " " + details);
        }
        catch (final RuntimeException ignored)
        {
            // Best effort: a broken logger or observation must not interfere with cleanup.
        }
    }

    /** Keeps names and room IDs with spaces or control characters on one log line. */
    private static String quoteLogValue(final String value)
    {
        if (value == null)
        {
            return "\"<none>\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }

}

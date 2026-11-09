package com.planet_ink.coffee_mud.Combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An unstructured combat encounter initialized with its starting participants.
 * Lifecycle methods are public to support CoffeeMud's separate class loaders.
 * For registered encounters, callers should use {@link EncounterManager}'s
 * lifecycle methods so room ownership checks and completion cleanup run together.
 */
public class GeneralEncounter implements Encounter
{
	/** Phases owned by the room encounter. */
	public enum State
	{
		/** The participants have been selected and setup is in progress. */
		PENDING,
		/** The encounter has finished setup. */
		ACTIVE,
		/** Combat is paused while the encounter owner resolves rescue. */
		AWAITING_RESCUE,
		/** The encounter has ended. */
		COMPLETED
	}

	private final List<EncounterParticipant> participants;
	/** Phase reads must be safe while the party library holds its character lock. */
	private volatile State state=State.PENDING;
	private int round=0;
	private EncounterParticipant currentParticipant;

	/**
	 * Creates a pending encounter with a snapshot of the initial participants.
	 *
	 * @param initialParticipants the participants present at initiation
	 * @throws NullPointerException if the list or any participant is null
	 */
	public GeneralEncounter(final List<EncounterParticipant> initialParticipants)
	{
		final List<EncounterParticipant> copy=new ArrayList<EncounterParticipant>(initialParticipants);
		for(final EncounterParticipant participant : copy)
			Objects.requireNonNull(participant, "participant");
		participants=Collections.unmodifiableList(copy);
	}

	/**
	 * @return the encounter's current lifecycle state
	 */
	public State getState()
	{
		return state;
	}

	/**
	 * @return the current round, or zero before the encounter first activates
	 */
	public synchronized int getRound()
	{
		return round;
	}

	/**
	 * @return the participant taking the active turn, or null when no turn is active
	 */
	public synchronized EncounterParticipant getCurrentParticipant()
	{
		return (state==State.ACTIVE)?currentParticipant:null;
	}

	/**
	 * Activates the encounter at round one once setup is complete.
	 *
	 * @return true if the encounter moved from pending to active
	 */
	public synchronized boolean activate()
	{
		if(state!=State.PENDING)
			return false;
		round=1;
		// Setup supplies the participant order; initiative rolling is separate.
		currentParticipant=participants.isEmpty()?null:participants.get(0);
		state=State.ACTIVE;
		return true;
	}

	/**
	 * Pauses an active encounter while another party member may rescue it.
	 * The caller decides eligibility and owns any rescue deadline.
	 *
	 * @return true if the encounter moved from active to awaiting rescue
	 */
	public synchronized boolean awaitRescue()
	{
		if(state!=State.ACTIVE)
			return false;
		state=State.AWAITING_RESCUE;
		return true;
	}

	/**
	 * Resumes an encounter after its caller accepts a rescuer.
	 *
	 * @return true if the encounter moved from awaiting rescue to active
	 */
	public synchronized boolean resume()
	{
		if(state!=State.AWAITING_RESCUE)
			return false;
		state=State.ACTIVE;
		return true;
	}

	/**
	 * Completes an active encounter or one whose rescue opportunity has ended.
	 * Completion is terminal; pending encounters must first finish setup.
	 * Registered encounters should be completed through
	 * {@link EncounterManager#completeEncounter(GeneralEncounter)} to reset
	 * exploration activities and release the room's encounter slot.
	 *
	 * @return true if the encounter moved to completed
	 */
	public synchronized boolean complete()
	{
		if((state!=State.ACTIVE)&&(state!=State.AWAITING_RESCUE))
			return false;
		currentParticipant=null;
		state=State.COMPLETED;
		return true;
	}

	@Override
	public List<EncounterParticipant> getParticipants()
	{
		return participants;
	}
}

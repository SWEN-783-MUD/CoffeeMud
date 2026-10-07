package com.planet_ink.coffee_mud.Combat;

import java.util.Collection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private final List<EncounterParticipant> participants;
    private int roundNumber = 0;
    private int currentParticipantIndex = 0;

    public enum State {
        SETUP,
        ACTIVE, 
        ENDING, 
        ENDED
    }

    private State state = State.SETUP; // The encounter begins in setup while initiative is determined.

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
		this.participants = new ArrayList<EncounterParticipant>();
		mobList.forEach(mob -> participants.add(new EncounterParticipant(mob)));
		determineInitiative();
    }

	/**
	 * Determines and stores the initiative order for the MOBs in this encounter.
	 *
	 * The initial implementation will use each MOB's Wisdom value as its
	 * Perception value. More complete turn handling will be added later.
	 */
	/**
	 * Determines the initiative order for the participants in this encounter.
	 * <p>
	 * Perception is currently represented by each MOB's Wisdom value. Participants
	 * are sorted from highest initiative to lowest initiative. MOB name is used as
	 * a deterministic tie-breaker when two participants have the same initiative.
	 */
	public void determineInitiative()
	{
		// Highest initiative acts first; name ordering keeps ties deterministic.
		participants.sort(Comparator
				.comparingInt(EncounterParticipant::getInitiative)
				.reversed()
				.thenComparing(participant -> participant.getMob().Name(), String.CASE_INSENSITIVE_ORDER));
	}

	/**
	 * Begins the next round of the encounter.
	 * <p>
	 * The first participant in the initiative order becomes the current
	 * participant. Action and reaction refreshes will be added later.
	 */
	public void beginRound()
	{
		roundNumber++;
		currentParticipantIndex = 0;
	}

	/**
	 * Returns the participant whose turn is currently active.
	 * @return the current encounter participant
	 */
	public EncounterParticipant currentParticipant()
	{
		return participants.get(currentParticipantIndex);
	}

	/**
	 * Begins the current participant's turn.
	 * <p>
	 * Action points, reactions, and queued actions will be prepared here later.
	 */
	public void beginTurn()
	{
		// TODO: Reset the current participant's actions and reaction.
	}

	/**
	 * Ends the current participant's turn and advances the encounter.
	 * <p>
	 * Queued actions will be resolved or discarded here later.
	 */
	public void endTurn()
	{
		// TODO: Resolve or discard the current participant's remaining actions.
		advanceTurn();
	}

	/**
	 * Advances the encounter to the next participant in initiative order.
	 * <p>
	 * When every participant has acted, a new round begins.
	 */
	public void advanceTurn()
	{
		currentParticipantIndex++;
		if (currentParticipantIndex >= participants.size())
		{
			beginRound();
		}
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

    /**
     * Returns the current state of the encounter.
     * <p> This method is synchronized to ensure thread safety when accessing the state variable.
     * @return the current state of the encounter
     */
    public synchronized State getState()
    {
        return state;
    }

    /**
     * Transitions the encounter from SETUP to ACTIVE after initiative has been determined.
     * @return true if the transition was successful, false otherwise
     */
    synchronized boolean beginActive()
    {
        if (state != State.SETUP)
        {
            return false;
        }
        state = State.ACTIVE;
        return true;
    }

    /**
     * Transitions the encounter to the ENDING state if it is currently ACTIVE.
     * <p> This method is synchronized to ensure thread safety when accessing and modifying the state variable.
     * @return true if the transition to ENDING was successful, false if the encounter was not in the ACTIVE state
     */
    synchronized boolean beginEnding() {
        if (state != State.ACTIVE) {
            return false; // Cannot transition to ENDING if not currently ACTIVE
        }
        state = State.ENDING;
        return true;
    }

    /**
     * Transitions the encounter to the ENDED state if it is currently ENDING.
     * <p> This method is synchronized to ensure thread safety when accessing and modifying the state variable.
     * @return true if the transition to ENDED was successful, false if the encounter was not in the ENDING state
     */
    synchronized boolean finishEnding() {
        if (state != State.ENDING) {
            return false; // Cannot transition to ENDED if not currently ENDING
        }
        state = State.ENDED;
        return true;
    }


}

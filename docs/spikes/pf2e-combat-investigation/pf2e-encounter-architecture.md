# PF2e Encounter Architecture

## Purpose

This document answers the PF2e combat design question at the encounter-architecture level:

- How can CoffeeMUD support Pathfinder 2e-style encounters by building on its existing room, MOB, and message infrastructure?

This document does not answer the separate questions of how to remove or deprecate CoffeeMUD combat or how to refactor NPC behavior. Those are separate spikes.

## PF2e concepts used as design inputs

The intended PF2e-inspired combat model uses:

- Encounters as the unit of combat.
- Encounter participants as the unit of per-MOB combat state.
- Rounds and turns.
- A setup phase before active combat begins.
- Deterministic initiative order.
- Three actions per participant per turn.
- One reaction per participant per round.
- Actions that may cost one, two, or three actions.
- Free actions and reactions with separate rules.
- Triggered reactions during another participant's action.
- No banking of unused actions between turns.
- A turn system that can pause while waiting for player input.

## Recommended ownership model

A room should own one EncounterManager, while the manager should own zero or more encounters.

The room is the physical location. The encounter is the combat context.

This distinction is important because all MOBs in a room are not necessarily part of the same fight. A room may contain multiple independent fights, neutral observers, or creatures that are present but not hostile.

The room's inhabitant list should therefore not be treated as the encounter members.

## Responsibilities of the room

The room should remain responsible for all the normal things that dont involve combat. Instead, the room should owns combat state indirectly, while still handling location validation and other messaging responsibilities.

The existing CoffeeMUD room message boundary can still be used to validate combat:

~~~java
Room.okMessage(source, message)
Room.send(source, message)
~~~

The room can continue to validate whether an action is possible in the location, while the encounter determines whether the action is legal at that point in the encounter.

For example:

When a player attacks another MOB, the room passes the request to the EncounterManager. The manager checks whether either MOB is already in combat and creates an Encounter if necessary. The encounter validates whether the target can be attacked, while the room’s okMessage() checks location-based conditions such as visibility, reachability, and room restrictions. If both validations succeed, the room sends the message, the encounter registers the participants, and combat begins.

## Responsibilities of EncounterManager

EncounterManager is the room-level registry and routing service. Encounters "belong" to a EncounterManager. Encounters are created by the EncounterManager, and mobs leaving and joining combat go through the EncounterManager. 

It should:

- Create encounters.
- Add and remove encounter participants.
- Find the encounter associated with a MOB.
- Route combat-related messages to the appropriate encounter.
- Handle participants entering or leaving the room.
- Support multiple simultaneous encounters.
- Remove completed encounters.
- Prevent a MOB from belonging to multiple encounters at the same time.

Conceptually:

~~~java
public interface EncounterManager
{
	List<Encounter> encounters();

    Encounter startEncounter(Collection<MOB> mobs);

    Encounter findEncounter(MOB mob);

    boolean isInEncounter(MOB mob);

    void addMob(Encounter encounter, MOB mob);

    void removeMob(MOB mob);
	
	// et cetera...
}
~~~

The manager should be a coordinator, not the place where attack rolls, damage formulas, or NPC decision-making are implemented. That is the responsibility of the encounter.

## Responsibilities of Encounter

An Encounter should be the authority for combat state and timing.

It should manage:

- Encounter participants.
- Party members.
- Initiative order.
- Round number.
- Current turn.
- Action resources.
- Reaction availability.
- Waiting for player input.
- End conditions.

An encounter begins in a `SETUP` state. During setup, the encounter validates its MOB collection, creates an `EncounterParticipant` for each MOB, and determines the initiative order. Once setup is complete, the encounter transitions to `ACTIVE` combat. It may later transition through `ENDING` before reaching `ENDED`.

The encounter should retain combat-specific state in its participants instead of adding that state directly to the MOB. This keeps the encounter responsible for temporary combat information while allowing a MOB to exist in the world outside of an encounter.

## Responsibilities of EncounterParticipant

`EncounterParticipant` represents one MOB's role in a specific encounter. It is the place to store values that belong to the MOB only for the duration of that encounter.

It should initially provide:

- The participating MOB.
- The participant's Perception value.
- The participant's initiative value.

For the initial implementation, Perception is represented by the MOB's Wisdom value. Initiative is then based on that Perception value. This is intentionally simpler than the full PF2e proficiency system while preserving the terminology needed for future expansion.

The participant can later hold:

- Remaining actions for the current turn.
- Reaction availability for the current round.
- Queued actions.
- Conditions and temporary effects.
- Participant-specific turn state.

## Initiative and turn order

When an encounter is created, it constructs its participants and calls `determineInitiative()` after validation and participant creation. The method sorts participants from highest initiative to lowest initiative. MOB name is used as a deterministic tie-breaker so that equal initiative values do not produce arbitrary ordering.

Conceptually:

~~~java
public void determineInitiative()
{
    // Perception currently equals Wisdom.
    // Sort highest initiative first and use name to break ties.
}
~~~

The ordered participant list is the encounter's turn order. The encounter also tracks the current round and the index of the current participant.

## Round and turn flow

The encounter coordinates turn progression through small lifecycle operations rather than one large turn-processing function:

~~~java
beginRound()
currentParticipant()
beginTurn()
endTurn()
advanceTurn()
~~~

`beginRound()` increments the round number and resets the current participant to the first participant in initiative order. `beginTurn()` prepares the current participant's turn. `endTurn()` completes the current participant's turn and advances the encounter. `advanceTurn()` moves to the next participant, beginning a new round after the final participant has acted.

The initial turn system is intentionally structural. It establishes the order and lifecycle needed for action resolution, but does not yet resolve actions, refresh action resources, process reactions, or wait for player input.

## Encounter lifecycle

The lifecycle is:

~~~text
SETUP -> ACTIVE -> ENDING -> ENDED
~~~

`SETUP` is used for participant creation and initiative determination. `ACTIVE` represents normal round and turn processing. `ENDING` allows cleanup to occur before the encounter is removed. `ENDED` is terminal.

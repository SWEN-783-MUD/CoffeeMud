# PF2e Encounter Architecture

## Purpose

This document answers the PF2e combat design question at the encounter-architecture level:

- How can CoffeeMUD support Pathfinder 2e-style encounters by building on its existing room, MOB, and message infrastructure?

This document does not answer the separate questions of how to remove or deprecate CoffeeMUD combat or how to refactor NPC behavior. Those are separate spikes.

## PF2e concepts used as design inputs

The intended PF2e-inspired combat model uses:

- Encounters as the unit of combat.
- Rounds and turns.
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

TODO


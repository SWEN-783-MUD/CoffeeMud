---
status: accepted
date: 2026-11-09
---

# Use a Two-Dimensional Tactical Grid Within Rooms

## Context and Problem Statement

CoffeeMUD's target-relative distance does not fully describe positions among multiple combatants, obstacles, and entrances. Milestone 1 needs precise movement, occupancy, and melee reach in a text-based encounter.

Room-to-room exploration remains based on CoffeeMUD rooms and exits. Tactical positions describe locations within an encounter room and do not replace the world navigation model.

## Decision Drivers

- Make positions and movement understandable through text and coordinates.
- Support consistent movement costs and melee reach.
- Respect walls, obstacles, entrances, and occupied tiles.
- Reuse a room's authored layout across encounters.
- Keep the initial supported actions and authoring scope small.

## Considered Options

- A two-dimensional grid within each tactical room.
- A one-dimensional line shared by combatants.
- Abstract zones or range bands.

## Decision Outcome

Chosen option: a two-dimensional tactical grid within rooms. It directly supports the milestone's destination-based Stride command, occupancy rules, and melee targeting without approximating relative positions.

### Layout and Starting Positions

- Tactical layouts describe terrain, obstacles, entrances, and NPC starting areas.
- Encounter initialization places participants using entry direction, exploration approach, terrain, and occupancy. Invalid preferred positions fall back to valid positions without overlap.
- Ordinary exploration does not require precise tactical coordinates. Entry direction and exploration activity are retained for encounter setup.
- Late arrivals also require a valid starting position. Arrival alone must not place a participant in an occupied or impassable tile.
- Runtime participant positions are distinct from the authored room layout. Encounter completion must preserve that layout.

Layout definition, validation, and Archon authoring are owned by the room/grid stories. This decision does not choose a pathfinding algorithm or storage format.

### Movement and Reach

- Players choose a destination, for example `stride D4`. One successful Stride costs one action and follows a legal route within the participant's preset Speed.
- Horizontal and vertical steps normally cost 5 feet. Diagonal steps alternate between 5 and 10 feet.
- Each participant's diagonal count continues across all Strides in the same turn. Horizontal or vertical steps do not reset it; the next turn starts with a fresh count.
- Route calculation accounts for the existing diagonal count, walls, obstacles, and occupancy. An open destination does not permit crossing a wall diagonally.
- When multiple routes are available, choose a lowest-cost route with a consistent tie-breaker.
- Validate the entire route before moving or spending an action. Invalid, occupied, blocked, or unreachable destinations leave position, action budget, and diagonal count unchanged.
- A destination beyond one Stride is rejected; the command does not automatically spend additional actions.
- Melee Strikes require a legal target within weapon reach and an unblocked attack path.

### Presentation and Scope

Use text maps with coordinates shared by player commands and Archon previews. A player's turn-start feedback includes the current map, actions, and remaining time. Players may request the map without spending an action, including during another participant's turn.

Movement updates identify the character and destination only when the observer is permitted to know them. Short combat updates need not redraw the entire map after every action; redraw frequency remains a usability decision.

Flanking, cover, ranged combat, spell areas, and advanced movement are deferred. The grid can support later work without making those features part of Milestone 1.

### Consequences

- Good, because combatants share one consistent coordinate and distance model.
- Good, because exact destinations and occupancy can be tested directly.
- Bad, because route finding must include per-turn diagonal history, not just geometric distance.
- Bad, because room layouts, validation, and readable text maps require additional work.

### Confirmation

Test cardinal and diagonal movement, diagonal counts across separate Strides, reset on a new turn, blocked corners, occupied tiles, fallback placement, and deterministic route selection. Rejected movement must have no partial effects.

Verify melee reach and blocked attack paths against the same layout. Check that exploration navigation still uses room connections and that ending an encounter preserves authored terrain. Previewing a layout must not create an encounter or move occupants.

## Pros and Cons of the Options

### Two-Dimensional Grid

- Good, because it represents routes around obstacles and consistent reach.
- Bad, because it requires pathfinding, occupancy management, and map authoring.

### One-Dimensional Line

- Good, because positions and distance calculations are simpler.
- Bad, because it cannot represent the required two-dimensional destinations and obstacle routes without adapting the model.

### Abstract Zones or Range Bands

- Good, because named destinations can be easy to describe in text.
- Bad, because exact Speed costs, diagonal movement, and melee reach require approximations that differ from the current milestone stories.

## More Information

- [Combat ruleset](pathfinder2e-madr.md)
- [Turn management](turn-system-madr.md)
- [Epic 2: Rooms, Tactical Grids & Presentation](https://github.com/SWEN-783-MUD/CoffeeMud/issues/33)
- [MUD-3.3: Starting Positions, Initiative & Awareness](https://github.com/SWEN-783-MUD/CoffeeMud/issues/42)
- [MUD-3.4.4: Stride & Tactical Movement](https://github.com/SWEN-783-MUD/CoffeeMud/issues/58)

---
status: proposed
date: 2026-09-22
---

# Define a Positional Combat Model

## Context and Problem Statement

CoffeeMUD tracks distance to a target, but this does not fully describe
where combatants stand relative to one another. We need a positional
model to support movement, weapon reach, targeting, and area effects.

This decision considers how to represent combat positions in a text-based
MUD while supporting our chosen PF2e ruleset. Turn handling is a separate
decision.

## Decision Drivers

- Make positions and movement easy to understand through text.
- Support meaningful choices about movement and targeting.
- Support PF2e positioning rules where practical.
- Keep implementation and room-building effort manageable.
- Maintain consistent distances between multiple combatants.

## Considered Options

- A two-dimensional grid.
- A one-dimensional line.
- Abstract zones or range bands.

## Decision Outcome

No option has been selected. This MADR records preliminary approaches
for further discussion and prototyping.

## Pros and Cons of the Options

### Two-Dimensional Grid

Each combatant occupies one or more cells on a map. Players move using
directions or coordinates, and an ASCII map shows positions.

- Good, because it represents movement around enemies and obstacles.
- Good, because it supports reach, flanking, cover, and area shapes.
- Good, because it provides a close foundation for PF2e positioning.
- Bad, because maps may be harder to read in a text interface.
- Bad, because it requires pathfinding, occupancy rules, and map data.
- Bad, because existing rooms would need layouts or generated maps.

### One-Dimensional Line

Each combatant occupies a position along a shared line. Distance is
calculated from those positions, and movement goes in either direction.

- Good, because positions and distances are easy to describe in text.
- Good, because movement and distance calculations are simpler.
- Good, because it can support melee reach and ranged combat.
- Bad, because moving around another creature is difficult to represent.
- Bad, because flanking, cover, and area effects need adapted rules.
- Bad, because passing and blocking rules could create bottlenecks.

### Abstract Zones or Range Bands

Combatants occupy named zones, such as a doorway, the center of a room,
or a balcony. Connections between zones define movement and range.

- Good, because players can use readable commands such as
  “move to doorway.”
- Good, because terrain can matter without tracking every square.
- Bad, because exact distances and weapon reach require approximation.
- Bad, because PF2e movement costs and area effects need adapted rules.
- Bad, because zone connections must keep movement and range consistent.
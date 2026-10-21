# PF2e Combat Scope Requirements

## Purpose

This document is designed to help nail down a defined scope for the implementation of PF2e combat in CoffeeMUD specifically for the MVP. This is not a permanent limit on the potential future scope of combat.

Below is a list of requirements for the PF2e combat implementation.

## Encounter management

- The system shall represent combat as an `Encounter`.
- The MVP shall support one player character against one NPC per encounter.
- A room shall be able to manage multiple independent encounters.
- An encounter shall track its participants independently of the room's full inhabitant list.
- A MOB shall not belong to more than one encounter at the same time.
- Encounters shall support clear lifecycle states, including setup, active combat, ending, and ended.
- An encounter shall define completion conditions, including a side being defeated.
- An encounter shall end when either participant is defeated or otherwise unable to continue.
- If the player disconnects during an encounter, the player shall be treated as defeated and lose the encounter.

## Initiative and turn order

- The system shall determine initiative when an encounter begins.
- The initial implementation shall use Wisdom as the simplified Perception value.
- The system shall establish a deterministic participant order.
- The system shall track rounds and the current participant (current turn).
- Participants shall take turns in initiative order.
- The system shall support advancing from one turn to the next.
- The system shall start a new round when every participant has had a turn.

## Actions

- Each participant shall receive three actions per turn.
- Actions shall have explicit costs.
- The system shall support one-action, two-action, and three-action activities.
- The system shall support free actions.
- The system shall validate whether a participant has enough actions.
- The system shall prevent unused actions from carrying into later turns.
- Participants shall be able to queue actions during their turn.
- Queued actions shall be validated before they resolve.
- Queued actions shall resolve in deterministic order.

## Reactions

- Each participant shall have access to one reaction per round.
- When an action occurs, the system shall determine whether it triggers any available reactions.
- If a valid reaction can occur, the system shall provide the participant with a choice of reactions.
- The participant affected by a reaction trigger shall choose whether to use an available reaction.
- The encounter shall pause action resolution while a reaction choice is pending.
- Participants shall be able to use Shield Block after raising a shield.
- Raising a shield shall make Shield Block available until the start of the participant's next turn.

## Combat resolution

- The encounter shall determine when an action resolves.
- Actions shall be resolved in a deterministic order.
- The system shall support a melee Strike as a one-action activity.
- A participant shall only use a melee Strike against a target within 5 feet.
- The encounter shall validate that an action's target is within the action's applicable range before resolving it.
- The system shall allow stepping for one action.
- The system shall allow striding for one action.
- The system shall allow raising a shield, for one action.

## Messaging and integration

- Encounters shall be started through a `CMMsg` event.
- Encounter actions and lifecycle events shall use the `CMMsg` system for validation, dispatch, and notification.

## Positioning and Movement

- Encounters shall maintain an internal two-dimensional grid.
- Each participant shall have a grid position represented by coordinates.
- The grid shall track valid and occupied spaces.
- Participants shall not occupy the same space.
- Movement actions shall validate whether the destination is reachable.
- Stride shall move a participant across the grid according to its Speed.
- A participant's default Speed shall be 25 feet.
- Step shall move a participant exactly 5 feet.
- The grid shall support basic distance checks between participants.
- The grid shall use 5-foot squares.
- A participant with the default 25-foot Speed shall be able to move up to 5 squares with one Stride.
- The MVP grid shall use fixed 10-by-10 boundaries.

## Out of scope

This is specifically what is out of scope for the MVP- this should not be taken as a hard line for future features.

- Complete PF2e rules implementation.
- Full proficiency, class, feat, ancestry, and skill systems.
- Ranged attacks.
- Spells and complex abilities.
- Complex terrain, flanking, cover, and line-of-effect rules.
- Large-scale or cross-room encounters.
- Multiple-player or multiple-NPC encounters.
- Advanced NPC tactics.
- Every PF2e reaction and free action.
- Persistent conditions and complex status effects.
- Production-ready user interfaces or combat visualization.

## Possible Stories

TODO

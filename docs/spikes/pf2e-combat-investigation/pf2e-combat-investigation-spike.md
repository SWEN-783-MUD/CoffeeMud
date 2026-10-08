## Question(s)

1. How can CoffeeMUD’s existing combat, `CMMsg`, room, MOB, class, ability, and damage systems support PF2e-inspired turn-based combat?
2. How should encounters, rounds, turns, actions, reactions, and action queues be represented?
3. How can queued actions support different PF2e action types while remaining compatible with CoffeeMUD’s existing systems?

## Why it matters

This investigation will determine whether PF2e combat should replace CoffeeMUD’s current combat flow or be implemented as a new encounter layer over the existing infrastructure.

The results will guide future work on encounter management, action queues, turn resolution, reactions, room integration, and NPC behavior.

## Timebox

Sprint 1, ending on October 7, 2026.

The investigation is limited to approximately six hours of effort. When the timebox ends, the findings and recommendation will be documented, even if some questions remain unanswered.

## Scope

In:

- Trace how CoffeeMUD’s current combat begins, proceeds, and ends.
- Trace how `CMMsg` objects move through MOBs, rooms, abilities, and combat logic.
- Investigate how a room-owned `EncounterManager` could support multiple encounters.
- Define an `Encounter` model for rounds, phases, turns, actions, reactions, and waiting for input.
- Consider how party members could plan turns together and queue multiple actions.
- Consider how one-action, two-action, three-action, reaction, and free-action abilities could be represented.
- Determine how queued actions should be validated, revalidated, resolved, and reported.
- Identify integration risks and boundaries with the existing combat system.
- Create follow-up stories for the recommended implementation.

Out:

- Implementing the complete PF2e combat system.
- Rewriting CoffeeMUD’s existing attack, damage, class, ability, or message systems.
- Implementing every Pathfinder 2e rule, condition, spell, feat, or trait.
- Fully refactoring NPC behavior.
- Building a production-ready combat interface.
- Merging prototype code into the main branch.

## Acceptance Criteria

- [ ] Each question answered, or marked unanswered with the reason.
- [ ] Findings committed to `docs/spikes/`.
- [ ] Recommendation made.
- [ ] Follow-up stories created and linked as sub-issues.
- [ ] Any prototype code left on a `spike/` branch, not merged.

## Findings

### Structural conclusion

PF2e-inspired combat is best represented as an encounter layer. The encounter provides a bounded combat context with its own participants, initiative order, rounds, turns, actions, reactions, and completion rules. This separates combat state from the general state of the room and the individual MOB.

The room represents physical presence and environmental context. An `EncounterManager` represents the room's collection of combat contexts. An `Encounter` represents one independent fight, allowing multiple encounters to exist in the same room without treating every MOB in the room as part of the same battle.

### Encounter ownership

The ownership structure is:

~~~text
Room
  └── EncounterManager
        ├── Encounter
        ├── Encounter
        └── Encounter
~~~

The manager is responsible for creating, locating, and removing encounters. It also coordinates membership so that a participant belongs to the appropriate encounter and cannot unintentionally belong to multiple encounters at once.

An optional encounter directory can provide a broader membership index. The manager remains responsible for encounters in its room, while the directory answers the question of which encounter currently contains a particular MOB.

### Participants

An encounter should contain `EncounterParticipant` objects rather than treating MOBs as the complete representation of combat state. A participant associates a MOB with the temporary state that MOB has within one encounter.

The participant model provides a natural place for:

- Perception and initiative values.
- Current action resources.
- Reaction availability.
- Queued actions.
- Conditions and temporary effects.
- Turn-specific state.

This structure keeps encounter-specific state within the encounter system while allowing MOBs to retain their broader world, social, and behavioral roles.

### Encounter lifecycle

The encounter lifecycle can be represented as:

~~~text
SETUP -> ACTIVE -> ENDING -> ENDED
~~~

`SETUP` is the preparation phase. Participants are established and initiative order is determined. `ACTIVE` represents normal round and turn processing. `ENDING` represents resolution and cleanup before removal. `ENDED` is the terminal state.

The lifecycle gives the combat system clear boundaries for preparing a fight, resolving combat, and releasing its state.

### Initiative and turn order

Initiative should be determined during encounter setup. Each participant receives a Perception value, and the encounter uses those values to establish an ordered turn sequence. For the simplified PF2e model, Perception can initially be represented by Wisdom while leaving room for proficiency, circumstance, item, and status modifiers later.

The encounter owns the ordered participant list and the current position within it. Ties should be resolved consistently so that the same encounter state produces the same turn order.

### Round and turn flow

The encounter should coordinate turn progression through distinct operations:

~~~text
begin round
  begin participant turn
    accept or queue actions
    resolve actions and reactions
  end participant turn
advance to next participant
repeat
~~~

The encounter tracks the current round and current participant. Beginning a round establishes the first participant in initiative order. Beginning a turn prepares that participant's resources. Ending a turn completes or resolves its actions and advances the encounter. Once all participants have acted, the next round begins.

This model supports synchronized planning because participants can queue actions during the planning portion of a round while the encounter remains responsible for deterministic resolution.

### Answers to the spike questions

1. CoffeeMUD can support PF2e-inspired combat through a separate encounter layer that owns combat state while preserving the room, MOB, message, ability, and damage boundaries as integration points.
2. Encounters provide the unit of combat; participants provide per-MOB combat state; and the encounter owns setup, initiative, rounds, turns, actions, reactions, and completion.
3. Queued actions should be represented as encounter-owned requests with explicit costs, targets, requirements, and resolution behavior. The encounter can validate them when queued and revalidate them when resolved.

### Unresolved findings

This spike establishes the structural direction, but it does not establish every implementation detail. The following findings remain open:

- The exact `CMMsg` hook points for routing an action into an encounter have not been fully identified.
- The precise division of responsibility between room message validation, encounter validation, and message dispatch requires further tracing and testing.
- The complete action representation and queue contract have not been defined.
- The ordering and timing of reaction windows has not been specified.
- The boundary between encounter-owned resolution and existing attack, damage, ability, and effect systems requires additional design.
- The mechanism for NPCs and player parties to submit actions during the same planning period remains open.
- The rules for movement between rooms during an encounter have not been determined.
- The exact conditions for ending an encounter and releasing its participants require further definition.

These unresolved items do not change the recommended ownership model. They identify the next investigations and stories needed before the encounter system can support complete PF2e combat behavior.

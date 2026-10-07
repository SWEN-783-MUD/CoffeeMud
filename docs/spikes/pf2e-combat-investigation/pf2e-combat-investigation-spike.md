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
TODO
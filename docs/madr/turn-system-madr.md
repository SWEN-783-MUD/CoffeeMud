---
status: accepted
date: 2026-11-09
---

# Use Encounter-Owned Sequential Turns

## Context and Problem Statement

CoffeeMUD's native combat progresses through MOB ticks, opponent relationships, commands, and messages. Milestone 1 needs sequential multiplayer turns with a three-action budget, bounded waiting, and consistent behavior for player commands, NPCs, and timeouts.

An encounter must own combat timing and resources while CoffeeMUD rooms continue to provide location and message validation. Turn legality must not depend on native MOB action credits or automatic attacks.

## Decision Drivers

- Give one encounter authority over initiative, rounds, turns, and resources.
- Process player, NPC, and timeout actions through the same validation rules.
- Support split parties without indefinite waiting or free preparation actions.
- Keep informational commands available while combat actions are restricted.
- Prevent rejected, duplicate, or stale requests from changing later turns.

## Considered Options

- Retain native tick-based automatic combat.
- Give each MOB an independent turn scheduler.
- Collect complete turns and resolve them simultaneously.
- Let an encounter coordinate sequential participant turns.

## Decision Outcome

Chosen option: encounter-owned sequential turns. A room's EncounterManager coordinates admission and the room's encounter; the encounter owns participant order, rounds, the active participant, and combat resources. Milestone 1 supports one ongoing encounter in the test dungeon. Dungeon-wide enforcement must coordinate room managers; a room slot alone does not enforce that limit.

CoffeeMUD messages remain the boundary for location validation and dispatch. Native automatic attacks, damage, and command processing must not independently advance encounter combat.

### Alignment with the Epics

[Encounter Management (#14)](https://github.com/SWEN-783-MUD/CoffeeMud/issues/14) establishes room-managed encounters, initially one player against one NPC. [Epic 3 (#39)](https://github.com/SWEN-783-MUD/CoffeeMud/issues/39) extends that foundation to the milestone's multiplayer encounter. The one-encounter test-dungeon limit is milestone scope, not a requirement that the entire server share one encounter.

[Round and Turn Management (#22)](https://github.com/SWEN-783-MUD/CoffeeMud/issues/22) covers initiative, rounds, turns, actions, and reactions. [Action Queue (#10)](https://github.com/SWEN-783-MUD/CoffeeMud/issues/10) establishes prerequisite checks and action results. The lifecycle below follows those responsibilities. Reaction support remains part of the broader design, but [MUD-3.4.1 (#55)](https://github.com/SWEN-783-MUD/CoffeeMud/issues/55) explicitly defers reactions and advanced action types beyond Milestone 1.

### Lifecycle and Setup

Use `PENDING`, `ACTIVE`, `AWAITING_RESCUE`, and terminal `COMPLETED` states.

- An opening attack starts setup before damage or action costs resolve. It grants no free attack and is not automatically replayed.
- Eligible party members already present become initial participants. Other rooms' party members are not enrolled automatically.
- When nearby eligible party members can respond, a configurable joining window defaults to 15 seconds. If all eligible members are already present, setup proceeds without the countdown.
- Already-enrolled participants cannot attack, move, change equipment or exploration activity, or otherwise prepare during pending setup. Map, status, help, and chat remain available without action costs.
- Arrivals before the fixed joining deadline join the initial set. Arrivals at or after it follow late-participation rules. Repeated triggers and arrivals do not extend the deadline.
- Finalize starting positions, detection, and initiative before ordinary turns begin.

### Rounds and Turns

- Activation begins round one. Use the initiative order supplied by setup, with a documented deterministic tie-breaker.
- Track the active participant. Only that participant may perform turn-bound actions or voluntarily end the turn.
- Each eligible participant receives three actions at turn start. Unused actions do not carry forward, and attack and diagonal movement counts reset for the new turn.
- Turn-start and turn-end processing occur once at their respective boundaries. Spending the last action, voluntarily ending, becoming unable to act, or finishing timeout handling ends the turn.
- Skip defeated NPCs and unconscious players without waiting for input or performing timeout actions.
- After the final eligible participant finishes, advance the round and begin with the first eligible participant.
- Late participants become eligible at the next round boundary without interrupting an existing turn.
- Pending, awaiting-rescue, and completed encounters cannot advance ordinary combat turns.

### Actions and Timeouts

Individual commands resolve as they are processed; planning and submitting a complete turn is outside Milestone 1. Actions follow `pre_use`, `execute`, and `post_use`: validate current legality, apply effects and costs once, then check turn or encounter progression.

Resolution is serialized. Revalidate actor eligibility, turn ownership, remaining resources, targets, and parameters immediately before execution. Invalid requests explain the failure without consuming actions or changing combat state. Informational commands do not spend actions.

Queued requests belong to their originating encounter, participant, and turn. Finished-turn requests cannot execute in a later turn. Pause or completion discards pending requests instead of replaying them. The mechanism for associating requests with turns remains an implementation decision.

Player turn limits are configurable. Commands accepted before the deadline resolve before timeout handling; commands accepted at or after the deadline are rejected as expired. Timeout preferences initially support End Turn and Raise Shield if possible. An unavailable fallback safely ends the turn. Repeated processing must not apply an action or end a turn twice.

### Rescue and Completion

When all enrolled players are unconscious and enemies remain, eligible conscious party members outside the encounter may receive a 15-second rescue window. Combat and ordinary turn timers pause while the rescue deadline continues in real time. The encounter still occupies the dungeon's encounter allowance.

An eligible rescuer must arrive before the deadline through a valid room connection and receive a valid position. Successful rescue resumes at the next round boundary, preserving existing initiative order and incorporating the rescuer. It does not restart the opening joining window or wake unconscious participants.

If rescue is unavailable or expires, complete the encounter in defeat. Victory and defeat apply their respective recovery and reset policies from the combat-ruleset MADR. Completion cleans up runtime encounter state while preserving authored room terrain. Duplicate events cannot reopen a completed encounter.

### Consequences

- Good, because turn legality and resource changes have a single authority.
- Good, because the same action lifecycle serves players, NPCs, and timeout behavior.
- Good, because independent setup, turn, and rescue deadlines can be tested at exact boundaries.
- Bad, because native combat paths require deliberate interception and isolation.
- Bad, because request ordering, eligibility, late joining, and cleanup need explicit handling.

### Confirmation

Verify round-one activation, fixed turn order, round wraparound, three-action refresh, per-turn resets, and rejection of out-of-turn actions. Exercise empty or ineligible participant sets so progression cannot loop indefinitely.

Test duplicate End Turn requests and stale timeout events, including events arriving during a later turn of the same participant. Test command acceptance immediately before and at deadlines, failed validation without resource changes, and serialized resolution without double application.

Check joining and rescue deadlines without relying on long real-time sleeps. Confirm that pending and paused participants cannot prepare or act, late arrivals wait for the correct boundary, completed encounters remain terminal, and native combat cannot apply extra attacks or damage outside the encounter. These are design validation targets; the current implementation is incremental.

## More Information

- [Combat ruleset](pathfinder2e-madr.md)
- [Positional combat](positional-combat-madr.md)
- [Epic 3: Unstructured Encounter Lifecycle](https://github.com/SWEN-783-MUD/CoffeeMud/issues/39)
- [MUD-3.2: Party Notifications & Encounter Joining Window](https://github.com/SWEN-783-MUD/CoffeeMud/issues/41)
- [MUD-3.4.2: Action Queue & Resolution](https://github.com/SWEN-783-MUD/CoffeeMud/issues/56)
- [MUD-3.4.3: Round & Turn Management](https://github.com/SWEN-783-MUD/CoffeeMud/issues/57)
- [MUD-3.10: Party Rescue Window & Defeat](https://github.com/SWEN-783-MUD/CoffeeMud/issues/49)

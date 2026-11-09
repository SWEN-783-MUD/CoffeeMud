---
status: proposed
date: 2026-11-09
---

# Use a Scoped Pathfinder 2e Remaster Combat Ruleset

## Context and Problem Statement

CoffeeMUD has its own combat calculations, character statistics, abilities, and equipment. Milestone 1 needs one complete multiplayer tactical encounter using a consistent set of rules without first adapting every native character and ability.

We will use a defined subset of Pathfinder 2e Remaster combat, supplied through fixed player and NPC presets. This is a milestone scope decision, not a claim that the full PF2e ruleset or all CoffeeMUD integrations are implemented. Turn management and positional combat are covered by separate MADRs.

## Decision Drivers

- Use consistent attack, defense, movement, and action rules for players and NPCs.
- Deliver a playable encounter with deterministic, testable rule boundaries.
- Keep character conversion and equipment integration manageable.
- Preserve a path to broader PF2e support after the milestone.
- Make MUD-specific recovery and multiplayer policies explicit.

## Considered Options

- Retain CoffeeMUD's native combat rules.
- Create a custom tactical ruleset.
- Adopt the full PF2e combat and character systems immediately.
- Adopt a scoped PF2e combat subset using fixed presets.

## Decision Outcome

Chosen option: adopt a scoped PF2e Remaster combat subset using fixed presets. It provides shared combat rules while limiting the initial adaptation work.

### Milestone 1 Rules

- Participants normally use Perception for initiative. Eligible characters Avoiding Notice may use Stealth, with detection resolved relative to relevant observers.
- Each eligible participant receives three actions on their turn. Unused actions do not carry forward.
- Supported actions are Stride, melee Strike, Raise a Shield, and End Turn.
- Strike compares a d20 plus attack modifier and multiple attack penalty against AC. Totals at least 10 above AC critically succeed; totals at least 10 below AC critically fail. Other totals succeed when they meet AC.
- A natural 20 improves the calculated degree of success by one step; a natural 1 worsens it by one step, bounded by the four available degrees.
- A successful Strike deals normal weapon damage; a critical success deals double damage. Failure and critical failure deal no damage for the supported basic Strike.
- Initial weapons are non-agile melee weapons. Repeated Strikes use penalties of 0, -5, and -10. A legal miss still spends its action and advances the attack count; invalid requests do neither.
- Stride uses preset Speed and the movement rules in the positional-combat MADR.
- Raise a Shield costs one action and grants its configured AC bonus until the start of the participant's next turn or encounter completion. It does not stack with itself.

### Combat Values and Integration

Presets provide maximum HP, AC, Perception, Stealth, Speed, attack modifier, weapon damage, reach, and supported weapon or shield details. Players and NPCs use the same rules. Missing or unsupported presets are reported before encounter admission; native abilities and equipment do not silently replace the supported rules.

Current HP is separate from preset maximum HP. Inspecting a preset does not heal a character. Presets cannot change during pending, active, or awaiting-rescue encounters. Definitions and saved dungeon NPC assignments must survive reloads. The storage and mapping to CoffeeMUD character data remain integration work; this MADR does not select that mechanism.

### Explicit MUD Policies

The joining window, turn timeouts, rescue window, and recovery rules are project-specific policies, not assertions about standard PF2e rules.

- Players reaching zero HP become unconscious; HP is clamped at zero. They cannot act or move but can use informational commands.
- Victory restores unconscious players to exactly 1 HP. Conscious players retain their current HP.
- When all enrolled players are unconscious and enemies remain, eligible conscious party members outside the encounter may receive a 15-second rescue opportunity. Participants stay unconscious during that window.
- If rescue is unavailable or unsuccessful, defeat restores knocked-out participants to 1 HP and returns them to the dungeon entrance. Only that encounter's enemies reset; earlier cleared rooms keep their progress. Uninvolved party members are not moved or healed.

### Deferred Scope

Full character creation, attribute/proficiency conversion, ranged attacks, spellcasting, reactions including Shield Block, advanced conditions, weapon effects, and PF2e dying/wounded/stabilization rules are deferred. Presets are a temporary source of combat values, not a replacement design for future character customization.

### Consequences

- Good, because a small shared ruleset supports an end-to-end encounter and controlled tests.
- Good, because later character systems can provide the same combat values.
- Bad, because this subset does not inherit every balance guarantee of a complete PF2e implementation.
- Bad, because native combat paths must be controlled so they cannot bypass encounter rules.
- Bad, because persistent HP and preset assignment require explicit CoffeeMUD integration.

### Confirmation

Use controlled attack and damage rolls to test all four degrees of success, natural-roll adjustments, multiple attack penalties, action expenditure, and shield expiration. Verify that invalid requests change neither HP nor action resources.

Test knockout, rescue, victory, and defeat separately from damage formulas. Confirm that preset inspection never heals a character, unsupported presets are rejected, and saved definitions and NPC assignments survive reloads.

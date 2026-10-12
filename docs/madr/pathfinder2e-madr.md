---
status: proposed/accepted
date: 2026-09-22
---

# Use Pathfinder 2e (Pathfinder Second Edition Remaster) as the Combat Ruleset

## Context and Problem Statement

CoffeeMUD has its own character attributes, combat calculations, and action rules. We want to adopt Pathfinder 2e’s attribute modifiers, proficiency scaling, three-action economy, and d20 checks with four degrees of success as a consistent foundation for combat.

Using these rules together gives us an established framework for balancing characters, abilities, equipment, and encounters. This decision covers the combat ruleset; turn-handling architecture and positional combat are separate decisions.

## Decision Drivers

* Use PF2e’s balance framework for characters, abilities, equipment, and encounters.
* Apply consistent rules for attribute modifiers and proficiency scaling.
* Use defined rolls, difficulty classes, and degrees of success.
* Support tactical choices through PF2e’s actions and reactions.
* Reuse CoffeeMUD infrastructure where practical.

## Considered Options

* Retain CoffeeMUD’s existing combat rules.
* Create a custom turn-based ruleset.
* Adopt Pathfinder 2e combat rules.

## Decision Outcome

Proposed/accepted choice: Pathfinder 2e combat rules, because they provide a shared
foundation for balancing characters, abilities, equipment, and encounters.
We will use its attribute modifiers, proficiency scaling, action economy,
and d20 checks with four degrees of success.

This decision covers the combat ruleset. How turns are managed and how
position affects combat will be addressed in separate MADRs.

### Consequences

- Good, because combat uses an established framework for balancing
  characters, abilities, equipment, and encounters.
- Good, because attributes, proficiency, actions, and rolls follow
  consistent, documented rules.
- Good, because expected behavior can be tested against PF2e rules.
- Bad, because CoffeeMUD’s combat calculations, character statistics,
  abilities, and equipment will need substantial adaptation.
- Bad, because adopting only parts of PF2e or changing its rules may
  affect balance and require additional testing.
 
### Confirmation

Validate the ruleset with a small combat prototype. Test attribute
modifiers, proficiency bonuses, action costs, attack rolls, saving throws,
and the four degrees of success against expected PF2e results.

Use updated characters, abilities, equipment, and creatures to
check combat balance. Verify that players and NPCs follow the applicable
rules.
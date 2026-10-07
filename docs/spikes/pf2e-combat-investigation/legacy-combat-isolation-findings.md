# Spike findings: isolating CoffeeMud's legacy combat

## Questions

1. Where does legacy combat begin, advance, and end, and what code depends on those pathways?
2. Which boundaries can be intercepted to let an encounter system take over, and what does a minimal prototype demonstrate?
3. What additional work is required before legacy combat can be safely deprecated for future PF2e combat?

## Why it matters

A future encounter system needs authority over who is fighting, when actions happen, and when combat ends. CoffeeMud already performs these tasks through commands, MOB state, ticks, messages, abilities, behaviors, and the combat library. Introducing encounters without addressing those dependencies could leave automatic attacks, restored targets, or direct damage happening outside the encounter's rules.

This investigation identifies concrete places to inspect or refactor and recommends an incremental approach. The prototype explores two integration boundaries. It is not a complete replacement combat system.

## Timebox and scope

Sprint 1 ends October 7, 2026. The planned investigation timebox was approximately six hours. Actual effort has not been recorded here. At the deadline, report unresolved questions rather than expanding the prototype.

In scope:

- Trace combat initiation, scheduling, messages, damage, targeting, and cleanup.
- Identify dependencies.
- Demonstrate limited encounter takeover.
- Recommend next steps and prepare follow-up stories.

Out of scope: implementing PF2e turns or three-action rules, rewriting NPC behavior, suppressing every route in the prototype, implementing rewards and comprehensive disconnect handling, or merging the prototype into main.

## Findings: where legacy combat can enter or resume

### The central flow

A typical weapon fight follows this route:

```text
Command or behavior requests an attack
  -> MUDFight.postAttack()
  -> room validates and dispatches a weapon-attack CMMsg
  -> MOB message handlers establish combat relationships
  -> MUDFight resolves weapon attack and damage
  -> later MOB ticks continue automatic combat
```

This is one common flow, not the only flow. Spells can send malicious messages and request damage without postAttack. Behaviors and scripts can assign victims directly. Damage processing can lead into death handling.

A MOB's victim is a key legacy relationship. Encounter membership is separate state: registering a MOB does not itself prevent other code from assigning a victim. Clearing a victim once does not prevent later reassignment.

### A. Starting fights and choosing opponents

| Inspected location | What it does | Implication for encounters |
| --- | --- | --- |
| [Kill.java](../../../com/planet_ink/coffee_mud/Commands/Kill.java), execute() | Requests an attack or changes the current opponent. | Our hook covers players in the test room. Other routes still need encounter checks. |
| [StdMOB.java](../../../com/planet_ink/coffee_mud/MOBS/StdMOB.java), executeMsg() | Reacts to hostile messages and can establish a legacy fight. | Messages can start combat without going through postAttack. |
| [MUDFight.java](../../../com/planet_ink/coffee_mud/Libraries/MUDFight.java), handleBeingAssaulted() | Sets up retaliation and resolves weapon attacks from received messages. | Encounter rules must control retaliation and attack resolution here too. |
| MUDFight.makeFollowersFight() | Makes eligible allies join a legacy fight by assigning an opponent. | Allies must follow encounter joining rules instead of entering automatically. |
| [Aggressive.java](../../../com/planet_ink/coffee_mud/Behaviors/Aggressive.java), startFight() | Submits attack commands for an NPC, sometimes including backstab. | NPC attack requests need encounter admission. Our KILL hook only redirects players. |
| [TargetPlayer.java](../../../com/planet_ink/coffee_mud/Behaviors/TargetPlayer.java), tick() | Switches an NPC's existing opponent to a player in that opponent's group. | Target changes must stay within the encounter's allowed targets. |
| [CombatAbilities.java](../../../com/planet_ink/coffee_mud/Behaviors/CombatAbilities.java) and [Arrest.java](../../../com/planet_ink/coffee_mud/Behaviors/Arrest.java) | Choose opponents or initiate combat through NPC and law-enforcement logic. | Coordinate these decisions with encounter rules and the NPC spike. |

### B. Scheduling attacks and commands

| Inspected location | What it does | Implication for encounters |
| --- | --- | --- |
| StdMOB tick processing and MUDFight.tickCombat() / subtickAttack() | Repeatedly processes legacy fights, including attacks and range changes. | Blocking postAttack does not stop the rest of combat processing. |
| StdMOB.enqueCommand() / dequeCommand() | Executes commands immediately or holds them until they can run. | Clearing pending commands does not cancel commands already executing. |
| StdMOB.actions() / setActions() and ticks | Stores and replenishes credit used to schedule commands and attacks. | Resetting credit is temporary. Encounters need their own action budget. |
| [Fighter_BackHand.java](../../../com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_BackHand.java), tick() | Attempts an extra attack and may restore the previous opponent afterward. | Our guard blocks the attack request, but encounter rules must govern its timing and targeting. |

### C. Messages, damage, and additional effects

| Inspected location | What it does | Implication for encounters |
| --- | --- | --- |
| [StdRoom.java](../../../com/planet_ink/coffee_mud/Locales/StdRoom.java), okMessage() / send() / dispatch | Checks and delivers events that game objects react to. | Combat events need encounter checks while ordinary world events remain available. |
| MUDFight.postDamage() | Sends a damage event without calling postAttack. | Our attack guard does not block this damage route. |
| StdMOB.executeMsg() and MUDFight.handleBeingDamaged() | Applies damage to HP and may trigger death, panic, bleeding, or injury. | Encounters must control damage consequences as well as damage requests. |
| [Spell_Fireball.java](../../../com/planet_ink/coffee_mud/Abilities/Spells/Spell_Fireball.java), invoke() | Sends hostile spell events, calculates damage, and calls postDamage. | Spell execution can bypass both prototype guards. |
| [Fighter_Charge.java](../../../com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Charge.java), invoke() | Changes targeting, range, and effects before an optional weapon attack. | Blocking the final attack does not prevent the earlier changes. |
| [FlamingSword.java](../../../com/planet_ink/coffee_mud/Items/Weapons/FlamingSword.java), executeMsg() | Adds fire damage after a qualifying damage event. | Item effects can cause additional damage outside encounter action handling. |
| [Trap_SnakePit.java](../../../com/planet_ink/coffee_mud/Abilities/Traps/Trap_SnakePit.java) | Damages the target and can put spawned snakes into legacy combat. | Trap damage and newly involved creatures both need encounter rules. |
| [Disease_Plague.java](../../../com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Plague.java) and [FieryRoom.java](../../../com/planet_ink/coffee_mud/Behaviors/FieryRoom.java) | Apply disease or environmental damage through postDamage. | Decide whether this damage follows world time or encounter time. |

### D. Scripts and saved opponents

| Inspected location | What it does | Implication for encounters |
| --- | --- | --- |
| [DefaultScriptingEngine.java](../../../com/planet_ink/coffee_mud/Common/DefaultScriptingEngine.java), MPKILL / MPHIT cases | Sets opponents or attempts attacks and restores saved opponents. Other script paths apply damage. | Scripts can recreate legacy combat state or bypass weapon-attack requests. |
| [StdAbility.java](../../../com/planet_ink/coffee_mud/Abilities/StdAbility.java), saveCombatState() / restoreCombatState() | Saves and restores a character's or group's legacy opponents. | Restoring old opponents must not undo encounter takeover or cleanup. |
| StdMOB.setVictim() / getVictim() / isInCombat() | Sets or reads the current opponent. Reads can also clean invalid state. | Check opponent changes against encounters and keep logging from changing gameplay state. |

### E. Ending fights and handling death

| Inspected location | What it does | Implication for encounters |
| --- | --- | --- |
| [Flee.java](../../../com/planet_ink/coffee_mud/Commands/Flee.java), execute(), and StdMOB.makePeace() | Ends or withdraws from a legacy fight. | Ending a legacy fight does not release encounter membership. |
| [DefaultSession.java](../../../com/planet_ink/coffee_mud/Common/DefaultSession.java), preLogout() | Performs legacy fight cleanup when a character logs out. | Logout must also notify the encounter system and handle membership. |
| MUDFight.postDeath() / handleDeath() and related death processing | Handles death and related experience rewards or penalties. | Define encounter ending and rewards for death separately from cancellation or disconnect. |
| Kill.execute(), administrative DEAD path | Directly kills a target before reaching our KILL hook. | Decide whether admin kills bypass encounters or notify them. |


These are representative source-inspected dependencies, not a claim that every route has been reproduced during an encounter. Caller conditions still determine whether each path runs. Search results identify candidates. They do not prove that every caller needs its own patch.

Several callers share central boundaries. One policy may cover many callers, but side effects before that boundary and direct state restoration need separate treatment.

## Findings: the minimal prototype

The prototype separates membership from lifecycle ownership:

- [EncounterDirectory](../../../com/planet_ink/coffee_mud/Combat/EncounterDirectory.java) tracks live MOB identity and registers all participants or none.
- [EncounterManager](../../../com/planet_ink/coffee_mud/Combat/EncounterManager.java) owns room encounters and coordinates engagement and cleanup.
- [CMEncounters](../../../com/planet_ink/coffee_mud/Libraries/CMEncounters.java) exposes the shared directory.
- [Encounter](../../../com/planet_ink/coffee_mud/Combat/Encounter.java) holds identity and state.

Ordinary player KILL in New Area#0 redirects to engagement and returns before legacy attack initiation. postAttack refuses registered attackers or targets before weapon drawing and attack-message creation. Membership, rather than ACTIVE alone, controls protection.

[EncounterControl](../../../com/planet_ink/coffee_mud/Commands/EncounterControl.java) provides STATUS and manual END. Cleanup clears each participant's victim, pending commands, and scheduling credit before releasing membership. Cleanup failure retains ENDING and membership for retry. Lifecycle logs correlate operations by encounter ID without telemetry failures disrupting cleanup.

## Answers and unresolved work

**Question 1: partially answered.** The map identifies central boundaries and representative command, NPC, ability, item, script, and session dependencies. An exhaustive caller list and reproduction of every route remain unfinished within the timebox.

**Question 2: answered for the limited experiment.** Player initiation and the weapon-attack request can be intercepted using shared membership checks. Cleanup can occur before release. These two guards do not prevent all combat state changes or damage.

**Question 3: partially answered.** The complete suppression strategy remains a team decision. 

## Recommendation

Deprecate legacy combat incrementally behind explicit encounter policies rather than deleting MUDFight, MOB ticking, or room messages wholesale. Separate control of combat timing and membership from world infrastructure that can still be reused.

Prioritize malicious-message handling and victim assignment alongside the existing postAttack boundary, then direct damage and delayed/restored state. Define an explicit route for authorized encounter actions to apply damage without restarting automatic combat.

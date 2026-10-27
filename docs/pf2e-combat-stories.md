# PF2e Combat Stories

These stories break the MVP requirements into implementation-sized pieces. The MVP supports one player character against one NPC in a fixed 10-by-10 encounter grid.

## Feature 1: Encounter Creation and Message Pipeline

### Story: Manage encounters in a room (EncounterManager)

As a room, I want to manage its active encounters so that combat state is organized separately from the room's general inhabitants.

#### Acceptance criteria

- [ ] The room owns an encounter manager.
- [ ] The encounter manager can add, find, and remove encounters.
- [ ] A MOB cannot belong to more than one active encounter.
- [ ] Multiple independent encounters can exist in the same room.

### Story: Ways to start an encounter

As a player, I want to start an encounter with an NPC so that combat can begin through the existing CoffeeMUD message flow.

#### Acceptance criteria

- [ ] The encounter contains one player and one NPC.
- [ ] Both participants are present and eligible to fight.
- [ ] Neither participant is already in another encounter.
- [ ] The encounter begins only after validation succeeds.

### Story: End an encounter

As a participant, I want the encounter to end when combat is no longer possible so that the system records a clear result.

#### Acceptance criteria

- [ ] The encounter ends when either participant is defeated or unable to continue.
- [ ] A disconnected player is treated as defeated.
- [ ] The result identifies whether the player won or lost.
- [ ] Participants are removed from the active encounter when it ends.

### Story: Track encounter lifecycle states

As the encounter system, I want to track lifecycle states so that an encounter can only perform valid operations at each stage.

#### Acceptance criteria

- [ ] An encounter can enter setup, active, ending, and ended states.
- [ ] Initiative is determined during setup before active combat begins.
- [ ] Actions can only resolve while the encounter is active.
- [ ] An ended encounter cannot accept new actions or participants.

## Feature 2: Initiative and Turn Management

### Story: Determine initiative

As a participant, I want initiative to be determined when combat begins so that the encounter has a predictable turn order.

#### Acceptance criteria

- [ ] Initiative is determined during encounter setup.
- [ ] Wisdom is used as the initial simplified Perception value.
- [ ] Initiative ties are resolved deterministically.
- [ ] The resulting order is stored by the encounter.

### Story: Advance turns and rounds

As a participant, I want the encounter to advance through turns and rounds so that each combatant receives an opportunity to act.

#### Acceptance criteria

- [ ] The encounter tracks the current participant.
- [ ] The encounter tracks the current round.
- [ ] Turns advance according to initiative order.
- [ ] A new round begins after both participants complete a turn.

### Story: Reset turn actions

As a participant, I want my actions to reset at the start of my turn so that I receive the correct number of actions each round.

#### Acceptance criteria

- [ ] The active participant receives three actions at the start of each turn.
- [ ] Actions cannot be carried into a later turn.
- [ ] The encounter prevents actions from being taken outside the participant's turn.

### Story: Reset reactions each round

As a participant, I want my reaction to refresh at the start of each round so that I can use one reaction per round.

#### Acceptance criteria

- [ ] Each participant can use no more than one reaction per round.
- [ ] A participant's reaction becomes available at the start of a new round.
- [ ] The encounter tracks whether each participant has used their reaction.

## Feature 3: Actions and Movement

### Story: Queue encounter actions

As a participant, I want to queue actions during my turn so that I can plan my turn before actions resolve.

#### Acceptance criteria

- [ ] A participant can queue one or more actions during their turn.
- [ ] Each action has an explicit action cost.
- [ ] The encounter rejects a queue that exceeds the participant's available actions.
- [ ] Queued actions are validated before resolution.
- [ ] Queued actions resolve in deterministic order.

### Story: Support PF2e action types

As a participant, I want to use actions with different costs so that the encounter can represent basic PF2e turn structure.

#### Acceptance criteria

- [ ] The encounter supports one-action activities.
- [ ] The encounter supports two-action activities.
- [ ] The encounter supports three-action activities.
- [ ] The encounter supports free actions.
- [ ] Each action declares its action cost.
- [ ] The encounter validates the cost against the participant's remaining actions.

### Story: Resolve actions at a defined time

As a participant, I want queued actions to resolve at a defined point in the turn so that combat behavior is predictable.

#### Acceptance criteria

- [ ] The encounter defines whether actions resolve immediately when submitted or at a turn/round resolution point.
- [ ] The selected timing is applied consistently to queued actions.
- [ ] Reactions can interrupt action resolution at the defined reaction window.
- [ ] Invalid actions do not apply their effects.

### Story: Move on the encounter grid

As a participant, I want to move on a combat grid so that positioning affects which actions are possible.

#### Acceptance criteria

- [ ] The encounter uses a 10-by-10 grid.
- [ ] Each grid square represents 5 feet.
- [ ] Each participant has a tracked grid position.
- [ ] Participants cannot occupy the same space.
- [ ] Movement cannot leave the grid boundaries.

### Story: Stride and Step

As a participant, I want to Stride or Step so that I can reposition during combat.

#### Acceptance criteria

- [ ] Stride costs one action.
- [ ] A participant with 25-foot Speed can Stride up to 5 squares.
- [ ] Stride distance is calculated from the participant's Speed.
- [ ] A participant without a custom Speed uses the 25-foot default.
- [ ] Step costs one action.
- [ ] Step moves the participant exactly 1 square.
- [ ] Movement destinations are validated before movement resolves.

### Story: Make a melee Strike

As a participant, I want to make a melee Strike so that I can attack an adjacent opponent.

#### Acceptance criteria

- [ ] Strike costs one action.
- [ ] The target must be within 1 square, or 5 feet, of the attacker.
- [ ] The encounter validates the target before resolving the Strike.
- [ ] The Strike produces a combat result and appropriate notification.

### Story: Validate action target range

As a participant, I want the encounter to validate target range before an action resolves so that actions cannot affect invalid targets.

#### Acceptance criteria

- [ ] Actions with range requirements declare or provide their applicable range.
- [ ] The encounter checks distance before resolving the action.
- [ ] An out-of-range action is rejected without applying its effects.
- [ ] The target is still validated as an active and eligible participant.

### Story: Raise a Shield

As a participant, I want to raise a shield so that I can become eligible to use Shield Block.

#### Acceptance criteria

- [ ] Raising a shield costs one action.
- [ ] The participant remains eligible for Shield Block until the start of their next turn.
- [ ] The encounter records the participant's shield state.

## Feature 4: Reactions and Combat Resolution

### Story: Choose a reaction

As a participant, I want to choose whether to use an available reaction so that I control how my character responds to combat events.

#### Acceptance criteria

- [ ] The encounter detects valid reaction triggers.
- [ ] The affected participant is offered the available reaction choice.
- [ ] Action resolution pauses while the choice is pending.
- [ ] The encounter resumes after the participant chooses or declines.
- [ ] A participant cannot use more than one reaction per round.

### Story: Use Shield Block

As a participant who raised a shield, I want to use Shield Block so that I can respond to an eligible attack.

#### Acceptance criteria

- [ ] Shield Block is available only after raising a shield.
- [ ] Shield Block is offered during its valid reaction window.
- [ ] Using Shield Block consumes the participant's reaction.
- [ ] Shield Block resolves through the encounter.

## Feature 5: Defeat and Disconnect Handling

### Story: Handle defeated participants

As an encounter, I want to prevent defeated participants from acting so that combat ends consistently.

#### Acceptance criteria

- [ ] A participant at 0 HP or otherwise unable to act cannot queue new actions.
- [ ] A defeated participant cannot resolve queued actions.
- [ ] The encounter determines the winner when a participant is defeated.
- [ ] The encounter transitions to its ended state.

### Story: Handle player disconnects

As an encounter, I want to detect a player disconnect so that the encounter can resolve without waiting indefinitely.

#### Acceptance criteria

- [ ] A player disconnect during an encounter is detected.
- [ ] The player is treated as defeated.
- [ ] The NPC is recorded as the winner.
- [ ] The encounter ends and releases both participants.

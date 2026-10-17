# PF2e Combat Scope Requirements

## Purpose

This document is designed to help nail down a defined scope for the implementation of PF2e combat in CoffeeMUD specifically for the MVP. This is not a permanent limit on the potential future scope of combat.

Below is a list of requirements for the PF2e combat implementation.

## Encounter management

- The system shall represent combat as an `Encounter`.
- A room shall be able to manage multiple independent encounters.
- An encounter shall track its participants independently of the room's full inhabitant list.
- A MOB shall not belong to more than one encounter at the same time.
- Encounters shall support clear lifecycle states, including setup, active combat, ending, and ended.

## Initiative and turn order

- The system shall determine initiative when an encounter begins.
- The initial implementation shall use Wisdom as the simplified Perception value.
- The system shall establish a deterministic participant order.
- The system shall track rounds and the current participant.
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

## Reactions

- Each participant shall have access to one reaction per round.
- (TODO)

## Combat resolution

- The encounter shall determine when an action resolves.
- Actions shall be resolved in a deterministic order.
- The system shall allow striking the enemy as an action
- (TODO)

## Messaging and integration

- Encounter shall be started using `CMMsg`.
- Encounter behavior shall be handled using `CMMsg`.
- Encounter lifecycle messages shall be handled through messages.

## Scope boundaries

TODO

## Possible Stories

TODO

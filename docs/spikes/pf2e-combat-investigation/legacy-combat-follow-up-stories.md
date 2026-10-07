# Follow-up story drafts: legacy combat isolation

These are drafts for discussion, not GitHub issues. After review, create the agreed stories and link them to the combat-isolation spike. They describe future work and do not add requirements to the prototype.

## 1. Activate encounters after setup completes

**Problem:** New encounters remain in SETUP. The existing command flow cannot reuse or manually end them.

**Goal:** Activate an encounter when setup succeeds. Clean up safely if setup fails or is cancelled.

**Acceptance criteria:**

- [ ] A prepared and registered encounter moves from SETUP to ACTIVE.
- [ ] Repeating an engagement request reuses the active encounter.
- [ ] An active encounter can be manually ended, and its participants can start a new one.
- [ ] Failed or cancelled setup releases reserved membership after successful cleanup.
- [ ] Cleanup failure keeps membership protected and allows a retry.

**Coordination:** Agree on when setup is complete with the teammate implementing initiative and turns.

## 2. Extend protection against legacy combat

**Problem:** The KILL hook and postAttack guard do not stop every way legacy combat can start or apply damage.

**Goal:** Identify additional routes and decide which to block, redirect, or keep during encounters. Add protection at the selected locations.

**Acceptance criteria:**

- [ ] Review additional routes from the findings document, including hostile messages, victim assignments, automatic attacks, and direct damage.
- [ ] Record the intended behavior for each route selected for this story.
- [ ] Implement and test the selected protection without changing ordinary combat outside encounters.
- [ ] Document routes that remain unprotected and effects that can happen before a guard.

**Coordination:** Agree on the lifecycle rules first. Review NPC routes.

**Scope note:** Choose a small set of routes before creating the card. This draft is too broad to imply complete suppression in one story.

## 3. Find an encounter's manager after a participant moves

**Problem:** END looks for the manager in the caller's current test room. It cannot reliably find the owner after the caller moves elsewhere.

**Goal:** Find the manager from the participant's encounter membership, regardless of current location.

**Acceptance criteria:**

- [ ] Membership can be used to find the manager that owns the encounter.
- [ ] Moving to another room does not prevent the owner from cleaning up the encounter.
- [ ] Another room's manager cannot end an encounter it does not own.
- [ ] Copied rooms do not accidentally reuse a manager bound to the original room.
- [ ] Finding membership and ownership remains separate from deciding who may join.

Scope note: This is heavily dependent on how the team decides to treat movement while in encounters. 

## 4. End encounters for different reasons

**Problem:** Only manual ending is connected to encounters. Logout still uses legacy cleanup, and cancellation needs different treatment from victory.

**Goal:** Record why an encounter ends and route supported events through its manager.

**Acceptance criteria:**

- [ ] Represent ending reasons such as cancellation, disconnect, death, and victory.
- [ ] Supported events notify the owning manager and run the appropriate cleanup.
- [ ] Repeated ending requests do not repeat completed cleanup or rewards.
- [ ] Cleanup failure retains membership and allows a retry.
- [ ] Distinguish endings that permit rewards from endings that only require cleanup.

**Dependency:** Finding the owner independently of room location.

**Scope note:** Select supported events before creating the card. Cancellation and disconnect can be the first implementation. Death, victory, and reward implementation can follow separately.

## 5. Define rules for joining and using commands

**Problem:** The prototype uses a fixed room and rejects additional participants. Rules for commands during an encounter are not yet defined.

**Goal:** Make joining rules replaceable and define which commands participants may use.

**Acceptance criteria:**

- [ ] Joining decisions can consider the room, encounter, acting MOB, and target.
- [ ] A rule that rejects additional participants remains available.
- [ ] An initial list of permitted commands is defined and can be extended.
- [ ] Rejected requests do not change membership or start legacy combat.
- [ ] Permitted requests follow encounter rules.

**Scope note:** Joining rules and command restrictions may become separate cards.

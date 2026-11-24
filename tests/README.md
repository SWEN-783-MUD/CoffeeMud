# Automated CoffeeMud tests

From the repository root, with JDK 17 and Python 3.9 or newer installed:

```text
python tests/run_tests.py --verify-harness
```

No Python packages or Ant installation are needed. Set `JAVA_HOME` to your JDK 17 directory, or put its `java` and `javac` on PATH.

The launcher compiles fresh classes with the project's Java 8 source/target settings. It runs `PartyMembershipTest` and `PartyCommandTest` standalone, then starts CoffeeMud and runs the registered `party` test group. It also checks that a temporary Archon character can discover those tests through the real `TEST LIST` command.

The host runner calls the same `CMTest.doTest` methods as the in-game `TEST` command. A null result passes. A failure message, exception, cleanup failure, or timeout fails the run. The command's boolean return value is not a test verdict, so the runner collects the test results directly.

## What the fixture creates

Each invocation gets a new directory under ignored `test-output/`:

- A fresh FakeDB containing only the supplied schema before boot. Existing local player records are excluded.
- Copies of the engine resources and configuration with optional network services disabled.
- A loopback listener on an available port. This is separate from the local demo server.
- A temporary room, player character, and recording session for each host test. These are created through Java and are not saved as player accounts.

CoffeeMud creates its default empty-database world during boot. Tests needing additional rooms, players, or content should create and clean up their own fixtures. Start with a known test group; legacy `all` includes tests with other setup requirements.

Results and logs are retained in that run's directory. Successful runs remove their disposable runtime directory. Failed runs retain it for debugging. Use `--keep-runtime` to retain a successful runtime too.

```text
python tests/run_tests.py --group party
python tests/run_tests.py --test PartyCommandTest
python tests/run_tests.py --test FormationTest --keep-runtime
```

Group names and test IDs are matched exactly, ignoring case. An unknown or empty selection fails. The two fast party fixtures run before any selected host suite.

Host group selections omit tests tagged `ci_exclude` and log the exclusions. A selection with no remaining tests fails. Use `--test <ID>` to investigate an excluded test explicitly; the in-game `TEST` command is unaffected.

Tests also have `exec_` groups describing their execution requirements, alongside the existing feature groups. See [execution classifications and the complete inventory](CLASSIFICATION.md) for the categories, limitations, and selection examples. The dedicated party and encounter matrix suites select their feature groups; categorical suites also exercise those fixtures according to their execution requirements. A fixture with multiple tags runs once per selected job, so PartyEncounterTest runs in the party, encounter, and integration jobs.

Use `--skip-standalone` to run only the registered host selection. This lets another epic reuse the host harness without depending on the standalone party fixtures.

## Failure handling

`--verify-harness` checks pass/fail aggregation, exception handling, cleanup, cancellation, and empty selections. It also launches a deliberate failing test in a separate process and requires exit status 1. Expected exception traces in `harness-contracts.log` come from these negative checks.

Each registered test has a 30-second default deadline. Host startup has a 120-second deadline, and the entire host process has a 240-second deadline. Override these with `--test-timeout`, `--startup-timeout`, and `--timeout`. A timed-out test stops the suite because it may still hold world state. The outer launcher kills the Java process if it exceeds the whole-process deadline.

The host runner writes `results.xml` in JUnit format. The launcher requires a nonempty, consistent report as well as a successful process exit. Startup failure or a missing report cannot produce a green result.

Run the launcher's own regression checks with:

```text
python -m unittest discover -s tests -p test_launcher.py -v
```

## GitHub Actions

### Local equivalents of the CI jobs

With JDK 17 selected, run these commands sequentially on a workstation; concurrent full compiles can exceed the compilation deadline. CI runs them on independent workers. Each host invocation compiles fresh classes and creates its own disposable runtime and result directory. Core also runs the harness contracts and standalone party fixtures.

```text
python -m unittest discover -s tests -p test_launcher.py -v
python tests/run_tests.py --group party --skip-standalone
python tests/run_tests.py --group encounter --skip-standalone
python tests/run_tests.py --group exec_core --verify-harness
python tests/run_tests.py --group exec_integration --skip-standalone
python tests/run_tests.py --group exec_randomized --skip-standalone --test-timeout 180 --timeout 900
python tests/run_tests.py --group exec_stress --skip-standalone --test-timeout 180 --timeout 300
```

The stress test needs more than the default 30-second per-test deadline. Group runs filter `ci_exclude`; this job selection does not include world, benchmark, manual, or placeholder groups.

`test-coffeemud.yaml` runs a launcher-check job and six independent matrix jobs for party, encounter, core, integration, randomized, and stress checks on pushes to `master`, pull requests, and manual runs. Feature-branch pushes rely on their pull request run, avoiding duplicate push and PR checks; branches without an open PR can be tested manually. All jobs use Windows and Python 3.13; the Java suites use JDK 17. Core also verifies the Java harness contracts and standalone party fixtures. Matrix fail-fast is disabled so a failure in one suite does not cancel the others. Each suite uploads its own logs and XML results even after a failed test step, retained for seven days. This workflow is separate from the existing distribution/release workflow.

To add coverage, write a `StdTest` with a `getTestGroups()` category and add your assertions to `doTest`. Existing tests remain available through `TEST <group>` in-game. Keep socket login, reconnect, and client rendering checks separate from this programmatic test context.

## Encounter command service

Player encounter commands use `EncounterLibrary` through `CMLib.encounters()`.
`CMEncounters` coordinates access to the current room's manager; the manager and
`GeneralEncounter` retain lifecycle and turn state. The library does not create a
second encounter store or maintain an authoritative cross-room enrollment index.

`ENDTURN` submits an `EndTurnAction` through that library. The library creates an
`ActionRequest` identifying the encounter, participant, and action. `ActionResolver`
runs the shared lifecycle immediately under the manager and encounter locks:

1. **pre_use:** validate enrollment, encounter state, turn ownership, ability to
   act, and available actions, followed by the action's own `validate` method.
2. **execute:** apply validated effects and consume the reported resources once.
3. **post_use:** run the action's optional follow-up, then end or advance the turn
   if requested or if its action budget is exhausted.

`ValidationResult` provides a typed failure code and explanation. `ActionOutcome`
reports execution success, attack/diagonal usage, and an explicit turn-end request.
End Turn costs zero actions; shared follow-up discards the unused budget and
advances. Existing native command costs and player feedback are preserved.

Actions must not mutate turn ownership or spend resources themselves. Failed
validation has no effects. An execution rejection must also leave state unchanged.
Unexpected callback exceptions propagate; there is no automatic rollback of
already-applied effects. The resolver guard is released even after an exception.
If follow-up fails after costs were committed, an exhausted or explicitly ended
turn is still finalized; the same request cannot replay the committed effects.

Each request can be processed once, including rejected requests. Resolution is
serialized, and nested action submissions during callbacks are rejected. This is
not an ordered queue: no requests are retained, and a request does not capture its
originating turn for later execution. Do not store requests for delayed replay.
The player-facing library rejects NPC commands; the same resolver can serve
enrolled NPCs through a future manager-owned NPC entry point.

The actor check uses CoffeeMud's existing alive/awake/mobile/unbound checks;
encounter-specific unconscious state and automatic turn skipping remain future
work. Only End Turn has a production action implementation. Missing grid, preset,
or combat-math integrations must be reported by action validation before effects
or resource consumption, rather than returning simulated success.

`EncounterCommandTest` runs the registered command in a host with real player
fixtures. It checks library discovery, room-scoped lookup, argument errors,
resource preservation on rejection, voluntary turn progression, rescue pause,
and completion. It belongs to `encounter` and `exec_integration`.

`EncounterActionTest` exercises shared prerequisite rejection, lifecycle ordering,
zero-cost actions, exhausted budgets, completion during follow-up, request replay,
nested callbacks, exception propagation, and concurrent spending. It also belongs
to `encounter` and `exec_integration`.

The ordered queue, originating-turn identity, timeouts, automatic turn notifications,
other combat actions, and cross-room enrollment tracking remain separate work.

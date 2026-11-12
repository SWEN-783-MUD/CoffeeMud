# Automated CoffeeMud tests

Run this from the repository root with JDK 17 and Python 3.9 or newer.

```text
python tests/run_tests.py --group party --verify-harness
```

The launcher uses Python's standard library and calls `javac` directly. Set
`JAVA_HOME` to your JDK 17 directory or put its `java` and `javac` on PATH.

## How a run works

1. `run_tests.py` creates a fresh server folder and compiles the project with its Java 8 source and target settings.
2. The launcher runs `PartyMembershipTest` and `PartyCommandTest` as standalone checks before it starts the server.
3. `HostTestRunner.java` boots CoffeeMud, checks that a temporary Archon can use `TEST LIST`, and runs the selected tests.
4. The runner saves its report and shuts down the server. The launcher checks the report and process exit code.

The runner calls the same `CMTest.doTest` methods as the in-game `TEST` command.
A `null` result passes. A failure message, thrown error, cleanup failure, or
timeout fails the run. Reading `doTest` results directly gives the launcher a
pass or fail for each test.

## Temporary server files

Each run gets a folder under `test-output/`. CoffeeMud starts with a fresh
FakeDB made from the supplied schema and builds its default world. The launcher
copies the assets and config it needs, binds the host to loopback on an available
port, and turns off optional network services.

The runner creates a temporary room, Archon, and recording session for each host
test. Tests that need more rooms, players, or items create and clean up those
objects themselves.

Each run keeps its logs and report. The launcher removes the server folder after
a pass and keeps it after a failure. Use `--keep-runtime` to keep it after a pass.

## Choose what to run

```text
python tests/run_tests.py --group party
python tests/run_tests.py --test PartyCommandTest
python tests/run_tests.py --test FormationTest --keep-runtime
```

Use `--group` or `--test` to pick one group or test ID. The default group is
`party`. Names match exactly, ignoring case, and a selection with no matches
fails the run. Some legacy groups include tests that need extra setup.

Use `--skip-standalone` to run the host selection by itself. This is useful when
checking a built-in test or using the runner from another epic.

## Failure handling

`--verify-harness` checks how the runner handles failures, cleanup, timeouts,
and empty selections. It runs a deliberate failure in a separate process and
expects exit status 1. Those checks produce the expected exception traces in
`harness-contracts.log`.

Each registered test has 30 seconds by default. The host has 120 seconds to
start, and the full host process has 240 seconds. Adjust these with
`--test-timeout`, `--startup-timeout`, and `--timeout`, all in seconds. The
runner stops the suite when a test times out because its worker may still use
the world. The launcher ends the Java process if it exceeds the full deadline.

The host runner writes a JUnit XML report at `results.xml`. A pass requires a
successful process exit and a complete report with at least one check.

Run the launcher checks on their own with this command.

```text
python -m unittest discover -s tests -p test_launcher.py -v
```

## GitHub Actions

`test-coffeemud.yaml` runs the launcher on pushes, pull requests, and manual
runs with Windows, JDK 17, and Python 3.13. The workflow selects `party` and
uploads the available logs and XML even when a test fails.

## Add a test

Create a `StdTest` in CoffeeMud's `Tests` package. List its groups in
`getTestGroups()` and put the setup and assertions in `doTest()`. Tests added to
`party` run on the next CI build and through `TEST PARTY` in-game. To run a
separate group in CI, add a launcher call that selects it with `--group`.

Use real rooms, characters, and game commands when the test needs to check a
game mechanic. Login, reconnect, and browser checks need a client test driver.

#!/usr/bin/env python3
"""Build CoffeeMud and run its tests in a fresh server folder for each run."""

import argparse
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET


# Find the checkout even when the launcher is called from another directory.
REPO = Path(__file__).resolve().parents[1]


def positive_int(value: str) -> int:
    """Read a timeout from the command line and require at least one second."""
    number = int(value)
    if number <= 0:
        raise argparse.ArgumentTypeError("must be positive")
    return number


def java_tool(name: str) -> str:
    """Find java or javac, using JAVA_HOME if the caller set it."""
    suffix = ".exe" if os.name == "nt" else ""
    if os.environ.get("JAVA_HOME"):
        candidate = Path(os.environ["JAVA_HOME"]) / "bin" / (name + suffix)
        if candidate.is_file():
            return str(candidate)
        raise RuntimeError(f"JAVA_HOME has no {name}: {candidate}")

    found = shutil.which(name)
    if not found:
        raise RuntimeError(f"Install JDK 17 and put {name} on PATH, or set JAVA_HOME.")
    return found


def run_step(
    label: str,
    command: list[str],
    cwd: Path,
    log: Path,
    timeout: float,
    expected: int = 0,
) -> None:
    """Save a process's output and raise if it times out or exits unexpectedly.

    The deliberate failure check passes ``expected=1`` to verify that a test
    failure makes it all the way back to this launcher.
    """
    print(label, flush=True)
    with log.open("wb") as output:
        try:
            result = subprocess.run(
                command,
                cwd=cwd,
                stdout=output,
                stderr=subprocess.STDOUT,
                timeout=timeout,
            )
        except subprocess.TimeoutExpired as error:
            raise RuntimeError(f"{label} timed out after {timeout}s. See {log}") from error

    # Show the last few lines in CI and keep the full log for debugging.
    if result.returncode != expected:
        tail = log.read_text(encoding="utf-8", errors="replace").splitlines()[-35:]
        print("\n".join(tail), file=sys.stderr)
        raise RuntimeError(f"{label} exited with {result.returncode}, expected {expected}. See {log}")


def prepare_runtime(runtime: Path) -> None:
    """Copy the files a test server needs and give it a fresh FakeDB.

    The existing player database stays in the checkout. The new server uses
    the copied schema and writes its own data under ``runtime``.
    """
    # Copy game assets and libraries, skipping generated files and local data.
    for directory in ("resources", "web", "guides", "lib"):
        shutil.copytree(
            REPO / directory,
            runtime / directory,
            ignore=shutil.ignore_patterns("fakedb", "*.class", "__pycache__"),
        )

    # CoffeeMud needs the schema even when the database starts empty.
    database = runtime / "resources" / "fakedb"
    database.mkdir()
    shutil.copyfile(REPO / "guides/database/fakedb.schema", database / "fakedb.schema")

    settings = {
        # Bind locally and skip services the tests do not use.
        "MUD_NAME": "CITestHost",
        "BIND": "127.0.0.1",
        "RUNWEBSERVERS": "FALSE",
        "RUNSMTPSERVER": "FALSE",
        "RUNI3SERVER": "FALSE",
        "RUNGVSERVER": "FALSE",
        "RUNCM1SERVER": "FALSE",

        # Point the test host at its own database and log.
        "DBCLASS": "com.planet_ink.fakedb.Driver",
        "DBSERVICE": "jdbc:fakedb:resources/fakedb",
        "DBUSER": "",
        "DBPASS": "",
        "LOGNAME": "ci",
    }

    ini = (REPO / "coffeemud.ini").read_text(encoding="utf-8")
    for key, value in settings.items():
        pattern = rf"^{key}=.*$"
        if re.search(pattern, ini, flags=re.MULTILINE):
            ini = re.sub(pattern, f"{key}={value}", ini, flags=re.MULTILINE)
        else:
            ini += f"\n{key}={value}\n"
    (runtime / "coffeemud.ini").write_text(ini, encoding="utf-8")


def compile_sources(runtime: Path, run_dir: Path) -> str:
    """Build CoffeeMud and the harness, then return their runtime classpath."""
    sources = sorted((REPO / "com").rglob("*.java"))
    sources += sorted((REPO / "tests/harness").glob("*.java"))

    # javac reads the source paths from a file to stay within command line limits.
    source_list = run_dir / "sources.txt"
    source_list.write_text("\n".join(f'"{source.as_posix()}"' for source in sources), encoding="utf-8")

    classpath = os.pathsep.join(str(runtime / "lib" / jar) for jar in ("js.jar", "jzlib.jar"))
    command = [
        java_tool("javac"),
        "-encoding", "UTF-8",
        "-source", "8",
        "-target", "8",
        "-cp", classpath,
        "-d", str(runtime),
        "@" + str(source_list),
    ]
    run_step(f"Compiling {len(sources)} Java sources...", command, REPO, run_dir / "compile.log", 180)
    return os.pathsep.join([str(runtime), classpath])


def check_report(report: Path) -> int:
    """Check the host's XML report and return the number of passing checks.

    The launcher calls this after a successful exit. A usable report must
    also contain at least one result and agree with its own totals.
    """
    if not report.is_file():
        raise RuntimeError("The host exited without results.xml. This is a failed run.")

    suite = ET.parse(report).getroot()
    cases = suite.findall("testcase")
    if not cases or int(suite.attrib["tests"]) != len(cases):
        raise RuntimeError("The host report is empty or inconsistent.")

    # A failure in either the summary or an individual case fails the run.
    if int(suite.attrib.get("failures", "0")) or int(suite.attrib.get("errors", "0")):
        raise RuntimeError("The host report contains failures.")
    if any(case.find("failure") is not None or case.find("error") is not None for case in cases):
        raise RuntimeError("A test failed despite a successful host exit.")
    return len(cases)


def remove_runtime(runtime: Path, run_dir: Path) -> None:
    """Delete the temporary runtime while leaving its reports and logs in place."""
    # Resolve the path before deleting it to confirm it belongs to this run.
    resolved = runtime.resolve()
    if resolved.parent != run_dir.resolve() or resolved.name != "runtime":
        raise RuntimeError("Unexpected runtime path. Cleanup stopped.")
    shutil.rmtree(resolved)


def parse_args() -> argparse.Namespace:
    """Read the test selection, runtime options, and deadlines from the CLI."""
    parser = argparse.ArgumentParser(description=__doc__)

    # Accept one registered group or one exact test ID per run.
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument("--group", help="registered test group (default: party)")
    selection.add_argument("--test", help="one registered test ID, matched exactly")
    parser.add_argument(
        "--verify-harness", action="store_true",
        help="also check failure, cleanup, and timeout reporting",
    )
    parser.add_argument(
        "--keep-runtime", action="store_true",
        help="keep the disposable runtime after a successful run",
    )
    parser.add_argument(
        "--skip-standalone", action="store_true",
        help="run only the selected registered host tests",
    )

    # Give individual tests, startup, and the full server process separate limits.
    parser.add_argument(
        "--timeout", type=positive_int, default=240,
        help="whole host process deadline in seconds",
    )
    parser.add_argument(
        "--test-timeout", type=positive_int, default=30,
        help="deadline per registered test in seconds",
    )
    parser.add_argument(
        "--startup-timeout", type=positive_int, default=120,
        help="host startup deadline in seconds",
    )
    return parser.parse_args()


def main() -> int:
    """Run the selected checks and return 0 for success or 1 for failure."""
    args = parse_args()

    # Give this run a folder for logs and a separate folder the server can modify.
    output = REPO / "test-output"
    output.mkdir(exist_ok=True)
    run_dir = Path(tempfile.mkdtemp(prefix="run-", dir=output))
    runtime = run_dir / "runtime"
    runtime.mkdir()
    print(f"Results and logs: {run_dir}", flush=True)

    try:
        # Use the same build and runtime for the standalone checks and the host.
        prepare_runtime(runtime)
        classpath = compile_sources(runtime, run_dir)
        java = [
            java_tool("java"),
            "-Djava.awt.headless=true",
            "-Dfile.encoding=UTF-8",
            "-Xmx512m",
            "-cp", classpath,
        ]

        # Check that the runner itself can report success and failure correctly.
        if args.verify_harness:
            run_step(
                "Checking harness contracts...",
                java + ["HostTestRunnerContractTest"],
                runtime, run_dir / "harness-contracts.log", 20,
            )
            run_step(
                "Checking that a deliberate failure exits with status 1...",
                java + ["HostTestRunnerContractTest", "--fail"],
                runtime, run_dir / "expected-failure.log", 20, expected=1,
            )

        fixtures = () if args.skip_standalone else ("PartyMembershipTest", "PartyCommandTest")
        for fixture in fixtures:
            run_step(
                f"Running {fixture} standalone...",
                java + ["com.planet_ink.coffee_mud.Tests." + fixture],
                runtime, run_dir / (fixture + ".log"), 30,
            )

        # Ask the OS for an available port, then release it for CoffeeMud to bind.
        with socket.socket() as reservation:
            reservation.bind(("127.0.0.1", 0))
            port = reservation.getsockname()[1]

        # Match the argument order in HostTestRunner.main.
        kind, selector = ("test", args.test) if args.test else ("group", args.group or "party")
        host_command = java + [
            "HostTestRunner", kind, selector, str(port),
            str(args.test_timeout), str(args.startup_timeout),
        ]
        run_step(
            f"Running registered {kind} {selector} in a fresh host...",
            host_command, runtime, run_dir / "host.log", args.timeout,
        )

        # Require a usable report before removing the temporary server files.
        count = check_report(run_dir / "results.xml")
        print(f"PASS: {count} host checks and {len(fixtures)} standalone fixtures.", flush=True)
        if not args.keep_runtime:
            remove_runtime(runtime, run_dir)
        return 0
    except (OSError, RuntimeError, ValueError, ET.ParseError) as error:
        print(f"FAIL: {error}\nDiagnostics retained in {run_dir}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())

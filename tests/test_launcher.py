"""Check the launcher's reports, process results, and temporary server files."""

import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch


# Load the launcher next to this file so the checks can run from any directory.
spec = importlib.util.spec_from_file_location("run_tests", Path(__file__).with_name("run_tests.py"))
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)


class LauncherTest(unittest.TestCase):
    """Exercise launcher behavior with small files and short child processes."""

    def temporary_directory(self):
        """Create a folder under test-output that the check can remove afterward."""
        output = launcher.REPO / "test-output"
        output.mkdir(exist_ok=True)
        return tempfile.TemporaryDirectory(prefix="launcher-", dir=output)

    def check_xml_report(self, text: str) -> int:
        """Write a sample report and return the passing count from the launcher."""
        with self.temporary_directory() as directory:
            path = Path(directory) / "results.xml"
            path.write_text(text, encoding="utf-8")
            return launcher.check_report(path)

    def test_valid_report(self) -> None:
        """Count the checks in a complete passing report."""
        report = '<testsuite tests="1" failures="0"><testcase name="ok"/></testsuite>'
        self.assertEqual(1, self.check_xml_report(report))

    def test_empty_or_missing_report_fails(self) -> None:
        """Require evidence that the server ran at least one check."""
        with self.temporary_directory() as directory:
            with self.assertRaises(RuntimeError):
                launcher.check_report(Path(directory) / "missing.xml")

        with self.assertRaises(RuntimeError):
            self.check_xml_report('<testsuite tests="0"/>')

    def test_inconsistent_count_fails(self) -> None:
        """Reject a suite summary that counts more checks than it lists."""
        report = '<testsuite tests="2"><testcase name="only one"/></testsuite>'
        with self.assertRaises(RuntimeError):
            self.check_xml_report(report)

    def test_failure_cannot_be_hidden_by_summary(self) -> None:
        """Check both the totals and each test case for failures."""
        hidden_failure = (
            '<testsuite tests="1" failures="0">'
            '<testcase><failure message="broken"/></testcase>'
            '</testsuite>'
        )
        with self.assertRaises(RuntimeError):
            self.check_xml_report(hidden_failure)

        with self.assertRaises(RuntimeError):
            self.check_xml_report('<testsuite tests="1" errors="1"><testcase/></testsuite>')

    def test_runtime_never_copies_existing_database(self) -> None:
        """Check that the test host gets a fresh database and local settings."""
        with self.temporary_directory() as directory:
            # Make a small checkout with a database and a separate schema.
            source = Path(directory) / "source"
            runtime = Path(directory) / "runtime"
            for folder in ("resources/fakedb", "web", "guides/database", "lib"):
                (source / folder).mkdir(parents=True)
            players = source / "resources/fakedb/players"
            players.write_text("existing players", encoding="utf-8")
            (source / "guides/database/fakedb.schema").write_text("test schema", encoding="utf-8")
            (source / "coffeemud.ini").write_text("DBCLASS=other\nDBSERVICE=production\n", encoding="utf-8")

            # Point the launcher at the small checkout for this check.
            with patch.object(launcher, "REPO", source):
                launcher.prepare_runtime(runtime)

            # Check the copied files, the server settings, and the original data.
            database_files = [path.name for path in (runtime / "resources/fakedb").iterdir()]
            self.assertEqual(["fakedb.schema"], database_files)
            config = (runtime / "coffeemud.ini").read_text(encoding="utf-8")
            self.assertIn("DBSERVICE=jdbc:fakedb:resources/fakedb\n", config)
            self.assertIn("BIND=127.0.0.1\n", config)
            self.assertNotIn("production", config)
            self.assertEqual("existing players", players.read_text(encoding="utf-8"))

    def test_cleanup_rejects_source_directory(self) -> None:
        """Keep cleanup inside the run's runtime folder."""
        with self.temporary_directory() as directory:
            root = Path(directory)
            source = root / "source"
            source.mkdir()

            with self.assertRaises(RuntimeError):
                launcher.remove_runtime(source, root)
            self.assertTrue(source.exists())

    def test_process_failure_is_not_success(self) -> None:
        """Report an unexpected child process exit code as a failure."""
        with self.temporary_directory() as directory:
            path = Path(directory)
            command = [sys.executable, "-c", "raise SystemExit(3)"]
            with self.assertRaisesRegex(RuntimeError, "exited with 3"):
                launcher.run_step("expected failure", command, path, path / "failure.log", 5)

    def test_process_timeout_fails(self) -> None:
        """Report a deadline miss when a child process takes too long."""
        with self.temporary_directory() as directory:
            path = Path(directory)
            command = [sys.executable, "-c", "import time; time.sleep(60)"]
            with self.assertRaisesRegex(RuntimeError, "timed out"):
                launcher.run_step("expected timeout", command, path, path / "timeout.log", 0.1)


if __name__ == "__main__":
    unittest.main()

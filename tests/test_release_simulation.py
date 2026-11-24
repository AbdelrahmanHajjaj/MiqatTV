"""Classify simulator transport and completed instrumentation failures."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
from tempfile import TemporaryDirectory
import unittest


BASH = os.environ.get("MIQAT_TEST_BASH") or (shutil.which("bash") if sys.platform != "win32" else None)
SCRIPT = Path(__file__).resolve().parents[1] / "tools/device-simulation.sh"


@unittest.skipUnless(BASH, "Requires the CI Bash shell or MIQAT_TEST_BASH")
class SimulationResults(unittest.TestCase):
    def test_only_runner_failures_are_eligible_for_retry(self):
        for outcome, check, blocked in (
            ("pass", 0, None),
            ("offline", 1, None),
            ("assertion", 1, "Instrumentation failed: prayer"),
            ("missing-marker", 1, "Missing success marker: prayer"),
        ):
            with self.subTest(outcome=outcome), TemporaryDirectory() as temp:
                root = Path(temp)
                binary = root / "bin"
                binary.mkdir()
                adb = binary / "adb"
                adb.write_text('''#!/usr/bin/env bash
case "$*" in
  *"am start"*) echo 'Status: ok' ;;
  *"am instrument"*)
    case "$RESULT" in
      pass) printf 'PASS: checked\\nINSTRUMENTATION_CODE: -1\\n' ;;
      offline) echo 'error: device offline' >&2; exit 1 ;;
      assertion) printf 'AssertionError: incorrect focus\\nINSTRUMENTATION_CODE: 0\\n' ;;
      missing-marker) echo 'INSTRUMENTATION_CODE: -1' ;;
    esac ;;
esac
''', encoding="utf-8", newline="\n")
                adb.chmod(0o755)
                run = subprocess.run([BASH, "-c", 'PATH="$PWD/bin:$PATH" bash "$1" tv signed.apk',
                                      "simulation-test", SCRIPT.as_posix()], cwd=root, text=True,
                                     env={**os.environ, "RESULT": outcome, "SIMULATION_ATTEMPT": "2",
                                          "EMULATOR_PORT": "5556", "STAGING_TAG": "v0.7.9-staging.2"},
                                     capture_output=True, timeout=20)
                self.assertEqual(run.returncode, check, run.stderr + run.stdout)
                evidence = root / "state/device-simulation"
                self.assertTrue((evidence / "attempt-2/prayer.log").is_file())
                marker = evidence / "blocking-failure"
                self.assertEqual(marker.read_text().strip() if marker.exists() else None, blocked)

"""Regression coverage for affected checks and the required merge gate."""
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from tools.ci import JOBS, affected, require_jobs


class CiChecks(unittest.TestCase):
    def test_selection_cli_handles_renames_deletions_and_full_runs(self):
        script = Path(__file__).resolve().parents[1] / "tools/ci.py"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def git(*args):
                return subprocess.check_output(["git", *args], cwd=root, stderr=subprocess.PIPE).decode().strip()

            git("init")
            git("config", "user.name", "CI test")
            git("config", "user.email", "ci@example.invalid")
            (root / "shared").mkdir()
            (root / "shared/original").write_text("source")
            git("add", ".")
            git("commit", "-m", "base")
            base = git("rev-parse", "HEAD")
            (root / "docs").mkdir()
            git("mv", "shared/original", "docs/moved")
            git("commit", "-m", "move")
            event = root / "event.json"
            event.write_text(json.dumps({"pull_request": {
                "base": {"sha": base}, "head": {"sha": git("rev-parse", "HEAD")}}}))
            output = root / "output"
            for trigger in ("pull_request", "push", "workflow_dispatch"):
                output.write_text("")
                subprocess.run([sys.executable, "-S", "-B", str(script), "select"], cwd=root,
                               env={**os.environ, "GITHUB_EVENT_PATH": str(event),
                                    "GITHUB_EVENT_NAME": trigger, "GITHUB_OUTPUT": str(output)},
                               check=True, capture_output=True)
                self.assertEqual(dict(line.split("=") for line in output.read_text().splitlines()),
                                 {name: "true" for name in JOBS})

    def test_affected_consumers(self):
        cases = [
            (["docs/CI.md", "README.md"], set()),
            (["site/src/lib/test.ts"], {"preview"}),
            (["platforms/android-tv/build.gradle"], {"android"}),
            (["platforms/webos/app/index.html"], {"webos"}),
            (["installer/build.gradle"], {"android"}),
            (["companion/contract.json"], set(JOBS) - {"core"}),
            (["shared/core/rules.kt"], set(JOBS)),
            (["shared/media/adhan/test.mp3"], set(JOBS)),
            (["tools/catalogs.mjs"], set(JOBS)),
            ([".github/workflows/validate.yml"], set(JOBS)),
            (["new-consumer/source"], set(JOBS)),
            (["LICENSE"], set(JOBS)),
            (["docs/CLEANYOURTV-LICENSE.txt"], set(JOBS)),
            (["docs/THIRD-PARTY.md"], set(JOBS)),
            (["docs/UI-GAPS.md"], set(JOBS)),
            (["platforms/webos/ui-gaps.json"], set(JOBS)),
            (["site/new.ts", "platforms/webos/new.js"], {"preview", "webos"}),
            (["site/old.ts", "shared/data/new.json"], set(JOBS)),
        ]
        for paths, expected in cases:
            with self.subTest(paths=paths):
                self.assertEqual(affected(paths), expected)

    def test_gate_accepts_only_success_and_intentional_skips(self):
        for selected in (set(), {"android"}, set(JOBS)):
            needs = {name: {"result": "success" if name in selected else "skipped"}
                     for name in JOBS}
            needs["changes"] = {"result": "success", "outputs": {
                name: str(name in selected).lower() for name in JOBS}}
            require_jobs(needs)
            for name in (*JOBS, "changes"):
                for result in ("success", "failure", "cancelled", "skipped"):
                    if needs[name]["result"] == result:
                        continue
                    changed = copy.deepcopy(needs)
                    changed[name]["result"] = result
                    with self.subTest(name=name, result=result), self.assertRaises(ValueError):
                        require_jobs(changed)
            changed = copy.deepcopy(needs)
            changed["changes"]["outputs"]["core"] = ""
            with self.assertRaises(ValueError):
                require_jobs(changed)

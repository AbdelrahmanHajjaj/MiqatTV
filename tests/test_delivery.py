"""Focused checks for publishing complete APKs from trusted CI results."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from tools.check_apk_media import check_apk
from tools.release_source import resolve_inputs, validate_run


class DeliveryChecks(unittest.TestCase):
    def test_apk_rejects_missing_changed_and_extra_recordings(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            media = root / "media"
            (media / "adhan").mkdir(parents=True)
            (media / "adhan/test.mp3").write_bytes(b"reviewed recording")
            apk = root / "app.apk"

            def package(entries):
                with zipfile.ZipFile(apk, "w") as output:
                    for name, content in entries.items():
                        output.writestr(name, content)

            package({"assets/adhan/test.mp3": b"reviewed recording"})
            self.assertEqual(check_apk(apk, media), 1)
            for entries in ({}, {"assets/adhan/test.mp3": b"changed"},
                            {"assets/adhan/test.mp3": b"reviewed recording", "unexpected.mp3": b"extra"}):
                package(entries)
                with self.assertRaises(ValueError):
                    check_apk(apk, media)
            with self.assertRaises(ValueError):
                check_apk(apk, root / "absent")

    def test_release_accepts_only_successful_current_main_validation(self):
        sha, repo = "a" * 40, "owner/MiqatTV"
        run = {"status": "completed", "conclusion": "success", "head_branch": "main",
               "head_sha": sha, "path": ".github/workflows/validate.yml", "event": "push",
               "head_repository": {"full_name": repo}}
        validate_run(run, repo, sha, "v0.7.0-preview.1")
        for key, value in (("status", "in_progress"), ("conclusion", "failure"),
                           ("head_branch", "feature/untrusted"), ("head_sha", "b" * 40),
                           ("path", ".github/workflows/other.yml"), ("event", "pull_request"),
                           ("head_repository", {"full_name": "another/repo"})):
            changed = copy.deepcopy(run)
            changed[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                validate_run(changed, repo, sha, "v0.7.0-preview.1")
        with self.assertRaises(ValueError):
            validate_run(run, repo, sha, "--latest")

    def test_release_defaults_skip_reserved_tags_and_allow_independent_overrides(self):
        sha, repo = "a" * 40, "owner/MiqatTV"
        run = {"id": 42, "status": "completed", "conclusion": "success", "head_branch": "main",
               "head_sha": sha, "path": ".github/workflows/validate.yml", "event": "push",
               "head_repository": {"full_name": repo}}
        responses = {
            "git/ref/heads/main": json.dumps({"object": {"sha": sha}}),
            "actions/workflows/validate.yml/runs": json.dumps({"workflow_runs": [run]}),
            "actions/runs/42": json.dumps(run),
            "releases": "v0.7.6\nv0.7.7-preview.1",
            "tags": "v0.7.7-preview.2",
            "releases/latest": "v0.7.6",
        }
        with patch("tools.release_source.gh_api", side_effect=lambda endpoint, *_: responses[
                endpoint.removeprefix(f"repos/{repo}/")]):
            self.assertEqual(resolve_inputs(repo, sha, "", ""), ("42", "v0.7.7-preview.3"))
            self.assertEqual(resolve_inputs(repo, sha, "42", ""), ("42", "v0.7.7-preview.3"))
            self.assertEqual(resolve_inputs(repo, sha, "", "v0.8.0-rc.1"), ("42", "v0.8.0-rc.1"))
            with self.assertRaisesRegex(ValueError, "already in use"):
                resolve_inputs(repo, sha, "42", "v0.7.7-preview.2")
            with self.assertRaisesRegex(ValueError, "Main has moved"):
                resolve_inputs(repo, "b" * 40, "42", "v0.8.0-rc.1")
            responses["actions/workflows/validate.yml/runs"] = '{"workflow_runs": []}'
            with self.assertRaisesRegex(ValueError, "No successful Validate run"):
                resolve_inputs(repo, sha, "", "")

"""Focused checks for publishing complete APKs from trusted CI results."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from tools.check_apk_media import check_apk
from tools import release_source as release


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

    def test_latest_and_exact_staging_selection(self):
        def entry(tag, date, **extra):
            return dict({"tag_name": tag, "published_at": date, "id": 1,
                         "draft": False, "prerelease": True}, **extra)

        older = entry("v0.7.8-staging.1", "2026-11-20")
        latest = entry("v0.7.8-staging.2", "2026-11-21")
        candidates = [older, latest, entry("v0.7.7-preview.1", "2026-11-22"),
                      entry("v0.7.8-staging.3", "2026-11-23", draft=True)]
        self.assertIs(release.select_staging(candidates), latest)
        self.assertIs(release.select_staging(candidates, older["tag_name"]), older)
        with self.assertRaises(ValueError):
            release.select_staging(candidates, "v0.7.8-staging.3")
        with self.assertRaises(ValueError):
            release.select_staging(candidates, "--latest")

    def test_staging_requires_matching_successful_main_build(self):
        sha, repo, tag = "a" * 40, "owner/MiqatTV", "v0.7.8-staging.1"
        run = {"status": "completed", "conclusion": "success", "head_branch": "main",
               "head_sha": sha, "path": ".github/workflows/build.yml", "event": "workflow_dispatch",
               "head_repository": {"full_name": repo}}
        candidate = {"tag_name": tag, "published_at": "2026-11-23", "id": 1,
                     "draft": False, "prerelease": True, "author": {"login": release.BOT},
                     "target_commitish": sha, "body": f"Build: https://github.com/{repo}/actions/runs/42",
                     "assets": [{"name": "miqat.apk", "size": 5, "digest": "sha256:" + "a" * 64,
                                 "state": "uploaded", "uploader": {"login": release.BOT}}]}
        with patch.object(release, "releases", return_value=[candidate]), \
                patch.object(release, "tag_commit", return_value=sha), \
                patch.object(release, "read_api", return_value=run) as api:
            resolved = release.resolve(repo, tag)
            self.assertEqual((resolved["tag"], resolved["source"], resolved["production"]),
                             (tag, sha, "v0.7.8"))
            self.assertEqual(json.loads(resolved["manifest"]), {"miqat.apk": [5, "a" * 64]})
            summary = release.candidate_summary(repo, resolved)
            self.assertIn(f"https://github.com/{repo}/releases/tag/{tag}", summary)
            self.assertIn(f"https://github.com/{repo}/commit/{sha}", summary)
            self.assertIn(f"https://github.com/{repo}/actions/runs/42", summary)
            for key, value in (("status", "in_progress"), ("conclusion", "failure"),
                               ("head_branch", "topic"), ("head_sha", "b" * 40),
                               ("path", ".github/workflows/validate.yml"), ("event", "pull_request"),
                               ("head_repository", {"full_name": "other/repo"})):
                api.return_value = {**run, key: value}
                with self.subTest(key=key), self.assertRaises(ValueError):
                    release.resolve(repo, tag)

    def test_next_tag_skips_releases_drafts_and_existing_git_tags(self):
        with patch.object(release, "releases", return_value=[{"tag_name": "v0.7.8-staging.1"}]), \
                patch.object(release, "read_api", side_effect=[
                    {"tag_name": "v0.7.7"}, [{"ref": "refs/tags/v0.7.8-staging.3"}]]):
            self.assertEqual(release.next_tag("repos/owner/MiqatTV"), "v0.7.8-staging.4")

    def test_signed_build_versions_increase_across_candidates_and_releases(self):
        tags = ["v0.7.9-staging.1", "v0.7.9-staging.999", "v0.7.10-staging.1",
                "v0.8.0-staging.1", "v1.0.0-staging.1"]
        versions = [release.package_version(tag) for tag in tags]
        self.assertEqual(versions[0], ("0.7.9", 7_009_001))
        codes = [code for _, code in versions]
        self.assertEqual(codes, sorted(set(codes)))
        self.assertTrue(all(code > 40 for code in codes))
        for tag in ("v0.7.9", "v0.7.9-staging.1000", "v0.7.1000-staging.1",
                    "v0.100.0-staging.1", "v21.0.0-staging.1"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.package_version(tag)

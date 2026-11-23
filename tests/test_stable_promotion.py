"""Stable promotion keeps the verified preview bytes across retries."""
from hashlib import sha256
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import call, patch

from tools import promote_release as promotion


class StablePromotionChecks(unittest.TestCase):
    repository = "example/miqat"
    source = "1" * 40
    stable = "v0.7.8"
    preview = "v0.7.8-preview.1"

    def packages(self, directory):
        payloads = {"miqat.apk": b"tv", "miqat-phone.apk": b"phone",
                    "tv.miqat.webos_0.2.33_all.ipk": b"lg"}
        sums = "".join(f"{sha256(data).hexdigest()}  {name}\n"
                       for name, data in payloads.items())
        payloads["SHA256SUMS"] = sums.encode()
        for name, data in payloads.items():
            (directory / name).write_bytes(data)
        return {name: (len(data), sha256(data).hexdigest()) for name, data in payloads.items()}

    def release(self, manifest, **fields):
        return dict({
            "id": 20, "tag_name": self.stable, "target_commitish": self.source,
            "draft": True, "prerelease": False, "author": {"login": promotion.BOT},
            "assets": [{"name": name, "size": size, "digest": f"sha256:{digest}",
                        "state": "uploaded", "uploader": {"login": promotion.BOT}}
                       for name, (size, digest) in manifest.items()],
        }, **fields)

    def test_download_and_checksum_list_must_agree(self):
        with TemporaryDirectory() as temporary:
            directory = Path(temporary)
            manifest = self.packages(directory)
            promotion.verify_files(directory, manifest)
            (directory / "miqat.apk").write_bytes(b"xx")
            with self.assertRaisesRegex(ValueError, "Download differs"):
                promotion.verify_files(directory, manifest)
            manifest["miqat.apk"] = (2, sha256(b"xx").hexdigest())
            with self.assertRaisesRegex(ValueError, "SHA256SUMS does not match"):
                promotion.verify_files(directory, manifest)

    def test_retry_uploads_only_missing_assets(self):
        with TemporaryDirectory() as temporary:
            directory = Path(temporary)
            manifest = self.packages(directory)
            partial = self.release({"miqat.apk": manifest["miqat.apk"]})
            complete = self.release(manifest)
            previous = {"tag_name": "v0.7.7"}
            with patch.object(promotion, "read_api", side_effect=[
                    [[partial]], previous, complete, previous, self.release(manifest, draft=False)]), \
                    patch.object(promotion, "tag_commit", side_effect=[None, None, self.source]), \
                    patch.object(promotion.subprocess, "check_call") as gh:
                promotion.publish(self.repository, self.stable, self.source, manifest, directory, "notes")
            remaining = [str(directory / name) for name in sorted(set(manifest) - {"miqat.apk"})]
            self.assertEqual(gh.call_args_list, [
                call(["gh", "release", "upload", self.stable, *remaining, "--repo", self.repository]),
                call(["gh", "release", "edit", self.stable, "--repo", self.repository,
                      "--draft=false", "--prerelease=false", "--latest"]),
            ])

    def test_new_release_starts_as_a_draft(self):
        with TemporaryDirectory() as temporary:
            directory = Path(temporary)
            manifest = self.packages(directory)
            complete = self.release(manifest)
            previous = {"tag_name": "v0.7.7"}
            with patch.object(promotion, "read_api", side_effect=[
                    [[]], previous, self.release({}), complete, previous,
                    self.release(manifest, draft=False)]) as api, \
                    patch.object(promotion, "tag_commit", side_effect=[None, None, self.source]), \
                    patch.object(promotion.subprocess, "check_call") as gh:
                promotion.publish(self.repository, self.stable, self.source, manifest, directory, "notes")
            draft = api.call_args_list[2].args
            self.assertEqual(draft[:3], (f"repos/{self.repository}/releases", "-X", "POST"))
            self.assertIn("draft=true", draft)
            self.assertIn(f"target_commitish={self.source}", draft)
            self.assertEqual(gh.call_args_list[0].args[0][2:4], ["upload", self.stable])

    def test_inconsistent_draft_is_left_untouched(self):
        manifest = {"miqat.apk": (2, sha256(b"tv").hexdigest())}
        draft = self.release({"miqat.apk": (2, sha256(b"xx").hexdigest())})
        with patch.object(promotion, "read_api", return_value=[[draft]]), \
                patch.object(promotion, "tag_commit", return_value=None), \
                patch.object(promotion.subprocess, "check_call") as gh:
            with self.assertRaisesRegex(ValueError, "does not match this preview"):
                promotion.publish(self.repository, self.stable, self.source, manifest, Path("."), "notes")
            gh.assert_not_called()

    def test_verify_only_does_not_publish(self):
        run = {"status": "completed", "conclusion": "success", "head_branch": "main",
               "head_sha": self.source, "path": ".github/workflows/validate.yml",
               "head_repository": {"full_name": self.repository}, "event": "push"}
        preview = self.release({}, tag_name=self.preview, draft=False, prerelease=True,
                               body=f"Validation: https://github.com/{self.repository}/actions/runs/10")
        with patch.object(promotion, "read_api", side_effect=[
                preview, {"merge_base_commit": {"sha": self.source}}, run]), \
                patch.object(promotion, "tag_commit", return_value=self.source), \
                patch.object(promotion, "verify_files"), patch.object(promotion, "check_apk"), \
                patch.dict("os.environ", {"ANDROID_HOME": "sdk"}), \
                patch.object(promotion.subprocess, "check_call") as gh, \
                patch.object(promotion, "publish") as publish:
            promotion.promote(self.repository, self.source, self.preview, verify_only=True)
        self.assertEqual(gh.call_count, 1)
        self.assertEqual(gh.call_args.args[0][:3], ["gh", "release", "download"])
        publish.assert_not_called()

"""Production preserves the verified Staging assets across retries."""
from hashlib import sha256
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from zipfile import ZipFile
from unittest.mock import call, patch

from tools import promote_release as promotion


class StablePromotionChecks(unittest.TestCase):
    repository = "example/miqat"
    source = "1" * 40
    stable = "v0.7.8"

    def packages(self, directory):
        payloads = {"miqat.apk": b"tv", "miqat-phone.apk": b"phone",
                    "tv.miqat.webos_0.2.33_all.ipk": b"lg", "screenshots.zip": b"images"}
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
            with patch.object(promotion, "releases", return_value=[partial]), \
                    patch.object(promotion, "read_api", side_effect=[
                        previous, complete, previous, self.release(manifest, draft=False)]), \
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
            with patch.object(promotion, "releases", return_value=[]), \
                    patch.object(promotion, "read_api", side_effect=[
                    previous, self.release({}), complete, previous,
                    self.release(manifest, draft=False)]) as api, \
                    patch.object(promotion, "tag_commit", side_effect=[None, None, self.source]), \
                    patch.object(promotion.subprocess, "check_call") as gh:
                promotion.publish(self.repository, self.stable, self.source, manifest, directory, "notes")
            draft = api.call_args_list[1].args
            self.assertEqual(draft[:3], (f"repos/{self.repository}/releases", "-X", "POST"))
            self.assertIn("draft=true", draft)
            self.assertIn(f"target_commitish={self.source}", draft)
            self.assertEqual(gh.call_args_list[0].args[0][2:4], ["upload", self.stable])

    def test_inconsistent_draft_is_left_untouched(self):
        manifest = {"miqat.apk": (2, sha256(b"tv").hexdigest())}
        draft = self.release({"miqat.apk": (2, sha256(b"xx").hexdigest())})
        with patch.object(promotion, "releases", return_value=[draft]), \
                patch.object(promotion, "tag_commit", return_value=None), \
                patch.object(promotion.subprocess, "check_call") as gh:
            with self.assertRaisesRegex(ValueError, "does not match this Staging candidate"):
                promotion.publish(self.repository, self.stable, self.source, manifest, Path("."), "notes")
            gh.assert_not_called()

    def test_website_rejects_a_screenshot_swap(self):
        with TemporaryDirectory() as temporary:
            directory = Path(temporary)
            relative = "docs/screenshots/phone-home-en.png"
            source = directory / relative
            source.parent.mkdir(parents=True)
            source.write_bytes(b"approved image")
            archive = directory / "screenshots.zip"

            def pack(data):
                with ZipFile(archive, "w") as output:
                    output.writestr(relative, data)

            with patch.object(promotion.subprocess, "check_output", return_value=(relative + "\0").encode()):
                pack(b"approved image")
                promotion.use_screenshots(directory, archive)
                pack(b"modified image")
                with self.assertRaisesRegex(ValueError, "differs from its approved source"):
                    promotion.use_screenshots(directory, archive)

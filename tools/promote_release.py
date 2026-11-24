"""Verify Staging bytes and promote the same assets to Production."""
from hashlib import file_digest
import json
from fnmatch import fnmatchcase
import os
from pathlib import Path
import re
import subprocess
import sys
from zipfile import ZipFile

from tools.check_android_release import CERTIFICATES, check_apk
from tools.release_source import BOT, asset_manifest, read_api, releases, stable_version, tag_commit


APKS = {"miqat.apk", "miqat-phone.apk"}
SCREENSHOTS = "screenshots.zip"


def verify_assets(directory, manifest):
    if {file.name for file in directory.iterdir()} != set(manifest):
        raise ValueError("Downloaded files do not match the expected Staging assets")
    for name, (size, digest) in manifest.items():
        path = directory / name
        with path.open("rb") as file:
            if path.stat().st_size != size or file_digest(file, "sha256").hexdigest() != digest:
                raise ValueError(f"Download differs from the Staging asset: {name}")


def verify_files(directory, manifest):
    names = set(manifest)
    required = APKS | {"SHA256SUMS", SCREENSHOTS}
    lg = names - required
    if (not required <= names or len(lg) != 1
            or not re.fullmatch(r"tv\.miqat\.webos_\d+\.\d+\.\d+_all\.ipk", next(iter(lg)))):
        raise ValueError("Expected both APKs, the LG IPK, screenshots.zip and SHA256SUMS")
    verify_assets(directory, manifest)
    sums = {}
    for line in (directory / "SHA256SUMS").read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([a-f0-9]{64}) [ *]([^/\\]+)", line)
        if not match or match[2] in sums:
            raise ValueError("Invalid or duplicate SHA256SUMS entry")
        sums[match[2]] = match[1]
    if sums != {name: manifest[name][1] for name in names - {"SHA256SUMS"}}:
        raise ValueError("SHA256SUMS does not match the verified Staging assets")


def use_screenshots(candidate, archive):
    paths = subprocess.check_output(
        ["git", "-C", str(candidate), "ls-files", "-z", "docs/screenshots"]
    ).decode().strip("\0").split("\0")
    with ZipFile(archive) as images:
        files = [entry for entry in images.infolist() if not entry.is_dir()]
        if not paths[0] or sorted(entry.filename for entry in files) != sorted(paths):
            raise ValueError("Staging screenshot archive differs from the source inventory")
        for entry in files:
            source = candidate / entry.filename
            if entry.file_size != source.stat().st_size or images.read(entry) != source.read_bytes():
                raise ValueError(f"Staging screenshot differs from its approved source: {entry.filename}")
        for entry in files:
            (candidate / entry.filename).write_bytes(images.read(entry))


def prepare_website(candidate, directory, production):
    use_screenshots(candidate, directory / SCREENSHOTS)
    build_tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    certificates = json.loads(CERTIFICATES.read_text(encoding="utf-8"))
    android = {}
    for name, expected in certificates.items():
        version, code = check_apk(directory / name, build_tools)
        android[expected["applicationId"]] = {"versionCode": code, "versionName": version}
    lg_asset, = directory.glob("*.ipk")
    metadata = {"tag": production, "lgAsset": lg_asset.name, "android": android}
    (candidate / "site/src/lib/stable-release.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
    )


def release_notes(staging, repository):
    return (f"Miqat Phone, Android TV and LG webOS.\n\n"
            f"[Verified Staging candidate](https://github.com/{repository}/releases/tag/{staging}).\n\n"
            "SHA256SUMS covers the packages and approved release screenshots. "
            "Both APKs use Miqat's persistent release keys.\n")


def publish(repository, stable, source, manifest, directory, notes):
    repo = f"repos/{repository}"
    matches = [release for release in releases(repo) if release["tag_name"] == stable]
    tag = tag_commit(repo, stable)
    if tag and tag != source:
        raise ValueError(f"{stable} already tags a different commit")
    if matches:
        release, = matches
        uploaded = asset_manifest(release)
        if (release["target_commitish"] != source or release["prerelease"]
                or any(manifest.get(name) != asset for name, asset in uploaded.items())):
            raise ValueError(f"The existing release {stable} does not match this Staging candidate")
        if not release["draft"]:
            if tag != source or uploaded != manifest:
                raise ValueError(f"The published release {stable} is incomplete")
            print(f"Already published: {release['html_url']}")
            return
        if release["author"]["login"] != BOT:
            raise ValueError(f"{stable} must be an Actions-created draft")
    else:
        uploaded = {}
        release = None

    latest = read_api(f"{repo}/releases/latest")
    if stable_version(stable) <= stable_version(latest["tag_name"]):
        raise ValueError(f"{stable} must be newer than the current stable release")
    if release is None:
        release = read_api(f"{repo}/releases", "-X", "POST", "-f", f"tag_name={stable}",
                           "-f", f"target_commitish={source}", "-f", f"name=Miqat {stable}",
                           "-f", f"body={notes}", "-F", "draft=true")
    missing = sorted(set(manifest) - set(uploaded))
    if missing:
        subprocess.check_call(["gh", "release", "upload", stable,
                               *(str(directory / name) for name in missing), "--repo", repository])
    stored = read_api(f"{repo}/releases/{release['id']}")
    if (not stored["draft"] or stored["prerelease"] or stored["target_commitish"] != source
            or asset_manifest(stored) != manifest):
        raise ValueError("Stable draft differs from the Staging candidate")
    if tag_commit(repo, stable) not in (None, source):
        raise ValueError(f"{stable} points to a different commit")
    if stable_version(stable) <= stable_version(read_api(f"{repo}/releases/latest")["tag_name"]):
        raise ValueError("The current stable release advanced during promotion")
    subprocess.check_call(["gh", "release", "edit", stable, "--repo", repository,
                           "--draft=false", "--prerelease=false", "--latest"])
    published = read_api(f"{repo}/releases/latest")
    if (published["tag_name"] != stable or tag_commit(repo, stable) != source
            or asset_manifest(published) != manifest):
        raise ValueError("Published release does not match the verified draft")


if __name__ == "__main__":
    try:
        repository = os.environ["GITHUB_REPOSITORY"]
        staging = os.environ["STAGING_TAG"]
        manifest = {name: tuple(entry) for name, entry in json.loads(os.environ["ASSET_MANIFEST"]).items()}
        command, path = sys.argv[1:3]
        directory = Path(path)
        if command == "download":
            pattern = sys.argv[3] if len(sys.argv) > 3 else "*"
            selected = {name: entry for name, entry in manifest.items() if fnmatchcase(name, pattern)}
            if not selected or pattern != "*" and len(selected) != 1:
                raise ValueError(f"Expected one Staging asset matching {pattern}")
            directory.mkdir(parents=True, exist_ok=True)
            subprocess.check_call(["gh", "release", "download", staging, "--repo", repository,
                                   "--dir", str(directory), "--pattern", pattern])
            verify_assets(directory, selected)
        else:
            verify_files(directory, manifest)
        if command == "website":
            prepare_website(Path(sys.argv[3]), directory, os.environ["PRODUCTION_TAG"])
        elif command == "publish":
            stable = os.environ["PRODUCTION_TAG"]
            publish(repository, stable, os.environ["SOURCE_SHA"], manifest, directory,
                    release_notes(staging, repository))
        elif command != "download":
            raise ValueError("Expected download, website or publish")
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error)) from error

"""Promote the verified bytes of an Actions-built prerelease."""
from hashlib import file_digest
import json
import os
from pathlib import Path
import re
import subprocess
from tempfile import TemporaryDirectory

from tools.check_android_release import check_apk
from tools.release_source import gh_api, validate_run


APKS = {"miqat.apk", "miqat-phone.apk"}
BOT = "github-actions[bot]"
STABLE_TAG = r"v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)"


def stable_version(tag):
    match = re.fullmatch(STABLE_TAG, tag)
    if not match:
        raise ValueError("Expected a stable vMAJOR.MINOR.PATCH tag")
    return tuple(map(int, match.groups()))


def read_api(endpoint, *args):
    return json.loads(gh_api(endpoint, *args))


def tag_commit(repo, tag):
    refs = read_api(f"{repo}/git/matching-refs/tags/{tag}")
    for ref in refs:
        if ref["ref"] == f"refs/tags/{tag}":
            if ref["object"]["type"] != "commit":
                raise ValueError(f"{tag} must point directly to its source commit")
            return ref["object"]["sha"]
    return None


def asset_manifest(release):
    manifest = {}
    for asset in release["assets"]:
        name, size, digest = asset["name"], asset["size"], asset.get("digest") or ""
        if (name in manifest or asset["state"] != "uploaded" or size <= 0
                or not re.fullmatch(r"sha256:[a-f0-9]{64}", digest)):
            raise ValueError(f"Invalid GitHub release asset: {name}")
        manifest[name] = (size, digest.removeprefix("sha256:"))
    return manifest


def verify_files(directory, manifest):
    names = set(manifest)
    lg = names - APKS - {"SHA256SUMS"}
    if (not (APKS | {"SHA256SUMS"}) <= names or len(lg) != 1
            or not re.fullmatch(r"tv\.miqat\.webos_\d+\.\d+\.\d+_all\.ipk", next(iter(lg)))
            or {file.name for file in directory.iterdir()} != names):
        raise ValueError("Expected both APKs, the LG IPK and SHA256SUMS")
    for name, (size, digest) in manifest.items():
        path = directory / name
        with path.open("rb") as file:
            if path.stat().st_size != size or file_digest(file, "sha256").hexdigest() != digest:
                raise ValueError(f"Download differs from the preview asset: {name}")
    sums = {}
    for line in (directory / "SHA256SUMS").read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([a-f0-9]{64}) [ *](?:\./)?([^/\\]+)", line)
        if not match or match[2] in sums:
            raise ValueError("Invalid or duplicate SHA256SUMS entry")
        sums[match[2]] = match[1]
    if sums != {name: manifest[name][1] for name in APKS | lg}:
        raise ValueError("SHA256SUMS does not match the three verified packages")


def release_notes(stable, preview_tag, validation_url, repository):
    changelog = Path(__file__).resolve().parents[1] / "CHANGELOG.md"
    match = re.search(rf"^## {re.escape(stable)}(?:[ \t]+[^\n]+)?\n(.*?)(?=^## |\Z)",
                      changelog.read_text(encoding="utf-8"), re.M | re.S)
    summary = match[1].strip() if match else "Miqat for Android phones, Android TV and LG webOS."
    return (f"{summary}\n\n[Validation]({validation_url}) and "
            f"[signed preview](https://github.com/{repository}/releases/tag/{preview_tag}).\n\n"
            "SHA256SUMS covers all three packages. Both APKs use Miqat's persistent release keys.")


def publish(repository, stable, source, manifest, directory, notes):
    repo = f"repos/{repository}"
    pages = read_api(f"{repo}/releases?per_page=100", "--paginate", "--slurp")
    releases = [release for page in pages for release in page if release["tag_name"] == stable]
    tag = tag_commit(repo, stable)
    if tag and tag != source:
        raise ValueError(f"{stable} already tags a different commit")
    if releases:
        release, = releases
        uploaded = asset_manifest(release)
        if (release["target_commitish"] != source or release["prerelease"]
                or any(manifest.get(name) != asset for name, asset in uploaded.items())):
            raise ValueError(f"The existing release {stable} does not match this preview")
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
        raise ValueError("Stable draft differs from the preview")
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


def promote(repository, head, preview_tag, verify_only):
    match = re.fullmatch(rf"({STABLE_TAG})-preview\.[1-9]\d*", preview_tag)
    if not match:
        raise ValueError("Use a signed preview tag such as v0.7.7-preview.2")
    stable = match[1]
    repo = f"repos/{repository}"
    preview = read_api(f"{repo}/releases/tags/{preview_tag}")
    if preview["draft"] or not preview["prerelease"] or preview["author"]["login"] != BOT:
        raise ValueError("Choose a published preview created by the GitHub Actions draft workflow")
    source = tag_commit(repo, preview_tag)
    if not source or source != preview["target_commitish"]:
        raise ValueError("Preview tag differs from its recorded source")
    comparison = read_api(f"{repo}/compare/{source}...{head}")
    if comparison["merge_base_commit"]["sha"] != source:
        raise ValueError("Preview source is not on main")
    validation = re.search(rf"^Validation: (https://github\.com/{re.escape(repository)}"
                           r"/actions/runs/([1-9]\d*))$", preview["body"], re.M)
    if not validation:
        raise ValueError("Preview has no recorded validation run")
    validate_run(read_api(f"{repo}/actions/runs/{validation[2]}"), repository, source, preview_tag)
    if any(asset["uploader"]["login"] != BOT for asset in preview["assets"]):
        raise ValueError("Preview assets must have been uploaded by GitHub Actions")
    manifest = asset_manifest(preview)
    with TemporaryDirectory(dir=os.environ.get("RUNNER_TEMP")) as temporary:
        directory = Path(temporary)
        subprocess.check_call(["gh", "release", "download", preview_tag, "--repo", repository,
                               "--dir", temporary, "--pattern", "*"])
        verify_files(directory, manifest)
        build_tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
        for name in sorted(APKS):
            check_apk(directory / name, build_tools)
        if verify_only:
            print(f"Verified {preview_tag} for {stable}.")
            return
        publish(repository, stable, source, manifest, directory,
                release_notes(stable, preview_tag, validation[1], repository))


if __name__ == "__main__":
    try:
        promote(os.environ["GITHUB_REPOSITORY"], os.environ["GITHUB_SHA"],
                os.environ["PREVIEW_TAG"], os.environ["VERIFY_ONLY"] == "true")
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error)) from error

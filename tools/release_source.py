"""Allocate Staging tags and pin a published candidate for this workflow run."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys

BOT = "github-actions[bot]"
STABLE_TAG = r"v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)"
STAGING_TAG = rf"({STABLE_TAG})-staging\.[1-9]\d*"


def gh_api(endpoint, *options):
    return subprocess.check_output(["gh", "api", endpoint, *options], encoding="utf-8").strip()


def read_api(endpoint, *options):
    return json.loads(gh_api(endpoint, *options))


def releases(repo):
    pages = read_api(f"{repo}/releases?per_page=100", "--paginate", "--slurp")
    return [release for page in pages for release in page]


def stable_version(tag):
    match = re.fullmatch(STABLE_TAG, tag)
    if not match:
        raise ValueError("Expected a Production vMAJOR.MINOR.PATCH tag")
    return tuple(map(int, match.groups()))


def next_tag(repo):
    major, minor, patch = stable_version(read_api(f"{repo}/releases/latest")["tag_name"])
    prefix = f"v{major}.{minor}.{patch + 1}-staging."
    used = {release["tag_name"] for release in releases(repo)}
    refs = read_api(f"{repo}/git/matching-refs/tags/{prefix}")
    used.update(ref["ref"].removeprefix("refs/tags/") for ref in refs)
    number = 1
    while f"{prefix}{number}" in used:
        number += 1
    return f"{prefix}{number}"


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


def select_staging(candidates, tag=""):
    if tag and not re.fullmatch(STAGING_TAG, tag):
        raise ValueError("Enter a full Staging tag, for example v0.7.8-staging.2")
    published = [
        candidate for candidate in candidates
        if not candidate["draft"] and candidate["prerelease"] and candidate["published_at"]
        and re.fullmatch(STAGING_TAG, candidate["tag_name"])
        and (not tag or candidate["tag_name"] == tag)
    ]
    if not published:
        raise ValueError(f"No published Staging candidate found{f' for {tag}' if tag else ''}")
    return max(published, key=lambda candidate: (candidate["published_at"], candidate["id"]))


def resolve(repository, tag):
    repo = f"repos/{repository}"
    candidate = select_staging(releases(repo), tag)
    tag = candidate["tag_name"]
    source = tag_commit(repo, tag)
    if (candidate["author"]["login"] != BOT or not source or source != candidate["target_commitish"]
            or any(asset["uploader"]["login"] != BOT for asset in candidate["assets"])):
        raise ValueError("Staging tag, author or asset uploader does not match the Build workflow")
    build = re.search(rf"^Build: https://github\.com/{re.escape(repository)}/actions/runs/([1-9]\d*)$",
                      candidate["body"] or "", re.M)
    if not build:
        raise ValueError("Staging candidate has no recorded Build run")
    run = read_api(f"{repo}/actions/runs/{build[1]}")
    expected = {"path": ".github/workflows/build.yml", "event": "workflow_dispatch",
                "head_branch": "main", "head_sha": source, "status": "completed", "conclusion": "success"}
    if ((run.get("head_repository") or {}).get("full_name") != repository
            or any(run.get(key) != value for key, value in expected.items())):
        raise ValueError("Staging candidate must come from a successful Build run on main")
    return {
        "tag": tag, "source": source, "production": re.fullmatch(STAGING_TAG, tag)[1],
        "manifest": json.dumps(asset_manifest(candidate), separators=(",", ":")),
    }


if __name__ == "__main__":
    repository = os.environ["GITHUB_REPOSITORY"]
    try:
        if sys.argv[1] == "next":
            result = {"tag": next_tag(f"repos/{repository}")}
        elif sys.argv[1] == "resolve":
            result = resolve(repository, os.environ.get("STAGING_TAG", ""))
        else:
            raise ValueError("Expected next or resolve")
        with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
            for key, value in result.items():
                print(f"{key}={value}", file=output)
        print(f"Selected {result['tag']}.")
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error)) from error

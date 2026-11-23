"""Resolve and verify the inputs for a draft release from current main."""
import json
import os
from pathlib import Path
import re
import subprocess


def validate_run(run, repository, sha, tag):
    if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", tag):
        raise ValueError("Use a version tag such as v0.7.0-preview.1")
    expected = {"status": "completed", "conclusion": "success", "head_branch": "main",
                "head_sha": sha, "path": ".github/workflows/validate.yml"}
    if any(run.get(key) != value for key, value in expected.items()):
        raise ValueError("Choose successful Validate results for the current main commit")
    if run.get("event") not in ("push", "workflow_dispatch"):
        raise ValueError("PR artifacts cannot be published by this workflow")
    if (run.get("head_repository") or {}).get("full_name") != repository:
        raise ValueError("Artifacts must come from this repository")


def gh_api(endpoint, *options):
    return subprocess.check_output(
        ["gh", "api", endpoint, *options], encoding="utf-8"
    ).strip()


def resolve_inputs(repository, sha, run_id, tag):
    repo = f"repos/{repository}"
    if json.loads(gh_api(f"{repo}/git/ref/heads/main"))["object"]["sha"] != sha:
        raise ValueError("Main has moved. Run Draft build release again from main.")

    if run_id:
        if not re.fullmatch(r"[0-9]+", run_id):
            raise ValueError("Run ID must be numeric")
        run = json.loads(gh_api(f"{repo}/actions/runs/{run_id}"))
    else:
        result = gh_api(f"{repo}/actions/workflows/validate.yml/runs", "-X", "GET",
                        "-f", "branch=main", "-f", f"head_sha={sha}",
                        "-f", "status=success", "-F", "per_page=1")
        runs = json.loads(result)["workflow_runs"]
        if not runs:
            raise ValueError("No successful Validate run for current main. Run Validate first.")
        run = runs[0]

    # A draft can reserve a release name before its Git tag exists.
    used_tags = set(gh_api(f"{repo}/releases", "--paginate", "--jq", ".[].tag_name").splitlines())
    used_tags.update(gh_api(f"{repo}/tags", "--paginate", "--jq", ".[].name").splitlines())
    if not tag:
        stable_tag = gh_api(f"{repo}/releases/latest", "--jq", ".tag_name")
        stable = re.fullmatch(r"v([0-9]+)\.([0-9]+)\.([0-9]+)", stable_tag)
        if not stable:
            raise ValueError("Latest stable release has no vMAJOR.MINOR.PATCH tag. Enter a tag override.")
        major, minor, patch = map(int, stable.groups())
        prefix = f"v{major}.{minor}.{patch + 1}-preview."
        number = 1
        while f"{prefix}{number}" in used_tags:
            number += 1
        tag = f"{prefix}{number}"

    validate_run(run, repository, sha, tag)
    if tag in used_tags:
        raise ValueError(f"Release tag already in use: {tag}")
    return str(run["id"]), tag


if __name__ == "__main__":
    try:
        run_id, tag = resolve_inputs(os.environ["GITHUB_REPOSITORY"], os.environ["GITHUB_SHA"],
                                     os.environ["RUN_ID"], os.environ["RELEASE_TAG"])
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error)) from error
    with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
        output.write(f"run_id={run_id}\ntag={tag}\n")
    print(f"Validated source run {run_id} for draft {tag}.")

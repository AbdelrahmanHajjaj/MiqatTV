"""Permit draft publication only from successful validation of this main commit."""
import json
import os
from pathlib import Path
import re
import sys


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


if __name__ == "__main__":
    validate_run(json.loads(Path(sys.argv[1]).read_text(encoding="utf-8")),
                 os.environ["GITHUB_REPOSITORY"], os.environ["GITHUB_SHA"], os.environ["RELEASE_TAG"])
    print("Validated the completed main-branch build source.")

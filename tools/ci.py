"""Select affected PR checks and reject missing, failed or unexpectedly skipped jobs."""
import json
import os
from pathlib import Path
import subprocess
import sys

JOBS = ("core", "android", "webos", "preview", "companion")


def affected(paths):
    jobs = set()
    for path in paths:
        if path in ("LICENSE", "docs/CLEANYOURTV-LICENSE.txt", "docs/THIRD-PARTY.md",
                    "docs/UI-GAPS.md"):
            jobs.update(JOBS)
        elif path.startswith("docs/screenshots/") and path.endswith(".png"):
            jobs.add("preview")
        elif path.startswith("docs/") or path in (
                "README.md", "AGENTS.md", "CONTRIBUTING.md", "CHANGELOG.md",
                ".github/pull_request_template.md"):
            continue
        elif path.startswith("site/"):
            jobs.add("preview")
        elif path.startswith("platforms/android-tv/"):
            jobs.add("android")
            if path.endswith(("/ui-gaps.json", "/ui-choice-gaps.json")):
                jobs.update(JOBS)
        elif path.startswith("platforms/webos/"):
            jobs.add("webos")
            if path.endswith(("/ui-gaps.json", "/ui-choice-gaps.json")):
                jobs.update(JOBS)
        elif path.startswith("installer/"):
            jobs.update(("android", "companion"))
        elif path.startswith(("companion/android/", "companion/core/")):
            jobs.add("companion")
        elif path.startswith("companion/"):
            # TV shells and preview consume the companion contract, QR and LAN sources.
            jobs.update(("android", "webos", "preview", "companion"))
        else:
            # Unknown paths fail safe: shared sources, tooling and workflow changes reach all consumers.
            jobs.update(JOBS)
    return jobs


def require_jobs(needs):
    if needs["changes"]["result"] != "success":
        raise ValueError("Change selection did not pass")
    outputs = needs["changes"]["outputs"]
    for name in JOBS:
        selected = outputs[name]
        if selected not in ("true", "false"):
            raise ValueError("Missing selection for " + name)
        expected = "success" if selected == "true" else "skipped"
        if needs[name]["result"] != expected:
            raise ValueError(name + " must be " + expected)


if __name__ == "__main__":
    if sys.argv[1] == "select":
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
        selected = set(JOBS)
        if os.environ["GITHUB_EVENT_NAME"] == "pull_request":
            pr = event["pull_request"]
            paths = subprocess.check_output([
                "git", "diff", "--name-only", "--no-renames", "-z",
                pr["base"]["sha"] + "..." + pr["head"]["sha"],
            ]).decode("utf-8").split("\0")
            selected = affected(path for path in paths if path)
        elif os.environ.get("BUILD_CHANNEL") in ("development", "staging"):
            selected.remove("preview")
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            for name in JOBS:
                print(name + "=" + str(name in selected).lower(), file=output)
        print("Selected checks:", ", ".join(sorted(selected)) or "documentation only")
    elif sys.argv[1] == "require":
        require_jobs(json.loads(os.environ["RESULTS"]))
        print("All applicable checks passed.")
    else:
        raise ValueError("Expected select or require")

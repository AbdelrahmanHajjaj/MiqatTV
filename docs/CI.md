# Validation and build publication

## Main protection

Protect main requires a pull request, the GitHub Actions Required checks status,
an up-to-date branch and resolved review conversations. PRs use normal merge
commits; squash and rebase merging are disabled. Force
pushes and deletion are blocked. There is no bypass list or required approving
reviewer, so a solo maintainer can merge their own passing PR.

Required checks is an always-running aggregate. A failed, cancelled or skipped
required job fails it. The workflow runs on every PR to main and every main push,
including documentation changes. Actions are pinned to immutable commits, checkout
does not persist credentials, and validation has read-only repository access.

## Checks and artifacts

| Job | Coverage / output |
| --- | --- |
| Core | Canonical projections, complete media hashes, JVM/Java/JS and legacy ES5 tests |
| Android build | Full-audio TV APK, phone-installer APK, test APK, lint and local regressions |
| Android emulator | Native prayer/ICU linkage and Arabic/English UI bindings on API 34 |
| LG build and tests | Complete app/service, ES5, volume/scheduling/HTTPS mocks, browser suite and full-audio IPK |
| Preview | Data/token freshness, regressions and 18-route static build |

Download android-builds, webos-build or browser-preview from a successful Validate
run. Artifacts and failure reports expire after seven days. The test APK is for
instrumentation, not household installation. Browser/emulator output is not TV
picture, sound or physical-remote acceptance.

The approved recordings are checked in under shared/media/; no private originals,
stamps or device state are uploaded. Changes to audio need content review as well
as checksum and preparation tests.

## Draft release

Run Draft build release from main, supplying a successful Validate run ID for the
current main commit and a new tag such as v0.7.0-preview.1. The publisher rejects
PR/fork artifacts, stale commits, other workflows and unsuccessful runs. It reuses
the verified Android/LG artifacts, checks APK media, computes SHA256SUMS and creates
a draft prerelease. It never publishes a stable release automatically.

Only the manual publishing job has contents write permission. Its token has no
PR approval grant. The repository default remains read-only. No personal token
or signing key is stored by this setup.

Android artifacts use a runner-generated debug signing key. Fresh installation
may be needed between runs. Configure a persistent release keystore before using
this channel for stable updates. Source versions and physical acceptance limits
remain in [EVIDENCE.md](EVIDENCE.md); a green CI run does not remove those limits.

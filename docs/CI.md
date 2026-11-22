# Validation and build publication

## Main protection

Protect main requires a pull request, the GitHub Actions Required checks status,
an up-to-date branch and resolved review conversations. PRs use normal merge
commits; squash and rebase merging are disabled. Force
pushes and deletion are blocked. There is no bypass list or required approving
reviewer, so a solo maintainer can merge their own passing PR.

Required checks is an always-running aggregate. Select checks runs on every PR
and verifies workflow syntax, selection/gate regressions and PR whitespace. It
selects affected consumers from the complete merge-base diff, including deleted
and renamed paths. Required checks rejects failures, cancellation, missing
selection and unexpected skips; only deliberately unselected jobs may skip.
Companion is included in this gate when affected.
All code changes also check canonical projections and media before downstream
generators can repair stale files.

PR selection is conservative:

- Ordinary documentation and the PR template run only Select checks and Required
  checks. Packaged licences/notices and generated docs/UI-GAPS.md run all jobs.
- Site changes select Preview; Android TV or installer changes select Android
  build and emulator; LG changes select LG build and tests.
- Companion changes select Companion, Android, LG and Preview because the shells
  consume its contract, generated bindings, QR and LAN sources.
- Shared sources, tooling, workflow/build configuration and unrecognized paths
  select all jobs. Changes spanning areas select their union.

Main pushes and manual Validate runs always select the full suite. Validate and
Companion cancel superseded runs for the same PR or branch. Actions are pinned to
immutable commits, checkout does not persist credentials, and validation has
read-only repository access.

## Checks and artifacts

| Job | Coverage / output |
| --- | --- |
| Core | Canonical projections, complete media hashes, JVM/Java/JS and legacy ES5 tests |
| Android build | Full-audio TV APK, phone-installer APK, test APK, lint and local regressions |
| Android emulator | Native prayer/ICU linkage and Arabic/English UI bindings on API 34 |
| LG build and tests | Complete app/service, ES5, volume/scheduling/HTTPS mocks, browser suite and full-audio IPK |
| Preview | Data/token freshness, regressions and 18-route static build |
| Companion | Portable settings checks, native bilingual UI and pinned-TLS LAN regressions |

PRs still compile and validate packages; they do not publish review-build
artifacts. The Android jobs retain an android-test-inputs artifact containing
only the TV app and test APK for the emulator. Failure reports and native logs
remain available. All artifacts expire after seven days.

Download android-builds, webos-build, browser-preview or companion-debug from a
successful main Validate run. For an installable branch review build, choose
Actions > Validate > Run workflow and select the branch; this runs the full suite
and uploads the same artifacts without deploying the website. The workflow must
already exist on main before GitHub offers manual dispatch. Companion is reusable
by Validate and also supports a standalone manual run.

The test APK is for instrumentation, not household installation. Browser/emulator
output is not TV picture, sound or physical-remote acceptance.

The approved recordings are checked in under shared/media/; no private originals,
stamps or device state are uploaded. Actions verify approved checksums and packaged
bytes without installing FFmpeg or processing audio. Run preparation/decoding tests
and content review locally when recordings or preparation code change.

## Website

[Miqat's website](https://abdelrahmanhajjaj.github.io/MiqatTV/) serves the existing
product showcase, in Arabic at the root and English under /en/. It covers the
standalone Android phone, TV companion mode, Android/Google TV and LG, with
current bilingual screenshots and identified emulator/browser sources.
The site is static. Phone images show native app screens; the interactive
TV preview calculates prayer times and handles its settings locally.
It simulates TV integration. Its screen buttons switch the TV
preview in place without changing the URL. Existing direct screen links still
work; language, installation and GitHub links keep their destinations.

Validate deploys the same browser-preview artifact after Required checks succeeds
on main. Every main push rebuilds it; Run workflow on main can redeploy it. PRs
build and test the site without deploying. A failed validation leaves the last
successful website in place. No separate gh-pages branch or personal token is used.
Only the GitHub Pages job receives pages write and id-token write permissions.

Repository Settings > Pages uses GitHub Actions as its source. The github-pages
environment allows main deployments only. Astro's site/base configuration owns
the public address and /MiqatTV path; update it if the repository or domain changes.
The README links to both languages. Check the deployed Home, language switch,
Settings and asset requests after changing deployment or base-path behavior.
Canonical screenshot PNGs live under docs/screenshots/ with provenance; the site
build produces responsive web images. Keep source images free of private device
data, credentials and pairing QR material. Review phone and desktop widths in
both languages when changing product sections or screenshot layout.

## Draft release

Run Draft build release from main, supplying a successful Validate run ID for the
current main commit and a new tag such as v0.7.0-preview.1. The publisher rejects
PR/fork artifacts, stale commits, other workflows and unsuccessful runs. It reuses
the verified Android phone/TV, installer and LG artifacts, checks APK media, computes SHA256SUMS and creates
a draft prerelease. It never publishes a stable release automatically.

The phone asset is `miqat-phone.apk`; Android TV remains `miqat.apk` and the
standalone installer is `miqat-installer.apk`. SHA256SUMS covers all three APKs
and the LG IPK. The phone asset requires the same successful Validate run's
`companion-debug` artifact, rather than a separately built or unverified file.

Only the manual build-release job has contents write permission. Its token has no
PR approval grant. The repository default remains read-only. No personal token
or signing key is stored by this setup.

Android artifacts use a runner-generated debug signing key. Fresh installation
may be needed between runs. Configure a persistent release keystore before using
this channel for stable updates. Source versions and physical acceptance limits
remain in [EVIDENCE.md](EVIDENCE.md); a green CI run does not remove those limits.

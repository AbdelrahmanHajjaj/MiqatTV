# Test requirements

These are Miqat's requirements for changes into this project's
`main`. Choose checks from the changed behavior and its consumers, rather than
running every platform for a documentation edit. The PR must record commands,
results and untested paths. [CONTRIBUTING.md](../CONTRIBUTING.md) owns the contribution
workflow; the [architecture queue](WORK.md) owns current acceptance gates.

## Required evidence

- Add a focused regression for a bug or new product rule. For a bug, demonstrate
  the failure before the fix when practical. Test observable behavior and boundary
  cases; avoid tests that repeat implementation details. Prose-only edits need no
  runtime test.
- Put portable rule tests in `shared/core/src/commonTest/` so JVM and JS exercise
  the same cases. Keep OS, storage, network and media tests in the relevant shell.
- A shared change requires the union of affected consumer checks. Inventory
  Android, LG and preview bindings; validate canonical IDs, choices and explicit
  capability gaps. A passing core test alone does not establish UI parity.
- Keep existing checks enabled. Fix new failures and touched-code warnings;
  identify any existing failure against the base. Do not relax assertions, edit
  frozen outputs or declare a capability gap just to make a test pass.
- Identify the tested commit/app version and distinguish unit tests, mocked
  browsers, emulator runs, live TV diagnostics and human observations. Record
  unavailable hardware as pending. Required device acceptance remains open until
  it passes or the owner explicitly changes the scope.

## Setup and commands

Run from the repository root with JDK 17 and Node 24 on `PATH`. Android needs SDK
34 and `ANDROID_HOME` or ignored local SDK configuration. Install the relevant npm
lockfiles with `npm --prefix shared/core ci --ignore-scripts`,
`npm --prefix site ci` or `npm --prefix platforms/webos ci`.
On Windows use `gradlew.bat`, `npm.cmd` and `python`; elsewhere use `./gradlew`,
`npm` and `python3`. Use `--offline` only after caching Gradle dependencies.

Run core, preview and LG builds **sequentially**: their generators write the same
shared JS outputs. Run generated-file checks before a generator can repair stale
files, and inspect `git diff` afterwards.

| Changed area | Required automated checks |
| --- | --- |
| Documentation/templates only | `git diff --check`; verify local links, paths and documented commands against the owning scripts. Review claims against current evidence. |
| Portable core | `./gradlew -p shared/core check jvmJar --no-daemon`; this includes JVM, JS, Java interoperability and ES5/legacy checks. Then run affected shell/preview checks. |
| Canonical data, text, UI catalog or generators | `node tools/catalogs.mjs --check`, `node tools/catalogs.test.mjs`, `node tools/validate-shared.test.mjs`; regenerate intentional source changes and check each affected consumer. |
| Design/tokens, shared layout or scale | `npm --prefix site run tokens:check`, preview suite/build, and affected Android/LG rendering checks. Regenerate through the owning token/pattern tools. |
| Android TV | `./gradlew -p platforms/android-tv verifyApkSize lintDebug assembleDebugAndroidTest --no-daemon`, then `python3 -S -B -m unittest discover -s tests -v`. Build first: JVM regressions need the core jar and extracted Adhan sources. |
| Preview | `npm --prefix site run data:check`, `npm --prefix site test`, `npm --prefix site run build`; check changed flows in Arabic and English in a browser. |
| LG webOS | `npm --prefix platforms/webos test`, then `npm --prefix platforms/webos run test:browser`; these build and check shipped ES5, adapters, service, volume, HTTPS and mocked UI behavior. |
| Android phone installer / PC recovery | `./gradlew :app:assembleDebug :installer:assembleDebug :installer:lintDebug --no-daemon`, then root Python regressions. Exercise changed setup/recovery paths on an explicitly selected test device. |
| Media preparation | `node tools/prepare-adhans.test.mjs` (FFmpeg/ffprobe required); `node tools/prepare-adhans.mjs --check`; check the affected platform packages and audition any changed religious wording/cut. |

Android and LG builds use the 14 adhan and 12 announcement clips in shared/media/.
Check the complete set with sha256sum --check shared/media/SHA256SUMS and run
node tools/prepare-adhans.mjs --check. Original/review audio remains private under
state/. FFmpeg/ffprobe preparation and decoding tests run locally when media or
preparation code changes; Actions only check approved hashes and packaged bytes.
The TV APK must remain below 25,000,000 bytes with all recordings, and
python3 tools/check_apk_media.py PATH_TO_APK verifies exact packaged media.

## UI and input acceptance

Use [UI-CONTRACT.md](UI-CONTRACT.md) and the generated
[gap report](UI-GAPS.md). Tests must inspect actual bound views/DOM controls,
including choice values, rather than asserting only a support manifest.

For changed controls, exercise Arabic/RTL and English/LTR, draft versus saved
state, OK/Apply, Back cancellation, re-entry and return focus. Numeric controls use
core editor kinds, steps and bounds; ordinary numbers stay inline in their row.
Choice widgets follow `choiceEditor`; toggles use switches. Check shared theme
surfaces, the next-prayer outline and prayer-cell corner limits in rendered UI.
Translate physical horizontal keys for RTL once at the shell boundary.

Navigation changes check grid/header edges, Home's top controls and All apps,
and focus after closing a child page. Sizing/layout changes check 80–150% in 10%
steps, focused-row visibility, joined Arabic, clipping, Home/mihrab separation and
unscaled video. Keep pointer behavior working where the shell supports it.

Android's [instrumentation guide](ANDROID-TV.md#native-runtime-check)
documents the existing `prayer`, `ui`, `size`, `size-windows`, `online` and
`online-network` modes. Choose affected modes on an explicitly selected device or
emulator. `size-windows` requires an isolated owned API 29+ emulator and temporarily
changes accessibility/screensaver system settings; do not use it as a routine
physical-TV check. The real-network mode is separate from deterministic fixtures.

For preview navigation, check repeated screen changes without URL changes, browser
Back and return focus, settings Apply/cancel and re-entry, direct screen links and
language switching. The preview route/lifecycle regressions check route boundaries
and cleanup when a screen is replaced. Check the page at a narrow viewport too.

## Platform and device acceptance

The new Miqat identity uses a fresh installation. Capture the existing TV state,
restore a working Home where needed and disable previous scheduled alerts/startup
before removing an earlier app. Check the new Home/service/permissions and re-enter
preferences. Do not call the installer recovery record an app-settings export.
For later updates within the Miqat identity, verify data preservation. Restore
only temporary test-owned changes; preserve later user changes.

Test the affected lifecycle: duplicate/expired/cancelled callbacks, stop/end/error,
suspend/reopen and cleanup. Prayer changes cover minute flooring, offsets, civil
day/DST boundaries, tomorrow's Fajr and sunrise remaining informational. Online
comparison must preserve the offline schedule/settings and handle invalid replies,
cancellation and retry. Storage/volume recovery tests must include interrupted
and failed writes, overlap and user changes during temporary ownership.

Physical remote, speaker, moving HDMI and power behavior require human observation
on the target TV. Check relevant normal playback, temporary volume/mute handling,
input restoration and return focus. Run audible/power tests sequentially with a
watcher under the [LG rules](../platforms/webos/AGENTS.md) or
[Android rules](ANDROID-INTEGRATION.md). Read-only diagnostics may proceed without a watcher.
Do not schedule a reboot, change firmware or run historical probes as routine QA.

Label screenshots by source: browser, emulator or physical TV. LG screenshot APIs
do not reliably capture the video plane; live DOM/service state cannot establish
what the owner saw or heard. A result for one TV/firmware does not certify a brand.

## Review

The Validate workflow runs core JVM/JS/Java and canonical checks, Android/installer
builds and lint, Python/Java regressions, native prayer/UI checks on an API 34
emulator, complete LG ES5/service/browser tests and packaging, and the preview
suite/build. Required checks fails if any of those jobs fails or is skipped.
There are no path filters that could leave the required status pending.

GitHub jobs use separate workspaces; generators stay sequential within each job.
CI artifacts contain the reviewed audio and expire after seven days. CI does not
provide physical-TV acceptance or run all optional instrumentation modes.
After Required checks passes on main, GitHub Pages deploys the tested preview
artifact. PRs skip deployment. See [CI.md](CI.md) for protection, website deployment,
artifacts and draft publishing.

Use the [PR template](../.github/pull_request_template.md). Record commands, outcomes,
evidence type, skipped checks and remaining risk. Keep raw private logs under state/
and concise results in [EVIDENCE.md](EVIDENCE.md).

## Companion

Run [phone checks](../companion/README.md), shared core and affected shell/preview
checks. Native UI/LAN suites use owned emulators. QR validation uses
`node companion/check-qr.cjs <cached-zxing-core-jar>`. Physical Miqat TV
approval/camera/picture/sound acceptance remains separate.

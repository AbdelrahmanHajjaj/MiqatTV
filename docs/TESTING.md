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

The default bilingual phone suite also opens the embedded Android setup guide,
checks its private activity/language/direction, verifies cancelling and incomplete
setup return without pairing, and exercises the successful setup-to-pairing result.
Completion is a native UI fixture; this does not perform an ADB installation or
prove cleanup/restore on a physical TV. The standalone installer must still build
and lint when changing `installer/runtime` or its shared source directories.

LG phone installation: `tests/test_lg_package.py` exercises bounded IPK metadata
inspection and downgrade protection on the JVM. With the companion APK/test APK
on an explicitly owned emulator, run `node companion/check-lg-install.cjs SERIAL
AVD PATH_TO_INSTALLED_WEBOS_CLI MIQAT_IPK [ADB]`. The CLI dependency supplies its
existing ssh2 test server. This fixture binds only local ports 9922/9991, verifies
the emulator/AVD identity, and exercises native SSH/key/install behavior with
ephemeral keys and a real local package. It never contacts a physical TV. Do not
reuse these fixture credentials for a TV. Physical LG installation remains a
separate owner acceptance step.

Standalone phone suites: `-e suite phone-ui` checks actual bilingual prayer views,
Save/Cancel, location confirmation and persistence. `phone-size` checks every
80–150% app size in both languages, with voice/appearance/first-use/widget edges
at the smallest/largest sizes. Run in each owned-emulator orientation and with
enlarged OS text; pass `-e orientation portrait` or `-e orientation landscape`
to assert the requested configuration. Check screenshot dimensions: changing a
rotation setting alone may not rotate an emulator. Restore the session's prior
display settings afterwards.
`phone-denied` requires exact-alarm or notification access denied. Notification
denial must prevent scheduling/playback; exact-only denial registers a disclosed
approximate alarm without starting playback. `phone-runtime` requires both permissions granted on an owned
muted emulator; it exercises actual AlarmManager delivery, foreground output,
Stop/focus loss, disabled cleanup, recovery and early/expired/duplicate rejection.
The fixture saves/restores phone settings; it does not operate a TV. Companion CI
runs UI, portrait sizing, denial and actual-delivery suites in addition to existing TV UI/LAN checks.

Phone feedback suites: `phone-location-denied` requires foreground location denied;
`phone-location-approx` uses a framework network-provider fixture with coarse-only
access and mock-location app-op on an owned emulator; `phone-location` requires
precise access plus injected London GPS and checks calibrated travel refresh.
`phone-custom` isolates preferences/files to verify migration, weekday/quiet
policy and bounded private imports. `public-release` tests release metadata,
checksums and interrupted/atomic file preservation without a network or TV.
Phone CI runs these before the existing suites. Public binary end-to-end download
still needs an actually published release; draft assets are not public downloads.

## Apple portability boundary

On Apple-silicon macOS with Xcode 16.4, use `-PmiqatApple=true` for the two core
builds and run `macosArm64Test compileKotlinIosArm64 compileKotlinIosSimulatorArm64`.
The opt-in targets keep Windows/Android/ES5 builds independent of Apple tooling.
The bounded **Apple core** workflow runs common rule tests as native macOS code
and compiles both iOS library variants. It boots no iOS simulator and does not
establish an iPhone application, signed installation or background-audio result.
Target declarations follow [Kotlin's hierarchy documentation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-hierarchy.html).
The runner/toolchain is recorded in [GitHub's macOS 15 ARM64 image](https://github.com/actions/runner-images/blob/main/images/macos/macos-15-arm64-Readme.md).


Phone pass-3 journey: `-e suite phone-journey` exercises fresh/resumable first use,
real appearance Cancel, a temporary native AppWidgetHost bind/update and the
common TV platform/preparation/source flow, plus rejecting a late cancelled job.
The widget fixture requires API 29+ and releases its test-owned host and scoped
shell binding permission in finally. No physical TV or public release is used.

Pass-4 phone journey coverage follows the single-decision/Skip guide and real
carousel selection, then ordinary Appearance Apply/Cancel. Widget checks bind
all launcher variants through AppWidgetService, configure independent instances,
verify Cancel, resize fallback/restoration, strip rendering and missing-location
updates. The phone-size suite covers their actual previews at both scale edges.
The native LAN suite accepts an older TV's retired voice baseline without a
write, rejects an unknown choice and verifies the retired option is hidden in
the actual native selector. This is compatible read behavior, not a migration
command sent to a TV. Physical launcher/OEM/TV acceptance remains separate.


Pass-7 phone journeys exercise two-page first use, a single direct appearance
preview with Apply/Cancel (including display drafts), compact visual widget
choices and new-widget Add versus existing-widget Apply. Numbered TV preparation,
release choices and LG address/passphrase share one page; invalid addresses must
preserve session-only input without networking. Voice search tests selection
from filtered results as well as unchanged Cancel/Apply behavior.

Widget picker XML layouts are the source for static fallback images. When changing
those layouts/styles, run the owned-emulator phone journey with `-e capture true`.
It renders `en-picker-{day,next,strip}.png` and Arabic counterparts under the app’s
external files directory. Inspect them, then copy English images to native
`res/drawable-nodpi/widget_preview_*.png` and Arabic to `res/drawable-ar-nodpi/`.
These images are native renders, not separately authored preview designs.

Phone fidelity checks must inspect rendered names/time bounds as well as values;
zero-width labels can retain correct text. The phone UI suite also checks stable
pre-show dialog placement, day-override expansion and direct calendar navigation.
Hosted widget checks cover initial previews, all three allocated presentations,
modern/legacy bounds and the minimum size with enlarged text. Keep app UI scale
separate from launcher text scale. Save native/reference comparisons under state/.


Pass-8 phone journeys exercise welcome/location/style resumption, a single
notification decision (including denial and explicit opt-out preservation),
country/city detection and advanced-dialog Cancel. Phone UI covers direct weekday
choices, independent day overrides, master disabling, accessible percent edits,
and Hijri month/day selection against actual calculated times. The runtime
suite checks the selected recording's ending preview, including retained legacy
seconds caps. Short-tone/vibration channels preserve Android-owned user edits.

The optional `-e suite phone-ui -e map-network true -e capture true` check loads
real OSM tiles only after a simulated explicit Load map tap, using public Cairo
fixture coordinates. It checks native square geometry, restricted WebView access
and provider failures; inspect the resulting map capture for route/markers and
attribution. Ordinary CI phone UI uses no map network request.

Phone settings redraw checks retain the window container and inspect scroll on
its first drawn frame during repeated number edits and saving. Keep weekdays
on one row with complete targets when they fit, and preserve narrow scrolling.
Next-widget checks measure occupied height, canonical fonts and unclipped core
text at minimum bounds; a short landscape voice picker must keep its preview,
recording row and Apply reachable with enlarged text.


Phone background location: on an owned emulator, `phone-background-denied` checks
actual opt-in disclosure Cancel, manual fallback and absence of scheduled work.
Grant coarse/fine/background location for `phone-background-prepare`; it checks
real switch enable/disable, saves a typed private preference fixture and schedules
the Cairo fixture. End instrumentation, run `am kill tv.miqat.companion` (not
force-stop), verify no app PID, inject London GPS (`emu geo fix -0.1278 51.5074`),
and force `cmd jobscheduler run -f tv.miqat.companion 41703`. Allow the bounded
20-second request to finish, then run `phone-background-verify`. It checks
closed-process persistence, unchanged prayer calibration, stale/manual/off-on
rejection and restores preferences in finally. A failed pre-verify run leaves
its private fixture for verification/restoration, so do not start a second prepare.
A forced emulator job proves the closed-process path, not OEM timing or Doze latency.
The bilingual phone UI suite checks the exact canonical verse, in-card date/city,
actual event calendar links, timezone/Hijri correction and ordinary dates without
an empty event row. Migrations must keep the new background opt-in false.


`phone-calendar-return` is the short native regression for browsing days, then
closing by Done, actual Android Back or cancellation. Each path must expose today's
full hero/countdown/tools and actual prayer times. The bilingual phone UI suite
runs the same regression alongside calendar month/event/timezone tests. Calendar
browsing stays inside the popup and no longer persists another date on Home.
The hosted-widget journey checks strip date toggles independently, modern/legacy
bounds and full-height unclipped columns at small sizes and enlarged text. Refresh
native bilingual picker PNGs whenever their XML or generated sample times change.

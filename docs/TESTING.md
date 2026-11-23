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
| Android TV setup in Miqat Phone / PC recovery | `./gradlew :app:assembleDebug :companion:assembleDebug :companion:lintDebug --no-daemon`, then root Python regressions. Exercise changed setup/recovery paths on an explicitly selected test device. |
| Media preparation | `node tools/prepare-adhans.test.mjs` (FFmpeg/ffprobe required); `node tools/prepare-adhans.mjs --check`; check the affected platform packages and audition any changed religious wording/cut. |

Android and LG builds use the 13 adhan and 12 announcement clips in shared/media/.
Check the complete set with sha256sum --check shared/media/SHA256SUMS and run
node tools/prepare-adhans.mjs --check. Original/review audio remains private under
state/. FFmpeg/ffprobe preparation and decoding tests run locally when media or
preparation code changes; Actions only check approved hashes and packaged bytes.
The TV APK must remain below 25,000,000 bytes with all recordings, and
python3 tools/check_apk_media.py PATH_TO_APK verifies exact packaged media.

## UI and input acceptance

For Android release delivery, compile/lint both Android release variants and run
`python3 -S -B -m unittest discover -s tests -p test_release_signing.py -v`.
Verify real signed outputs with `tools/check_android_release.py` and the SDK
Build Tools directory, including the pinned certificate and non-debuggable
manifest. Verify the publisher rejects a real debug APK. These packaging checks
do not require an emulator or changes to a physical installation.

Use [UI-CONTRACT.md](UI-CONTRACT.md) and the generated
[gap report](UI-GAPS.md). Tests must inspect actual bound views/DOM controls,
including choice values, rather than asserting only a support manifest.

For changed controls, exercise Arabic/RTL and English/LTR, immediate
persistence, Back/re-entry and return focus. Picker cancellation must retain the
last valid saved choice; there are no ordinary row/page Apply steps. Numeric controls use
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
Back and return focus, immediate settings persistence and re-entry, direct screen links and
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

The Validate workflow selects affected PR consumers using tools/ci.py; shared,
workflow/tooling and unknown paths select all consumers. Ordinary documentation
changes run workflow/selection/whitespace checks. Packaged notices and generated
docs/UI-GAPS.md still select all consumers. Main and manual Validate runs execute
the automated suite: core JVM/JS/Java and canonical checks, Android TV builds
and lint, Python/Java regressions, complete LG ES5/service/browser tests and
packaging, preview suite/build, and Companion portable/host-JVM checks and builds.
Routine CI does not launch Android emulators or run native instrumentation.
Those suites remain available for targeted manual checks when requested.
Required checks verifies every selected job
succeeded and every unselected job skipped. Failures, cancellation, missing
selection or unexpected skips fail the gate. No workflow-level PR path filter
can leave the required status pending.

GitHub jobs use separate workspaces; generators stay sequential within each job.
CI artifacts contain the reviewed audio and expire after seven days.
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
prove cleanup/restore on a physical TV. Build and lint Miqat Phone when changing
`installer/runtime` or its source directories.

LG phone installation: `tests/test_lg_package.py` exercises bounded IPK metadata
inspection and downgrade protection on the JVM. With the companion APK/test APK
on an explicitly owned emulator, run `node companion/check-lg-install.cjs SERIAL
AVD PATH_TO_INSTALLED_WEBOS_CLI MIQAT_IPK [ADB]`. The CLI dependency supplies its
existing ssh2 test server. This fixture binds only local ports 9922/9991, verifies
the emulator/AVD identity, and exercises native SSH/key/install behavior with
ephemeral keys and a real local package. The native phone flow confirms a new
identity, persists an opted-in encrypted passphrase and reconnects from a blank
passphrase field. The transfer reports increasing percentages and moves through
verification and installation. It never contacts a physical TV. Do not
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
The fixture saves/restores phone settings; it does not operate a TV. UI, portrait
sizing, denial and actual-delivery suites are targeted manual checks, alongside
the native TV UI/LAN suites. Routine CI does not run them.

Phone radio uses `-e suite phone-radio` on an owned muted emulator. Grant
prayer notification access for the cases that schedule Adhan. The loopback
fixture covers the Home popup and shortcut, locked-screen playback,
scheduled Adhan interruption, and Android system media controls.
Use `-e case alarm-route` for Arabic and the alarm audio route,
`-e case offline` to verify bounded retries, failed-state controls and a
single-tap restart after the server recovers, or
`-e case dismiss` for paused media-card dismissal in Android Quick Settings.
Use `-e case artwork -e capture true` to verify live theme and scene updates on
the same playing session and capture the native media controls.
Run `-e case cairo` and `-e case qatar` separately for live MP3 and HLS
playback. These two checks depend on the broadcasters and internet access.

Phone feedback suites: `phone-location-denied` requires foreground location denied;
`phone-location-approx` uses a framework network-provider fixture with coarse-only
access and mock-location app-op on an owned emulator; `phone-location` requires
precise access plus injected London GPS and checks calibrated travel refresh.
`phone-custom` isolates preferences/files to verify migration, weekday/quiet
policy and bounded private imports. `public-release` tests release metadata,
checksums and interrupted/atomic file preservation without a network or TV.
Run these manually when their affected behavior needs checking. Public binary
end-to-end download still needs an actually published release; draft assets are
not public downloads.

## Apple portability boundary

On Apple-silicon macOS with Xcode 16.4, use `-PmiqatApple=true` for the two core
builds and run `macosArm64Test compileKotlinIosArm64 compileKotlinIosSimulatorArm64`.
The opt-in targets keep Windows/Android/ES5 builds independent of Apple tooling.
The bounded **Apple core** workflow runs common rule tests as native macOS code
and compiles both iOS library variants. It boots no iOS simulator and does not
establish an iPhone application, signed installation or background-audio result.
Target declarations follow [Kotlin's hierarchy documentation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-hierarchy.html).
The runner/toolchain is recorded in [GitHub's macOS 15 ARM64 image](https://github.com/actions/runner-images/blob/main/images/macos/macos-15-arm64-Readme.md).


Phone journey: `-e suite phone-journey` exercises fresh/resumable first use,
immediate appearance persistence, a temporary native AppWidgetHost bind/update and the
common TV platform/preparation/source flow, plus rejecting a late cancelled job.
The widget fixture requires API 29+ and releases its test-owned host and scoped
shell binding permission in finally. No physical TV or public release is used.

Pass-4 phone journey coverage follows the single-decision/Skip guide and real
carousel selection and immediate Appearance saving. Widget checks bind
all launcher variants through AppWidgetService, configure independent instances,
verify retained settings after Back, resize fallback/restoration, strip rendering and missing-location
updates. The phone-size suite covers their actual previews at both scale edges.
The native LAN suite accepts an older TV's retired voice baseline without a
write, rejects an unknown choice and verifies the retired option is hidden in
the actual native selector. This is compatible read behavior, not a migration
command sent to a TV. Physical launcher/OEM/TV acceptance remains separate.


Pass-7 phone journeys exercise two-page first use, a single direct appearance
preview with immediate display saving, compact visual widget
choices and new-widget Add versus existing-widget Update. Numbered TV preparation,
release choices and LG address/passphrase share one page; invalid addresses must
preserve session-only input without networking. Voice search tests selection
from filtered results as well as unchanged Cancel/Apply behavior.

Widget picker XML layouts are the source for static fallback images. When changing
those layouts/styles, run the owned-emulator phone journey with `-e capture true`.
It renders `en-picker-{day,next,strip,circle}.png` and Arabic counterparts under the app’s
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
The full phone UI suite also swipes the grid and timetable in both languages
and observes Home's seconds countdown advancing.

`phone-fasting` checks Hijri observance ranges and bilingual Settings controls on
an owned emulator. It verifies that a day-before reminder arms on its own, follows
the chosen local time and expires at midnight. It claims all three reminders at
simulated due times, checks their Android notification channel and confirms
cancellation when the last fasting choice is turned off. Pass `-e capture true`
to capture the actual notification shade. Portable tests cover overlap, fasting
exclusions and the Monday/Thursday option.

The hosted-widget journey checks strip date toggles independently, modern/legacy
bounds and full-height unclipped columns at small sizes and enlarged text. Refresh
native bilingual picker PNGs whenever their XML or generated sample times change.

Strip border regressions supply the renderer's clock explicitly and inspect
actual selected-column visibility, upcoming time and stroke pixels before/at
Fajr, after Dhuhr/Isha and after midnight on plain/artwork backgrounds. Overnight
Fajr must remain outlined with tomorrow's timestamp; sunrise cannot be next.


`phone-controls` is the focused native regression for immediate offset/before/after
steps, valid Arabic typing without replacing the input, invalid-value rejection,
retained restored text, cross-page and appearance persistence after Back.
The bilingual phone UI suite also runs it. Keyboard/sizing checks assert the
inline field and step buttons remain reachable without a per-control footer.
TV mock/native-LAN checks assert direct +/− saves (including the bearing dialog),
confirmed values, no lingering per-control/page appearance draft and immediate
appearance persistence after section changes. No physical TV is used by those fixtures.

Widget journeys check persistence before Android Add completion and after Back,
with selected placed widgets updating independently. Appearance has no page-level
Apply/Cancel; custom-palette dialogs still validate the complete value before saving.

Voice-sheet checks require device audio selection to stay visible before selecting
Custom audio, including compact landscape and enlarged text. The private custom
audio fixture imports distinct default/Fajr files and checks independent slots,
inherited defaults, malformed input, interrupted copies and oversize rejection.


0.5.9: `phone-dialog` measures actual sheet position/width across entrance frames
and checks the configured transform for horizontal movement. It first failed at
x=27,width=1026, then passes x=0,width=1080 after dialog initialization precedes
window sizing. Bilingual phone UI runs it alongside actual named-library search,
selection/re-entry and exact restoration of existing private metadata/files.
`phone-tests` requires notification/exact access on an owned muted emulator. It
checks real 30-second notification and Adhan delivery, cancellation, early/stale/
reboot/duplicate rejection and unchanged real settings/claims/schedule. The runtime
suite includes these checks, a 20-second audition fixture (past the former 12s cap),
and recording-metadata-driven percentage cutoff from position zero. Fixture caps
never change the product's complete Listen or percentage previews.


`phone-audio-route` checks the actual active player and media-session audio
attributes: media by default, alarm only after opting in, then media again after
opting out. It drives the real bilingual toggle and checks immediate persistence
and Activity volume-key routing. The phone runtime suite includes it; storage
fixtures check schema-6 migration preserves every field with alarm sound off.


The audio-route fixture starts the actual selected stream at 1, then requires a
70% Adhan level to set the maximum-based index (11/15 for media, 5/7 for alarm).
It checks both routes, pause/resume, zero, live 50% edits, manual volume changes
and restoration. Persisted recovery covers owned, user-changed and invalid
records. The fixture restores stream levels/settings and runs from a visible
activity because Android 17 restricts background volume APIs, including shell
media-session commands. The normal runtime suite includes this regression.


`phone-presentation` (`-e case home|dismiss|screen|actions`, default all) drives actual
scheduled playback. It checks the full-width card band, accessible canonical
control, changing native pixels with motion enabled and stable pixels with
reduced motion. The fixture temporarily enables/restores the owned emulator's
animator scale. It targets the SystemUI notification row through the accessibility
tree, requires the distinct prayer's actual dismiss action, invokes it and
requires audio to stop and the original media volume to return. OEM-specific
notification gestures are outside automation scope by owner instruction. It also requires
the opted-in foreground screen, even when lock-screen access is denied. On
Android 13+ it verifies automatically granted core-alarm permission. Full runtime
includes these checks; no system restriction is disabled.


Native TV header navigation must wait for loaded tiles and take its readiness
check and key assertions in one UI-thread snapshot; a queued catalog refresh can
otherwise clear the list between checks. Run the prayer/UI sequence used by CI
when validating a fresh installation, and require instrumentation code -1 with
no preference-restoration warning. Native notification checks expand only a
collapsed header: Android 14's row expansion action can toggle an already-
expanded alert closed. The focused `phone-presentation -e case actions` check
exercises the actual visible notification Stop button.

Notification polish: `phone-presentation -e case actions` imports a named private
recording, verifies that name in notification/session metadata and the visible
expanded view, then clicks the actual Pause, Play and Stop icons. `actions-ar`
exercises Arabic direction and labels. Existing private settings, names and files
are restored. Inspect native light/dark renders for contrast, wrapping and a
single progress bar. The compact view remains Android's system template.
The notification small-icon regression requires the Miqat mihrab; inspect the
separate prayer glyph in the expanded heading so app identity stays consistent.

`phone-setup-location` is the focused guided-location fixture. With foreground
access denied it handles the actual automatic permission request, checks manual
fallback and background-disclosure cancellation. With foreground/background
access granted and the test-only mock-location app-op, it seeds a real framework
GPS provider, checks automatic field filling and an explicit update opt-in, then
selects a city manually and confirms. No background job may exist before
confirmation. Returning to the location step cannot repeat detection/permission.
Preferences, language and the test provider are restored. Ordinary journey tests
also resolve the new automatic permission request before continuing manual setup.

`widget-small` binds every provider through AppWidgetService and checks its
40dp resize minimum and Next prayer's 1×1 cell target. It renders 40/56/80dp tiles
in English/Arabic at system text scales 1×/2× and widget scales 100%/120%, using
modern size maps and legacy bounds. Require full, unclipped prayer/time text,
accessible full time, a readable missing-location state and restored detail after
growing the widget. Native `*-picker-next-small.png` captures are the source for
the Next prayer fallback preview PNGs. Physical launcher grid behavior is separate.

The same focused suite checks wide one-row and narrow/tall bounds, a prominent
real Hijri date, full countdown text and pixels inside a 28dp launcher corner
outline for every shape. Reapply successive square/wide RemoteViews to the same
host view so resizing cannot retain the previous prayer layout. Inspect native
English/Arabic renders; these checks do not replace Samsung launcher acceptance.

The hosted multi-widget journey distinguishes Add during first configuration
from Update on re-entry, retaining immediate saves and independent instances.
The CI runtime verifies SystemUI dismissibility and actual dismissal cleanup;
it does not prescribe a touch gesture for different Android/OEM implementations.

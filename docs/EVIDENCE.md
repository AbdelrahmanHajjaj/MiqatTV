# Validation evidence

## One-cell widgets — 22 November 2026

Phone 0.5.15/code 31 declares 40dp resize minima on all three providers and a 1×1
default for Next prayer. Small bounds hide metadata/decorative icons and fit the
next prayer/time; narrow clocks omit AM/PM visually while retaining full accessible
time. Larger bounds restore detail without changing stored preferences. Picker
and app previews now represent the one-cell starting layout.

Owned API 34: bind every provider through the native AppWidget service; verify
reported minima and 1×1 target. Render 40/56/80dp bounds in English/Arabic at
system font scales 1/2 and widget scales 100/120, through exact size maps and the
legacy min/max path. Unclipped text, full accessible time, missing location,
style preservation and larger-size recovery pass. Inspect actual native captures;
English/Arabic Next picker PNGs were refreshed from those native XML renders.
These are emulator/content-bounds checks, not physical OEM launcher acceptance.

Build/test compilation and lint pass (0 errors, 40 existing warnings), generator
tests pass 12/12 and canonical checks pass. The signer and all 25 recordings remain
verified. APK: 35,368,113 bytes; SHA256
`43589D63BDDCC40D8B96AD4D52B2CAC065945EDB19F1607BE9131DD57A6B22D5`.
Full CI remains deferred during feedback. Launcher cell sizes/cached provider
metadata may require removing/re-adding existing widgets; owner testing remains.

## Status-bar identity — 22 November 2026

Owner reports the remaining 0.5.13 changes working and continues daily driving.
Phone 0.5.14/code 30 restores the Miqat mihrab as the playback notification's
small status-bar/header icon; the canonical prayer glyph stays beside the
expanded heading. No location/setup or playback behavior changed.

Owned muted API 34: focused native small-icon assertion, actual notification
Pause/Play/Stop and rendered mihrab/prayer-heading separation pass. Build/test
compilation and lint pass (0 errors, 40 existing warnings). APK entry comparison
with 0.5.13 changes only the manifest/version and playback dex; no entries are
added or removed. All 25 recordings and the unchanged signer are verified.
Review APK: 35,114,828 bytes; SHA256
`77C5C14316EEB213E017504A3B7DC006C3F12CD4C5F711438F1EE9138DBEFBAE`.
Full CI remains deferred during feedback; new-version OEM/daily acceptance stays
with the owner. Private captures/logs remain ignored under state/.

## Guided location and prayer icons — 22 November 2026

Phone 0.5.13/code 29 reuses the canonical prayer glyph in the compact notification
header and beside its expanded title, respecting RTL and dark theme tint. The
guided location step requests foreground access/detection once after Welcome,
retains manual selection after denial, and includes the existing optional
background consent control. Unconfirmed setup cannot schedule a background job.

Owned muted API 34: native denied-permission/manual fallback and disclosure
Cancel pass; the granted path receives a real framework GPS fixture, fills the
country/city, retains opt-in through manual city choice, and enables background
scheduling only after confirmation. Back/rerender does not re-request detection.
Arabic/dark notification rendering and actual Pause/Play/Stop controls pass.
Inspect the retained private captures under state/. Fixtures restore private
preferences/language and remove their mock provider; no physical device operated.

Build/test compilation and lint pass (0 errors, 40 existing warnings); companion
generator tests pass 12/12. The icon source and TV/LG/preview bindings are unchanged.
All 25 packaged recordings and the unchanged signer are verified. Review APK:
35,368,472 bytes; SHA256
`809191886FC3AC57A459B8F2E8E8FFA9DA07B3991B4FB0D5C499E54CC59246DA`.

Owner reports most device tests working and will daily-drive this candidate.
Full CI is deferred during this feedback loop, to be checked before merge/release;
the new version's OEM permission journeys and natural background cadence remain
for owner acceptance. PR #9 stays draft; no merge, release or deployment.

## Notification polish — 22 November 2026

Owner reports that swiping 0.5.11 on physical hardware dismisses the notification
and stops Adhan. The previous CI-only swipe failure remains separate evidence;
the owner requested hardware handoff instead of more emulator investigation.

Phone 0.5.12/code 28 removes notification artwork, displays the playing recording's
saved library name and retains the prayer/time heading. Compare the former large
thumbnail/text-action layout with the decorated expanded icon layout: one native
progress bar, wrapping recording label and accessible Pause/Play/Stop controls.
The compact template, MediaSession, delete intent and audio routing remain.

Owned muted API 34: real custom recording playback and visible library name;
actual SystemUI Pause, Play and Stop; English/light and Arabic/dark native renders
pass. Inspection caught duplicate progress and dark icon contrast/RTL alignment;
these were corrected and the affected render/control check repeated. A focused
real swipe still stops output and restores volume. The private fixture restores
settings, file names/content identities, library metadata and system theme.
Raw logs/captures remain under ignored state/.

Build/test compilation and lint pass (0 errors, 40 existing warnings).
Canonical catalog/source checks, catalog tests, companion generator 12/12 and
shared validation checks pass. The additive canonical Pause icon is consumed by
the phone notification; existing TV/LG/preview controls are unchanged. No broad
device suites or physical deployment were repeated for this presentation change.

Review APK: 35,366,169 bytes, SHA256
`EE9BC8542264A7BB22E8D0E87452DAD2D51035F3D3B06165FA4CA30CB959889B`.
All 25 exact approved recordings and the unchanged signer are verified. Native
emulator rendering does not establish manufacturer notification layouts or the
new version's physical-phone acceptance. Keep draft PR #9 for owner review.

## Cross-version native validation — 22 November 2026

Initial CI for d250feb found an empty TV catalog snapshot and a phone swipe
expectation failure on API 34. The TV fixture now waits for loaded tiles and runs
its navigation assertions in the same UI-thread snapshot. The swipe fixture uses
a distinct prayer and waits for stable notification bounds. Repeated isolated
Android 14 swipes passed with unchanged app bytes. The full Android 14 sequence
also exposed the fixture toggling an already-expanded notification closed; it
now checks the header's expansion state and supports content-description labels.
All navigation and actual notification-action assertions remain enabled.

Owned isolated API 34: bilingual phone UI, 80–150% portrait sizing, real background
swipe with original-volume restoration, complete alarm/playback/30-second tests,
notification Stop and lock-screen gestures pass. The TV shell prayer/ICU and full
UI contract pass at CI's landscape bounds, with explicit instrumentation code -1
and no preference-restoration warning. An initial standalone fresh TV UI run
materialized ui_scale; it was not counted as a clean pass. The same warning was
confirmed in CI despite its success marker. Pairing checks now restore the
original value or absence, and the preference guard removes the success marker
on failure. Two fresh-state native UI runs pass the preference guard; local
wrappers requesting a raw result code without `am instrument -r` failed their
post-check because Android's pretty output omits it, not because the UI failed.
These are phone-image emulator checks, not physical
TV acceptance. Runtime restrictions remain enabled; fixtures restore animator
settings. Raw logs and SDK/device details stay ignored under state/.

Follow-up changes are tests/docs only. The 0.5.11/code 27 APK and its recorded
SHA256 remain unchanged. Exact-head CI is tracked in draft PR #9; no release,
merge or physical deployment.

CI then exposed a library search assertion reading the previous visible child
after the adapter changed from one result to another. It now waits for the
expected rendered name and visible bounds in the same UI snapshot. The full
bilingual phone UI suite passes on API 34 without capture delays; production
picker code and APK bytes are unchanged.

The complete local runtime sequence then caught a volume assertion observing
the pause flag before its main-thread callback finished restoring volume. The
fixture now waits for UI idle after pause; its restoration assertion is retained.
The full API 34 alarm/playback suite passes, including actual background swipe,
absolute media/alarm levels, notification Stop and 30-second tests.

Exact-head CI still rejected the shell swipe on API 34 after all preceding phone
suites passed. Locally it also passed with CI's animation settings, so the CI
cause remains unproven. The fixture now waits for a settled full notification
row, asserts SystemUI offers ACTION_DISMISS, and injects one synchronous native
touchscreen gesture. There is no retry or dismiss-action fallback. The focused
case passes locally; complete runtime/CI results are tracked in draft PR #9.

## Adhan playback refinements — 21 November 2026

Phone **0.5.11/code 27** targets the selected media/alarm stream's maximum rather
than multiplying its previous level. The native regression failed at 1/15 instead
of the expected 11/15 for 70%, then passed both streams, pause/resume, live edits,
zero, manual-change preservation and private persisted recovery. Core JVM tests
cover step rounding and invalid bounds. Volume is restored before relinquishing
normal playback; process-death recovery waits until the next Miqat entry.

Home has a full-width playing band inside the card, replacing the detached
square Stop. Native captured pixels change with motion enabled and remain stable
with reduced motion. The band opens playback controls through a canonical action
and a 48dp target. Artwork/frame clipping and native Home/screen renders were
inspected. The notification keeps progress, MediaSession and Pause/Stop in an
ordinary alert, allowing a real sideways swipe to stop output on API 37. An
actual swipe while another app is active also restores the prior media volume.

The opted-in screen opens directly while Miqat is foreground, including with
lock-screen intent access denied. Android still owns presentation over other
apps and the lock screen: unlocked devices normally get heads-up alerts.
Android 13+ core prayer alarms now use the automatically granted USE_EXACT_ALARM
permission; SCHEDULE_EXACT_ALARM is limited to Android 12. Native package/timing
checks and background alarm delivery pass with the old manual timing app-op
denied. Notification permission and voluntary prayer opt-outs are preserved.
See Android's [timing permissions](https://developer.android.com/develop/background-work/services/alarms)
and [time-sensitive presentation](https://developer.android.com/develop/ui/views/notifications/time-sensitive).

Owned muted read-only API 37: complete runtime, actual 30-second tests, media
focus/Stop/pause/resume, native notification button interaction, lock-screen
screen/gestures and bilingual UI pass. Motion fixtures enable/restore the owned
emulator's original animator scale. The old missing-wave, swipe and foreground-
screen regressions all failed before implementation. Final focused background
swipe and UI checks pass. No Android restriction was disabled. Canonical checks,
generator **12/12**, production/test build and lint pass: **0 errors, 41 existing
warnings**. Exact-head CI is tracked in draft PR #9. Private logs/captures stay
ignored. Main 26a32c7 remains merged; no TV shell policy changed.

Signed export: `state/builds/companion/Miqat-latest.apk`, **35,364,670 bytes**,
SHA256 `010F4CB796515CD711A2737A526C0178E31BF5CEF5A451A001EC286235FA5B0E`.
All 25 exact shipped recordings and the original signing identity are verified.
Physical/OEM behavior and older Android swipe behavior remain unverified. No
release, PR merge or physical deployment.

## Adhan volume routing — 21 November 2026

Phone **0.5.10/code 26** uses media volume by default. The optional sentence-case
**Use alarm sound** toggle selects alarm volume. Player/focus attributes, media
session and Activity volume buttons agree. Switching mode stops existing output;
the next session uses the new stream. Short-tone notification channels remain
unchanged. Schema 7 preserves all v1–v6 preferences with alarm sound off.

The focused native regression failed against the old live player (expected media
usage 1, received alarm), then passed media → alarm → media in English and Arabic.
It checks actual playback configurations, session attributes, immediate persisted
toggle state and volume-button routing. Bilingual renders were inspected. Storage
migration/custom-library, bilingual UI and complete actual playback/30-second tests pass on the
owned muted read-only API 37 emulator. Canonical checks and generator **12/12**
pass. Production/test build and lint pass: **0 errors, 41 existing warnings**.
Exact-head CI is tracked in draft PR #9; private logs/captures stay ignored.

Signed export: `state/builds/companion/Miqat-latest.apk`, **35,161,895 bytes**,
SHA256 `D65D3B2F6FA5A3F715C0C678B4689594A7EB4384F1187540E5780C0CE8E57834`.
All 25 exact shipped recordings and the signing identity are verified. Main
26a32c7 remains merged. Physical/OEM volume behavior remains unverified; no
release, PR merge or physical deployment.

## Named audio library and delayed phone tests — 21 November 2026

Review **0.5.9/code 25** retains multiple named private imports, including old
recordings and independent prayer selections. Full Listen starts at zero;
percentage preview starts at zero and stops at its selected fraction. Adhan and
notification tests wait 30 seconds, support cancellation and leave real settings
and schedules untouched. The redundant motion control is hidden. Native frame
capture reproduced the sheet's 27px first-frame inset; initializing the dialog
before sizing fixes it, with all 16 captured frames stable.

Latest main **26a32c7** was merged normally in **4f6ca5c** before the PR push.
All 13 retained Adhan files exactly match its approved cleaned bytes; the retired
voice remains excluded. All 25 shipped media hashes pass, totaling 16,464,111
bytes for Adhan. Main's reusable affected-CI and artifact policy are preserved.

Owned muted read-only API 37: private library/migration, actual alarm/media/Stop/
focus, full audition beyond 12 seconds, metadata-based percentage cutoff, actual
30-second notification/Adhan tests, early/stale/duplicate/reboot/cancel rejection,
bilingual UI and dialog regressions pass. First-use/widget journey, native TV
bindings and pinned-TLS LAN pass. English/Arabic sizing at 80–150% passes normal
portrait and font 1.3 portrait/landscape. Library and test UI renders inspected.
No physical device was operated.

Production/test build and lint pass: **0 errors, 41 existing warnings**.
Canonical generator **12/12**, shared/design checks, media preparation regressions,
CI selection tests and actionlint pass. Local Node is 26.13.1; CI uses Node 24.
Exact-head CI is tracked by draft PR #9. Private logs/captures remain ignored.

Signed export: `state/builds/companion/Miqat-latest.apk`, **35,369,692 bytes**,
SHA256 `06FA767B9AD5E62DB75FBBB9D76450C8CC3BF8EAB821D1F21D7FC8508EE6D0FE`.
Signing identity is unchanged. Physical provider selection and OEM background/
notification reliability remain unverified. No release or physical deployment.

## Direct device adhan selection — 21 November 2026

Phone review **0.5.8/code 24** exposes **Choose audio from device** in every
phone voice picker without first selecting Custom audio. Android document
selection and bounded private import already existed and remain the single path
for default and per-prayer recordings. Existing files remain reusable. No new
storage permission, transport, dependency or shipped recording is introduced.

Owned muted read-only API 37: private import/migration checks and bilingual phone
UI pass. Distinct default/Fajr imports preserve independent slots and other
prayers' inheritance; malformed audio, interrupted/oversize copies retain the
previous recording. The always-visible action is measured at app scales 80–150%,
normal portrait and font 1.3 portrait/landscape. All pass after reserving a device-
action row and using the compact sheet earlier on short windows. English/Arabic
normal and compact voice renders were inspected. Actual provider-file selection
on a physical phone remains unverified.

Production/test builds and lint pass: **0 errors, 41 existing warnings**.
Generator 11/11 and design handlers pass. All 25 exact shipped recordings and
notices are preserved; signing identity is unchanged. Exact-head CI is tracked
in draft PR #9. Private captures/logs remain under ignored state/. No merge,
release or physical deployment.

Latest review export: `state/builds/companion/Miqat-latest.apk`, **35,342,955 bytes**,
SHA256 `7A71C1EC5B4B55F497AD63C82747D856BE656633C1B9625F49EA8B60CA699803`.


## Immediate appearance and widgets — 21 November 2026

Phone review **0.5.7/code 23** follows the owner's clarification that full-page
settings must save immediately too. Phone appearance/display and widget controls
persist valid changes directly; Back retains them. Placed widgets update on each
change; opacity commits when dragging ends. TV appearance selections use the
existing confirmed save, offline, conflict and uncertain-write protections. Page
Apply/Cancel and obsolete appearance draft state are removed. Android widget Add
completion and validated complete-value dialogs retain their actions.

Owned muted read-only API 37: bilingual phone UI and first-use/hosted-widget
journeys pass, including persisted values before Add and after Back. TV native
bindings and pinned-TLS LAN checks pass. Both languages pass app sizing 80–150%,
normal portrait and font 1.3 portrait/landscape. Appearance/widget renders were
inspected in English and Arabic. The binding check now explicitly asserts removed
companion Apply/Cancel actions are absent; the shared TV contract remains intact.
Landscape checks scroll the appearance rail into view before measuring it.

Final production/test build and lint pass: **0 errors, 41 existing warnings**.
Generator 11/11 and all design handlers pass. All 25 exact approved recordings,
notices and signing identity are preserved. Exact-head CI is tracked in draft
PR #9. Private logs/captures stay ignored under state/. No physical phone/TV
acceptance, merge, release or physical deployment is claimed.

Latest review export: `state/builds/companion/Miqat-latest.apk`, **35,343,067 bytes**,
SHA256 `EB9CE8316BB571EE3CAA025A9EB83AF2DEF0F7AD5AFF7D933271B70C071B6761`.


## Immediate controls and Home cleanup — 21 November 2026

Phone review **0.5.6/code 22**, feature/companion-installation, draft PR #9.
Welcome omits the ad/account label; Home omits the verse reference and location
success/confirmation narration. Location setup and meaningful errors remain.
Shared phone numeric controls save each step and valid typed value immediately,
without replacing the active input. Full-page preview Apply/Cancel remains.
TV +/− controls use their existing guarded confirmed-save path; manual TV typed
entry retains Done. Unsupported LG bearing capability stays unavailable.

A focused native regression failed before the fix when switching offset/before/
after controls lost an unsaved adjustment. It now passes Arabic typing, invalid/
off-step rejection, input identity, restored input, cross-page persistence and
one-tap full-page preview Cancel. Bilingual phone UI and first-use/widget journeys,
TV native bindings and pinned-TLS LAN checks pass on an owned muted read-only
API 37 emulator. Dialog Back checks wait for actual window focus before input.
Both languages pass 80–150% app sizing in normal portrait and Android font 1.3
portrait/landscape. Actual Home, welcome and prayer-control renders were inspected.

Production/test APK and lint pass: **0 errors, 41 existing warnings**. Generator
11/11, canonical catalogs/shared checks and design handlers pass. The export
preserves all 25 exact recordings, notices and existing signing identity. Private
logs/captures remain ignored under state/. Exact-head CI is tracked in PR #9.
No physical phone/TV acceptance, merge, release or physical deployment is claimed.

Latest review export: `state/builds/companion/Miqat-latest.apk`, **35,104,834 bytes**,
SHA256 `EED139936A27A8C7835BF330F362E8668075D02FAD347C951B7692155562DDB7`.


## Strip next-prayer border — 21 November 2026

Phone review **0.5.5/code 21** includes the completed 0.5.4 refinements and the
owner's follow-up: the border marks the **next prayer, matching Home**. A visible
2dp theme-colored rounded rectangular border fills that prayer column. After
Isha, the strip highlights Fajr and uses tomorrow's actual Fajr time; sunrise
remains informational. Other widget presentations retain their existing policy.

Owned API 37 bilingual hosted-widget/first-use checks pass. A renderer clock input
lets regressions cover before/at Fajr, after Dhuhr, after Isha and after midnight.
They assert exactly one selected column, its actual upcoming timestamp, and opaque
border pixels on plain/artwork variants. Existing small-size/text/date/legacy
checks stay enabled. English/Arabic renders and refreshed strip picker fallbacks
were inspected. The 0.5.4 head **198f365** passed all three CI workflows; final
0.5.5 head checks are tracked in draft PR #9.

Production/test build and lint pass with **0 errors, 41 existing warnings**.
Recordings, notices and signing identity remain unchanged. Private captures/logs
stay ignored under state/. No physical launcher/device acceptance is claimed.

Latest review export: `state/builds/companion/Miqat-latest.apk`, **35,170,920 bytes**,
SHA256 `C5AD68050424760E2E1B2B4CD155A85A3C7B519145A70DABCC9993BE1A9FDDB3`.
No merge, release or physical deployment.


## Home/calendar/strip refinement — 21 November 2026

Phone review **0.5.4/code 20**, feature/companion-installation, draft PR #9.
The verse reference omits “excerpt”. Date browsing stays inside the Hijri popup;
Home always shows its live hero, tools and today's prayers. Fresh preferences use
12-hour time; existing saved 24-hour choices survive migration and reopening.
The prayer strip retains both enabled dates and fills its height with six columns.

A focused native regression first failed on 0.5.3 with “Closing the Hijri calendar
via done must return to the full live Home”. The root cause was the selection
callback changing the page underneath the dialog. Removing that duplicate Home
state/callback fixes Done, actual Android Back and cancellation; all three paths
now pass. Existing month/day/event, timezone and actual-prayer-time checks remain.

Owned muted read-only API 37 emulator: bilingual phone UI and hosted-widget/first-use
journeys pass. Strip regressions exercise independent date toggles, modern/legacy
bounds, 110/140/179dp scaled heights, text scales 1/1.5/2 and widget scales 100/120,
with full-height bounds and no clipped/truncated text. Rendered English/Arabic
strips show both dates and readable 12-hour times. All six native picker fallbacks
were regenerated from their XML/generated samples and inspected. App sizing
80–150% passes in both languages, normal portrait and font 1.3 portrait/landscape.

Build/test APK/lint: **0 errors, 41 existing warnings**. Generator 11/11,
canonical catalogs/shared checks and design handler checks pass. Media/signing
remain unchanged. Initial min-strip clipping and new layout/font lint findings
were fixed with a compact date line and anchored columns; assertions stayed enabled.
Raw native captures/logs stay ignored under state/. Exact-head CI is tracked in PR #9.
No physical launcher/device was operated; OEM resize behavior remains unverified.

Latest review export: `state/builds/companion/Miqat-latest.apk`, **35,284,597 bytes**,
SHA256 `4CB506888770C8E2A6D2D759E06B497C2CC54A2CA4574C20D07E474EFDD1FCEB`.
Keep the PR draft; no merge, release or physical deployment.


## Approved Home/calendar/location implementation — 21 November 2026

Phone review candidate **0.5.3/code 19**, feature/companion-installation, draft PR #9.
Both headers, in-card Hijri date/single city action, compact Location controls,
five-event calendar and explicit optional background location are implemented.
Schema 6 preserves v1–v5 preferences without expanding foreground-only consent.

Local checks: companion core `check jvmJar`, generator 11/11, catalogs/shared
validation, QR/Android-shared checks and design handler checks pass. Final native
build/test APK/lint passes with **0 errors, 41 existing warnings**. APK verification
confirms all **25 exact recordings**, **25 packaged notices/source files** and the
unchanged signing certificate.

Owned, muted, read-only API 37 emulator evidence: bilingual phone UI, actual
calendar event/day/timezone/Hijri correction, migration, one-shot precise/coarse/
denied location, in-place Home refresh, first-use/style/widget journeys and
native TV bindings pass. App sizes **80–150%** pass in both languages, normal
portrait and Android font **1.3** portrait/landscape. Rendered Home captures show
the greeting, canonical joined Arabic verse, date/city metadata and six prayers.
No physical device or TV was operated.

Background checks exercise real opt-in on/off and disclosure Cancel, permission
denial/manual fallback and absent scheduled work. After instrumentation ended,
the app process was killed and verified absent; a forced persisted job saved
injected London coordinates from a Cairo baseline. Verification checks unchanged
prayer settings, authoritative coordinates, timestamp, stale/manual/off-on
rejection and restoration. The final production APK passed this closed-process
check. A forced job proves the execution path; it does not establish Android's
natural cadence, Doze latency, reboot timing or OEM battery behavior.

Raw captures/logs and failed attempts remain ignored under state/. Initial native
assertions still expected the old external metadata/timezone layout; they now
check the approved in-card/Advanced locations. The cancelled-disclosure check
waits for actual window focus before asserting. No product assertion was weakened.
Final-head Android/LG/preview and Apple portability results are exposed by PR #9.

Latest review export: `state/builds/companion/Miqat-latest.apk`, **35,285,640 bytes**,
SHA256 `76CCAA9B6209E52A364DB6A035B650F374490C81C467ECEDC53AE9524F118810`.
Keep the PR draft; no merge, release or physical deployment is authorized here.


## Home/calendar/location design proposal — 21 November 2026

Owner follow-up: the demo now defaults to greeting plus the TV verse. Browser
inspection at 360px confirms both headers, all six prayer rows, Qibla/Widgets and
bottom navigation fit the phone frame. The handler self-check verifies the exact
canonical verse alongside the greeting and prayer card. Capture:
`state/pass11-design/home-both-headers.png`. This remains design-only.

`docs/design/phone-polish/home-calendar.html` is a new English interactive proposal,
separate from the approved revision-2 gallery. It reuses canonical artwork, icons,
fonts and the exact three TV verse lines. No Android/LG/runtime files changed.
The owner explicitly selected optional updates while the app is closed; the mock
shows the background-access decision, rather than claiming support in 0.5.2.

Browser evidence: Home date opens a combined calendar/event/timetable sheet;
selecting Eid al-Adha updates its label and illustrative times together. All six
rows fit beneath the calendar at the demonstrated 360px size. Both 360/390px
artboards have no horizontal content overflow. Ordinary days remove the Home
event row. Greeting shares the toolbar; verse/none variants are selectable.

Background denial leaves automatic updates off and the saved city unchanged.
Granting access enables simulated travel to Madinah; a manual London selection
stays a draft until Done, then stops automatic following. Revocation leaves manual
selection usable. The actual prototype event handlers are checked by the existing
`node docs/design/phone-polish/check.cjs`; all prior 125-state checks still pass.
Country/city, permission fallback, selected-day events/times and header variants
were also exercised in the in-app browser. Accepted full-view captures are under
ignored `state/pass11-design/*-overview.jpg`; incorrect clipped exports were rejected.

Research: Android's current background-access guide confirms the separate Android
11+ settings grant, an explicit decline route and inherited approximate accuracy.
Event examples are date labels, not religious rulings or push reminders. Arafah/
Ashura reference Sahih Muslim 1162b; the existing TV excerpt is Qur'an 4:103. Links
are in the proposal. Dates follow the existing Hijri calendar/adjustment, with local
month-announcement differences retained as an implementation consideration.

Limits: browser design evidence only. Prayer times and travel are fixtures; no
real GPS, permissions, background jobs, alarms or event notifications run here.
No emulator/device tests are needed for these design-only sources. Native battery,
permission, approximate-location and date-boundary behavior remain for implementation
following design review. The existing 0.5.2 review APK and its prior green CI remain.

## Recording feedback refinement — 21 November 2026

Review APK **0.5.2/code 18**, `state/builds/companion/Miqat-latest.apk`,
35,246,674 bytes; SHA-256:
`7B4582862908E186DF6779A8C607E82CDF2E551AAC2E54E4A0EA3457A406749D`.
Prior signing certificate retained. Export matches build. All 25 canonical MP3s,
25 notice/license/source files and both regenerated next-widget picker PNGs
are verified against packaged bytes. No schema, scheduling or media change.

The owner's recording shows 4+3 weekdays and settings jumps. Home day arrows
are removed; its date still opens the combined calendar/timetable. Weekdays
stay on one row, all seven 48dp targets fit the 360dp viewport, and enlarged/narrow
layouts can scroll. The window container is retained and scroll restored before
first drawing. Next widgets fill their allocated card without an inset outline;
canonical Latin/Arabic fonts follow app language independently from OS locale.
Signature remains the default and is first in both style lists; saved choices stay.

Local checks on an owned, muted API37 emulator:

- Bilingual phone UI, retained container and first-frame scroll during repeated
  +/- edits and Save pass, along with existing numeric Cancel/persistence,
  disabled controls, atomic drafts, calendar boundaries and settings themes.
- Full phone journey/hosted widgets pass: modern and legacy allocated sizes,
  independent styles, canonical icons/fonts, next-card height and 140×100 minimum
  at OS font 1/1.5/2 with real line/bounds/truncation checks. English/Arabic native
  next-picker images were rendered, inspected and packaged.
- Both languages pass 80–150% sizing in normal/enlarged-text portrait and enlarged
  text 1.3 landscape. Final container/voice changes repeat landscape successfully.
  This is viewport evidence, not physical rotation/sensor acceptance.
- Final build/test APK/lint pass: 0 errors, 41 existing warnings. Generator10/10,
  canonical catalog and shared validation pass. No dependency added.

Two genuine sizing failures were retained in private logs: the voice picker
clipped in a short landscape window at 150%, and the explicit Arabic widget font
needed more height at minimum size/text 2. The fixes adapt the picker to font-scaled
height and remove minimum-card padding; original reachability/clipping assertions
remain. Initial local SDK selection/import errors and new nested-weight warnings
were corrected before the final build. Draft PR #9 records final-head CI links.
Native review/captures and the supplied recording stay ignored under state/.

The narrow portrait/native host are emulator evidence. Owner-phone perceived
fluidity, OEM launcher resize/pin behavior and physical TV/sound/sensor acceptance
remain separate. TV artifacts and policy were unchanged in this refinement.
No merge, release or physical deployment.

## Phone fidelity refinement — 21 November 2026

Review APK **0.5.1/code 17**, `state/builds/companion/Miqat-latest.apk`,
35,129,203 bytes; SHA-256:
`6F459FE43F8C01E6CDC23E1478C77037847BFA0629E6D49F9D5CAE3DBAF904C0`.
Prior signing certificate retained. Export matches build; all 25 recordings,
25 notices/license/source files and six reviewed native widget picker PNGs match
packaged bytes. Schema 5 preserves v1–v4 values and explicit background opt-out.

Current-run native captures were compared with the approved Home, prayer and
widget references. This exposed zero-width prayer names that value-only tests
missed; explicit wrap-content times and positive-width/non-overlap assertions now
protect both Home and the calendar. Screenshots also verify grouped rows,
spaced round weekday marks, compact numeric editors, joined Arabic and portrait
Signature/Dusk/Aurora compositions. Existing artwork masters are unchanged.

Owned API 37 phone checks pass: bilingual UI, pointer-driven date arrows,
immediate calendar timetable changes, dialog placement before show, inline day
overrides, appearance cancellation, migration, complete setup/hosted widgets and
native companion TV bindings. Widget checks cover API 31+ exact size maps and
legacy min/max bounds, three responsive presentations, independent styles,
initial preview selection, icons/highlights and measured 140×100 text at font
scales 1/1.5/2. Six native English/Arabic picker images were regenerated and read.

App scale 80–150% passes in both languages: normal portrait, Android text scale
1.3 portrait and landscape. The landscape run used a verified 2400×1080 emulator
viewport; rotation-setting commands had retained portrait and that failed fixture
was preserved. This establishes layout behavior, not a physical rotation sensor.
An earlier test expected the previous widget font size; its launcher/app scaling
assertion now uses the revised 12sp timetable style. Required checks were kept.

Build/test APK/lint: 0 errors, 41 existing warnings. Companion generation 10/10,
canonical/shared validators and preview data/tests/build pass. LG ES5/unit/TLS
and full bilingual browser 80–150% checks pass. Its first new outline assertion
caught a generated CSS border-width reset; the canonical semantic rule now wins.
LG **0.2.29** review IPK is 19,437,994 bytes, SHA-256
`B63AE27135BD5E18D3F94E209A03E64036781AE1964ED128B8F04CEC4CF08123`;
all 25 media files and app/service MIT notices match source. No deployment.

PR #9 records final-head CI. Raw evidence remains ignored under state/. Native
launcher pin confirmation, physical old-phone/OEM behavior, compass/sound and TV
remote/picture/power acceptance remain for owner review. Widget remaining time is
a snapshot at existing refreshes. No new audio/scheduling policy or dependency.

## Approved phone implementation — 21 November 2026

Review APK **0.5.0/code 16**, primary checkout
`state/builds/companion/Miqat-latest.apk`, 35,121,868 bytes; SHA-256:
`067203783FCC52012580B6C23A7366E1B361A57787869CEE5A8D7619B2C56EC0`.
Export matches the built APK; prior signing certificate retained. All 25 exact
recordings and 25 license/notice/source files match canonical packaged bytes.
The transparent mark is the approved canonical asset; no dependency was added.

Build/test APK/lint pass: 0 errors, 41 warnings. Companion portable rules
`check jvmJar`, generator 10/10, catalog/shared validators, QR and Android-shared
checks pass. The approved English gallery has 125 states across 15 route families;
it remains separate design evidence, not native acceptance.

Owned API34 emulator evidence:

- Bilingual phone UI, atomic Advanced coordinates/timezone Apply/Cancel, inline
  numbers, visible prayer/day choices, disabled controls, appearance/voice drafts,
  native widgets, calendar month/day/year/correction and selected-date restoration
  pass. Selected dates use all six actual engine results, never mock times.
- Precise and coarse one-shot location, stale/denied fixes, single confirmation,
  Home routing, unmapped coordinates and calibration-preserving travel refresh
  pass. Test-only framework GPS/network fixtures isolate emulator position.
- Supported Android/LG setup, native TV bindings, media rows, numeric drafts and
  capability boundaries pass. Pin-unavailable launcher fallback remains covered.
- App scale 80–150% passes in both languages: normal portrait and Android text
  scale 1.3 in portrait/landscape. Circular weekday text and minimum touch bounds
  pass after correcting compact-control padding. Portrait/default text restored.
- Real foreground OSM tiles, route/endpoints/label and full attribution were
  rendered and inspected. Map SVG parsing/geometry and square viewport pass.
  Welcome, Arabic location and full-page style renders were also inspected.
- Final bilingual fresh-use, Explore first/resume, existing opt-out and actual
  notification denial pass. Exact-access denial registers a disclosed approximate
  event; notification denial schedules none. Actual alarm delivery, retained
  custom 30-second cutoff, percentage ending preview, navigation cleanup,
  twelve-second audition, pause/Stop, focus interruption, silent/vibration
  channels and duplicate/early/expired rejection pass. System alarm volume is
  preserved. Headless emulator output was muted; this is not an audible audition.

Earlier failures were retained under ignored state/: reused-task instrumentation
launches, an unowned emulator GPS position and a released test activity reference.
Fixtures now launch bounded isolated activities and own their mock providers;
production detection assertions were not weakened. Final location-dialog review
also found and fixed early timezone saving with a bilingual regression.

No physical sensor accuracy, audible output, OEM background reliability, launcher
pin confirmation or TV remote/picture/sound/power acceptance is claimed. Timing
access denial uses a disclosed inexact fallback; Android may delay it beyond the
existing two-minute stale-event limit. No merge, release or physical deployment.
Raw logs, screenshots and emulator ownership receipts remain ignored under state/.

## Phone design review after 0.4.6 — 20 November 2026

Design-only gallery: docs/design/phone-polish/index.html; research and proposal
boundaries are linked there. `node --check docs/design/phone-polish/mockups.js`
and `node docs/design/phone-polish/check.cjs` pass: 122 unique artboards, all
15 current route families, internal destinations, weekday controls and setup
invariants. Browser checks confirm canonical fonts/art/icons load, no empty icons
or missing mock destinations and no horizontal content overflow. At an actual
391px CSS viewport the review page has no horizontal overflow.

Rendered welcome/location/style, calculation and prayer controls inspected.
Location → notification denial → style navigation, full-page Dusk palette,
weekday/stepper interaction, country search, widget-only Frost theme/shape,
background opacity without fading text and layout selection pass as browser
mocks. Captures remain under ignored state/phone-polish-*.
No new native acceptance, scheduler/migration tests, physical operation, build
or APK. The existing 0.4.6 artifact is unchanged. Review scope: PHONE-PASS8.md.

## Phone interaction refinement — 20 November 2026

Review APK **0.4.6/code 15**, `state/builds/companion/Miqat-latest.apk`,
34,588,278 bytes; SHA-256:
`C8D575C784841FD9E02C1668C25C4A6F7BFC8348B400F5B8754D68F0081FDD4E`.
Prior signing identity retained. All 25 recordings and 25 packaged notices match
canonical bytes; exact portrait signature and six widget-picker PNGs retained.

Build/test APK/lint pass (0 errors, 42 existing warnings); generator 8/8,
canonical catalogs and shared validation pass. Owned API34 emulator passes:
two-page bilingual first use and completion; staged alert/location confirmation;
one live appearance preview, full-page Apply/Cancel; filtered voice selection;
independent native hosted widgets, opacity/shape/Hijri/cancel/resize; combined
numbered Android/LG preparation, release/local sources and invalid LG host with
private input retained in memory. New-widget Add uses Android's invalid-ID
constant; manual action shows the correct Widgets fallback on this launcher,
which does not support pin requests. Actual launcher pin confirmation remains
unverified. No physical TV connection or deployment performed.

80–150% app sizing passes in portrait and landscape with Android text at 1.3.
Representative native English/Arabic normal and enlarged renders inspected;
normal review screenshots exported under `state/builds/companion/screenshots/`.
Logs/captures remain in ignored `state/ux7-*`. Final delivery journey ran against
the exact exported APK. Playback policy/core/TV rendering unchanged in this pass;
final-head CI and actual OEM/TV acceptance remain distinct evidence on PR #9.

## Portrait first use and widget customization — 19 November 2026

Review APK **0.4.5/code 14**, `state/builds/companion/Miqat-latest.apk`,
34,464,466 bytes; SHA-256:
`2763C3472A2F67D4B6183256562ED1B3BB6355B2706A12716F43EC67763CF1FF`.
Prior signing identity retained. All 25 recordings and 25 packaged notices match
approved bytes; the dedicated portrait asset and six bilingual picker images
are packaged. TV/LG artwork roots remain unchanged. No dependency added.

Companion build/test APK/lint pass (0 errors, 42 existing warnings); generator
8/8, canonical catalogs and shared validation pass. Owned muted API34 emulator:
three-page Arabic/English first use, contextual permissions, live combined
personalization, saved completion, native hosted-widget independence/Cancel,
resize fallback/restoration, missing location, picker contents, opacity/shape,
Hijri date and actual Alexandria binding pass. Legacy transparent presentation
and invalid option bounds are checked in that existing host fixture.

80–150% app sizing passes in portrait and landscape with Android text at 1.3.
Actual native renders inspected, including Arabic picker order, combined setup,
widget controls and the new portrait signature on the optional adhan screen.
Actual scheduled background playback, notification controls/metadata, accidental
swipe cancellation, deliberate/accessibility Stop and screen dismissal pass.
The exported APK differs from that runtime installation only by removal of three
unused labels; its bilingual UI/keyboard/location/save smoke check also passes.
That fixture first reproduced activity reuse (START_DELIVERED_TO_TOP); it now
uses the existing bounded owned-task launcher with all assertions unchanged.
An earlier CI TV job failed downloading an invalid emulator ZIP before any app
ran; phone and Apple-core checks passed. Final-head CI is tracked on draft PR #9;
private command logs,
process receipts and versioned captures stay under ignored state/.
A later complete phone run passed setup/widgets/location/UI/LAN/sizing, then
exposed an immediate post/read race in the silent/vibration fixture. Its exact
channel predicate now uses the existing bounded await; production is unchanged.
Android [queues notification publication](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android14-release/services/core/java/com/android/server/notification/NotificationManagerService.java#L6350)
on its service handler, so notify returning is not a posted-notification barrier.
Failure-only window diagnostics identified the separate cold-CI focus blocker as
“Pixel Launcher isn’t responding.” Companion CI now force-stops only that
external launcher before testing. Miqat focus/ANR assertions remain unchanged;
there is no app workaround or generic dismissal of system dialogs.

Physical launcher/OEM background delivery, speakers/compass, TVs and iOS remain
owner acceptance. Android controls notification layout and launcher clipping.
No merge, release or physical deployment is part of this review handoff.

## Bottom actions and concise phone copy — 19 November 2026

Review APK **0.4.4/code 13**, `state/builds/companion/Miqat-latest.apk`,
32,560,100 bytes; SHA-256:
`EC35F3A1E7AE3D122B404CD58478B5F64FA209896A3FB646E82FF42CBD7310DE`.
First use now shares the canonical signature portrait composition with a
readability scrim, consistent brand chrome and a blended sky/artwork transition.
Bilingual guided theme/background and completed Home renders pass inspection.
Prior signing certificate retained; all 25 recordings and 25 license notices
match exact approved source bytes. No production dependency added.

Build/test APK/lint pass (0 errors, 43 existing warnings), generator 8/8 and
canonical catalogs pass. Owned muted emulator verifies bottom gesture accidental
cancellation, deliberate release and accessible Stop; fixed footers, bilingual
keyboard visibility and 80–150% app sizing with enlarged Android text pass.
The landscape keyboard initially exposed fullscreen extraction and then a zero
height scroll viewport; no-extraction inputs and deferred header collapse now
keep the real editor and Save/Cancel visible. Corrected native render inspected.
Updated bilingual first-use/widget/TV journeys, default native TV bindings,
actual scheduled playback and local LG SSH fixture also pass. Final adhan/Home/
first-use renders inspected. Later owner refinements center the swipe label and
remove duplicate Next/Previous; native Android notification layout is retained.
Updated API34 portrait journey and actual runtime pass. Android delivered a
fixture launch to an existing activity; the shared bounded test launcher now
owns a new task. No production launch logic or assertions were changed for this. Final-head Actions are tracked in PR #9; earlier
runs do not certify this candidate.

No physical OEM, TV, speaker, power-key or iOS acceptance is claimed. Android
still owns notification styling and background/full-screen policy.

## Themed adhan and notification metadata — 19 November 2026

Review APK **0.4.3/code 12**, primary `state/builds/companion/Miqat-latest.apk`,
32,358,695 bytes; SHA-256:
`CBDB11AB24404E85694B25260E897244E5D77ABD599248E523E74CF6D72E69B2`.
Signing certificate matches prior builds. All 25 approved recordings and 25
license notices match exact source bytes. No production dependency added.

Build/lint pass (0 errors, 43 existing warnings). Owned muted API37 emulator:
actual scheduled-alarm/media checks, bilingual phone UI and 80–150% app sizing
with Android font1.3 in portrait/landscape pass. Runtime deliberately separates
alarm firing time from scheduled prayer time and verifies the latter in media
metadata, along with actual voice and artwork. Expanded SystemUI notification
renders prayer/time/voice and selected artwork; native check presses real Stop.
Bilingual Dusk/peaks adhan screens inspected, including allowed lock-screen alarm.
Wide full-screen-denied fallback runtime and completed bilingual setup journey
also pass; wide screen and completed Home captures inspected. Owned emulator
PID32500 was verified and stopped after all native checks.
The first English capture preceded the first submitted frame; frame-commit
synchronization now yields the actual screen. State logs/captures remain private
under `state/adhan-art-*`; PHONE-WORK.md owns remaining CI/cleanup state.

The shared scene renderer preserves Home/preview sizing. Native time formatting
is reused by UI, widgets and media; portable prayer rules/TV contracts unchanged.
Android owns final notification colors/layout and dismissal availability.
No physical OEM, speaker, power-key, TV or iOS acceptance is claimed.

## Prayer card and background adhan — 19 November 2026

Review APK **0.4.2/code 11**, primary `state/builds/companion/Miqat-latest.apk`,
32,553,634 bytes; SHA-256:
`EC4613AF1DA4AA5519178272AB37ADE4FB120BAB42132DE1C40C853955EB62AB`.
Certificate matches prior review builds. All 25 approved recordings and 25
notices match exact source bytes. No production dependency was added.
Android13+ media controls have an explicit session Stop custom action. The owned
native run waits for the visible SystemUI control, captures the real expanded
notification and presses Stop successfully. The same state publisher preserves
Stop before/after pause. Final build/runtime: card-media-final-build.log and
card-media-shade-runtime.log; accepted notification render was inspected.

Phone assemble/test APK/lint pass (0 errors, 43 existing warnings); companion
generator tests 8/8 and canonical/shared validation pass. Native bilingual
phone UI/journey and rendered completed Home checks pass. The card separates
highlighted prayer name/time and centers countdown/status with explicit text
alignment; actual previews use the selected canonical palette. Existing short
scroll, lower locked section and setup-completion persistence remain verified.
80–150% app text with Android font1.3 passes in both asserted orientations.

The owned muted emulator passes actual AlarmManager delivery with screen off,
then resuming another app and entering/leaving Miqat without stopping scheduled
output. Notification Play/Pause retains position and remaining 30-second custom
playback duration. Delete callbacks stop audio and close the optional screen;
stale callbacks do not stop a later session. Background touches/Back cancellation
preserve audio; explicit Stop-and-close and opted-in volume key stop it. Stop is
reachable after scrolling in wide/enlarged text. The denied-FSI run preserves
background playback and manual screen opening. Native v1/v2-to-v3 migration
preserves values and rejects malformed records; new toggles default off.

Private evidence: `state/card-background-*`, `state/card-final-*` and
`state/card-fsi-*`. The first runtime fixture retained an Activity before its
later fresh-launch audition, then stalled; its owned process was stopped.
The fixture now finishes/waits for that Activity before later cases and passes;
no assertion was removed. An explicit allowed-FSI run passes an actual scheduled alarm opening the
Activity over keyguard through ActivityMonitor, with real Back confirmation.
API34 CI later exposed stale active-root polling in an existing Coming soon
fixture. It now waits for the actual AlertDialog window event registered before
the click, retains real Back/focus checks and reports observed events on failure.
The bilingual no-capture/animations-disabled journey passes locally in
card-dialog-event-native.log; source APK is unchanged.
All required final-head CI checks remain the delivery gate; exact receipts/head
belong to draft PR #9.

Android may pin active foreground media notifications, so universal swipe
removal is not claimed; Stop remains available. Power keys are not intercepted.
The volume shortcut is limited to the visible adhan screen, including keyguard.
Actual OEM notification gestures, lock-screen/power behavior and speaker output
remain owner hardware acceptance. No TV runtime/deployment/release changed.

## Phone Home spacing follow-up — 19 November 2026

Review APK **0.4.1/code 10**, primary `state/builds/companion/Miqat-latest.apk`,
32,358,047 bytes; SHA-256:
`41FEE585EB05F5A4EC63C116996C06EA3620A5DCEFB57C7DF39E4E221AF96027`.
Signing certificate matches 0.4.0; 25 approved recordings match exact source bytes.

Phone assemble/test-APK/lint pass. Owned muted emulator checks both languages at
80–150% app text plus Android font 1.3 in asserted portrait and landscape. New
native geometry checks protect separated Home actions, tomorrow Fajr before
tools, lower locked features and location within the prayer card. The first
portrait attempt correctly failed its orientation precondition because the AVD
booted wide; explicit dimensions were then set and both full matrices passed.
The native journey verifies completed setup removes the prompt, repeated-guide
cancellation preserves completion, and a cold launch does not restore the prompt.
The embedded location action still opens calculation/location settings. Normal
completed English/Arabic portrait renders were inspected; all six times and
tomorrow Fajr precede separated tools, with locked features below.

Private logs/captures: `state/home-followup-*`. Final-head CI receipts belong to
[draft PR #9](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/9), and all required
checks must pass before handoff. No prayer policy, alarm, widget, transport,
installer or TV runtime change; prior pass evidence below covers those paths.
Physical phone/OEM/TV acceptance remains separate; no release or deployment.

## Phone feedback pass 4 — 19 November 2026

Review APK: **0.4.0/code 9**, `state/builds/companion/Miqat-latest.apk`,
32,358,043 bytes. SHA-256:
`EE269BC9E18208ABCC8A6F1390161D2F5383CE39DF1F6355F5BA0F702870F435`.
The review signing certificate matches the prior APK; an update needs no uninstall.
Standalone installer source/build is 1.1.4/code 7. Existing LG review IPK remains
0.2.28; no physical install or release publication was performed.

Phone/installer assemble and lint pass. Fresh packaging removes the retired
recording completely: exactly **25 approved recordings** match their canonical
bytes and all **25 notices** match source. The final clean phone build includes
the widget preview context correction below and passes assemble/test-APK/lint.
Clean Android TV size/lint/test APK, LG build/service/native TLS matrix and the
preview build pass. Portable JVM/JS/Java/legacy ES5, canonical metadata checks,
preview tokens/data/tests and 47 repository regressions pass. The LG browser
matrix passes English/Arabic at every supported size; its voice count follows
the canonical list and explicitly rejects a selectable Al Afasy entry.

Owned muted Android emulator evidence, separate from hardware acceptance:

- Bilingual first-use resume/Skip, focused notification/precise-alarm steps,
  real portrait/carousel previews, draft Cancel and independent phone settings.
- Native AppWidgetService binds all launcher variants. Two configured instances
  preserve independent settings; Cancel, resize fallback/restoration, strip and
  missing-location updates pass. Final clean-package journey also passes.
- Native TV source/guarded Android handoff, LG one-field address/passphrase pages,
  locked unsupported platforms, selected channel and the complete scan drawing.
- Actual local TLS/credential recovery and no-replay suite: older retired-voice
  metadata preserves its real baseline without a write, unknown choices remain
  rejected, and the native selector hides the retired option.
- 80–150% app text in both languages, combined with Android font 1.3, passes in
  asserted portrait and actual 2400×1080 landscape. Native rows/buttons remain
  reachable. Accepted captures are identified by PNG dimensions, not folder name.
- A native font-size assertion reproduced widget preview double-scaling red;
  application-context inflation now matches launcher/system text scale. The
  assertion passes across both complete size/orientation matrices.
- API-34 CI exposed a Coming soon dialog synchronization race. The fixture now
  waits for its actual active accessibility window before physical Back and
  still requires restored Activity focus. No-capture/animations-disabled native
  verification passes; final-head CI remains the delivery gate.
- Actual AlarmManager/foreground output, private 30-second cutoff, audition,
  Stop/focus loss, denied access, duplicate/early/expired events and cleanup pass.
  Global phone volume remains untouched. Migration/import checks preserve valid
  private audio and unrelated settings on malformed/interrupted/oversized input.

Private commands/logs/captures use the `state/pass4-*` prefix. The final branch
head must pass Validate, Companion and Apple-core CI before handoff; exact run
receipts and head are kept in [draft PR #9](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/9).
Apple verifies portable libraries, not an iOS app. Physical phone/OEM delivery,
compass calibration and TV picture/sound/remote/power remain owner acceptance.
Published public release assets are still unavailable; draft release assets are
not a public installer source. Existing TV volume audit finding remains deferred.
Requirements and audit boundaries: PHONE-PASS4.md and PHONE-AUDIT.md.

## Phone feedback pass 2 delivery — 19 November 2026

Phone 0.2.1/code 7 and installer 1.1.2/code 5 assemble/lint pass. Portable quiet
policy tests pass for weekdays, overnight periods, equal start/end and exact end
boundaries. Native isolated storage checks preserve v1 values through v2 migration,
reject missing old fields, keep per-prayer audio independent and find next week's
allowed event without repeatedly rearming suppressed events. Required channels
exclude unused sound channels for vibration and all-day quiet overrides.

An owned muted API 37 emulator passes private audio import/decoder checks,
malformed/interrupted/oversized-copy preservation, actual foreground custom audio
with a measured 30-second per-prayer cutoff, dedicated silent/vibration output,
actual alarm delivery, duplicate/early/expired claims, Stop/focus/permission denial
and disabled cleanup. Global Android alarm volume stays unchanged. Only the
existing 14 approved built-in recordings ship; custom files stay private and are
excluded from backup/transfer.

Native bilingual clock Save/Cancel, weekday selection, Arabic numbers, per-prayer
overrides, saved-value/choice sheets, root navigation and every canonical control
pass. Portrait and landscape 80–150% checks include new editor buttons; landscape
also combines 1.3× Android text. Renders of English/Arabic clocks, weekdays and
phone settings were inspected. Opt-in travel uses the fixture fix while preserving
method, offsets and fixed timezone, then releases location providers. These are
emulator/fixture results, not physical GPS, OEM power or speaker acceptance.

47 Python/Java regressions, canonical checks and eight companion generator tests
pass. The exported APK contains all 26 exact approved recordings; its signing
certificate matches the previous review build. Final review export SHA256:
7B203855FF55F7671746EA3C1BB8468218A2A78F4D84BDD0E51E1F65CC21F541.
All CI workflows passed on the preceding compact-UI milestone c519e6b; final-head
CI remains a separate gate, with authoritative links on PR #9.

Private logs include phone-custom-core, phone-custom-runtime, phone-custom-denied,
phone-custom-travel, phone-pass2-root-ui, phone-pass2-custom,
phone-pass2-editor-size, phone-pass2-landscape-editors, phone-pass2-channels,
phone-pass2-regressions and phone-pass2-package under state/. No original Athar
recording, private profile/location capture or imported user media enters Git.

## Compact phone controls — 19 November 2026

Native English/Arabic phone checks pass for compact saved values, focused numeric
Done/Cancel, off-step rejection, choice-sheet saved selection and persistence.
Owned-emulator sizing passes 80–150% in portrait and landscape with 1.3× OS text,
including actual preview/Stop bounds and stable root navigation. Rendered phone
settings/voice sheets were inspected. Review caught oversized preview rows when
two match-width buttons shared a row; a weighted preview plus labelled Stop icon
corrects this, with a native bounds regression. Existing TV binding and pinned-LAN
checks pass locally after updating assertions for the intended nested TV tabs.
Phone assemble/lint pass. Private logs: phone-compact-preview-build,
phone-compact-preview-size, phone-compact-preview-ui, phone-compact-landscape-large,
phone-compact-tv-ui-2 and phone-compact-lan.

CI at ac510e9 exposed an outer test Activity finish/start race before embedded
setup; the fixture now waits for destruction at that transition as it already did
inside setup. CI at 47dbe87 also failed the untouched TV Settings focus assertion;
keep final-head CI as a gate and investigate any recurrence. Passing local checks
do not replace that gate or physical acceptance.

## Published-package installer feedback — 19 November 2026

Android and LG now share anonymous public-release selection and size/SHA-256
verification. Native deterministic checks pass for preview/draft selection,
versioned LG assets, wrong repository/HTTP source, missing/duplicate checksum,
truncated/wrong-digest download and preservation of the previous file with no
partial-file leak. Companion/standalone installer assemble and lint pass; no new
touched-code warnings remain. 47 Python/Java regressions pass. Native bilingual
setup/navigation checks and inspected renders confirm public download defaults
and local files under Advanced; no physical TV was contacted. The website builds
with the canonical public-release link (after installing its locked dependencies).
The current repository has only a draft, so published-binary end-to-end transfer
remains unverified; no release was published. Private logs: public-release-native,
public-release-ui, public-release-final-build, public-release-regressions and
public-release-site-2 under state/.

## Phone location feedback pass — 19 November 2026

Foreground one-shot detection, explicit confirmation and Advanced coordinates pass
English/Arabic native checks on an owned muted API 37 phone emulator. Precise GPS
uses an injected London fix; approximate-only access uses a framework test network
provider with 3 km accuracy and precise permission denied. Denied access returns
without listening; stale fixes are rejected; requests close on success/navigation.
Rendered English/Arabic location pages were inspected. Core quality/distance tests,
phone assemble/lint and native test build pass. Fixtures do not establish physical
GPS performance or OEM delivery. Private logs: phone-location-denied.log,
phone-location-granted-2.log, phone-location-coarse-network.log,
phone-location-capture.log, phone-location-core.log and phone-location-build.log.

The owner’s actual Athar recording was sampled visually across its full duration;
only generic UX observations/timestamps enter PHONE-REFERENCE.md. No private
profile/location data, original video or recording assets enter distribution.

## Standalone phone foundation — 19 November 2026

Phone prayer settings are independent from TV settings/cache and default to alerts
off until location confirmation. Shared canonical phone metadata declares modes,
before/after reminders, per-prayer voice/offset/vibration and phone appearance.
Eight generator tests pass. Native checks persist/reload phone settings, reject
unknown IDs/types/ranges/steps/timezones and verify companion TV preferences are
unchanged. Device-protected prayer preferences are excluded from transfer; no TV
credential moves into this store.

Five portable phone reminder tests pass, covering before/call/after, sunrise/off
exclusion, handled IDs, simultaneous events, strict 120-second lateness and invalid
inputs. Native phone astronomy compiles the same pinned Adhan source and minute
adapter as TV. JVM checks reproduce the frozen 12 November 2026 Cairo vector,
all methods' exact offsets, explicit Tokyo civil date despite a different device
zone, New York's 23-hour DST day and unavailable polar results. Existing Java
regression group passes (15 tests). No fixture/vector was rewritten.

Phone assemble/lint and native metadata/storage plus bilingual companion/setup
checks pass. The activity fixture now waits for each finished setup's destruction
and caller resumption before opening the next; final outcome assertions remain.
Prayer screens, alarms and playback are the next stages, not claimed complete here.

## LG phone setup and aligned installers — 18 November 2026

Phone setup now uses JSch 2.28.8, based on official LG CLI 3.2.6's Developer Mode
key acquisition, SSH transport and public Luna install/list/launch protocol.
Local SSH/HTTP fixture through the owned phone emulator passes unknown/changed
host-key rejection, bad-passphrase rejection, real Miqat IPK upload/MD5 verification,
failed install with unchanged version, explicit retry, installed-version readback,
launch and removal of only the successful operation's temporary package. No
physical TV was connected or changed. SSH keys/passphrases stay session-only;
accepted public host keys persist. Legacy SSH algorithms are scoped to LG setup.

Three new pure-Java/Python checks cover IPK structure/size/truncation, other-app
and duplicate metadata rejection, and numeric downgrade ordering. Full root
suite: 46 pass. Canonical/generator/isolated validation passes. Companion and
standalone installer assemble/lint pass; inherited warnings remain identified.
Native English/Arabic checks pass for LG entry/invalid-address rejection/private
password state and Android setup completion/cancellation. Portrait renders of
both setup flows were inspected after matching canonical palette, Alexandria
headings, filled cards/buttons and compact static hero. Existing pinned-LAN
behavior remains separate from privileged installation.

Companion CI initially exposed a fixture race: UI-idle did not imply the Activity
result had reached the caller. The regression now waits for the actual resumed
pairing page with a deadline; assertions/result checks remain intact. Final CI
and combined owner physical acceptance remain open until full phone delivery.

## Companion installation integration — 18 November 2026

Based on merged companion main `3c5b38e`; phone 0.1.4, standalone installer 1.1.0.
The companion and standalone app build/lint with one shared installer runtime.
The existing ADB dependencies/versions and their full notices are retained;
their pre-existing TLS-manager lint warnings now also appear in the companion.

Shared core JVM/Java/JS/97 ES5 assertions, companion core, canonical generation,
seven companion generator tests, JVM LAN/QR checks and 43 Python/Java regressions
pass. Owned read-only API 37 emulator checks pass for both languages: setup opens
with host language/direction, its activity is private, opening does not create
ADB credentials, cancellation/incomplete setup do not open pairing and successful
completion offers ordinary pairing without automatic approval. Native pinned-LAN,
revocation/credential-tamper and uncertain-write checks also pass.
Portrait and landscape native renders were inspected. The standalone installer
still launches under its existing app/activity identity. Both APKs retain all 16
installer asset/notice files byte-for-byte; the companion retains all 26 approved
recordings exactly.

An initial native launch failed because a locale override ran after resource
access; host-context localization fixes it and the same regression passes. Native
setup completion is simulated; no TV was connected or changed. Physical Android
installation/cleanup/recovery, LG phone installation and iOS remain pending.
Private logs/captures stay in ignored state/; [WORK.md](WORK.md) owns the sequence.

## Affected CI checks — 21 November 2026

The owner approved affected PR validation, full main/manual builds and automatic
cancellation of superseded runs. PR selection remains conservative for shared,
tooling and unknown paths; packaged notices and generated UI-gap documentation
are not treated as prose. Companion is part of the applicable required gate.

- actionlint 1.7.12 passes for all three workflows.
- Three focused CI regressions pass: affected-consumer unions, fail-closed gate
  outcomes, and real Git rename/deletion selection plus full push/manual runs.
- Both delivery regressions pass, including rejection of untrusted/stale release
  sources. The publisher and trusted-main artifact names are unchanged.
- Canonical catalog checks/tests and isolated shared validation pass;
  `git diff --check` passes. Workflow documentation was checked against its
  owning scripts. No application/runtime sources changed or local platform
  builds were needed. The PR owns hosted CI and normal-merge delivery receipts.
- The superseded Companion run 44934495042 was canceled and its final Cancelled
  status verified. Current runs were preserved.
- Base main run [44936708883](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44936708883)
  failed the existing LG browser startup-artwork assertion before this change;
  the PR retains that check without relaxing it.

## Approved adhan cleanup — 21 November 2026

On `feature/adhan-mild-cleanup`, based on `3c5b38e`, the maintainer approved the
mild cleaned candidates after reviewing the 14-voice before/after set. Shipped
files match those exact candidates; all 12 reminder hashes are unchanged.
The adhan set is 18,335,775 bytes, down 9,457 bytes. Each complete cleaned file
decodes without errors, preserves its decoded sample count/rate/channels/bitrate,
and has sample peaks below full scale. This does not certify intersample peaks
or physical-TV sound. Existing wording and cut points are unchanged.

- Node 24.14.0: preparation regression and read-only check pass, including
  rejection of stale stamps and unapproved regenerated output, input/backup
  preservation on failure, and successful exact approved-trim reproduction.
- Canonical catalog checks/tests and isolated shared validation pass.
- JDK 17 / SDK 34: Android `verifyApkSize lintDebug assembleDebugAndroidTest`
  passes; APK **22,770,463 bytes**, below 25,000,000. All 26 packaged MP3s match
  shared/media byte-for-byte. Lint reports 0 errors and 59 untouched-area warnings.
- All **43** Python/Java regressions pass.
- LG `npm test` passes, including ES5/build, service, media/volume and LAN
  regressions. IPK **21,297,060 bytes** contains all 26 exact shared recordings.
  CLI notices about requiredACG and target Node versions remain; no service/API
  or runtime-code change is included. The complete Arabic/English mocked browser
  suite also passes at all supported sizes.

Runtime checks are automated/local; human acceptance here covers the supplied
listening excerpts, not physical speakers or every word of a complete playback.
No TV deployment, release publication or physical-TV operation was performed.
The preview does not package these MP3s; its runtime is unchanged. Original audio,
comparison files, measurements and raw validation logs remain ignored under state/.

## Foundation checks — 17 November 2026

Checked on 17 November 2026 in the new Miqat checkout, using JDK 17, Node 24.14.0
and SDK 34. Source versions: Android TV 0.7.0, installer 1.1.0, LG 0.2.23.

| Check | Result |
| --- | --- |
| Canonical catalogs, geometry, isolated validation and token freshness | Pass |
| Core check/jvmJar | Pass: JVM/Java/JS tests and 94 legacy assertions in browser/CommonJS ES5 hosts |
| Android TV verifyApkSize/lintDebug/assembleDebugAndroidTest | Pass; complete local-audio APK 21,290,236 bytes, below 25,000,000 |
| Installer assembleDebug/lintDebug | Pass |
| Python/Java regressions | 41 pass, including bilingual branding and forbidden legacy-name checks |
| Native prayer and UI instrumentation | Pass: core/reminder/ICU linkage and Arabic/English control coverage |
| Native size-windows | Pass: both languages at 80–150%, including popup/dialog/bar/banner/screensaver/screen-off |
| LG test and test:browser | Pass: ES5/build/service/volume/mock-HTTPS and Arabic/English at all eight sizes |
| Preview tokens:check, test and build | Pass; 18 static routes |
| Built preview in Edge | Arabic/English Home and Settings inspected; Home HARs have no failed asset requests at /MiqatTV/ |
| Source/doc audit | No old branding/links outside retained MIT notices and prohibited-brand test expressions; local Markdown links resolve |

Native checks used an isolated API 37 phone emulator at 1920×1080/density 160,
with system animations disabled. A cold-boot SystemUI ANR blocked the first UI
check; after closing that emulator dialog, the unchanged UI test and full window
suite passed. Home was visually inspected. This is not Android TV hardware evidence.
LG screenshots are browser simulations; Luna and decoder boundaries are mocked.
Raw logs, original/review recordings, HARs and captures remain under ignored state/.

## Open checks

- Fresh-install acceptance on physical Android TV and LG, including Home/startup,
  real remote, picture/sound and prayer/media restoration under the new identities.
- Complete installer operation on a real phone/TV. Newer LG and Samsung are unverified.
- Portable prayer/appearance settings export/import is not implemented. Installer
  recovery exports Android setup state only.
- The preview's Astro advisories were fixed in the GitHub Pages pass by updating
  the lockfile to Astro 7.4.12. npm audit still reports 10 high findings propagated
  from the unpatched picomatch build dependency (GHSA-fc5q-39xc-vjpf). It processes
  repository file patterns in the Node build/dev tools, not visitor input in the
  published static site. Pages serves only site/dist; no Node server, dependencies,
  source checkout or private state is deployed. Do not use npm audit fix --force:
  its proposed Astro downgrade reintroduces unrelated fixed advisories.

No physical TV was changed. The old platform-core and companion checkouts remain
clean and unchanged. No companion code, inherited Actions, previous history,
credentials, private device state or recordings are part of this repository's sources.

## Appearance review draft — 18 November 2026

Branch `design/miqat-themes`, based on `e612c11`, in an isolated linked worktree.
All work remains unstaged and uncommitted pending owner review. Other main-checkout
and companion work is separate and was not modified. Local review: `state/theme-review/index.html`, revision 6.

- Revised after owner feedback: seven non-arch ornamental families, one fully
  framed arch composition and a star/diamond default tile. All SVG coordinates
  and control points stay within an eight-unit margin, covered by a regression.
  Existing selection IDs remain stable; bilingual background labels were updated.
  Aggregate text hashes changed intentionally for those labels only; religious
  text and formatter expectations are unchanged.
- Canonical freshness, tokens/data checks, preview tests and 18-route build pass.
  All 20 themes render in both languages without page errors; all 23 background
  views were regenerated. Actual-opacity overview sheets were inspected; gallery
  theme/language/background/detail controls and image loading pass at desktop/mobile.
- Android build/size/lint/AndroidTest assembly and all 41 Python/Java regressions
  pass, including palette contrast and full-resolution mask checks. Complete
  local-audio APK: **21,976,961 bytes**.
- LG build/ES5/adapter/service/mock HTTPS checks pass. Palette-tinted masks,
  opacity bounds, draft-theme image loading and full-composition sizing are checked.
  Full browser checks pass in Arabic/English at 80–150%, including all six scenic
  carousel previews, full-resolution decoding, Apply and saved Home re-entry. The
  new test mirrors physical arrows for Arabic; decoder and Luna are mocked.

Six neutral landscape assets are reused unmodified under CleanYourTV's MIT license,
with source filenames/revision recorded in THIRD-PARTY.md. They total 367,296 bytes.
All six render natively in the task-owned read-only API 37 phone emulator at
1920×1080/density 160, alternating English and Arabic; captures were inspected.
Only this emulator was installed/configured, then closed. These are emulator
spot checks, not physical-TV or real-remote acceptance. The review gallery also
contains 12 bilingual browser Home examples using complementary palettes. Raw
captures and private media stay ignored. Physical TV, media/remote/power acceptance,
deployment, commit and push are outside this review. Existing preview dependency
advisories remain separate work. Reload the local gallery to see revision 6;
versioned image URLs avoid stale copies.

### Handcrafted presets and customization

Six existing IDs now pair the landscapes with dedicated palettes, bilingual names
and surface treatments (see DESIGN.md). All palette roles and glass-over-white
checks pass. Appearance places the complete preset carousel first, then Customize
with the existing controls. Selecting a different preset restores its bundled
background and lighting only in the draft. Clamped input preserves manual changes.

Preview regressions/build and actual bilingual page checks pass: draft-only preset
selection, manual background override and Apply are verified. Android build, size,
lint and AndroidTest assembly pass, along with all 41 Python/Java regressions.
The native UI contract check passes on the task-owned read-only API 37 emulator,
including preset defaults, customization preservation, row order and both languages.
Six final native theme captures were inspected; the emulator was then closed.
LG build/service suites and full bilingual browser checks pass, including preset
background selection, unsaved draft isolation and later manual overrides. These
remain mocked LG checks, not physical picture/remote acceptance. Current gallery
includes the new presets, before/after palette comparisons and customization page.
No files are staged, committed or pushed. Other ongoing checkout work is preserved.

### Miqat flagship

The canonical default now selects original `miqat_signature` artwork rather than
the lattice. Built-in ImageGen produced the petrol/jade ceramic relief with gold
inlay; the app bundles a 115,784-byte 1920×1080 JPEG. The generation brief is in
`shared/design/artwork/miqat-signature.md`. Existing manual backgrounds stay intact.
Canonical/catalog/token checks, preview regressions/18-route build and all 41
Python/Java regressions pass. Android build/size/lint/AndroidTest assembly pass;
all seven local scene files are confirmed in the APK. Actual browser and native
API 37 emulator renders were inspected in both languages with Theme resolving
the new default. The read-only task-owned emulator was closed afterward.
LG build/service and full bilingual browser suites pass for the new default,
including fresh preferences, manual-background preservation and all seven scene
previews/save/re-entry. LG remains mocked. Review revision 6 includes before/after
and current native flagship captures.
No physical-TV acceptance, commit or push is claimed.

### Home opacity and clearer prayer icons — review revision 7

Home fill now supports 50–100% in 5% steps, default/recommended 85%, preserving
opaque text/outlines and non-Home frames. Shared JVM/JS/legacy checks pass. Native
focused opacity and UI-contract instrumentation passed on the task-owned read-only
API 37 emulator: bilingual limits, live draft, both cancellation layers, Apply,
saved re-entry and actual fill-only paint alpha. The emulator was closed after
final bilingual native captures. No physical TV was operated.

Owner rejected the first decorative icon proposal as unclear. The revised review
set removes diamond ornaments and uses familiar solar/lunar symbols with a heavier
2-unit stroke. Canonical paths feed Android, preview and LG. LG Home icons share
the tag header strip, preserving long labels at 150% sizing. This replaces the
failed inline placement that wrapped Maghrib. Final full LG bilingual browser
suite passes at all supported sizes, including opacity persistence and all six
canonical paths; service/ES5/mock HTTPS suites pass. Decoder/Luna remain mocked.

Preview regressions and 18-route build pass. Actual bilingual page checks verify
50–100% bounds, live preview, numeric/outer cancellation, Apply/re-entry, opaque
text/outlines and six canonical paths. Android build/size/lint pass; current local
APK is 21,835,655 bytes. All 41 Python/Java regressions pass. Review revision 7
contains refreshed Home/preset images, opacity controls, a rejected/revised icon
comparison with small-size samples, and final native flagship captures. Gallery
controls/image loading pass. Native instrumentation was run before the final
path-only icon revision; the final APK was installed and visually inspected.

No physical-TV acceptance, staging, commit or push. Existing dependency advisories
remain separate work. Main-checkout work was preserved.

### Miqat quiet addition and 75% next-prayer fill — review revision 8

Owner selected a 75% next-prayer fill and approved keeping both original Miqat
scenes. `miqat_quiet` is added with English/Arabic labels; `miqat_signature`
remains the canonical default. Neither background is automatically mirrored.
The added 1920×1080 JPEG is 90,742 bytes. Android/preview/LG apply absolute 75%
alpha to the selected surface panel RGB, without dimming text/outline or
compounding Glass. The mihrab's separate opacity control is unchanged.

Canonical freshness/token checks, preview regressions/18-route build, Android
size/lint/AndroidTest assembly and all 41 Python/Java regressions pass. Current
local-media APK: 22,040,731 bytes, below 25,000,000; both Miqat JPEGs are confirmed
byte-for-byte in the APK. Aggregate text hashes were intentionally regenerated
for the additional background label; sacred text/formatter fixtures are unchanged.
Actual bilingual preview checks pass for preserved default, quiet carousel draft,
Apply/re-entry, 75% fill and opaque labels. Both scenes were rendered in English
and Arabic on the isolated read-only API 37 emulator; native captures were
inspected and that emulator was closed. No physical TV was operated.

LG packaging/ES5/service/mock HTTPS and full bilingual browser checks pass,
including all eight scene decodes, saving/re-entry and computed 75% card alpha.
Decoder/Luna remain mocked. Gallery revision 8 includes the added background and
updated previews; gallery controls/images pass. Native original/quiet captures
are retained under ignored state. No staging, commit, push or physical acceptance.

credentials or private device state are part of this repository's sources.
The maintainer approved publishing the reviewed 26-clip set on 18 November 2026;
those copies now live under shared/media/, with exact SHA-256 checksums.

## GitHub delivery setup, 18 November 2026

Local core, Android/installer lint and builds, 43 Python/Java regressions, audio
preparation fixtures, full LG browser/service tests and preview checks passed.
The TV APK contains all 26 approved recordings byte-for-byte and is below 25 MB;
LG app/service IPK packaging also passed. No recording was re-encoded in this pass.

The active [Protect main ruleset](https://github.com/AbdelrahmanHajjaj/MiqatTV/settings/rules/27542002)
requires a PR, the GitHub Actions Required checks result, an up-to-date branch,
resolved conversations and normal merge commits, as requested by the owner.
Squash/rebase merging is disabled. It blocks force pushes/deletion, has
no bypasses and requires zero approving reviewers. Actions requires full SHA pins;
the default token is read-only and cannot approve PRs.

[PR #1](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/1) contains the validation
and draft-publication workflows. GitHub-hosted checks must pass before merging;
manual draft publication can be exercised after the workflows reach main.
CI APKs use ephemeral debug signing keys; stable update signing is not configured.

API 34 checks exposed split UI observations and an arrange-menu/catalog-refresh
race. Header navigation and favourite-marker assertions now use coherent UI
snapshots, with queued-refresh regressions. Android defers catalog rebuilding
until the menu closes and preserves the originating app's focus. A direct native
regression failed before the fix; five complete Arabic/English UI runs passed
afterward on an isolated API 34 emulator. Build/lint, all 43 local regressions and
the 26-recording APK check passed. Temporary probes were removed.

[Validate run 44296900776](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44296900776)
passed all six jobs for 0229237: core, Android build, API 34 emulator, LG browser/
package checks, preview and the required aggregate. Full-audio Android/LG artifacts
were uploaded successfully. The owner then confirmed normal merges, never squash;
repository and ruleset merge options now match that preference.


## GitHub Pages, 18 November 2026

The existing Arabic/English static preview is configured for
https://abdelrahmanhajjaj.github.io/MiqatTV/. Pages uses GitHub Actions; the
created github-pages environment permits main only. The deployment job reuses
the current run's browser-preview artifact after Required checks passes, with
job-scoped Pages/OIDC permissions and pinned official actions. PRs skip deployment.

Local data/token freshness, preview regressions and the Astro 7.4.12 static build
passed. All 18 routes have correct canonical URLs; 450 internal link/asset
references resolve under /MiqatTV/. The artifact contains no Node dependencies,
private state, keys, app binaries or recordings. Edge checks passed for Arabic/
English Home, language switching, Settings tab changes and return navigation,
with joined Arabic, loaded branding and no browser console errors.
Workflow actionlint and documentation checks passed. The owner authorized a
normal merge of PR #2 after required checks pass, followed by live publication.
PR #2 and its Actions runs own the final merge/deployment receipts; initial live
acceptance follows the successful main run.


## Interactive website refresh, 18 November 2026

Based on main 4ccf0ef after the theme-library merge. The bilingual product page
uses current Miqat tokens/artwork. Nine screens mount locally without URL changes;
18 static direct-entry routes and separate language URLs remain available.
Replaced screens abort timers, observers, global listeners, media and online work.

Data/token freshness, the complete preview suite (including route boundaries and
screen cleanup), Astro static build and documentation checks passed. All 18
canonical URLs and 828 internal references resolve; no private state, Node
modules, app binaries or recordings are in the output. Local Node was 26.13.1;
CI uses the documented Node 24. No dependencies or shared/app sources changed.

Actual Edge checks passed for all nine screens in each language, same-URL screen
changes, direct Settings entry, selected-screen language switching, browser Back,
return focus, settings Apply/cancel/re-entry, saved inline size and restored test
preferences. Arabic is joined; the 390px page has no horizontal overflow. Default
artwork loads and console errors are absent. Captures are under ignored state/.
Radio streams and online prayer comparison were not requested in these browser
checks; their existing deterministic regressions passed. This is browser evidence,
with no physical-TV operations. The PR owns CI, merge and publication receipts.


### Website publication

[PR #4](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/4) was normally merged as
5d0e1c2 after six successful required jobs. [Main run 44332260024](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44332260024)
passed all seven jobs, including Pages deployment. Public Arabic/English Home,
same-URL Settings navigation, language links and Arabic appearance rendering were
verified in Edge with no console errors. Local verification server was stopped.

### CI audio-processing scope

The owner requested removing FFmpeg from Actions and retained the other Ubuntu
checks/builds. The workflow no longer installs FFmpeg or runs synthetic audio
processing. Approved-media hashes, prepared-output hashes and packaged APK byte
checks remain. FFmpeg preparation/decoding and listening remain local checks for
changed media/preparation code. No recording or package content was changed.

Workflow actionlint, documentation links/paths, the remaining prepared-output hash
check and diff checks passed locally. All workflows were scanned to confirm no
FFmpeg/ffprobe installation, invocation or audio-processing fixture test remains.
The existing required-job aggregate, build artifacts and publication guards are
unchanged. The focused PR owns the GitHub validation result.


## README branding refresh, 18 November 2026

The README removes its header icon and uses the reviewed PR #3 native Home captures
in English/Arabic, showing the default Miqat signature scene and current palette.
These are 1920×1080 API 37 emulator captures, not physical-TV photographs. The
capture worktree commit a567978 and main c6ad64f have identical Android, installer,
core, data and design sources. The PNGs are copied unchanged from the reviewed
captures; no new app build, emulator session or physical-TV operation was needed.
The design guide's obsolete uncommitted/review-draft claims are removed.

Both PNGs decode, match the reviewed bytes and retain 1920×1080 dimensions.
Arabic shaping, RTL placement and the current background/prayer symbols were
visually inspected. Documentation link/path checks and diff checks passed.


## Companion LG replacement (18 November 2026)

On the separately authorized companion branch, latest main `9aaf542` is merged.
Preview generated-data, unit, production build and bilingual navigation/pairing
checks pass. LG 0.2.24 unit/service/build/ES5 and full mocked bilingual browser
checks pass, including the added initial splash visibility/dismissal regression.

Live developer-service readback verifies all 31 old preference fields preserved,
plus the new default mihrab opacity; input, startup and automatic alerts restored.
Old scheduling was disabled before Miqat scheduling was enabled. Old launcher
uninstallation and settings preservation through the 0.2.24 update are verified.
Backups, logs and device configuration stay private under ignored state/.
The owner confirmed the branded splash, picture and sound all work on the
physical LG with 0.2.24 on 18 November 2026. Physical remote, new-identity
pairing/camera, phone commands and power/lifecycle acceptance remain pending.


## Companion polish and legacy pairing performance (18 November 2026)

Owner feedback confirms fresh Miqat LG pairing and smooth connected controls and
settings. Phone 0.1.1 adds persistent navigation, clearer selection/typography,
compact controls and retained loading content; launcher label is Miqat. Native
Arabic/English behavior checks pass in compact portrait and inherited landscape;
phone build/lint, generator and native TLS/lost-ack regressions pass. Compact
portrait native checks also pass at 130% system font size in both languages;
Arabic joining and persistent navigation were visually inspected.

Ephemeral physical LF6300 identity-only benchmarks measured 65,284 ms for the
previous Forge path and 2,004 ms for the native OpenSSL path. Existing trust was
not read, reset or replaced for the benchmark. These single samples do not prove
full camera-to-approval latency. LG 0.2.25 adds bounded native generation and
unchanged-QR reuse. Modern/legacy identity, storage/error/fallback tests and full
LG mocked browser checks pass; final bilingual QR-cache checks pass at
80/100/150%. The update is installed/launched with current preferences, input and
startup preserved by live API readback. New phone UI and timed complete physical
pairing acceptance remain pending. Private logs/captures remain ignored.


## Pairing icon alignment and LG focus (18 November 2026)

Phone 0.1.2 centers Pair TV, Scan QR and pairing Cancel icons with their labels.
Measured bounds/gaps and native behavior pass in Arabic/English at normal and
130% text sizes; actual portrait
renders were inspected. Phone assembleDebug/lintDebug/assembleDebugAndroidTest pass.
LG Down-from-Back regression failed before the fix and passes with ordered
vertical navigation, including Reject/Approve without activation. Independent
control scrolling preserves the complete QR at 150%. LG unit/service/build/ES5
and bilingual pairing-browser checks at 80/100/150% pass. Raw failures and passing
receipts stay under ignored state/companion/alignment/. LG 0.2.26 is installed
and launched; current preferences, input and startup were verified preserved
through live API readback. Physical owner acceptance
of these fixes remains pending.

### Standalone phone native lifecycle (2026-11-19)

Owned muted emulator: actual AlarmManager delivery starts private foreground
media service and visible Stop notification; duplicate, early and expired events
are rejected. Stop, audio-focus interruption and disabling alerts stop/release
playback; disabled alarms are removed. Native build and lint pass. The suite
snapshots/restores phone prayer preferences; it does not touch TV credentials.
Private receiver/service are direct-boot aware; boot/time changes only reschedule.
Packaged audio playback lifecycle is checked; audible output, manufacturer power
management and physical reboot acceptance await the owner's combined test.

### Standalone phone screens (2026-11-19)

Owned phone emulator: standalone Prayer landing and phone settings work without
a paired TV. Arabic/English native checks pass for location confirmation,
inline numeric Save/Cancel, Arabic digits, numeric step rejection, independent
per-prayer notification mode, timezone reconfirmation, theme palette application,
every declared phone control and reopen persistence. Existing bilingual TV
settings/Remote and embedded installer cancellation/incomplete/success handoff
checks pass after the navigation change. Captured portrait Prayer/audio/location/
settings screens were inspected; joined Arabic, RTL and corrected Umm al-Qura
month names are present. Screen-size/large-text matrix is still pending.
Denied exact-alarm permission produces an honest status with no scheduled event
or audio; receiver/service are private and direct-boot aware. Physical delivery,
sound and manufacturer power behavior remain awaiting the combined owner review.

Phone portrait sizing passes at every 10% step from 80–150 in both languages,
including combined resource font scale, complete native navigation geometry,
48dp targets and explicit western/eastern digit conversion. The private recovery
broadcast rebuilds an alarm without directly playing audio; actual alarm delivery
and Stop/focus/disabled cleanup pass again. Canonical generation checks, eight
companion generator tests, QR/LAN helper checks and all 47 Python/Java regressions
pass. Shared installer also respects OS/host reduced motion. Final localized-date
formatter build/lint passes without touched-code warnings.

Landscape with 1.3× Android text also passes every 80–150% app scale in both
languages. Inspected largest Arabic landscape and English portrait captures:
root navigation stays visible, labels wrap, and content scrolls within the
remaining viewport. Owned-session display size and font scale were restored.

### Final phone lifecycle/package and Apple boundary (2026-11-19)

Phone 0.2.0/code 6 builds and lints. Native delivery passes with immediate preview
replacement, twelve-second preview timeout, Stop, focus interruption, disabled
cleanup and an assertion that Android's global alarm volume is unchanged. The
Prayer screen refreshes on civil-day/timezone/clock changes; active playback
uses current phone volume/duration. Reminder channel denial now affects readiness.
Bilingual native phone UI and pinned TLS/credential/LAN regressions pass after the
lifecycle changes. The package contains all 26 exact approved recording bytes and
25 complete notice/source files matching their existing sources, including the
pinned astronomy and SSH notices. No original audio or private state is packaged.

[Apple core CI on 68a91fe](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44414849445)
passed native macOS ARM64 common rule tests and iOS ARM64 device/simulator library
compilation for both cores (roughly four minutes of build steps). No iOS simulator
was booted, no Swift app exists and no signing/background-audio behavior is claimed.
The opt-in target configuration also passes ordinary local JVM/ES5 checks,
including 97 legacy host assertions. Remaining required CI and physical owner
acceptance are recorded in PHONE-WORK.md.

### Combined review candidate and Athar reference (2026-11-19)

Owner supplied Athar as their mobile reference; findings and future discussion
ideas are in PHONE-REFERENCE.md. The phone now has a compact two-column daily
schedule, linked prayer customization, country-qualified city search, battery/
background repair help, consistent palette tints and raised surfaces. Native
bilingual checks pass, including explicit before-Fajr/after-Isha rendering: a
past Fajr is not highlighted as the next prayer after Isha. All 80–150% sizes pass
in portrait and landscape with 1.3× OS text. A new outline-bounds assertion first
reproduced clipped card corners at 80%; top-aligning the rows fixes that cause.
Inspected final English/Arabic portraits and largest Arabic landscape. The final
countdown uses a translated format placeholder; build/lint passes.

The final phone also passes the actual native LG RSA/TLS/settings fixture:
normal pairing, full metadata, 5% volume, Apply/Cancel, reconnect without uploads,
actual transport loss and bilingual stale cache. This is an owned-emulator/local
LG service fixture, not physical LG acceptance.

LG's startup browser assertion raced its transient splash after DOMContentLoaded.
A delayed-observer repro failed the exact assertion. Gating app initialization
makes that observer pass while preserving visible-artwork, image-decode,
dismissal and initialized-layout assertions. Full LG service and bilingual
browser/size/audio/HDMI-mock suites pass. No shipped LG app/service source or
version changed. Prevention: explicitly control transient startup test boundaries.

[Validate](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44415872877),
[Companion](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44415872904) and
[Apple core](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44415872954)
all passed on 666ce6a. The final compact-UI/startup-test checkpoint is gated by the
[current PR checks](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/9/checks)
before handoff. The fixed phone export is 0.2.0/code 6, SHA256 prefix
`D6B4983DA58E3CF3`, with all 26 exact recordings verified. Physical owner review
remains open; the PR stays draft and no release/device deployment was performed.


## Phone feedback pass 3 checkpoint — 19 November 2026

Worktree feature/companion-installation, following requirements commit 75f5017;
not a final build or physical acceptance. Phone build/lint and test APK pass after
cohesive UI ownership/navigation changes. Shared JVM/JS/Java/legacy-ES5 and
companion-core rules pass. Both installer hosts build/lint. New native fresh-use,
resumable onboarding, real appearance Cancel, widget AppWidgetService host
bind/update, common TV platform/source, Qibla screen and cancelled late-result
checks pass in English/Arabic. Existing bilingual TV drafts/editor/install
handoff, phone settings, v1/private-audio migration, release/channel/atomic-import
failure cases and pinned-TLS/LAN checks pass on the owned muted phone emulator.
Private logs: pass3-cohesive-{default,journey,phone,custom,public,lan}-2.log,
pass3-audio-navigation-build.log, pass3-cancel-build.log and
pass3-shared-final-check.log. An earlier default invocation failed in am argument
parsing before instrumentation; the successful invocation uses explicit user 0.

Remaining: final phone audio/appearance and installer palette polish, enlarged
text/landscape visual review, high-risk whole-repository audit completion,
affected TV/LG/preview union checks, final artifacts/docs/CI. Public binary
transfer still lacks a published release. No physical sensor/OEM alarm/TV
picture-sound acceptance or iOS app is established. Exact next action and scope
remain in PHONE-WORK.md and PHONE-PASS3.md.


Follow-up checkpoint: both installer hosts build/lint after the per-host palette
change (pass3-palette-build-3.log). Actual bilingual setup keeps the selected
Dusk palette; prepared Back, cancelled local picker, incomplete/success handoff
and existing bindings pass (pass3-palette-native.log). English/Arabic screenshots
were inspected. Shared Qibla coordinates now match the pinned Adhan source;
JVM/JS/Java/legacy-ES5 pass (pass3-qibla-coordinate-check.log). This remains an
intermediate checkpoint, with final acceptance work listed in PHONE-WORK.md.


## Phone sound and layout checkpoint — 19 November 2026

Draft voice sheets audition without saving and stop on Cancel. Actual native
playback checks pass for global/per-prayer custom audition independent of saved
inheritance, real alarm delivery, 30-second private audio cutoff, twelve-second
preview, focus/Stop and disabled cleanup. Bilingual UI Cancel/Apply persistence
passes (pass3-audition-ui-2.log, pass3-audition-runtime.log). Shared native choice
styling replaces duplicate adapters. APK import validates a real APK through
Android PackageManager while staged before atomic publication
(pass3-native-archive-parser.log).

Extended 80–150% native sizing checks pass with 1.3× OS text in portrait and
verified 2400×1080 landscape, covering saved editors, voice sheets, actual widget
timetable, appearance Apply and pinned onboarding actions. Final captures were
inspected for Arabic shaping/RTL, action visibility and real artwork/times.
Widget preview uses system text scale and the app font. Exact successful logs:
pass3-widget-portrait-os130.log and pass3-verified-landscape-os130.log.
An earlier rotation-setting run stayed portrait; pass3-widget-landscape-os130.log
does not establish landscape. The runner now supports an explicit orientation
assertion; CI requests portrait. Session display settings were restored.

LG ES5/service/TLS/browser matrix, preview tokens/data/tests/build, Android TV
size/lint/test-APK, both installer hosts and 47 root Python/Java regressions pass
on this source checkpoint. Logs are pass3-webos-{tests,browser}.log,
pass3-preview-{tokens,data,tests,build}.log, pass3-tv-installer-build.log and
pass3-python-regressions.log. See PHONE-AUDIT.md for inspected boundaries,
corrected findings, retained meaningful tests and explicit uninspected areas.
These are intermediate local results; final-head CI/export and physical acceptance
remain distinct. No public release or physical operation was performed.


The native phone-to-LG UI fixture also passes over actual local RSA/TLS/settings
patches: Apply/Cancel, 5% volume, reconnect without upload, transport loss and
bilingual stale cache (pass3-lg-phone-live.log; approval injected, no TV).
Final local fresh-use/widget-host/TV-source/Qibla sensor-event and custom migration
rechecks pass (pass3-final-{journey,custom}-local.log). Qibla's visible arrow and
single calibration guidance were inspected in English/Arabic. Preparation steps
now have separate spacing; build/lint and the complete bilingual journey pass
pass3-guide-spacing-{build,native}.log. Generator 8/8 passes after final copy.
Lint has no errors; retained warnings concern the existing SDK target, dynamic
icon lookup/resources, launcher artwork and explicitly pinned/custom or upstream
ADB TLS managers. This is not a dependency upgrade or store-publication pass.


Shared phone labels/choice rows now use the app's selected paragraph direction,
rather than relying on the system text locale. This corrects mixed Arabic/English
instruction order. After the correction, build/lint, bilingual first use/real
widget host/Qibla orientation events and enlarged-text portrait/verified-landscape
matrices pass: pass3-explicit-text-direction-build.log and
pass3-rtl-{journey,portrait-size,wide-size}.log. Arabic preparation and compass
renders were inspected. The guest display configuration was restored in finally.


## Android 0.3.0 delivery candidate — 19 November 2026

Phone 0.3.0/code 8 and standalone installer 1.1.3/code 6 build/lint/test APK pass
(pass3-delivery-build.log). Final runtime review separates validation, media
session/notification, player preparation and output; private audio validation is
focused and standard Consumer replaces redundant callback interfaces. Existing
native suites cover all changed callers, without new trivial tests.

Owned-emulator checks pass on this APK: default bilingual TV/editor/handoff,
fresh/resumable first use, widget host, real Qibla sensor callback, phone UI,
private/v1 migration, staged APK parsing, public channel/integrity failures,
location denial, approximate and precise detection, calibrated travel refresh,
alarm permission denial and actual AlarmManager/foreground audio/custom cutoff/
audition/Stop/focus/duplicate/late/disabled cleanup. Private logs:
delivery-030-{default,phone-journey,phone-ui,phone-custom,public-release,
phone-location-denied,phone-location-approx}.log and
delivery-030-{phone-location,phone-denied,phone-runtime}-2.log. The first precise
fixture incorrectly expected travel refresh from unseen onboarding; the corrected
fixture marks/restores the existing-user state and retains all location/calibration
assertions. No failed result was counted as a pass.

Exported phone APK: 34,208,723 bytes, SHA256
3548858DCCAEF3472324FCD2A3F8E9F2F54584BCFEE19B85D5E049C773763D97.
The certificate matches the previous review build. All 26 recordings and 25 notice
files match canonical/source bytes. GitHub published releases rechecked empty.
Final-head CI gates handoff; its authoritative results and links belong on draft
PR #9. Physical/OEM, public binary transfer and iOS-app limits remain unchanged.


## Combined review handoff validation

Implementation commit **4c8e38d** passed all source-head CI:
[Validate](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44473850009)
(including Required checks),
[Companion](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44473849927)
(actual API 34 native suites), and
[Apple core](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44473849807)
(native macOS rule tests plus iOS device/simulator libraries). Pages correctly
skips PR deployment. The documentation-only delivery head must also be green;
its authoritative final results are on [draft PR #9](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/9).

The fixed local export remains phone 0.3.0/code 8, hash and signing identity as
recorded above. Final native screenshots were inspected at ordinary size and
kept in ignored primary state/builds/companion/screenshots; enlarged-text examples
stay in this worktree's state/. No captures, credentials, raw reference media or
outputs enter Git. The owned emulator was stopped only after matching its launch
receipt/process/command/creation time; the private stop receipt confirms exit.

This completes the implementation/local/CI review pass, subject to the final
head gate. The owner will test one combined build and supply feedback. Keep the
PR draft and stop implementation after handoff. Physical/OEM, actual public binary
transfer and iOS-app acceptance remain unclaimed; the pre-existing next-TV audio
finding stays explicitly recorded in PHONE-AUDIT.md.


## Phone design revision 2 — 21 November 2026

Design artifacts only in docs/design/phone-polish; production app unchanged.
125 states cover all 15 current route families. check.cjs passes graph/route,
permission-flow, prayer enable/sound/length, Hijri month/year boundaries,
Qibla bearing and research-anchor assertions. JavaScript syntax and diff
whitespace checks pass.

Browser checks: disable/re-enable prayer configuration; Short tone disables
Adhan audio while retaining vibration; save Tuesday independently; next/previous
Hijri month and selected civil day; Hijri-first widget/Gregorian toggle; TV
media targets aligned horizontally at 48px with 10px gaps. Four OSM tiles
loaded on explicit request with the computed Cairo-to-Kaaba route. Transparent
welcome branding and calendar renders inspected. Private captures under state/
phone-polish-r2-*.jpg.

Ending-preview timing math is checked; actual audible cutoff, sensors, native
permission/OEM behavior and scheduled delivery were not accepted. Dates use
browser Umm al-Qura; prayer times are illustrative fixtures, not calculations.
Exact-alarm fallback, existing schedule migration and production map provider/
privacy remain implementation work. No APK, release, deployment or merge.

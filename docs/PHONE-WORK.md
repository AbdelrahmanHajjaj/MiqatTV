# Miqat phone app delivery ledger

Updated: 2026-11-21. Branch feature/companion-installation, draft PR #9.
Latest scope: direct device-audio selection after immediate page settings; WORK.md owns next action.

## Device adhan selection — 0.5.8/code 24

Choose audio from device is always visible in every phone voice picker, rather
than hidden until the list selects Custom audio. It reuses Android document
selection and bounded private import for the default voice and each prayer.
Existing saved custom recordings remain reusable; failed imports retain them.

## Immediate appearance and widgets — 0.5.7/code 23

The owner confirmed that full-page settings should save immediately too. Phone
appearance/display, widget preferences and TV appearance now persist each valid
change; Back retains it. Placed widgets update directly. Page-level Apply/Cancel
is removed; Android Add completion and validated custom-color dialogs remain.

## Immediate controls and Home cleanup — 0.5.6/code 22

Numeric step buttons now commit each change, instead of replacing one unsaved
number draft when another field opens. Phone typing validates/saves without
replacing the focused field; invalid/off-step input keeps the last valid value.
Appearance and widgets retain their full-page Apply/Cancel previews. TV step
buttons use the existing baseline/conflict/offline guards and confirmed save path.
Welcome ad/account copy, the reference below the verse and Home location success/
confirmation narration are removed. Location setup and actual errors remain.

## Strip highlight — 0.5.5/code 21

Owner clarified that the border follows the next prayer, matching Home. A visible
rectangular outline fills its column. Overnight Fajr uses tomorrow's actual time;
other widget layouts keep their existing behavior. Native transition/border-pixel
regressions and bilingual previews pass; exact-head CI is tracked by PR #9.

## Home/calendar/strip refinement — 0.5.4/code 20

Remove the extra verse-reference word. Calendar selection now stays in the popup;
Home no longer owns a hidden browsed-date page or restores its obsolete date state.
Done/Back/cancellation expose the complete live Home. Fresh settings default to
12-hour time while migrations keep saved choices. The strip retains both enabled
dates and expands prayer columns through its height. Native evidence is recorded
in EVIDENCE.md; keep the existing PR draft for owner acceptance.

## Home, location and calendar — 0.5.3/code 19

Owner approved both headers: toolbar greeting and exact canonical TV Quran excerpt.
Native Home now puts the date and one city action inside the prayer hero. A
permitted city action refreshes in place; denied access opens compact full Location
settings. Calendar event markers, selected-day labels, month event rows and one
relevant Home line use a canonical five-event catalog and corrected calendar.

Automatic location while closed is a fresh optional opt-in with Android background
permission. A persisted periodic job obtains bounded fixes, preserves prayer
calibration and rejects late results after manual changes or opt-out. Schema 6
preserves v1–v5 preferences without expanding old foreground consent.

Native acceptance and final-head CI are recorded in EVIDENCE.md. No physical-device
deployment, merge or release is part of this review delivery.

## Recording feedback — 0.5.2/code 18

Owner requested simpler Home date entry without arrows, one-row weekdays, stable
settings updates, a full next-prayer widget card and default-first style choices.
Local native checks pass: retained root/first-frame scroll, full weekday targets,
calendar entry and selected-day timetable, responsive widgets with canonical
fonts at OS text 1/1.5/2, and 80–150% portrait/enlarged-text landscape. Short-window
voice selection also retains reachable actions. Build/lint: 0 errors, 41 existing
warnings; two next-widget picker images regenerated. Schema/TV policy unchanged.
PR #9 owns final-head CI. Exact 0.5.2 review artifact is recorded in EVIDENCE.md.

## Refinement candidate — 0.5.1/code17

Grouped prayer/settings rows, compact numbers/language, spaced weekday circles,
inline day overrides, first-frame sheet placement and combined calendar/timetable
are implemented. Home arrows are tested with pointer events. A dimmed Home scene
can be disabled, and portrait scenes reuse the canonical artwork. Widgets adapt
by allocated dimensions with modern size maps and legacy resize bounds, preserve
per-instance styles and include canonical icons/serif heroes/highlights. Six
reviewed native picker images were regenerated. Schema5 preserves older values.

API37 native UI, migration, setup/widgets, companion TV controls and 80–150%
sizing pass in both languages, including Android text1.3 portrait/landscape.
Minimum140×100 widget text is checked at text scales1/1.5/2. Preview and LG
ES5/browser checks pass. LG0.2.29 carries matching Today highlights; no Android
TV rendering change was needed because its existing choices already have gaps.

Artifact identities and limitations are in EVIDENCE.md; PR9 owns final-head CI
receipts. Keep this PR draft for owner device review and continue this refinement
checklist with subsequent feedback. No merge, release or physical deployment.

## Approved implementation — 0.5.0/code16

Owner approved the revision-2 English design and requested full implementation.
The native Java Views now bind welcome/location/style, country/city and advanced
location, shared visible choices, per-prayer enable/sound/vibration/day overrides,
relative length/ending preview, real Hijri date browsing, explicit Qibla map,
Hijri-first widgets and supported-only TV controls. Canonical assets are reused;
no dependency or physical-device operation was added.

Schema 4 retains legacy settings, quiet-hour event boundaries and seconds caps.
Timing access is optional with disclosed inexact scheduling limitations. The
research/gallery remain design evidence; native acceptance is recorded separately
in EVIDENCE.md. Build/lint, portable policy, generators, bilingual UI/location and
journey and actual playback checks pass. Final-head PR CI remains the handoff gate.
The fixed latest review export is 0.5.0/code16, SHA256 prefix 067203783FCC5201.
No merge, release publication, physical deployment or iOS app in this pass.
Earlier candidate evidence below remains historical.

## Review candidate — 0.4.6/code 15

Owner rejected the prior interaction structure. PHONE-PASS7.md now owns scope.
Baseline native renders captured on 20 November: first use has large empty areas;
widget controls bury content below stacked selectors; LG preparation advances
one numbered instruction at a time. Implementation now combines location/alerts,
uses two first-use pages, direct app/widget visual rails with one live preview,
and numbered TV preparation plus relevant source/credentials on one page.
Short phone choices reuse compact rails; long choices retain search. Existing
feature regressions pass for bilingual setup/widgets/TV, appearance drafts,
filtered voices and native bindings. Enlarged-text portrait and landscape pass;
normal and enlarged native renders inspected. Widget Add reaches the correct
manual-add fallback on the owned emulator launcher (pin unsupported). Review
caught the old new-widget ID mismatch; the fix uses the OS invalid-ID constant.
0.4.6 exported and byte-verified; receipt in EVIDENCE.md. Final-head CI is tracked
on draft PR #9. No merge/release/deployment; stop for owner review.

## Previous candidate — 0.4.5/code 14

Latest owner scope is PHONE-PASS6.md, now implemented: dedicated phone-only
portrait signature artwork; three-page first use with contextual alert access
and combined theme/background preview; populated native widget-picker previews
and bilingual fallback images; independent background/opacity/shape/Hijri
customization with app typography/colors. Build/lint, generator/canonical checks,
bilingual native journeys/hosted widgets, enlarged-text portrait/landscape sizing
and actual scheduled playback pass. Exact APK receipt is in EVIDENCE.md.
Final-head CI belongs to draft PR #9 and ignored state receipts. Stop for owner
feedback after checks; no merge/release/deployment or follow-up scope.

## Previous candidate — 0.4.4/code 13

Latest owner refinements: swipe label centered in the prayer area; no duplicate
footer Next/Previous beside Continue. Top/system Back owns return navigation.
Native Android notification alignment/control positions remain OS-managed.
First use now has the canonical signature portrait background on every step,
with consistent brand chrome, a readability scrim and a smooth artwork join.
Native bilingual guided previews and Home renders pass. New assets/frameworks
were unnecessary; the existing background renderer is reused. Final CI remains
open: 14a8169 created the activity but did not observe window focus within its
bound. The shared helper now includes active package and lifecycle/power state
in that failure only; assertions and timeouts stay unchanged.
API34 repeated journeys pass with CI animation settings. Later CI reached the
runtime but failed waiting for a new audition activity. Local system logs prove
START_DELIVERED_TO_TOP: the activity was reused. Reopening after a display change
reproduced the same blocked first launch. The existing native launch helper now
owns a separate task and bounded creation/focus; both runtime entry/audition and
first-use reuse it. Local actual runtime and portrait journey pass. Temporary
window diagnostics were removed; original focus/Back/playback assertions remain.
Final API34 portrait runtime and bilingual journey pass; centered English/Arabic
adhan and contextual first-use renders inspected. Final-head CI is pending. Prior
Validate and Apple core on 138c3f5 passed; prior phone CI failed at the diagnosed
launch and does not certify the fix.

- Native bottom swipe-up adhan handle replaces centered Stop. Accidental taps,
  short/horizontal/cancelled pulls keep playing; completed upward release and
  explicit accessibility activation stop. Background player/session unchanged.
- Shared bottom actions serve onboarding, phone/TV numeric editors, appearance,
  widget setup, pairing and LG setup. Contextual Skip/Cancel accompany one primary;
  short landscape uses a compact row. Dialog actions stay in bottom sheets.
- Phone text editors request no fullscreen IME extraction. Short landscape
  headers collapse outside the layout traversal; the focused input and footer
  remain above the real keyboard. Bilingual native keyboard check now passes.
- Removed repeated instructional copy and unused hints; concise action labels,
  actual status and required TV access instructions remain. Future-copy rule
  recorded in AGENTS.md. Permission requests use onboarding's bottom action.
- Build/test APK/lint pass (0 errors, 43 existing warnings); generator 8/8 and
  canonical catalogs pass. Native bilingual UI/keyboard, widget Cancel, actual
  alarm/swipe/accessibility, portrait/wide sizing pass. Updated first-use/widget journey,
  default TV bindings, actual-alarm runtime and local LG SSH fixture also pass.
  Final-head CI status is tracked in draft PR #9.
- Exported 0.4.4 review APK, 25 exact recordings/notices and retained signing
  identity checked; exact receipt in EVIDENCE.md. No physical deployment.

Next: require final-head Validate/Companion/Apple core, update draft PR and
private receipt, stop the verified task-owned emulator, then await owner feedback.
Local native/rendered acceptance is complete. No merge,
release or unrelated new work. Private logs/captures/ownership stay in state/.

## Historical implementation notes

The dated sections below retain prior findings and acceptance limits. Their
old “active” or “next” wording is superseded by the current checklist above.

## Bound the diagnosed audition activity launch

Replace only audition's unbounded startActivitySync with the existing native
ActivityMonitor pattern: start on main, require actual MainActivity within 5s,
then require window focus within 5s. Wake/unlock is checked at this exact call.
This preserves actual UI audition and fails with a specific launch/focus error
rather than hanging a whole CI command. No product change, generic wrapper,
new dependency or skipped assertion. Compile and require final-head CI; stop
superseded phone runs once their useful diagnostics are preserved.

## Unlock at UI use, not before delayed playback checks

Move the wake/unlock assertion into audition(), immediately before its
startActivitySync. The preceding focus/replacement/twelve-second preview checks
can let Android's screen timeout expire again; a successful unlock earlier in
the suite is not the UI launch precondition. Also assert interactive state.
Production APK/hash unchanged; no new helper or relaxed check. Compile/push and
require final-head CI. Preserve f29ef78 run result if it finishes; it is not final.

## API34 runtime root cause and correction

746a73c Validate/Apple pass. Diagnostic Companion was deliberately cancelled
after its 90s snapshot: main thread idle, instrumentation blocked in
Instrumentation.startActivitySync at PhoneRuntimeCheck.audition440/run280.
Private evidence state/adhan-art-runtime-thread-snapshot.log. The preceding
show-when-locked adhan checks leave keyguard active; ordinary MainActivity
cannot resume for audition on API34. Restore test-owned wake/unlocked state
immediately after lock-screen checks, assert keyguard dismissal within 5s,
then continue all remaining real audition/custom/channel checks. Production
playback is not blocked. Remove the now-unneeded diagnostic timer. Preserve
native assertions and 420s command bounds. Compile and require final-head CI.
Phone APK/hash unchanged; local emulator remains stopped.

## Remaining API34 runtime stall

4c7a6d6 Validate and Apple pass. Companion passes every preceding suite, including
Coming soon event/input-root Back, migration, packages, location, native/LAN/UI,
sizing and permission denial, then phone-runtime times out at 420s. Private log
state/adhan-art-phone-window-failure.log. Moving accessibility predicates off
main was necessary but insufficient. Add one cancellable daemon timer to emit
main/instrumentation thread stacks at 90s; do not change product behavior,
assertions or deadlines. Compile and inspect the next CI stack before another
fix. API37 local runtime/render passed; API34 remains unverified. Only API37 image
is installed locally and SDK command-line tools are absent; no new SDK downloaded.
Phone review artifact/hash unchanged. No task emulator running.

## Dialog input-window readiness

b6db36f Validate passes every required job, including corrected TV emulator;
Apple passes. Companion hits intermittent Coming soon restored-focus failure
before runtime. Losing activity focus is not proof the new dialog has gained
input focus. Keep the actual AlertDialog event, then on API29+ inspect this
process's public WindowInspector roots and require the dialog root's own focus
before real Back injection; afterwards require root detachment and activity
focus. API26-28 retains existing event/focus checks. No click-to-dismiss shortcut,
sleep, retry or assertion waiver. Test APK compilation and new-head CI required.
Phone APK/hash unchanged; no emulator running locally.

## Phone CI main-thread accessibility correction

Superseded Companion1950827 was cancelled after runtime stalled; logs show all
preceding suites (including real Coming soon Back) passed and phone-runtime
started. Runtime await() executes predicates on the app main thread, but three
predicates call UiAutomation.getRootInActiveWindow, which can synchronously query
that same process. Move OS accessibility waits to the instrumentation thread;
retain app-state checks on main and the five-second window deadline. This removes
a self-query deadlock risk exposed on API34. Bound each CI instrumentation command
to 420 seconds, matching the local helper limit, so a future stall retains its
logs and fails instead of waiting hours. No required check skipped or weakened.
Phone production APK/hash remains unchanged. Compile and require new-head CI.

## TV fixture readiness follow-up

d7ed95a refresh assertions pass but focus navigation still fails at com.android.stk
in Arabic. The fixture uses tiles.indexOf(getCurrentFocus()) while tiles contains
null placeholders for off-screen apps: transient null focus can falsely equal a
target slot. It also recomputes its guard limit from a catalog temporarily cleared
by rebuild. Match production's null-focus handling, capture the fixed finite step
budget once, and await a loaded catalog before each real remote step. Preserve
all focus/order/menu assertions; no arbitrary sleep, direct target focus or test
waiver. Compile and require new-head CI; artifact/hash unchanged.

## Final-head CI follow-up

1950827: Apple/core/preview/Android build/LG pass; Validate TV emulator fails in
FavouriteUiDeviceCheck.open at com.android.stk. Its verifyOrder predicate posts
an extra catalog rebuild but returns before the rebuild finishes. The next
remote-navigation loop can run while packages/tiles are cleared, exhaust its
guard and then only poll without navigating. Keep the extra refresh and all
order/focus/marker assertions: explicitly build after the matching snapshot,
then await that same snapshot again before returning. Test-only correction;
phone APK/hash unchanged. Build test APK then require a new-head full CI run.
Companion1950827 is still running; preserve its result separately. No blind retry,
production TV change or waived test. All local emulators remain stopped.

## Active 0.4.3 artwork/metadata and input barrier

Owner requirements are implemented: optional adhan screen uses the canonical
mihrab, selected background and theme; notification supplies matching media
artwork, prayer name, scheduled time and actual playing voice. PhoneBackdrop
reuses the existing compositor; PhoneTime shares native formatting across UI,
widgets and media. No new dependency, renderer policy, player or service.

Build/lint pass (0 errors, 43 existing warnings). Native actual-alarm/media suite
passes with scheduled timestamp deliberately different from wall clock, actual
SystemUI Stop, pause/resume, allowed full-screen alarm, Back confirmation,
stale callbacks and volume shortcut. Notification and bilingual Dusk/peaks
screens were visually inspected. English lock-screen capture initially caught
the transition; a real frame-commit barrier now precedes screenshots and its
render is confirmed. Logs: state/adhan-art-{final-build,runtime-frame,phone-ui,
size-portrait,size-wide}.log. Both 80–150% matrices pass at Android font1.3.

CI3235806 had passed the dialog event but failed restored focus after
Instrumentation Back. Real Back/volume input now uses synchronized
UiAutomation.injectInputEvent; assertions remain unchanged. Local no-capture,
animations-disabled journey passes. Final captured bilingual journey also passes (adhan-art-journey-capture.log).
New-head CI is still required.

Wide full-screen-denied runtime passes (adhan-art-runtime-wide-denied.log);
its large-text render was inspected. Final completed Home captures checked.
Owned read-only emulator5564/PID32500 was verified by PID/start/AVD/port and
stopped; state/adhan-art-emulator-stop.json records cleanup. No task emulator
remains. Accepted 0.4.3 screen/notification captures exported beside the APK. APK0.4.3/code12 exported: 32,358,695 bytes, SHA256
CBDB11AB24404E85694B25260E897244E5D77ABD599248E523E74CF6D72E69B2.
25 exact media and 25 notices verified; signing certificate unchanged.

Next: commit/push, require final-head
Validate/Companion/Apple CI, update draft PR and private receipt, hand over one
APK and stop for owner feedback. No physical deployment/release/merge or new work.

## Active 0.4.2 — final CI/handoff

All owner steering is implemented: Home allows a short scroll, separates date/
prayer/actions, lowers locked modules, places tomorrow Fajr before tools and
integrates location. Setup prompt is absent after completion/cold launch.
Balanced card uses canonical highlights and explicit text alignment; actual
appearance preview uses the same labels/palette. No TV/iOS scope expansion.

Scheduled adhan continues with screen off and other apps. Notification Play/
Pause retains decoder position and remaining duration; Stop/dismissal ends it
where Android permits dismissal. A default-off full-screen view uses Android
alarm access, shares the existing session and closes when it ends. Background
touch/app switching do not stop audio. Explicit Stop-and-close does; real Back
confirms. Default-off volume shortcut works on that visible screen over keyguard.
Power key is OS-reserved; no screen-off heuristic, hidden volume API, overlay,
accessibility service, second player or idle observer was added. Foreground
notifications may be pinned by Android; Stop remains available.

Strict schema3 migration preserves exact v1/v2 field sets and defaults both new
toggles off. Runtime/preview/media owners stay separate. Android13+ notification
buttons derive from PlaybackState: one publisher adds custom Stop for initial/
paused/resumed states and its callback, alongside legacy notification actions.

Final source APK0.4.2/code11 exported to primary state/builds/companion/
Miqat-latest.apk: 32,553,634 bytes, SHA256
EC4613AF1DA4AA5519178272AB37ADE4FB120BAB42132DE1C40C853955EB62AB.
25 media/25 notices exact; prior certificate matches. EVIDENCE.md owns receipt.
No new production dependency. Source-head fb715c2 was pushed, but MediaSession
Stop fix and final docs/test changes still need their final commit/push.

Validation PASS:
- card-media-final-build.log and card-media-shade-build.log: build/lint/test APK.
- card-media-shade-runtime.log: actual screen-off alarm, other-app continuity,
  visible SystemUI Stop clicked and screenshot inspected, pause position/custom
  remaining duration, deliberate close, real Back, stale callbacks and volume.
- card-fsi-runtime-allowed.log: actual scheduled allowed-FSI screen opening over
  keyguard via ActivityMonitor (no manual fallback on failure).
- card-final-runtime-wide-denied.log: denied-FSI wide/enlarged text still plays,
  manual screen opens and Stop remains reachable.
- card-background-custom.log: strict v1/v2 preservation/malformed-record cases.
- card-final-{phone-ui,journey}.log: final bilingual UI/setup/preview/completion.
- card-final-size-{portrait,wide}.log: all80-150% with OS font1.3. The later media
  Stop fix changes no Home/layout rules; its exact APK passes runtime above.
- generator8/8, canonical/shared validation; lint0 errors/43 existing warnings.

Private rendered sources: card-final-portrait (normal Home), card-fsi-review
(normal optional screen), card-media-shade-review/adhan-notification-playing.png
(real active system media controls). Accepted copies go to primary screenshots/
0.4.2-*.png. The initial media capture was premature and is not delivery evidence.
First runtime stall was fixture Activity reuse before an existing fresh-launch
case; fixed by finishing/waiting for its Activity. Assertions remain intact.

Current owned emulator is alive, idle after all checks: receipt
state/card-media-emulator.json, PID29728, creation2026-11-19T13:20:39.2795369Z,
port5564/AVD Medium_Phone_API_37.0, read-only/muted, portrait1080x2400/density420/
font1.0. Old PID38152 is stopped. Verify exact current process/start/command
before stopping; save ignored cleanup receipt. No other instrumentation runs.

Final media fix was pushed as de346927e48bc5558dbc876aef06b24a18804fe1.
Apple CI passed; Companion44534299812 failed before runtime at the existing
Coming soon active-root text poll (PhoneJourneyCheck222). Prior4aeb had the same
snapshot timeout. No product assertion was waived and no blind retry.
The fixture now registers executeAndWaitForEvent before the Roku click, requires
the actual AlertDialog event/title, and retains focus loss, real Back and restored
focus. It reports observed window events if the expected event is absent.
Native no-capture/animations-disabled bilingual journey PASS:
state/card-dialog-event-native.log; test build/lint PASS:
state/card-dialog-event-final-build.log. Source APK is unchanged EC4613.

The final dialog-only verification emulator is currently alive/idle: exact
receipt state/card-dialog-emulator.json, PID4460, creation
2026-11-19T13:39:55.4718108Z, port5564, same AVD/read-only/muted. Old PID29728 is
stopped; do not reuse that receipt. Finish final test/docs commit/push and new-head
CI, then stop only this verified emulator and update private cleanup receipt.

Next: commit/push validated final fix/docs; require final-head Validate,
Companion and Apple-core CI; update PR exact head/runs; export accepted captures,
stop only the verified owned emulator, and hand over one APK. Keep PR draft.
No merge, release or physical operation. Stop after handoff for owner feedback.

## Active Home spacing follow-up

The owner found fused/squashed Home buttons in the 0.4.0 screenshot. Short
scrolling is explicitly allowed: lower locked destinations, integrate location
in the prayer card, and show tomorrow Fajr before secondary actions. The setup
prompt must disappear after completion. This supersedes the earlier density
interpretation; do not compress every Home element to fit a single viewport.

0.4.1/code 10 implementation is limited to PhoneHome plus focused existing
journey/size checks. Setup already persists completion; verify prompt absence
and publish completed-state captures rather than the intentionally unfinished
phone-ui fixture. Build/lint, bilingual journey and both 80–150% size matrices with OS font 1.3
pass (state/home-followup-*). Initial portrait precondition caught a wide boot;
explicit phone metrics fix the harness setup, with strict assertions unchanged.
Final portrait journey/captures and fixed APK export are verified. Next:
commit/push, final-head CI and draft PR receipt. Stop
for owner testing after handoff; no unrelated feature or physical operation.

## Active pass 4 — final verification/delivery

Approved scope is PHONE-PASS4.md, all 12 items; the active goal tracks the same.
Implementation commit 6acd892 is pushed. Validate (44518150917) and Apple-core
(44518150929) CI pass. Companion (44518151037) fails in its API-34 journey fixture:
it injects Back after Activity focus loss but before the Coming soon dialog is
the active input window. The fixture now waits for that actual dialog message
through UiAutomation, then still requires Back to restore Activity focus.

Final audit reproduced a real widget-preview scale regression with a new native
assertion (state/pass4-widget-scale-red.log): the preview used the app's text
scale instead of the launcher's. WidgetUi now inflates with application context
and sizes its preview using system font scale. The strict native font assertion
remains. Current clean build: state/pass4-scale-fix-build.log.

Final native checks pass: state/pass4-widget-scale-portrait-green.log and
state/pass4-widget-scale-wide-green.log include the strict preview-font assertion
at every app size in both languages with OS text 1.3. The no-capture,
animations-disabled journey passes in state/pass4-dialog-journey-green.log.
Final clean APK has been exported; EVIDENCE.md holds its exact hash. Commit/push
these fixes and require all new-head CI green before handoff.

The remaining implementation is complete locally. Current candidate is phone 0.4.0/code 9,
installer 1.1.4/code 7. The exported primary APK hash is in EVIDENCE.md and ignored
state/pass4-artifact.json. Keep PR #9 draft for the owner's combined feedback.

Compact controls/floating main navigation, one-decision first use/Skip, carousel
portrait previews, explicit motion effects, Kaaba compass, independently
configurable native widget variants and full-screen one-step TV/LG setup are
implemented. Al Afasy is removed from choices and distribution; old local choices
migrate without resetting other settings. An older TV's reported metadata is
readable but never silently rewritten or offered again. Radio is separate.

Essential local results are in EVIDENCE.md. Owned emulator runs cover real widget
binding/configuration/Cancel/resize/strip, native LAN/legacy metadata guards,
bilingual editors/handoff, actual alarms/private audio/denial and cleanup, plus
all app text sizes with OS font 1.3 in asserted portrait/landscape. Core, shared
metadata, TV build/47 regressions, LG service/browser and preview checks pass.
The final clean phone package includes the preview context correction and
passes its native journey. The retired media bytes are removed.
All 25 recordings/25 notices match canonical source. No private logs/captures ship.

Next: final staged review and implementation commit/push; finish final-head
Validate/Companion/Apple CI and exact PR receipts, and complete the exported
capture/emulator cleanup. A final size/capture run can finish while CI runs;
private logs are state/pass4-*. Current owned emulator receipt is
state/pass4-emulator-session.json; verify its process identity before stopping.
Normal source captures are state/pass4-040-portrait and state/pass4-040-wide;
the former's English 80% captures were already overwritten by the wide run.
Check actual PNG dimensions; selected accepted captures are exported separately.
After the CI-gated combined handoff, stop and await owner feedback. No merge,
physical deployment, public release or automatic unrelated follow-up.

## Authority and delivery

The owner approved continuing sequentially through installation and the fully
standalone Android app, durably recording progress across compaction. They will
test one combined build and provide a complete feedback list. Do not stop after
each slice to request physical feedback. Preserve existing pairing/settings and
TV behavior. No physical-device operation or public release is authorized by
this instruction. Keep raw logs, keys and captures in ignored state/; concise
validation belongs in EVIDENCE.md. Commit validated milestones; never squash.

## Product decisions

- One repository, one Android phone app (existing `tv.miqat.companion` identity).
  Retain existing native Java Views, Kotlin product rules, shared data/design/media.
  Android TV stays Java Views; LG stays legacy ES5. No UI framework rewrite.
- Phone works without a TV, cloud account or network. Phone settings and each
  TV's settings are independent. TV installation and ordinary authenticated LAN
  pairing remain separate permissions/credentials.
- Android TV: reuse guarded ADB installation, cleanup, backup and recovery.
  Preserve standalone installer and its separate private records.
- LG: guided Developer Mode preparation, user-approved SSH identity, local IPK
  selection, install/update verification and launch; protect existing app data.
  No root, firmware change, system Home replacement or Android-style debloat.
  Explain Developer Mode expiry/renewal and recovery without promising standby.
- Align installer hierarchy, typography, surfaces, spacing and controls with the
  companion. Keep English/Arabic, joined text, RTL, large text and reduced motion.
- Standalone prayer: offline city/custom coordinates, timezone, calculation
  method, Asr school, high-latitude handling and offsets; daily schedule, next
  prayer and tomorrow's Fajr. Reuse canonical choices and astronomy source.
- Per prayer: off/notification/adhan, voice including Fajr choice, vibration and
  advance reminder. Global sound level, bounded duration, preview/Stop, notification
  status and permission repair. Preserve sunrise as informational.
- Native scheduled alarms, persistent duplicate/late-event prevention, foreground
  audio only while playing, audio focus/interruption cleanup, Stop notification,
  boot/time/timezone/settings rescheduling. Report disabled permissions honestly;
  no silent DND bypass or permanently running idle service.
- Phone appearance: shared themes/palettes/artwork, language/digits/clock/text size
  and a phone-appropriate background. Reuse static assets; adapt composition and
  drawing to portrait rather than promise every TV renderer is ported.
- iOS later: maintain portable boundaries and add a lightweight macOS shared-core
  compile/test check if viable. No uncompiled Swift app or untested iPhone audio
  claim. Android is the first standalone deliverable.
- Pass 3 explicitly adds Qibla and widgets. Quran/adhkar are locked placeholders;
  accounts, feeds and unrelated features are outside scope.

## Ordered implementation and acceptance

1. [x] Android installer integration: commit `7415045`; shared runtime, private
   host, bilingual entry/cancel/incomplete/success handoff. Local build/lint,
   core/43 Python regressions and owned-emulator UI/LAN checks pass. Validate CI
   passed; companion CI exposed an asynchronous Activity-result test race on API
   34. The fixture now waits for the actual resumed pairing page with a deadline.
2. [x] LG installation: research official CLI protocol, choose minimal SSH client,
   implement strict input/package bounds, host-key confirmation/pinning, private
   credentials, local package install/verify/launch and recovery/status guide.
   Tested JSch 2.28.8 against the local SSH/HTTP fixture through the owned phone
   emulator: unknown/changed host key rejection, bad passphrase, real IPK transfer
   and MD5, failed install with unchanged version, explicit retry, version readback
   and launch pass. Private keys/passphrases are session-memory only; only the
   approved host key persists. Three pure-Java/Python archive/version tests pass.
   No physical TV changes. Sources: LG CLI 3.2.6 install/novacom/luna and its
   command-service.json; LG official Developer Mode/CLI documentation.
3. [x] Unify Android/LG setup presentation with phone styling; preserve safe
   cancellation/recovery. Native
   bilingual portrait setup/invalid-host/password-state/handoff checks pass;
   renders inspected, 46 Python/Java regressions and generator checks pass.
4. [x] Standalone phone prayer storage/calculation/settings and portable reminder
   policy. Phone-only metadata is canonical in shared/data/phone-ui-contract.json.
   Six portable policy tests pass for modes, before/after reminders, duplicate
   claims, exact late boundaries and simultaneous events. The native adapter uses
   the same Adhan 1.2.1 source and minute-flooring adapter as TV; JVM checks pass
   for the frozen Cairo vector, all methods/offsets, explicit civil zone, 23-hour
   DST day and unavailable polar results. Eight generator tests and native
   independent storage/type/range/TZ validation pass. Phone defaults keep alerts
   off and require location confirmation. No prayer UI/alarm playback is exposed yet.
5. [x] Native alarm/notification/audio lifecycle; UI permission repair and per-prayer
   controls follow in step 6. Actual owned-emulator alarm delivery, foreground
   notification/audio, Stop, focus interruption, disabled cleanup and duplicate/
   early/expired rejection pass. Private receiver recovers on
   boot/time changes; CI runs the same actual-delivery suite. Permission,
   reboot/time changes, duplicates, stale events and Stop/interruption regressions.
6. [x] Independent prayer Home/phone settings/appearance with existing TV areas;
   no TV dependency. Bilingual rendered/native tests pass for location confirmation,
   Arabic numeric Save/Cancel and step rejection, every canonical phone control,
   independent per-prayer modes, theme changes and persistence. Existing TV/setup
   native regressions pass. Hijri month symbols corrected after rendered review.
   Permission denial and private/direct-boot component checks pass; final matrix
   and CI remain in step 7.
7. [x] Local affected checks, bilingual portrait/landscape/large-text inspection,
   exact media/notices, private-data staged review, final PR scope and fixed
   `Miqat-latest.apk` export are complete. Final-head PR checks gate handoff;
   their authoritative result is recorded on PR #9. Physical acceptance awaits
   the owner's combined feedback. No release or physical deployment.

## Owner's mobile reference

On 19 November the owner supplied Athar (their daily app) as a standalone mobile
reference. Sources, learned patterns and future discussion candidates are in
[PHONE-REFERENCE.md](PHONE-REFERENCE.md). This steers the current phone UI toward
a compact six-time daily overview and background-delivery repair help; broader
worship modules are recorded for feedback rather than silently added.

## Feedback pass 2 — approved 19 November 2026

The owner skim-tested the Android app: broad functionality is present, but phone
UI/UX needs substantial polish. They requested location detection, broader adhan
customization and public automatic TV package downloads, keeping local files in
Advanced. Asked to prioritize adhan areas, the owner answered **all three**:
per-prayer volume/duration/silent/vibration, custom audio/more voices, and weekday/
quiet-hour/travel behavior. Continue sequentially in this worktree/PR; preserve
TV controls and earlier validated work. No public release publication or physical
deployment has been requested. Public repository currently has only a draft
prerelease; there is no anonymous published APK/IPK release yet.

Ordered work:
1. [x] One-shot foreground location with approximate permission support, explicit
   location confirmation and city-search fallback; coordinates move to Advanced.
2. [x] Canonical GitHub public-release lookup/verified downloads for APK/IPK,
   ordinary setup as default and local packages in Advanced. Never embed tokens,
   install unverified/partial files, silently downgrade or uninstall to update.
   Website should link to the same release assets. Handle no public release honestly.
3. [x] Per-prayer volume/duration overrides, silent/vibration alert modes,
   weekday schedules and quiet hours. Custom user audio remains private on-device;
   no unreviewed media enters Git or distribution. Existing 14 approved recordings
   remain available; adding shipped recordings needs the established content review.
4. [x] Travel behavior: opt-in foreground location refresh and device/fixed
   timezone choice, with clear stale/denied/location-disabled states. No continuous
   background tracking or automatic religious travel rulings are implied.
5. [x] Phone UX pass: compact settings summaries, focused inline numeric editors,
   short choice sheets, clear first-run/location/alert/setup states, consistent
   surfaces/spacing and accessible Arabic/English, large text and RTL.
6. [x] Local affected checks, guide/evidence/PR scope and final review APK export.
   Final-head CI remains the handoff gate on PR #9; physical acceptance and public
   release publication stay open.

### Pass-2 implementation notes

Location is committed in ac510e9; public downloads in 47dbe87. Compact saved-value
rows, focused numeric editors, bottom choice sheets and stable root tabs were
implemented before adding the extra adhan fields. TV Remote/Settings now have
nested tabs. Keep the Prayer / Your TVs / Phone settings root bar stable.

Sound now preserves v1 preferences through strict v2 migration. Portable policy
handles weekday masks and overnight quiet hours; the shell plans a week ahead,
excludes suppressed events and rechecks eligibility at claim/output. Per-prayer
level/duration, silent/vibration channels and private custom recordings are bound
to the native controls. Custom files use bounded decoder validation, immutable
private content identities and one settings commit; existing selections survive
failed imports. The 14 built-in approved recordings remain unchanged. Travel
refresh is opt-in/foreground, preserving calibration and device/fixed timezone.

## Feedback pass 3 — approved 19 November 2026

Detailed binding requirements and engineering/compaction guardrails:
[PHONE-PASS3.md](PHONE-PASS3.md). Read it before resuming or changing pass-3 scope.

Owner rejected the partial standalone experience. Complete the prayer/adhan
journeys and phone polish, using the actual Athar recording for interaction and
audio-control references. Shipped audio still requires approved provenance;
reference recordings are not permission to redistribute Athar content.

1. [x] Unify Set up my TV / Pair new TV behind platform selection. Android/Google
   TV and LG webOS supported; Samsung Tizen/Roku and other unimplemented platforms
   visibly locked Coming soon. Preparation instructions precede release selection
   (latest default, stable/preview choices); Advanced switches to local APK/IPK.
   Continue into guarded setup, then explicit ordinary pairing. Preserve recovery.
2. [x] Guided fresh-install location, notifications/alarms and personalization;
   explicit permission decisions, resumable progress, no TV requirement.
3. [x] Phone theme/background previews and coherent daily prayer, audio/per-prayer,
   settings and TV journeys, in Arabic/English at supported text/window sizes.
4. [x] Audit repository entry points, contracts, runtime/security/storage, rendering
   responsibilities and tests. Fix concrete duplication/dead code/mixed jobs;
   share deterministic policy without speculative frameworks or weakening checks.
   Record findings, fixes and genuinely uninspected areas.
5. [x] Build/lint, focused native journey/failure checks and affected consumers,
   screenshots inspected, accurate docs/PR, final CI and single combined export.

Owner clarified scope: include Qibla and home-screen widgets now. Quran, adhkar
and broader worship modules have locked Coming soon placeholders; no content
or incomplete working screens. Add portable Qibla bearing to shared/core; keep
sensor calibration in the phone shell. Widgets reuse the offline schedule and
refresh lifecycle, without another scheduler or idle service.
Persistent quality rules are in AGENTS.md. No release/physical deployment/merge.

### Active pass-3 implementation / resume

Requirements/guardrails committed as `75f5017`; explicit goal is active. Guided
journeys/cohesive ownership checkpoint committed as `eca7a7c`. Pass-3
source is implemented in this worktree, not ready for export. New native owners:
TvSetup, PhoneOnboarding, PhoneAppearance, PhoneHome, PhoneLocationUi, PairingUi,
TvSettingsUi, TvRemoteUi, TvListUi and CompanionViews. MainActivity retains host,
lifecycle and navigation; no compatibility delegation wrappers were retained.
QiblaUi owns foreground sensors; PrayerWidget uses native RemoteViews and the
existing refresh lifecycle. Canonical metadata includes supported/locked TV
platforms, lock artwork and the fresh-install Fajr voice. Existing saved choices
are preserved. Core LocationRules, ReminderRules quiet policy, DisplayText and
QiblaRules are shared across JVM/ES5; OS integration stays in native shells.

Current source builds and phone lint pass: state/pass3-audio-navigation-build.log.
Both installer hosts built/linted in state/pass3-cancel-build.log. Shared JVM/JS,
Java interop and legacy ES5 pass in state/pass3-shared-final-check.log; companion
core passes state/pass3-companion-core.log. Canonical/generator checks passed.
Actual bilingual TV bindings/editor/draft/install handoff checks pass after all
extractions in state/pass3-cohesive-default-2.log. Fresh-use/resume, appearance
Cancel, real widget-host bind/update, common TV entry/source, Qibla screen and
late-success-after-cancel checks pass state/pass3-cohesive-journey-2.log.
The first cohesive default invocation failed before instrumentation (empty suite
argument / am user parsing); its replacement supplies explicit user 0. Do not
count that failed invocation as product evidence. Cohesive phone-ui, phone-custom, public-release and LAN checks also pass,
sequentially: state/pass3-cohesive-{phone,custom,public,lan}-2.log.

Current concrete fixes include atomic package import preserving previous bytes,
LG channel/cache identity, embedded Android Back returning to common source,
cancellable public downloads rejecting late callbacks, pinned onboarding actions,
real artwork previews without intrinsic-height inflation, accurate auto-digit
labels, single compass calibration hint and actual phone settings Back history.
Audio groups general settings, per-prayer effective voices, private import and
quiet hours. Actual audio/runtime regression still needs rerun after final edits.

Both installer hosts now follow an immutable per-host palette; the embedded
host reads the selected canonical phone theme and standalone retains defaults.
Bilingual themed native handoff, local-picker Cancel and prior bindings pass in
state/pass3-palette-native.log; English/Arabic rendered setup inspected. Both
hosts build/lint: state/pass3-palette-build-3.log. Earlier command attempts failed
before compilation (wrong project selection, then missing process SDK variable);
the successful command uses root tasks and ANDROID_HOME. Shared Qibla coordinates
now match the pinned Adhan adapter; JVM/JS/interop/ES5 pass in
state/pass3-qibla-coordinate-check.log.

Sound selection now uses PhoneSound for draft audition/Stop/Cancel/Apply and
private import, preserving scheduled settings. Shared native choice-row styling
replaces duplicate adapters. Global/per-prayer custom audition selects the
candidate's source, independently of its saved voice or volume override. Native
UI and actual playback/alarms/custom-source/Stop/focus/cutoff checks pass:
state/pass3-audition-ui-2.log and pass3-audition-runtime.log. The first UI run
asserted before Android's posted Apply callback; its replacement waits for actual
persisted state. No product assertion was weakened.

Widget preview now uses the launcher's system text scale and Alexandria font.
Extended native 80–150% sizing includes voice, actual six-prayer widget, appearance
and first-use footer controls. Portrait with enlarged OS text passes in
state/pass3-widget-portrait-os130.log. A rotation-setting-only run misleadingly
remained portrait (pass3-widget-landscape-os130.log is NOT landscape evidence).
The replacement forces 2400x1080 and asserts landscape at each scale: all pass
state/pass3-verified-landscape-os130.log; captures were dimension-checked and
inspected. Final wide captures use state/pass3-wide-os130-*.png. Guest restored
to 1080x2400, density420, user_rotation0, font_scale1.0. Native APK parser also
accepts the atomic import's staged file: pass3-native-archive-parser.log.

Repository boundary/high-risk/test audit is recorded in PHONE-AUDIT.md, including
its explicit limits and a pre-existing TV volume intervention finding deferred
to the next TV runtime correction. LG ES5/service/browser matrix passes:
pass3-webos-tests.log, pass3-webos-browser.log. Preview tokens/data/tests/build
pass: pass3-preview-{tokens,data,tests,build}.log. Android TV/installer build,
size/lint/test-APK and all 47 root regressions pass: pass3-tv-installer-build.log,
pass3-python-regressions.log. Native live phone-to-LG UI/real TLS/settings fixture also passes:
state/pass3-lg-phone-live.log (approval injected; no physical TV). Phone build/lint/test APK
passes pass3-widget-font-build-2.log, with later test-only orientation/parser
builds in pass3-orientation-build.log. A later common text-direction correction
also builds/lints in pass3-explicit-text-direction-build.log; it uses the app's
chosen paragraph direction rather than the system text locale. English/Arabic
journeys and enlarged-text portrait/verified-wide matrices pass again in
pass3-rtl-{journey,portrait-size,wide-size}.log. Restored guest display settings;
Arabic preparation and compass renders inspected in state/pass3-rtl-*.png.
Guides/architecture describe the new flows;
version0.3.0 in PHONE.md remains prospective until bumped/built/exported.

Latest native first-use/widget-host/platform journeys and v1/custom import cases
pass again: pass3-final-journey-local.log and pass3-final-custom-local.log. Qibla
now waits for a real orientation event/visible arrow (or explicit unavailable
sensor fallback) before capture; current English/Arabic compass renders were
inspected, with physical calibration still pending. Preparation instructions now
render each numbered step with spacing; build/lint and bilingual complete journey
pass pass3-guide-spacing-{build,native}.log. The updated Arabic LG guide render
is state/pass3-spaced-ar-lg-guide.png. Final generator cases pass 8/8 in
pass3-audition-final-generator.log. No build/check process remains pending.

Delivery candidate: phone 0.3.0/code 8 and standalone installer 1.1.3/code 6.
Final playback/import methods have focused validation/session/preparation/output
jobs; redundant Changed/Picked interfaces use standard Consumer, with callers
updated. Both hosts build/lint/test APK: state/pass3-delivery-build.log.
Phone 0.3.0 native default, journey, phone-ui, custom, public-release, location
denied/approximate checks pass in state/delivery-030-*.log. Precise/travel,
alarm denial and actual runtime pass in delivery-030-{phone-location,phone-denied,
phone-runtime}-2.log. The first precise run reached its travel cold-launch case
while the fixture still represented unseen onboarding; it now sets/restores the
seen flag for an opted-in existing user. Assertions were retained. No product
location change or retry masking a failing assertion.

The local fixed APK is exported to primary state/builds/companion/Miqat-latest.apk:
SHA256 3548858DCCAEF3472324FCD2A3F8E9F2F54584BCFEE19B85D5E049C773763D97,
34,208,723 bytes. Its certificate matches 0.2.1 (SHA256 D5B22399A9EBDF6181E68DD55EA3C618FCB086EB82B73BDE620AC9B9B0784F02).
All 26 approved recordings and 25 source notices match packaged bytes. GitHub
published-release list rechecked empty; no publication was attempted.

Implementation commit `4c8e38d` is pushed and all source-head checks passed:
Validate [44473850009](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44473850009),
Companion [44473849927](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44473849927),
Apple core [44473849807](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44473849807).
The final documentation head's checks remain the last handoff gate on draft PR #9.
No further product/code work is pending in this pass; physical/OEM acceptance and
actual public binary transfer are explicit owner/publication limits, not claimed
passes. PR title/body now describe the complete final scope and acceptance limits.

Candidate screenshots are in primary state/builds/companion/screenshots/:
0.3.0-en-phone-themed.png, 0.3.0-ar-voice.png,
0.3.0-ar-journey-platforms.png, 0.3.0-ar-journey-personalization.png and
0.3.0-ar-journey-qibla.png. These are owned Android-emulator renders, not LG TV
captures. The clean voice screenshot was captured through the actual native UI;
an earlier fixture screenshot with a transient toast is not a handoff image.

The owned emulator's process, command, AVD/port and creation instant were checked
against ignored state/pass3-emulator-session.json. It was stopped successfully;
state/delivery-emulator-stop.json records verification and exit. A first timestamp
comparison double-parsed PowerShell's auto-converted JSON date; no process was
stopped until the raw UTC receipt matched. No physical TV was operated.

Next: require final documentation-head CI, verify the unchanged export/hash,
then hand over one combined APK with screenshots/evidence. After handoff, wait
for the owner's full feedback. Do not add another feature, automatically fix a
future TV finding, publish, merge, deploy, or continue implementation after handoff.

## Current resume point (pass 2 history)

Pass 2 is implemented and locally verified for phone 0.2.1/code 7 and standalone
installer 1.1.2/code 5. Location, public downloads and compact UI milestones are
ac510e9, 47dbe87 and c519e6b. Full Validate, Companion and Apple-core CI passed on
c519e6b. Require the final PR #9 head's checks before handoff; update its body with
the resulting links. Keep the PR draft for owner acceptance, with no physical
TV operation or public release publication.

The final local review APK is exported to the fixed primary-checkout companion
path, SHA256 7B203855FF55F7671746EA3C1BB8468218A2A78F4D84BDD0E51E1F65CC21F541.
Signing certificate matches the previous 0.2.0 review APK. Native settings
migration, private custom playback/30-second cutoff, actual alarms, quiet/weekday
policy, calibrated travel refresh, bilingual editors, 80–150% portrait/landscape
with 1.3× OS text, 47 regressions and all 26 packaged recordings pass locally.
See EVIDENCE.md for concise results; raw logs/captures stay under ignored state/.

Owned emulator record is ignored state/feedback-emulator-session.json. Verify
recorded process ownership before cleanup. It is read-only/headless/muted; display
has been restored to the session's portrait/default text settings. Do not adopt
another emulator or use the fixture against a physical device.

Previous pass: implementation and the 0.2.0 review export were complete. Before handing over, require
all latest PR #9 checks to pass; record their final run links in the PR body.
Then wait for the owner's combined physical-device feedback, keeping this PR
draft. Do not merge past the open human acceptance or add broader Athar modules
without the owner's subsequent direction.

Phone artifact: primary checkout state/builds/companion/Miqat-latest.apk,
0.2.1/code 7, SHA256 prefix 7B203855FF55F767. LG artifact remains the existing
state/builds/webos/Miqat-latest.ipk (0.2.28). Standalone installer: 1.1.2/code 5.
First-use and combined-review checklist: PHONE.md. Durable reference: PHONE-REFERENCE.md.

Latest private logs: phone-delivery-ui.log, phone-handoff-portrait.log,
phone-delivery-landscape.log, phone-export-build.log, phone-bounded-preview.log,
phone-final-lg-live.log, lg-final-tests.log and lg-final-browser-3.log. The clipped
card assertion reproduced red in phone-card-clip-before.log before the alignment
fix. LG startup repro red/green: lg-splash-before.log and lg-splash-after.log.
47 Python/Java checks, canonical/QR/LAN helpers, actual alarm/12-second preview/
Stop/focus/disabled cleanup and package checks are recorded in EVIDENCE.md.
All 26 approved media files and 25 notices/source files match packaged bytes.
Native alarms use current audio settings, refresh after clock/civil-day changes,
and preserve global alarm volume. Phone and TV settings remain independent.

Chosen Android approach: user-authorized prayer alarms, a service only during
audio, packaged approved clips, relative alarm volume (never change global volume
or DND), and phone-only device-protected settings/claims for direct-boot recovery.
Pairing credentials remain credential-protected/encrypted; never copy them into
the phone prayer store. Device-protected preferences are excluded from transfer.
Sources checked: Android official alarms, background-FGS restrictions, Direct Boot
and Auto Backup documentation. The opt-in Apple-core workflow runs: macOS ARM64 native rule tests and
iOS device/simulator library compilation only, bounded to 20 minutes. No iOS UI,
notification/audio adapter or signed build is included. Apple CI passed on 68a91fe (run 44414849445); ordinary local JVM/ES5 also pass.

Resolved earlier CI ordering issue: a later license-only run still caught the setup fixture opening
the next activity before the previous finish completed. Local native checks now
wait for destroyed setup + resumed caller at *each* transition, not only the final
result; they pass locally and on Companion CI for 666ce6a.
Owned read-only emulator details are in ignored state/emulator-session.json.
Verify the recorded process/command before stopping it; do not adopt another
session. Local fixture servers exit after each test.
Current private logs are state/lg-install-wire.log, lg-build.log and catalogs-test.log.
LG feedback mentioned by owner has not yet been supplied; do not invent its cause.

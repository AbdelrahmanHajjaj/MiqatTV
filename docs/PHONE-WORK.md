# Miqat phone app delivery ledger

Updated: 2026-11-19. Branch `feature/companion-installation`, worktree
`state/companion-installation` under the primary Miqat checkout. Base: companion
merge `3c5b38e`. Draft PR #9 owns this work and must describe the final scope.

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
- No Qibla, Quran reader, accounts, feeds or unrelated product features are implied.

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
2. [ ] Canonical GitHub public-release lookup/verified downloads for APK/IPK,
   ordinary setup as default and local packages in Advanced. Never embed tokens,
   install unverified/partial files, silently downgrade or uninstall to update.
   Website should link to the same release assets. Handle no public release honestly.
3. [ ] Per-prayer volume/duration overrides, silent/vibration alert modes,
   weekday schedules and quiet hours. Custom user audio remains private on-device;
   no unreviewed media enters Git or distribution. Existing 14 approved recordings
   remain available; adding shipped recordings needs the established content review.
4. [ ] Travel behavior: opt-in foreground location refresh and device/fixed
   timezone choice, with clear stale/denied/location-disabled states. No continuous
   background tracking or automatic religious travel rulings are implied.
5. [ ] Phone UX pass: compact settings summaries, focused inline numeric editors,
   short choice sheets, clear first-run/location/alert/setup states, consistent
   surfaces/spacing and accessible Arabic/English, large text and RTL.
6. [ ] Validate affected behavior and final CI, update guide/evidence/PR, export
   the replacement review APK. Physical acceptance and publication stay open.

## Current resume point

Work pass 2 in the existing feature/companion-installation worktree (clean base
298f195). Location detection is implemented and validated (EVIDENCE.md). Continue
with public downloads, adhan schedules/audio
and phone UX. Reuse native Java Views, shared phone contract/core and existing
installer runtime. The previous 0.2.0 review APK remains the latest delivered
build until this pass is verified/exported. All final 298f195 CI gates passed;
links are in PR #9. Keep the PR draft while owner acceptance remains open.

Previous pass: implementation and the 0.2.0 review export were complete. Before handing over, require
all latest PR #9 checks to pass; record their final run links in the PR body.
Then wait for the owner's combined physical-device feedback, keeping this PR
draft. Do not merge past the open human acceptance or add broader Athar modules
without the owner's subsequent direction.

Phone artifact: primary checkout state/builds/companion/Miqat-latest.apk,
0.2.0/code 6, SHA256 prefix D6B4983DA58E3CF3. LG artifact remains the existing
state/builds/webos/Miqat-latest.ipk (0.2.28). Standalone installer: 1.1.1/code 4.
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

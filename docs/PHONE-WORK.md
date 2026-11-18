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
7. [ ] Complete affected tests/CI and final bilingual portrait/landscape/large-text
   UI checks, inspect packaged media/notices and private-data
   staged review, rewrite PR for final scope, export fixed `Miqat-latest.apk`.
   Record physical acceptance as awaiting owner's combined feedback.

## Current resume point

Steps 1–6 are implemented. Finish step 7: screen-size/large-text matrix, native
permission recovery suite, full affected checks and CI, optional bounded Apple
core compilation, final docs/version/PR and fixed review APK export. Do not stop
for intermediate physical feedback. Current UI evidence: state/phone-independent-ui-3.log
and state/phone-ui-regression-2.log; denial: state/phone-permission-denied.log.
Build/lint: state/phone-ui-final-lint.log (localized date cleanup included).
Recovery broadcast + actual delivery: state/phone-runtime-recovery.log. Portrait
80–150% both languages passes; landscape with 1.3× OS text also passes, including native navigation geometry.
All 47 Python/Java regressions and canonical/catalog/QR/LAN helper checks pass.
Version now phone 0.2.0/code 6, installer 1.1.1/code 4; final rebuild/export pending.

Chosen Android approach: user-authorized prayer alarms, a service only during
audio, packaged approved clips, relative alarm volume (never change global volume
or DND), and phone-only device-protected settings/claims for direct-boot recovery.
Pairing credentials remain credential-protected/encrypted; never copy them into
the phone prayer store. Device-protected preferences are excluded from transfer.
Sources checked: Android official alarms, background-FGS restrictions, Direct Boot
and Auto Backup documentation. An opt-in Apple-core workflow is being added: macOS ARM64 native rule tests and
iOS device/simulator library compilation only, bounded to 20 minutes. No iOS UI,
notification/audio adapter or signed build is included. Apple CI result pending.

CI ordering issue: a later license-only run still caught the setup fixture opening
the next activity before the previous finish completed. Local native checks now
wait for destroyed setup + resumed caller at *each* transition, not only the final
result; they pass. Push this checkpoint and check subsequent CI; do not blindly retry.
Owned read-only emulator details are in ignored state/emulator-session.json.
Verify the recorded process/command before stopping it; do not adopt another
session. Local fixture servers exit after each test.
Current private logs are state/lg-install-wire.log, lg-build.log and catalogs-test.log.
LG feedback mentioned by owner has not yet been supplied; do not invent its cause.

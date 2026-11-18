# Miqat phone app delivery ledger

Updated: 2026-11-18. Branch `feature/companion-installation`, worktree
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
   core/43 Python regressions and owned-emulator UI/LAN checks pass. PR CI pending.
2. [ ] LG installation: research official CLI protocol, choose minimal SSH client,
   implement strict input/package bounds, host-key confirmation/pinning, private
   credentials, local package install/verify/launch and recovery/status guide.
   Test protocol with a local fixture; no physical TV changes.
3. [ ] Unify Android/LG setup presentation with phone styling; inspect bilingual
   portrait/landscape and large text; preserve safe cancellation/recovery.
4. [ ] Standalone phone prayer storage/calculation/settings and portable reminder
   policy; focused civil-day/DST/offset/high-latitude regressions.
5. [ ] Native alarm/notification/audio lifecycle and per-prayer controls; permission,
   reboot/time changes, duplicates, stale events and Stop/interruption regressions.
6. [ ] Independent prayer Home/phone settings/appearance with existing TV areas;
   no TV dependency; bilingual rendered/native behavior checks and persistence.
7. [ ] Complete affected tests/CI, inspect packaged media/notices and private-data
   staged review, rewrite PR for final scope, export fixed `Miqat-latest.apk`.
   Record physical acceptance as awaiting owner's combined feedback.

## Current resume point

Start step 2: inspect LG CLI key acquisition/SSH install/status semantics and
existing cached dependencies. Step 1 is committed/pushed with a clean worktree.
Existing screenshot feedback: installer typography/large hero and outlined cards
differ from companion's compact native style; address in step 3.
LG feedback mentioned by owner has not yet been supplied; do not invent its cause.

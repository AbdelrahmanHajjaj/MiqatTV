# Current work

Updated: 2026-11-19.

Active follow-up: retain the 0.4.1 Home spacing/short scroll, but balance the
prayer card instead of one aligned text stack and restore theme color emphasis.
Scheduled adhan must play while other apps stay open. Add an optional full-screen
adhan mode (off by default), notification dismissal/Stop cancels output, and an
explicit Stop-and-close action/Back confirmation prevents accidental screen stops.
Existing notification-backed playback stays. Respect Android full-screen alarm
access and pinned foreground-notification limits; dismissal stops where Android
permits it, with Stop always available. No overlay bypass or idle service.

Working on feature/companion-installation / draft PR #9. 0.4.1 (4aeb82a) local
build/journey/size checks passed and CI was running when this feedback arrived.
Card/real preview, optional screen and notification controls are implemented.
Build/lint, actual background/screen-off/control runtime and migration pass.
Bilingual renders, phone-ui and both enlarged-text matrices pass. Denied-FSI and
allowed-FSI actual lock-screen opening/real Back checks pass. Final exported
0.4.2/code11 APK hash and acceptance limits are in EVIDENCE.md.

Android13+ Stop custom action and actual rendered/clicked SystemUI notification
control now pass native verification too. Final APK receipt is in EVIDENCE.md.
Next: push validated final scope, require Validate/Companion/Apple CI on that head,
record PR receipts and stop only the verified owned emulator. Hand off one APK
with accepted captures, then await owner feedback. Notification Play/Pause and
screen-only volume stopping are included; power keys are reserved by Android.
PHONE-WORK.md owns exact progress; private evidence stays under state/.

Keep PR draft. No merge, public release or physical operation in this pass.
Existing LG review IPK stays 0.2.28. Public installation needs published assets;
draft releases are not public downloads. Hardware/OEM/compass/TV acceptance and
the recorded next-TV volume correction remain separate. Do not start unrelated
TV/iOS/worship work or add scaffolding after handoff.

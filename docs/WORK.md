# Current work

Updated: 2026-11-19.

Owner follow-up 0.4.3/code12 is implemented and locally verified: balanced Home,
background adhan controls, optional themed mihrab screen, matching notification
artwork and actual prayer/scheduled time/playing voice. Shared native scene/time
helpers avoid duplicate rendering/formatting; no new dependency or player.

Build/lint, bilingual UI/journey, actual-alarm/media checks (allowed full-screen
and denied fallback), and portrait/landscape 80–150% sizing with OS font1.3 pass.
Rendered Home, notification and adhan screens inspected. Review APK exported;
PHONE-WORK.md and EVIDENCE.md own exact version/hash/results. Owned emulator
PID32500 was verified and stopped; no active task emulator remains.

1950827 CI exposed a TV test fixture racing its own queued catalog refresh.
Keep the refresh and assertions, await its restored focus before returning;
test-only correction leaves the phone APK unchanged.

Next: build the corrected TV test APK, push and require final-head CI. The fixture
uses synchronized UiAutomation input for real Back/volume, retaining all dialog/
focus assertions. Record final head/runs in draft PR and ignored handoff receipt,
then deliver one APK/screenshots and stop for owner feedback.

Keep PR draft. No merge, public release or physical operation in this pass.
Existing LG review IPK stays 0.2.28. Public installation needs published assets;
draft releases are not public downloads. Hardware/OEM/compass/TV acceptance and
the recorded next-TV volume correction remain separate. Do not start unrelated
TV/iOS/worship work or add scaffolding after handoff.

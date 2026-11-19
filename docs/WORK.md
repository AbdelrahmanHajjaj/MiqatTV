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
The repeated failure also exposed null focus matching off-screen null tile
placeholders and a navigation guard shrinking during rebuild. Correct the
fixture readiness checks; phone APK unchanged.

Phone CI reached runtime after earlier suites passed, then stalled. Move
accessibility window queries out of main-thread predicates and retain their
five-second deadline; bound CI instrument commands at 420s like local checks.

b6db36f Validate (including TV) and Apple pass. Intermittent Coming soon
Back still fails on API34: require actual dialog root input focus, not only
activity focus loss, then real Back and root detachment/focus restoration.

4c7a6d6 Validate/Apple and every preceding phone suite pass. API34 runtime
still stalls (420s timeout). Emit main/instrumentation stacks at 90s before any
further fix; keep assertions/deadlines. API37 runtime/render already passed.

Diagnostic snapshot: main idle; test blocked in startActivitySync(audition).
Lock-screen checks leave the owned emulator locked on API34. Wake/dismiss
keyguard immediately in audition(), after the delayed preview checks, and
assert interactive/unlocked just before ordinary UI launch.
Remove temporary thread timer; production APK unchanged.

Next: compile corrected fixture, push, require final-head CI, update draft PR/
private receipt and deliver the saved APK/screenshots. Stop for owner feedback.

Keep PR draft. No merge, public release or physical operation in this pass.
Existing LG review IPK stays 0.2.28. Public installation needs published assets;
draft releases are not public downloads. Hardware/OEM/compass/TV acceptance and
the recorded next-TV volume correction remain separate. Do not start unrelated
TV/iOS/worship work or add scaffolding after handoff.

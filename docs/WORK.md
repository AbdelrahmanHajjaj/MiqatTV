# Current work

Updated: 2026-11-21. Owner feedback on 0.5.1 approves a focused 0.5.2 refinement
on feature/companion-installation, draft PR #9. Remove Home day arrows while
keeping its calendar entry and combined timetable; keep weekdays on one row;
prevent settings redraw jumps; fill next-prayer widgets like the Home hero; put
Miqat Signature first in theme/background choices without changing saved choices.

Implementation/local validation complete: native bilingual UI retains its window
container and scroll before drawing; all weekday targets fit at 360dp. Widget
bounds/fonts and 80–150% portrait/large-text landscape checks pass. Native picker
images are regenerated. Exact artifact/evidence: EVIDENCE.md. Draft PR #9 owns
final-head CI receipts; green checks are required before owner handoff.
No merge, public release or physical deployment in this review pass.

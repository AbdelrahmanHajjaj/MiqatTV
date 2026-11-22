# Current work

Updated: 2026-11-22. Owner reports missing Hijri dates and clipped/small countdown
text on a wide one-row widget. Keep the date prominent, reserve safe countdown
bounds and rebuild the prayer row when resizing. Next prayer retains its 1×1
start, with a compact countdown where it fits. Native picker previews are refreshed.
Branch feature/companion-installation, draft PR #9. Version: 0.5.16/code 32.

The location request is once per guide history, with manual fallback. Background
updates remain off without explicit consent and cannot schedule before location
confirmation. Setup edits retain the update choice and cancel pending detection.
Reuse canonical prayer geometry and native notification layout/direction.

Focused native provider/small-text/date/corner-clipping/resize and accessibility checks, build/lint
and generator checks pass. The signed daily-test
APK is exported; EVIDENCE.md records its identity. Next: owner daily-drive feedback.

Owner explicitly defers full CI during feedback iterations; run it when preparing
to merge/release after hardware confirmation. Do not delay APK handoff for it.
Preserve unresolved CI evidence. Main must remain merged before pushing.
Keep PR #9 draft. No merge, release or physical deployment requested.

# Current work

Updated: 2026-11-22. Owner requests smaller widgets starting at 1×1 and continues
daily driving. All providers now allow one-cell resize bounds; Next prayer starts
at 1×1. Compact space shows prayer/time, preserving preferences and accessible
full time while larger sizes restore details. Native picker previews are refreshed.
Branch feature/companion-installation, draft PR #9. Version: 0.5.15/code 31.

The location request is once per guide history, with manual fallback. Background
updates remain off without explicit consent and cannot schedule before location
confirmation. Setup edits retain the update choice and cancel pending detection.
Reuse canonical prayer geometry and native notification layout/direction.

Focused native provider/small-text/resize and accessibility checks, build/lint
and generator checks pass. The signed daily-test
APK is exported; EVIDENCE.md records its identity. Next: owner daily-drive feedback.

Owner explicitly defers full CI during feedback iterations; run it when preparing
to merge/release after hardware confirmation. Do not delay APK handoff for it.
Preserve unresolved CI evidence. Main must remain merged before pushing.
Keep PR #9 draft. No merge, release or physical deployment requested.

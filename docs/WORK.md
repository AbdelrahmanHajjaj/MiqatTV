# Current work

Updated: 2026-11-22. Owner reports most physical-device tests working and will
daily-drive the next candidate before merge/release preparation. Current scope:
prayer notification icons, automatic foreground location request after Welcome,
and optional background update consent on the guided location screen.
Branch feature/companion-installation, draft PR #9. Version: 0.5.13/code 29.

The location request is once per guide history, with manual fallback. Background
updates remain off without explicit consent and cannot schedule before location
confirmation. Setup edits retain the update choice and cancel pending detection.
Reuse canonical prayer geometry and native notification layout/direction.

Focused native permission-denied/granted setup, manual choice, notification
controls/rendering, build/lint and generator checks pass. The signed daily-test
APK is exported; EVIDENCE.md records its identity. Next: owner daily-drive feedback.

Owner explicitly defers full CI during feedback iterations; run it when preparing
to merge/release after hardware confirmation. Do not delay APK handoff for it.
Preserve unresolved CI evidence. Main must remain merged before pushing.
Keep PR #9 draft. No merge, release or physical deployment requested.

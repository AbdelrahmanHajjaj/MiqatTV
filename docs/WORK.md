# Current work

Updated: 2026-11-21. Owner requested media volume by default for Adhan, with an
optional **Use alarm sound** toggle using alarm volume. Branch
feature/companion-installation, draft PR #9. Review export is 0.5.10/code 26.

Playback/focus, media-session volume controls and Activity volume keys now use
the same selected stream. The toggle saves immediately. Schema 7 preserves v1–v6
settings with alarm sound off. The actual-player regression failed before the
fix and now passes, including bilingual toggle persistence and media restoration.

Main 26a32c7 remains merged; its cleaned audio and affected-CI policy are intact.
Storage, actual playback/tests and bilingual UI checks pass; signed export and
canonical checks pass. Evidence is recorded. Next: verify exact-head CI before
owner review. Keep PR #9 draft. No PR merge, release or physical
deployment requested.

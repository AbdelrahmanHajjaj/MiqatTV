# Current work

Updated: 2026-11-21. Owner approved phone audio/testing/popup refinements on
feature/companion-installation, draft PR #9. Review export is 0.5.9/code 25.

Named reusable private adhans, complete Listen, start-to-percentage previews and
cancellable 30-second phone tests are implemented. The redundant phone motion
control is hidden; existing accessibility preferences remain compatible. The
Coming soon sheet first-frame width regression is fixed.

Latest main (26a32c7) was merged normally in 4f6ca5c before pushing, as requested.
Its cleanup for the 13 retained voices and affected-CI/artifact policy are
preserved; the previously retired recording stays excluded.

Merged storage/runtime/dialog, bilingual UI/sizing, journey, TV bindings and LAN
checks pass. Build/lint and signed export pass; see EVIDENCE.md.
Next: verify exact-head CI and keep PR #9 draft for owner review.
No PR merge, release or physical deployment requested.

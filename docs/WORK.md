# Current work

Updated: 2026-11-21. Owner approved phone audio/testing/popup refinements on
feature/companion-installation, draft PR #9. Current export is 0.5.8/code 24;
0.5.9/code 25 is in progress.

Build a named reusable private adhan library, preserving existing imports and
independent prayer selections. Listen plays the full recording from the start;
length preview plays from the start to the selected percentage. Add explicit
Adhan and notification phone tests with a cancellable 30-second grace period.
Remove the redundant phone Reduce motion setting and follow Android animation
accessibility. Reproduce/fix the Coming soon sheet's horizontal entrance shift.

Latest main (26a32c7) is being integrated before the PR push, as requested. Preserve
its approved cleanup for the 13 retained voices and its affected-CI/artifact policy
([CI](CI.md)); keep the previously retired recording excluded.

Next: finish the merge, run focused storage/runtime/dialog and bilingual UI/sizing,
export the review APK and verify exact-head CI. Keep PR #9 draft for owner review.
No PR merge, release or physical deployment requested.

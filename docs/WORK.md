# Current work

Updated: 2026-11-23. Quran Radio was approved and merged in PR #22.
Current scope in `feat/phone-hijri-reminders`: core and common Hijri
observances, every day of Ramadan and Eid, and opt-in day-before, suhoor and
iftar reminders for Ramadan, selected voluntary Hijri fasts, and Mondays and
Thursdays.

Next: review the standalone phone build and confirm the calendar and fasting
settings before merge. Keep website download copy aligned with stable packages.

Stable v0.7.3 contains TV 0.7.3/code 14, phone 0.5.19/code 35,
installer 1.1.5/code 8 and LG 0.2.30. Release builds/lint, focused rejection
checks, final-main validation and hosted signing pass. Downloaded APKs match
their pinned certificates, package IDs, release manifests and package checksums.
Stable promotion reuses v0.7.3-preview.1's exact signed bytes and source commit;
existing installations of that preview need no reinstall. Prior release tags and
assets are preserved. See EVIDENCE.md and the
release/PR records for validation and website deployment outcomes.

Keys stay outside Git and PR jobs, in private recovery and the main-only
android-release environment. Routine CI has no phone/TV emulator jobs; retain
build/lint, portable/JVM/browser and package/signature gates. Native device suites
remain targeted manual checks.

Transition from debug signing requires one fresh install and removes local data;
later signed releases preserve identity. Keep an off-device signing recovery copy.

Overwrite only the existing Miqat-latest review exports under state/builds/.
Keep the existing debug review signer. Release keys and recovery material are in
private state/signing/ and encrypted environment secrets; never print passwords.

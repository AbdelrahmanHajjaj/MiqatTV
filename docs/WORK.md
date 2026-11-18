# Current work

Updated: 2026-11-18.

## Approved scope and next action

The companion branch was normally merged in PR #8, main `3c5b38e`. Continue in
the isolated `feature/companion-installation` worktree, based on that merge.
Keep the completed companion polish out of this new work's scope.

Integrate TV installation/recovery into the phone companion. First reuse the
Android installer's existing guarded ADB, local APK, backup, cleanup and restore
flow. Preserve the standalone installer and keep setup access independent from
ordinary pinned-LAN pairing. Validate both phone languages and cancellation,
incomplete setup and successful return to pairing before delivery.

Next in the installation phase: LG Developer Mode installation, its manual
prerequisites/session limits and appropriate recovery. Do not advertise LG
installation or Android-style cleanup on LG before implementing and validating
it. Review owner-supplied LG feedback when available; Android parity is unverified.

After installation: expand the same phone app into a standalone Android prayer
app. Share portable rules, canonical content/settings/design; retain native
platform rendering, scheduling, audio, permissions and credentials. Phone and
each TV own their preferences. iOS is later, with shared Kotlin rules and a native
interface; prepare lightweight macOS CI builds/tests without claiming hardware
acceptance. No phone background prayer implementation is part of this slice.

Use gh and the PR template. Preserve commits with a normal merge after required
checks pass. Physical TV deployment and release publication need specific scope.
Keep the current slice's essential evidence in [EVIDENCE.md](EVIDENCE.md).

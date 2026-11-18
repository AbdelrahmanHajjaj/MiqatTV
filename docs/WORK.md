# Current work

Updated: 2026-11-18. Branch: feature/github-delivery. [PR #1](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/1).

## Approved scope

Configure main protection, the PR template, validation Actions and build publication
for MiqatTV. The owner approved including the current 14 adhan and 12 announcement
clips in public builds. Approved media lives in shared/media/; private originals,
stamps, credentials and device state remain ignored.

## Status and next action

Main protection is active: PR, passing GitHub Actions Required checks, up-to-date
branch, resolved conversations and linear history; no force pushes, deletion,
bypasses or required approving reviewers. Actions requires full SHA pins and uses
a read-only default token without PR approval permission.

PR #1 provides full-audio Android/LG artifacts, core/preview/native emulator checks
and manual draft prereleases. Local checks passed; observe its final GitHub run,
then obtain the owner's merge instruction and verify draft publication from a
successful main run. AGENTS.md requires corresponding approval for merging/releasing.
Edge is authorized because API credentials lack admin/PR access. Do not create
credentials, change token scopes, publish a stable release or touch TVs.

## Retained limits

Physical new-identity acceptance, full phone-installer testing, portable settings
transfer and companion integration remain separate. CI APKs use temporary debug
keys, so fresh installation may be needed between runs. Preview dependency
advisories remain in EVIDENCE.md. No new platform or cloud framework.

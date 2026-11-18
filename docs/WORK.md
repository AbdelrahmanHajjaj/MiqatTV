# Current work

Updated: 2026-11-18. Delivery: [PR #1](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/1).

## Approved scope

Configure main protection, the PR template, validation Actions and build publication
for MiqatTV. The owner approved including the current 14 adhan and 12 announcement
clips in public builds. Approved media lives in shared/media/; private originals,
stamps, credentials and device state remain ignored.

## Delivery and receipts

Main requires a PR, passing GitHub Actions Required checks, an up-to-date branch
and resolved conversations. Force pushes/deletion and bypasses are blocked. No
approving reviewer is required for the solo maintainer. Preserve branch commits
with normal merges; squash/rebase merging is disabled. Actions requires full SHA
pins and uses a read-only default token without PR approval permission.

PR #1 supplies full-audio Android/LG artifacts, core/preview/native emulator checks
and manual draft prereleases. The owner authorized its normal merge after required
checks pass, followed by one draft-publication verification. PR #1 and the linked
[Actions runs](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions) own the final
merge, validation and draft receipts; concise local evidence is in EVIDENCE.md.
Edge is authorized because API credentials lack admin/PR access. Do not create
credentials, change token scopes, publish a stable release or touch TVs.

## Further work

No additional product work is approved by this setup. Physical new-identity
acceptance, full phone-installer testing, portable settings transfer and companion
integration remain separate. CI APKs use temporary debug keys; configure persistent
release signing before stable updates. Preview dependency advisories remain in
EVIDENCE.md. No new platform or cloud framework.

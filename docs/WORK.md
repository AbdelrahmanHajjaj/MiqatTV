# Current work

Updated: 2026-11-18. Branch: feature/github-delivery.

## Approved scope

Configure main protection, the PR template, validation Actions and build publication
for MiqatTV. The owner approved including the current 14 adhan and 12 announcement
clips in public builds. Shared reviewed media now lives in shared/media/; private
originals, stamps, credentials and device state remain ignored.

## Current action

Implement and validate full Android/LG build artifacts, core/preview checks, native
emulator checks and manually triggered draft publication. Publish the workflows,
observe actual GitHub runs, then require their stable check on main. Edge is
authorized for repo settings because the configured API credentials lack admin/PR access.

Use PRs with passing checks, linear history, resolved conversations and blocked
force pushes/deletion. No required approving reviewer for the solo maintainer.
Do not create credentials, change token scopes, publish a stable release or touch TVs.

## Retained limits

Physical new-identity acceptance, full phone-installer testing, portable settings
transfer and companion integration remain separate. Existing preview dependency
advisories remain recorded in EVIDENCE.md. No new platform or cloud framework.

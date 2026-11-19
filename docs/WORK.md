# Current work

Updated: 2026-11-19.

## Approved scope and next action

The companion branch was normally merged in PR #8, main `3c5b38e`. Continue in
the isolated `feature/companion-installation` worktree, based on that merge.
Keep the completed companion polish out of this new work's scope.

Owner approved completing the installation phase and fully standalone Android
prayer app sequentially, without stopping for intermediate physical-device
feedback. Deliver one combined build for the owner's complete review. The durable
scope, decisions, ordered checklist and resume point are in [PHONE-WORK.md](PHONE-WORK.md).

The owner skim-tested 0.2.0 and approved a second pass: substantial phone UX
polish, foreground location detection, automatic public TV package downloads
(local files in Advanced), and all three adhan customization groups: per-prayer
sound controls, custom audio, and weekday/quiet-hour/travel behavior.

Proceed sequentially using [PHONE-WORK.md](PHONE-WORK.md)'s pass-2 checklist.
Pass 2 is implemented and locally verified; the combined 0.2.1 review APK is
exported. Require final-head PR #9 CI, then hand over for the owner’s combined
review. Keep the PR draft while human acceptance remains open.
The public repository has only a draft prerelease, so no anonymous published
release is available yet; implement the download flow and honest unavailable
state without publishing or embedding credentials. Keep physical acceptance and
public release publication separate. Retain native TV stacks and independent
phone/TV settings. Broader Athar modules remain future discussion candidates.

Use gh and the PR template. Preserve commits with a normal merge after required
checks pass. Physical TV deployment and release publication need specific scope.
Keep the current slice's essential evidence in [EVIDENCE.md](EVIDENCE.md).

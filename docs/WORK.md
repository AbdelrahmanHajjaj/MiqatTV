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

Proceed sequentially using [PHONE-WORK.md](PHONE-WORK.md)'s active pass-3 checklist
and the binding requirements in [PHONE-PASS3.md](PHONE-PASS3.md).
Pass 2 and its CI passed, but the owner rejected the overall experience as
incomplete. Continue pass 3 in PHONE-WORK.md: guided first use, unified TV
platform/preparation/release/setup journey, real phone appearance previews and
an evidence-led maintainability/test audit. Deliver a combined review build only
after verifying the complete journeys. Keep PR #9 draft for human acceptance.
The public repository has only a draft prerelease, so no anonymous published
release is available yet; implement the download flow and honest unavailable
state without publishing or embedding credentials. Keep physical acceptance and
public release publication separate. Retain native TV stacks and independent
phone/TV settings. Qibla and home-screen widgets are explicitly included in pass 3; Quran, adhkar
and other broader modules remain locked Coming soon placeholders.

Use gh and the PR template. Preserve commits with a normal merge after required
checks pass. Physical TV deployment and release publication need specific scope.
Keep the current slice's essential evidence in [EVIDENCE.md](EVIDENCE.md).

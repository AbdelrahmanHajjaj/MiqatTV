# Current work

Updated: 2026-11-21.

## Approved scope and next action

Apply the maintainer-reviewed mild cleanup to all 14 shared adhan recordings.
Keep the 12 reminders unchanged, preserve existing cuts and decoded duration,
update approved hashes and prevent preparation from accepting stale outputs.

Work is on `feature/adhan-mild-cleanup`, based on main 3c5b38e. Media and affected
Android/LG package checks pass; see [evidence](EVIDENCE.md). The owner requested
merging into latest main and stopping, with no additional local builds. Use gh
for the normal-merge PR and sync main. The PR owns CI/delivery receipts.
No TV operation or release is requested.

## Delivered and remaining limits

The [website refresh](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/4) is live.
[PR #5](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/5) removed FFmpeg processing
from Actions and recorded the standing gh/merge workflow. It was normally merged
as c6ad64f, local main was synced, and [post-merge validation/Pages](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44349978626)
passed all seven jobs. Its PR records the native focus-check intermittency and
single successful retry.

Physical Miqat/installer acceptance, settings transfer, companion integration and
persistent release signing remain separate. Existing build-tool advisories remain
in EVIDENCE.md. Raw captures and device details stay under ignored state/.

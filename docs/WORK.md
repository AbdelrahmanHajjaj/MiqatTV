# Current work

Updated: 2026-11-18.

## Approved scope and next action

Refresh the README for the current Miqat branding. Remove its header icon and
replace the old Android Home screenshots with the reviewed English/Arabic native
captures. Keep the README short and identify emulator evidence. Correct the stale
review-draft claims in the owning design guide; no app or artwork changes.

Work is on `feature/readme-current-brand`, based on main c6ad64f. Check screenshot
provenance, links, paths and copy, then use gh for the PR. Normal-merge after all
required checks pass and sync main under the owner's standing instruction. The
PR owns CI/delivery receipts. No TV operation or release is requested.

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

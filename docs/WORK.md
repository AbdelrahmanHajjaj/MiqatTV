# Current work

Updated: 2026-11-21.

## Approved scope and next action

Apply the approved CI policy: affected checks on PRs, full validation/build
artifacts on main, manual branch review builds, and cancellation of superseded
runs. Keep compilation/package checks and affected native/browser regressions;
include Companion in the applicable required gate. Work is on
`feature/affected-ci-checks`. Validate selection, workflow syntax and release
source regressions, then use gh for a normal-merge PR and sync main after required
checks pass. No release or physical-device deployment is requested.

The approved adhan cleanup was normally merged as PR #10 (2aaadb3).

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

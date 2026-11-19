# Current work

Updated: 2026-11-19.

Pass 3 implementation and local acceptance are complete on
`feature/companion-installation`, based on the companion merge `3c5b38e`.
The combined Android phone review build is **0.3.0 / code 8**, exported to the
primary checkout's `state/builds/companion/Miqat-latest.apk`. Standalone installer
source/build is 1.1.3 / code 6. Existing LG review IPK stays 0.2.28.

Validate, Companion and Apple-core CI passed on implementation commit `4c8e38d`.
The final documentation head must also pass the checks on
[draft PR #9](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/9) before handoff.
Exact artifact/hash, evidence types and run links are in [EVIDENCE.md](EVIDENCE.md).
Requirements and engineering guardrails remain in [PHONE-PASS3.md](PHONE-PASS3.md);
progress and stopping rules are in [PHONE-WORK.md](PHONE-WORK.md). The repository
boundary/test audit and retained findings are in [PHONE-AUDIT.md](PHONE-AUDIT.md).

The next action is the CI-gated combined handoff, then **await the owner's full
feedback list**. Do not start further implementation, broader worship/iOS work
or the recorded next-TV volume correction automatically. Keep PR #9 draft for
human acceptance; no merge, public release or physical deployment in this pass.
Public GitHub releases are empty, so actual public binary transfer still awaits
publication; local packages remain in Advanced. Physical phone/OEM reliability,
compass calibration and TV picture/sound/remote/power acceptance remain separate.
The task-owned emulator was verified and stopped; private receipts/captures stay
under ignored state/.

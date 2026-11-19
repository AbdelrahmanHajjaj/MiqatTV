# Current work

Updated: 2026-11-19.

The twelve-item owner feedback pass is implemented on
feature/companion-installation / draft PR #9. Requirements and engineering
constraints are in PHONE-PASS4.md, PHONE-PASS3.md and AGENTS.md. Phone review
version is **0.4.0/code 9**; standalone installer source/build is 1.1.4/code 7.
The exported APK is primary checkout state/builds/companion/Miqat-latest.apk.
Exact hash, local verification and limits are in EVIDENCE.md.

Delivery gate: review/commit/push the validated implementation and require all
final-head Validate, Companion and Apple-core CI checks to pass. PR #9 owns the
exact head/run receipts. Finish final capture/owned-emulator cleanup, then hand
over one combined APK and await the owner's feedback. PHONE-WORK.md owns resume
status; private logs/receipts stay under ignored state/.

Keep PR draft. No merge, public release or physical operation in this pass.
Existing LG review IPK stays 0.2.28. Public installation needs published assets;
draft releases are not public downloads. Hardware/OEM/compass/TV acceptance and
the recorded next-TV volume correction remain separate. Do not start unrelated
TV/iOS/worship work or add scaffolding after handoff.

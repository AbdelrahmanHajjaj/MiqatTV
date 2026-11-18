# Current work

Updated: 2026-11-18.

## Approved scope and next action

Remove FFmpeg installation and the audio-processing fixture test from GitHub
Actions. The owner chose to keep the existing Ubuntu builds, tests and triggers.
Retain lightweight approved-media hashes and packaged-file checks. Preparation,
decoding and listening checks remain local when media or preparation code changes.

The change is prepared on `feature/ci-without-ffmpeg`, based on main 5d0e1c2.
Local workflow/documentation/hash checks passed. Delivery uses a focused PR and
passing required CI. Follow the owner's standing instruction: use gh, normal-merge
after required checks pass and sync main. The PR owns CI and delivery receipts.
No further implementation is planned.

## Delivered and remaining limits

The [website refresh](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/4) was normally
merged as 5d0e1c2. [Main validation and Pages](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/44332260024)
passed all seven jobs. Public Arabic/English screens and in-place navigation were
verified in Edge; captures remain under ignored state/.

Physical Miqat installation, phone-installer acceptance, settings transfer and
companion integration remain separate. CI APKs use temporary debug keys. Existing
build-tool advisories and earlier Android CI intermittency remain documented in
EVIDENCE.md and PR #2. No release or TV deployment is requested.

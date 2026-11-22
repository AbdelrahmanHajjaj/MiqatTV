# Current work

Updated: 2026-11-22. Approved scope: configure persistent Android release signing
for the phone, Android TV and standalone installer. Keep keys out of Git and PR
jobs; restrict encrypted CI signing secrets to the main-only android-release
environment. Compile release variants in validation and sign only artifacts from
successful current-main validation. Verify package identity, release mode and
the pinned public signing certificate before publication.

Build and verify the three release APKs locally and through the release workflow.
Exercise rejection of debug APKs, wrong packages and unexpected signers. Deliver
through required checks and a normal merge. Preserve existing published binaries.
Local release builds/signatures, release lint and focused checks pass. Next:
required PR/main checks, then exercise the main-only signed-draft workflow.
The owner explicitly removed automatic phone/TV emulator jobs from routine CI.
Keep build/lint, portable/JVM/browser and package/signature gates; native device
suites remain available for targeted manual checks. No further emulator runs.

Next Android versions: TV 0.7.3/code 14, phone 0.5.19/code 35, installer 1.1.5/code 8.
LG remains 0.2.30. v0.7.2-preview.2 stays public and unchanged. Transition from
debug signing requires a fresh install; later signed releases preserve identity.

Overwrite only the existing Miqat-latest review exports under state/builds/.
Keep the existing debug review signer. Release keys and recovery material are in
private state/signing/ and encrypted environment secrets; never print passwords.

# Current work

Updated: 2026-11-23. Stable v0.7.5 contains phone 0.5.21/code 37,
Android TV 0.7.3/code 14 and LG 0.2.30. TV setup and recovery are in
Miqat Phone. The three published packages passed checksum and identity
verification. The website uses the v0.7.5 download links. See EVIDENCE.md.

Keys stay outside Git and PR jobs, in private recovery and the main-only
android-release environment. Routine CI has no phone/TV emulator jobs; retain
build/lint, portable/JVM/browser and package/signature gates. Native device suites
remain targeted manual checks.

Transition from debug signing requires one fresh install and removes local data;
later signed releases preserve identity. Keep an off-device signing recovery copy.

Overwrite only the existing Miqat-latest review exports under state/builds/.
Keep the existing debug review signer. Release keys and recovery material are in
private state/signing/ and encrypted environment secrets; never print passwords.

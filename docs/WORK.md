# Current work

Updated: 2026-11-23. Stable v0.7.5 contains phone 0.5.21/code 37,
Android TV 0.7.3/code 14 and LG 0.2.30. TV setup and recovery are in
Miqat Phone. The three published packages passed checksum and identity
verification. The website uses the v0.7.5 download links. See EVIDENCE.md.

LG adhan HDMI isolation is unreleased. On legacy hardware the owner observed
the HDMI picture cut and return. Physical acceptance of the opaque page and
separate-stream behavior on newer TVs is pending.

Miqat Phone TV setup exposes package download and transfer progress, and LG
setup supports remembering the Developer Mode passphrase. Physical TV setup
validation remains pending.

About Miqat on Android phone, Android TV and LG shows the installed app version,
developer credit and GitHub feedback. TV users scan a QR code. Email stays
disabled until a public address is chosen. The browser preview mirrors the LG page.

Keys stay outside Git and PR jobs, in private recovery and the main-only
android-release environment. Routine CI has no phone/TV emulator jobs; retain
build/lint, portable/JVM/browser and package/signature gates. Native device suites
remain targeted manual checks.

Transition from debug signing requires one fresh install and removes local data;
later signed releases preserve identity. Keep an off-device signing recovery copy.

Overwrite only the existing Miqat-latest review exports under state/builds/.
Keep the existing debug review signer. Release keys and recovery material are in
private state/signing/ and encrypted environment secrets; never print passwords.

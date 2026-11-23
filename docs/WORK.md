# Current work

Updated: 2026-11-23. Stable v0.7.6 contains phone 0.5.22/code 38,
Android TV 0.7.4/code 15 and LG 0.2.31. TV setup and recovery are in
Miqat Phone. The three published packages passed checksum and identity
verification. The website uses the v0.7.6 download links. See EVIDENCE.md.

The owner observed the legacy HDMI picture cut and return on the preceding
test build. LG 0.2.31 keeps the Arabic call inside the mihrab, enters Settings
content on OK, and uses one Close button. Legacy layout and newer-TV audio
remain pending on hardware.

Miqat Phone TV setup exposes package download and transfer progress, and LG
setup supports remembering the Developer Mode passphrase. Physical TV setup
validation remains pending.

About Miqat on Android phone and TV shows the installed app version, developer
credit and a GitHub feedback form. Email stays disabled until a public address
is chosen. No network client is added to either app.

Keys stay outside Git and PR jobs, in private recovery and the main-only
android-release environment. Routine CI has no phone/TV emulator jobs; retain
build/lint, portable/JVM/browser and package/signature gates. Native device suites
remain targeted manual checks.

Transition from debug signing requires one fresh install and removes local data;
later signed releases preserve identity. Keep an off-device signing recovery copy.

Overwrite only the existing Miqat-latest review exports under state/builds/.
Keep the existing debug review signer. Release keys and recovery material are in
private state/signing/ and encrypted environment secrets; never print passwords.

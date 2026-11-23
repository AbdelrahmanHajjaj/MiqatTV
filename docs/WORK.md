# Current work

Updated: 2026-11-24. Stable v0.7.7 contains phone 0.5.23/code 39,
Android TV 0.7.5/code 16 and LG 0.2.32. The three published packages passed
checksum and identity verification. Website downloads and update metadata
point to v0.7.7. See EVIDENCE.md.

The phone release includes countdown seconds, the Circle widget with adjustable
text, prayer time previews, calendar swipes and a Qibla map that opens at the
phone's current location. Physical phone launcher acceptance remains pending.

The owner observed the legacy HDMI picture cut and return on the preceding
test build. LG 0.2.31 keeps the Arabic call inside the mihrab, enters Settings
content on OK, and uses one Close button. Legacy layout and newer-TV audio
remain pending on hardware.

Miqat Phone TV setup exposes package download and transfer progress, and LG
setup supports remembering the Developer Mode passphrase. Physical TV setup
validation remains pending.

About Miqat on Android phone and Android TV shows the installed app version,
developer credit and GitHub feedback. The LG About page uses a feedback QR code, and the
browser preview mirrors it. Email stays disabled
until a public address is chosen.

Sideloaded Android phone and TV builds can explicitly check the stable update
metadata on GitHub Pages from About. Installation remains user-driven.

Keys stay outside Git and PR jobs, in private recovery and the main-only
android-release environment. Routine CI has no phone/TV emulator jobs; retain
build/lint, portable/JVM/browser and package/signature gates. Native device suites
remain targeted manual checks.

Transition from debug signing requires one fresh install and removes local data;
later signed releases preserve identity. Keep an off-device signing recovery copy.

Overwrite only the existing Miqat-latest review exports under state/builds/.
Keep the existing debug review signer. Release keys and recovery material are in
private state/signing/ and encrypted environment secrets; never print passwords.

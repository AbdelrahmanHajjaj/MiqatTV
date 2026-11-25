# Current work

For the iPhone port, the consolidated Apple builds, fixtures, eight simulator
journeys and package integrity check passed. The [iOS parity queue](IOS-WORK.md)
records the remaining signed iPhone, device behavior and live TV acceptance.
The iPhone LG setup screen uses the SSH installer and separate installation
credentials. Verify it against a local Developer Mode fixture. Classic Android
TV installation and Home/remote setup with a recovery journal are wired.
Android cleanup, recovery export and guided personalisation are wired.
Wireless pairing and its separate TLS debugging connection are wired into the
Android setup screen. The app reads and updates the TV-wide cleanup history.
The unsigned iPhone and simulator builds passed. Native acceptance is open.

Updated: 2026-11-24. Stable v0.7.9 contains phone and Android TV 0.7.9/code
7009003 and LG 0.7.9. The five Production asset digests match signed Staging.
Both Android emulator simulations and the LG browser mock passed. Website
downloads and update metadata point to v0.7.9. See [EVIDENCE.md](EVIDENCE.md).

The Android TV size regression forces a catalog rebuild to verify Home focus
and the All apps long-press menu. The hosted API 34 sweep passed. Required
simulations retry runner failures on fresh emulators and keep functional
assertions blocking. See [CI](CI.md).

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
metadata on GitHub Pages from About. Download and install fetches the signed
update for the installed app and opens Android's confirmation screen.

Keys stay outside Git and PR jobs, in private recovery and the main-only
android-release environment. Validate is PR-only. Build provides Development
artifacts and signed Staging prereleases. Production verifies a pinned Staging
candidate with Device simulation and deploys its screenshots and update metadata.
Staging assigns app versions and Android version codes before signing.
See [CI](CI.md) and [Releasing](RELEASING.md).

Transition from debug signing requires one fresh install and removes local data;
later signed releases preserve identity. Keep an off-device signing recovery copy.

Overwrite only the existing Miqat-latest review exports under state/builds/.
Keep the existing debug review signer. Release keys and recovery material are in
private state/signing/ and encrypted environment secrets; never print passwords.

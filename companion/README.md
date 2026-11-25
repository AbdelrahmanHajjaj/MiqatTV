# Miqat Phone and Your TVs

Use **Prayer** for the phone's own schedule, alerts and widgets. Open **Your
TVs** to install Miqat on a TV, pair it or use the remote. Each device keeps
its own prayer settings. [Android phone guide](../docs/PHONE.md)

## Install Miqat on a TV

In **Your TVs**, tap **Set up my TV** and choose your TV
platform. Follow the preparation steps. Select **Production** for the stable
release or **Staging** for a prerelease. Use **Advanced** to select a local APK
or IPK. Miqat shows download and transfer progress.

Android uses ADB and offers optional cleanup and recovery. LG uses Developer
Mode and asks you to approve the SSH identity. You can save the LG address and
credentials for later use. After installation, pair the TV to enable everyday
control. [Android setup and recovery](../docs/ANDROID-SETUP.md) · [LG setup](../docs/WEBOS.md)

## Pair and control

1. Connect the phone and TV to the same home network.
2. On the TV, open **Paired phones → Pair phone**.
3. On the phone, open **Your TVs → Pair a TV** and scan the TV's QR code.
4. Confirm the matching code with the TV remote.

Open a paired TV to change supported settings or use Home, inputs, radio and
the 12-second Adhan test. Settings save as you change them and read the TV's
latest values when you open them. Miqat checks for conflicting edits and keeps
unrelated settings. Each phone has its own revocable credentials.

Use **Your TVs → ⋮ → Forget TV** to remove the TV from your phone. To revoke
that phone's access, use **Paired phones** on the TV. Pair again when
moving from the old app identity. [Pairing protocol](../docs/COMPANION-LAN.md)

## Update a paired TV

Open a paired TV and choose **Check for updates**. Choose **Production** or
**Staging**, then confirm the download and installation if a newer version is
available. The phone uses the selected TV's saved installation connection.
Android TV needs the phone's existing debugging authorization and LG needs an
active Developer Mode session with remembered credentials. Android TV's
wireless debugging may need to be turned back on after a restart.

## Development

`core/` contains portable settings and draft rules. `android/` owns the UI,
camera and encrypted storage. `android-shared/` contains LAN and QR helpers.
`generate.mjs` builds profiles and resources from the companion contract and
shared sources. [Architecture](../docs/ARCHITECTURE.md) · [Companion history](../docs/COMPANION-WORK.md)

With JDK 17, Node 24 and Android SDK 34, run from the repository root:

```sh
node companion/generate.test.mjs
node companion/qr.test.cjs
node companion/android-shared/check.mjs
./gradlew -p companion/core check jvmJar --no-daemon
./gradlew :companion:assembleDebug :companion:lintDebug :companion:assembleDebugAndroidTest --no-daemon
pwsh -File companion/export-apk.ps1
```

The export replaces `state/builds/companion/Miqat-latest.apk` in the primary
checkout.

Run native suites on an owned emulator with
`tv.miqat.companion.test/tv.miqat.companion.CompanionCheck` and the relevant
`-e suite` option. Add `-e capture true` for native screenshots.
[Native suite requirements](../docs/TESTING.md#miqat-phone-and-tv-companion-mode)

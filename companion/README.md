# Miqat

Standalone Android prayer app with launcher label `Miqat`, independent offline
prayer alerts and integrated Android/LG TV setup and control. See
[phone first use, settings and limits](../docs/PHONE.md).
See [scope and evidence](../docs/COMPANION-WORK.md) and the
[foreground LAN protocol](../docs/COMPANION-LAN.md).

The phone uses Miqat's shared artwork, tokens, palettes, icons and mihrab geometry.
Java Views expose inline +/- controls, carousels and anchored choices. Appearance
keeps a representative phone preview with Apply/Cancel. Optional demo TVs remain
in Preferences; they do not establish hardware support.

Pair on the same home network through Settings / Phones on the active TV, then
confirm the matching code with its physical remote. Both TV shells use unique
pinned TLS identities and independently revocable phone credentials. Settings
reads only on explicit entry; reconnect never uploads cached values. The TV
validates baseline changes and preserves unrelated edits. Home, discovered
inputs, radio and a 12-second adhan preview/Stop are explicit foreground commands.
Uncertain writes/commands are never blindly replayed.
Use Your TVs → ⋮ → Forget TV to remove a saved TV and its cache from the phone.
Revoking phone access on the TV still requires the TV remote.

`core/` contains Kotlin settings/draft rules; `android/` owns Views, camera and
encrypted persistence; `android-shared/` owns LAN/QR helpers. `contract.json`
extends canonical TV controls, and `generate.mjs` produces ignored profiles and
Android resources from shared sources. Phone-only controls live in `shared/data/phone-ui-contract.json`; TV controls
retain their existing shared contract.

**Your TVs → Set up Android TV** opens the shared installer inside this app:
local APK/download, guarded ADB setup, optional reviewed cleanup and recovery.
It follows the phone language. Successful setup offers **Pair TV**; cancelling
or finishing incomplete setup returns to Your TVs. Installation does not grant
everyday remote access: normal TV approval is still required. LG can be paired
through **Your TVs → Set up LG TV**, using Developer Mode, SSH host-key approval
and a local Miqat IPK. SSH credentials are session-only and independent from
ordinary remote pairing. See [LG setup and limits](../docs/WEBOS.md).
See [setup and recovery](../docs/ANDROID-SETUP.md).

Miqat uses tv.miqat packages and its own QR prefix. Install/pair fresh; old-app
credentials are not migrated. No cloud, account, Bluetooth/Wi-Fi Direct,
background TV listener, unattended updates or iOS implementation is included.

With JDK 17, Node 24 and Android SDK 34:

```
node companion/generate.test.mjs
node companion/qr.test.cjs
node companion/android-shared/check.mjs
./gradlew -p companion/core check jvmJar --no-daemon
./gradlew :companion:assembleDebug :companion:lintDebug :companion:assembleDebugAndroidTest --no-daemon
pwsh -File companion/export-apk.ps1
```

Export the latest phone APK to the primary checkout's ignored
`state/builds/companion/Miqat-latest.apk`, consistently across worktrees. Keep only
the latest export; do not create per-task evidence folders or duplicate outputs.
Verification still runs, with concise results in chat.

Native suites use `tv.miqat.companion.test/tv.miqat.companion.CompanionCheck`,
optionally with `-e suite lan`, `camera`, `live`, `live-lg`, `phone-ui`, `phone-denied` or `phone-runtime`.
The runtime suite needs explicitly granted notification/exact-alarm access on
an owned muted emulator; the denied suite requires one of those permissions denied. Owned-emulator live
fixtures run via `companion/check-live.ps1` and `companion/check-lg.cjs` (historical
Node executable supplied as its final argument). These are not physical-TV passes.

Native screenshots are opt-in with `-e capture true`; LG browser captures use
`--capture`. Normal checks print results without creating capture folders.

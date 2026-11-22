# Miqat

Standalone Android prayer app with launcher label `Miqat`, independent offline
prayer alerts and integrated Android/LG TV setup and control. See
[phone first use, settings and limits](../docs/PHONE.md).
See [scope and evidence](../docs/COMPANION-WORK.md) and the
[foreground LAN protocol](../docs/COMPANION-LAN.md).

[Explore all Miqat apps](https://abdelrahmanhajjaj.github.io/MiqatTV/en/) ·
[Current phone/companion screenshots](../docs/screenshots/README.md)

## Two uses in one app

**Prayer** is this phone's offline prayer day, Hijri calendar/events, per-prayer
alerts, named audio library, Qibla and adaptive widgets. **Phone settings** controls
its location, appearance and Android permissions. **Your TVs** is optional TV
installation, pairing, settings and remote control. It never supplies the phone's
schedule or receives phone prayer edits. Ordinary phone and TV settings save
immediately; voice/import/calibration dialogs retain their explicit validation.

The phone uses Miqat's shared artwork, tokens, palettes, icons and mihrab geometry.
Java Views expose inline +/- controls, visible short choices and searchable catalogs. Appearance
for a TV keeps a representative phone preview and saves each valid selection directly. Phone prayer
preferences save independently, with immediate numeric steps and validated text entry. Optional demo TVs remain
in Preferences; they do not establish hardware support.

Phone Home and the Hijri calendar share a readable six-prayer timetable and recurring
Hijri events. Optional automatic location requires explicit background access and
uses Android scheduled jobs while the app is closed. Placed
widgets adapt their presentation to allocated size, with canonical prayer icons,
fonts and next-prayer highlights. Settings keep style/date choices per instance.
Home artwork can be dimmed or disabled; weekday overrides expand in place.

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

**Your TVs → Set up my TV** and **Pair a new TV** open the common platform
chooser, preparation guide and latest/stable/preview channel selection. Advanced
switches to local APK/IPK selection. Android uses the shared installer runtime
with optional reviewed cleanup/recovery and the phone's selected theme/language.
LG uses Developer Mode, explicit SSH host-key approval and verified public/local
IPKs. Unsupported platforms are omitted. Successful setup
offers ordinary pairing; installation never grants everyday remote access.
SSH credentials are session-only and separate from normal LAN pairing.
See [LG setup and limits](../docs/WEBOS.md).
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
optionally with `-e suite lan`, `camera`, `live`, `live-lg`, `phone-ui`, `phone-size`, `phone-denied` or `phone-runtime`.
The runtime suite needs explicitly granted notification/exact-alarm access on
an owned muted emulator; the denied suite checks notification denial prevents output and exact-only denial
uses approximate scheduling. Owned-emulator live
fixtures run via `companion/check-live.ps1` and `companion/check-lg.cjs` (historical
Node executable supplied as its final argument). These are not physical-TV passes.

Native screenshots are opt-in with `-e capture true`; LG browser captures use
`--capture`. Normal checks print results without creating capture folders.

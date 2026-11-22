# Build and install Miqat

Use JDK 17 and Node 24. Android builds also need SDK 34 and ANDROID_HOME or an
ignored local.properties. Run commands from the repository root. On Windows use
gradlew.bat and npm.cmd in place of ./gradlew and npm.

## Android TV

```sh
./gradlew -p platforms/android-tv verifyApkSize lintDebug --no-daemon
```

Output: platforms/android-tv/build/outputs/apk/debug/app-debug.apk.
Follow [Android setup](ANDROID-SETUP.md) to install and configure Home, prayer
service and permissions. Keep the stock Home available until setup works.

For installation from an Android phone, build both apps:

```sh
./gradlew :app:assembleDebug :installer:assembleDebug --no-daemon
```

Install installer/build/outputs/apk/debug/installer-debug.apk on the phone, copy
the TV APK to it, then choose **Install a local launcher APK**. The installer is
an ADB setup/recovery tool; it is separate from the companion app. Online download
requires a future stable release with a matching miqat.apk asset.

## LG webOS

Prepare the local audio files below, then follow the [LG guide](WEBOS.md).
The output is an IPK, not an Android APK. The new app ID is tv.miqat.webos and
its service is tv.miqat.webos.service. Developer Mode and CLI device pairing
are required for the documented installation path.

## Browser preview

```sh
npm --prefix shared/core ci --ignore-scripts
npm --prefix site ci
npm --prefix site run dev
```

Try the [hosted preview](https://abdelrahmanhajjaj.github.io/MiqatTV/) or follow
[website deployment](CI.md#website) for its automatic main-branch updates.
The preview simulates TV integrations. Run core, preview and LG builds sequentially
because they generate the same shared JavaScript files.

## Audio assets

The repository includes the reviewed 13 adhan recordings and 12 announcement
clips under shared/media/. Android and LG package the same bytes. Run
sha256sum --check shared/media/SHA256SUMS to check the complete set, then
node tools/prepare-adhans.mjs --check to verify the approved prepared files.

The maintainer approved their inclusion in public builds on 18 November 2026.
Media retains its own attribution and terms; see [notices](THIRD-PARTY.md).
Originals and preparation stamps stay under ignored state/media-originals/.
The 13 retained adhan voices received maintainer-approved mild cleanup on 21 November
2026; the reminder clips are unchanged. Restore missing reviewed files from Git.
The fetch scripts retrieve source material, not the exact cleaned outputs, and
are not build prerequisites. Review changed recordings and update checksums
before publishing replacements.

## CI builds

[Validate](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/workflows/validate.yml)
produces full-audio APK and LG IPK artifacts after building and testing the source.
Artifacts expire after seven days. They are test builds; Android uses a CI debug
signing key that can differ between runs. Stable signing is not configured.
See [CI and draft publication](CI.md) before distributing an update.

## Fresh installation

Miqat uses new identities: tv.miqat.home, tv.miqat.installer and tv.miqat.webos.
They install separately from earlier builds. Before removing a previous launcher,
record its prayer, appearance, input and startup choices and disable its scheduled
alerts/startup. On Android, restore a working stock Home before removing it.

The installer exports a device setup/recovery record. That record restores Android
system setup; it does not export Miqat prayer or appearance preferences. A portable
settings export/import feature is not included in this foundation. Re-enter those
choices after installation and check the prayer schedule before enabling alerts.

No physical TV is changed by building this checkout. Hardware installation and
watched picture/sound/remote checks need a separate authorized session.

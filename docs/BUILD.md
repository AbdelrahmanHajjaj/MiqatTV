# Build and install Miqat

Use JDK 17 and Node 24. Android also needs SDK 34 and `ANDROID_HOME` or an
ignored `local.properties`. Run commands from the repository root. On Windows
use `gradlew.bat` and `npm.cmd` in place of `./gradlew` and `npm`.

## Android phone

```sh
./gradlew :companion:assembleDebug :companion:lintDebug --no-daemon
```

Install `companion/android/build/outputs/apk/debug/companion-debug.apk` on Android
8 or later. The public release package is `miqat-phone.apk`.
[Phone guide](PHONE.md)

For owner review, `companion/export-apk.ps1` replaces
`state/builds/companion/Miqat-latest.apk`.

## Android TV

```sh
./gradlew -p platforms/android-tv verifyApkSize lintDebug --no-daemon
```

The APK is `platforms/android-tv/build/outputs/apk/debug/app-debug.apk`.
To install it from Miqat Phone, copy it to the phone, then open **Your TVs →
Set up my TV → Android / Google TV → Advanced → Choose a local package**.
The public release package is `miqat.apk`. Keep stock Home available until
setup works. [Android TV setup and recovery](ANDROID-SETUP.md)

## LG webOS

Follow the LG guide to build an IPK and install it through Developer Mode.
[LG setup](WEBOS.md)

## Product website and TV preview

```sh
npm --prefix shared/core ci --ignore-scripts
npm --prefix site ci
npm --prefix site run dev
```

Run core, preview and LG builds in sequence. They write the same generated
JavaScript files. The preview simulates TV features. [Website deployment](CI.md#website)

## Audio assets

Android and LG ship the same 13 Adhan recordings and 12 announcement clips
from `shared/media/`. Restore missing approved files from Git. Source fetch
scripts retrieve the original recordings. Keep originals and preparation stamps
under ignored `state/media-originals/`.

```sh
sha256sum --check shared/media/SHA256SUMS
node tools/prepare-adhans.mjs --check
```

Review changed recordings and update checksums before publishing replacements.
[Audio audit](AUDIO-AUDIT.md) · [Attribution and terms](THIRD-PARTY.md)

## CI builds

Choose **Development** for a branch build with seven-day artifacts. Its Android
debug key can vary between runs. Choose **Staging** on main for a signed
prerelease. [Build workflow](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/workflows/build.yml) · [Release procedure](RELEASING.md)

## Fresh installation

The app IDs are `tv.miqat.companion`, `tv.miqat.home` and `tv.miqat.webos`.
Before removing an earlier launcher, record its prayer, appearance, input and
startup choices, then turn off scheduled alerts and startup. On Android,
restore stock Home before removing the previous launcher.

The phone's recovery export restores Android system setup. Record Miqat's
prayer and appearance preferences separately and re-enter them after
installing. Check the schedule before enabling alerts.

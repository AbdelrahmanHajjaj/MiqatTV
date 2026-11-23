# Releases

[Validate](CI.md) builds Miqat for Android phones, Android TV and LG webOS.
The phone includes standalone prayers and TV companion mode. Each main commit must pass its applicable core, Android build/lint, LG and
website checks before its artifacts can enter the draft publication workflow.

## Create a draft

1. Open [Draft build release](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/workflows/publish.yml)
   on GitHub and click **Run workflow** on `main`. Leave both inputs blank to use
   the newest successful Validate run for that commit and the first unused
   preview tag for the next patch after the latest stable release.
2. To override either default, enter a successful Validate run ID for that same
   commit or an unused tag such as `v0.7.7-preview.1`. If validation is missing
   or its artifacts expired, run Validate on `main` first.
3. Inspect the draft assets and SHA256SUMS, source commit, validation link and
   signing configuration before publication.

The draft contains three packages and their SHA256SUMS:

| Asset | Purpose |
| --- | --- |
| `miqat-phone.apk` | Standalone Android prayers and optional TV companion/setup mode. |
| `miqat.apk` | Android TV / Google TV launcher. |
| `tv.miqat.webos_<version>_all.ipk` | LG webOS app and service. |

All come from the same successful current-main Validate run. Validation compiles
and lints the Android release variants, then the publisher signs those unsigned
APKs. Instrumentation/debug APKs are excluded. The publisher refuses stale,
failed, PR/fork or other-workflow runs. It creates an unpublished draft prerelease.

## Promote a verified preview to stable

With owner approval, create a clean stable tag at the preview's source commit and
copy its verified packages and SHA256SUMS to a new draft. Compare every
uploaded asset's size and SHA-256 digest with the published preview before
publishing without prerelease status and marking it Latest. Preserve the preview
tag and assets. Promotion reuses the signed bytes; no rebuild, new version code
or signing-key access is needed. Update website download links and current guides.
Set the tag and both APK version names/codes in `site/src/lib/stable-release.json`
from the verified published artifacts. GitHub Pages serves this as `updates.json`.
An installed copy of the same signed preview needs no reinstall.

## Persistent Android signing

The phone and Android TV APKs use separate RSA release keys in one encrypted
PKCS12 keystore. `tools/release-certificates.json` records only public certificate
fingerprints, aliases and package IDs. Preserve these identities for updates.

GitHub's `android-release` environment allows the `main` branch only. It contains
`MIQAT_SIGNING_KEYSTORE_BASE64` and `MIQAT_SIGNING_PASSWORD` as encrypted secrets.
PR validation has no access to them. Only the manual publisher decodes the key
into its private runner temporary directory, signs the validated release APKs,
and deletes the temporary keystore on success/failure. Signing material never
enters a cache or artifact.

The publisher checks signatures, package IDs and non-debuggable release mode with
`tools/check_android_release.py`, then verifies exact packaged audio and hashes
the signed outputs. A missing key, unexpected signer or debug APK fails delivery.

The primary checkout's private `state/signing/` contains the recovery keystore,
password and recovery notes, restricted to the owner and SYSTEM. Keep a secure
off-device copy of that entire folder; GitHub secrets cannot be downloaded for
recovery. Never commit, upload as an artifact, or paste its contents into chat.

For a local release build:

```sh
./gradlew :app:assembleRelease :companion:assembleRelease --no-daemon
```

These outputs are unsigned under each module's `build/outputs/apk/release/`.
Use Android Build Tools 35.0.0 `zipalign -P 16 -f 4` before `apksigner sign`.
Pass `--ks state/signing/miqat-release.keystore`, the matching `--ks-key-alias`
from the public certificate file, and both `--ks-pass env:MIQAT_SIGNING_PASSWORD`
and `--key-pass env:MIQAT_SIGNING_PASSWORD`. Load the password from the private
recovery file into that process environment; never place its value in arguments.
Name the signed outputs `miqat.apk` and `miqat-phone.apk`,
then verify:

```sh
python3 tools/check_android_release.py "$ANDROID_HOME/build-tools/35.0.0" state/release/miqat.apk state/release/miqat-phone.apk
```

Moving from the old debug-signed previews requires a fresh install once. Removing
an Android app removes its local data, including phone settings/imported audio;
keep original recordings and installer recovery records before switching.
Future release APKs keep the same signing keys. Debug review builds retain their
separate development key. Publishing a preview and building in release mode are
independent: signed release-mode APKs may still be labelled prerelease on GitHub.

## Versioning and publication

Phone versions live in `companion/android/build.gradle`, Android TV versions in
`platforms/android-tv/build.gradle`.
LG's version must match package.json, its lockfile, appinfo.json and service/package.json.
Increase version codes for distributed updates, retain the new Miqat identities
and chosen signing key, and never replace a published version's binaries.

The reviewed media set is included under shared/media/ with maintainer approval
and its own attribution; see [third-party notices](THIRD-PARTY.md). Private
originals and device data stay ignored. Follow [TESTING.md](TESTING.md) and record
the results.

Miqat Phone’s TV setup offers public Latest, Stable and Preview download
channels and can install local packages under Advanced. See [build/install](BUILD.md).

When publishing an app release, update availability copy in README, PHONE.md and
the bilingual website, and check the package links and [screenshot provenance](screenshots/README.md).
Public app release publication and physical-device deployment require separate
explicit authorization.

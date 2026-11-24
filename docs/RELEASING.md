# Releases

Miqat ships **Miqat Phone**, Android TV and LG webOS. TV companion mode is part
of the standalone Android phone app. [CI](CI.md) describes the PR gate.

| Channel | Where to get it | Android signing |
| --- | --- | --- |
| **Development** | Artifacts on an on-demand Build run | Development key |
| **Staging** | GitHub prerelease tagged `vMAJOR.MINOR.PATCH-staging.N` | Persistent release keys |
| **Production** | GitHub's Latest release and the public website | The same signed Staging APKs |

## Development or Staging build

1. Open [Build](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/workflows/build.yml).
2. Select **development** for a branch review build. Download
   `phone-development`, `android-tv-development` and `lg-development` from the
   run. These artifacts expire after seven days.
3. Select **staging** on `main` to publish a signed prerelease. Build allocates
   the first unused Staging tag for the next Production patch. It verifies the
   Android identities and packaged audio and records the source SHA and Build
   run in the release notes.

The Actions run title shows its channel and branch. Open the run's **Summary**
for **Built app versions**, including both Android version codes. A Staging
run also links the published release tag. Build allocates the bundle tag and
Staging suffix automatically. Each app keeps the version and Android version
code configured in its source.

The Staging prerelease contains `miqat-phone.apk`, `miqat.apk`,
`tv.miqat.webos_<version>_all.ipk`, `screenshots.zip` and `SHA256SUMS`.
The screenshot archive contains the reviewed bilingual PNGs and provenance
tracked under [docs/screenshots](screenshots/README.md) at the candidate commit.

## Production promotion

1. Open [Promote to Production](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/workflows/promote.yml)
   on `main`. Leave **Staging tag** blank for the most recently published
   Staging candidate, or enter a full tag such as `v0.7.8-staging.2`.
2. Click **Run workflow** to approve the live release and website deployment.
   The workflow pins the tag, source and asset digests before running
   **Device simulation**. In parallel
   it prepares and verifies the website using the candidate's screenshots and
   signed versions. A failed prerequisite blocks publication.
3. After both pass, the workflow publishes the Production tag at the same
   source commit with the same assets and deploys the verified site. The
   production tag drops the `-staging.N` suffix. About Miqat checks the
   Production version served at `/updates.json`.

For checks alone, open [Device simulation](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/workflows/device-simulation.yml)
on `main` with the same optional tag. It produces Android native and LG
browser-mock evidence. Promotion calls it automatically, so a separate run is
optional. Follow the platform guides for physical-device acceptance.

Retry failed jobs in the same Production run to keep the candidate pinned. If
you need to start a new run, enter the original Staging tag. A partial upload
resumes against matching assets. If publication succeeded and Pages failed,
retry the Pages job while its artifact is retained. A stale run cannot deploy
over a newer Production release.

## Persistent Android signing

The phone and Android TV APKs use separate RSA release keys in one encrypted
PKCS12 keystore. `tools/release-certificates.json` records only public certificate
fingerprints, aliases and package IDs. Preserve these identities for updates.

GitHub's `android-release` environment allows the `main` branch only. It contains
`MIQAT_SIGNING_KEYSTORE_BASE64` and `MIQAT_SIGNING_PASSWORD` as encrypted secrets.
PR validation has no access to them. Only the Staging job decodes the key
into its private runner temporary directory, signs the validated release APKs,
and deletes the temporary keystore on success/failure. Signing material never
enters a cache or artifact.

Staging checks signatures, package IDs and non-debuggable release mode with
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

Moving from a debug-signed Development package to Staging or Production requires
a fresh install. Removing an Android app removes its local data, including phone
settings and imported audio. Keep original recordings and installer recovery
records before switching. Staging and Production share the signing identities.

## Versioning

Phone versions live in `companion/android/build.gradle`, Android TV versions in
`platforms/android-tv/build.gradle`. LG's version must match package.json, its
lockfile, appinfo.json and service/package.json. Increase version codes when
shipping changed APKs. Preserve app identities and signing keys. Published
packages are immutable.

Miqat Phone TV setup offers **Production** and **Staging** public downloads.
For a Development build, download the Actions artifact and select its APK or
IPK through **Advanced** in TV setup.

Follow [Testing](TESTING.md) and record evidence in [EVIDENCE.md](EVIDENCE.md).
Keep README, the app guides and both website languages aligned with the shipped
features, and record [screenshot provenance](screenshots/README.md).
Public release publication and physical-device deployment require separate
explicit authorization.

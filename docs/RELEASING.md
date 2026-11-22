# Releases

[Validate](CI.md) builds the Android phone app, Android TV, the optional standalone
installer and LG webOS. The phone includes standalone prayers and TV companion
mode. Each main commit must pass its applicable core, phone/TV emulator, LG and
website checks before its artifacts can enter the draft publication workflow.

## Create a draft

1. Select a successful Validate run for the current main commit.
2. Run Draft build release on main with that run ID and a unique version tag,
   for example v0.7.0-preview.1.
3. Inspect the draft assets and SHA256SUMS, source commit, validation link and
   signing configuration before publication.

The draft contains four packages and their SHA256SUMS:

| Asset | Purpose |
| --- | --- |
| `miqat-phone.apk` | Standalone Android prayers and optional TV companion/setup mode. |
| `miqat.apk` | Android TV / Google TV launcher. |
| `tv.miqat.webos_<version>_all.ipk` | LG webOS app and service. |
| `miqat-installer.apk` | Optional standalone Android TV setup/recovery host. |

All come from the same successful current-main Validate run. Instrumentation APKs
are excluded. The publisher refuses stale, failed, PR/fork or other-workflow runs.
It creates an unpublished draft prerelease only.

## Before stable distribution

CI Android APKs have runner-generated debug signatures, which can change between
builds. Configure and back up a private persistent signing key before stable
updates. Never commit it or cache it in a public runner cache.

Phone versions live in `companion/android/build.gradle`, Android TV versions in
`platforms/android-tv/build.gradle`, and installer versions in `installer/build.gradle`.
LG's version must match package.json, its lockfile, appinfo.json and service/package.json.
Increase version codes for distributed updates, retain the new Miqat identities
and chosen signing key, and never replace a published version's binaries.

The reviewed media set is included under shared/media/ with maintainer approval
and its own attribution; see [third-party notices](THIRD-PARTY.md). Private
originals and device data stay ignored. Follow [TESTING.md](TESTING.md) and record
the results.

The standalone installer's default downloader expects miqat.apk in a stable
release. The main phone app's TV setup separately offers public Latest, Stable
and Preview channels. Private drafts/CI artifacts are not public downloads;
Advanced local packages remain usable. See [build/install](BUILD.md).

When publishing an app release, update availability copy in README, PHONE.md and
the bilingual website, and check the package links and [screenshot provenance](screenshots/README.md).
Public app release publication and physical-device deployment require separate
explicit authorization.

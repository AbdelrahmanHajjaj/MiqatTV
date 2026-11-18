# Releases

[Validate](CI.md) builds full-audio Android TV/installer APKs and an LG IPK. Each
main commit must pass its core, native-emulator, LG and preview checks before
its artifacts can enter the draft publication workflow.

## Create a draft

1. Select a successful Validate run for the current main commit.
2. Run Draft build release on main with that run ID and a unique version tag,
   for example v0.7.0-preview.1.
3. Inspect the draft assets and SHA256SUMS, source commit, validation link and
   stated hardware/signing limits before any public release.

The draft contains miqat.apk, miqat-installer.apk, the versioned tv.miqat.webos IPK
and SHA256SUMS. Instrumentation APKs are excluded. The publisher refuses stale,
failed, PR/fork or other-workflow runs. It creates a draft prerelease only.

## Before stable distribution

CI Android APKs have runner-generated debug signatures, which can change between
builds. Configure and back up a private persistent signing key before stable
updates. Never commit it or cache it in a public runner cache.

Android TV and installer versions live in their build.gradle files. LG's version
must match package.json, its lockfile, appinfo.json and service/package.json.
Increase version codes for distributed updates, retain the new Miqat identities
and chosen signing key, and never replace a published version's binaries.

The reviewed media set is included under shared/media/ with maintainer approval
and its own attribution; see [third-party notices](THIRD-PARTY.md). Private
originals and device data stay ignored. Follow [TESTING.md](TESTING.md) and record
actual TV/phone evidence. CI/emulator results do not establish physical acceptance.

The installer's latest-release downloader expects miqat.apk in a stable release.
Drafts and prereleases do not satisfy it. Stable publication and device deployment
require separate explicit authorization.

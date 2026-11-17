# Releases

Publish only an approved, validated commit in AbdelrahmanHajjaj/MiqatTV.
The new repository has no release assets or publishing workflow yet.

Android TV and the phone installer have separate versions in their build.gradle
files. LG's version must match its package.json, lockfile, appinfo.json and service
package.json. Increment version codes/names for distributed builds and retain the
chosen signing key for later updates within the new Miqat identity.

Follow [TESTING.md](TESTING.md), verify package IDs, versions, hashes and signing
certificates, and record actual hardware limits. Initial Miqat installation is a
fresh install; see [BUILD.md](BUILD.md). A build or emulator pass is not a TV pass.

Android outputs are platforms/android-tv/build/outputs/apk/debug/app-debug.apk and
installer/build/outputs/apk/debug/installer-debug.apk. Use miqat.apk and
miqat-installer.apk for public assets. The installer's stable-release downloader
expects miqat.apk. A draft/prerelease cannot satisfy its latest stable-release URL.
LG packaging follows [WEBOS.md](WEBOS.md) and produces tv.miqat.webos_VERSION_all.ipk.

Keep source/third-party notices in each artifact. Recordings require redistribution
permission; local build inputs alone do not grant it. Exclude keys and private state.
Create tags/releases only with explicit authorization, verify uploaded hashes,
and test the intended download before announcing it. Never substitute another
project's binary or silently replace the contents of a published version.

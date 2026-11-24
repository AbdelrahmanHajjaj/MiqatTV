# Miqat for iPhone

Development target for iOS 17 and later. It builds the shared prayer and companion
Kotlin code into the static `MiqatKit` framework. SwiftUI renders the iPhone
interface. Core Location, UserNotifications and the civil/Hijri calendars are
iOS adapters. Offline astronomy uses the same pinned Adhan release as the LG
shell in JavaScriptCore. The Kotlin core handles rounding and prayer selection.

## Build on Apple silicon

Use Xcode 16.4, JDK 17 and Node 24. From the repo root:

```sh
npm --prefix companion/ios ci --ignore-scripts
node companion/ios/astronomy.test.mjs
xcodebuild -project companion/ios/Miqat.xcodeproj -scheme Miqat \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath state/ios-build CODE_SIGNING_ALLOWED=NO build
```

For a local simulator run, open `companion/ios/Miqat.xcodeproj` and choose an
iPhone simulator. The Xcode process needs Node and JDK 17 on its PATH. The build
phase compiles the framework and copies canonical data and license notices into
the application bundle.

The app and widget targets use the `group.tv.miqat.ios` App Group to share
an offline prayer timetable. A signed widget check needs both targets in a
development team with that group enabled. Signing identities stay local.

In **Your TVs → Download latest TV build**, choose Android/Google TV or LG and
select Production or Staging. Miqat checks the release's SHA-256 and file size
before offering the package to share or save to Files. Use the existing
[Android TV setup](../../docs/ANDROID-SETUP.md) or [LG setup](../../docs/WEBOS.md)
to install the package on a TV.

For LG, **Choose a local package** imports an IPK from Files. Miqat reads its
package identifier and version from the control archive before sharing it.

[Parity queue and validation status](../../docs/IOS-WORK.md).

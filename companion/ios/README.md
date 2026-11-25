# Miqat for iPhone

Development target for iOS 17 and later. It builds the shared prayer and companion
Kotlin code into the static `MiqatKit` framework. SwiftUI renders the iPhone
interface. Core Location, UserNotifications and the civil/Hijri calendars are
iOS adapters. Offline astronomy uses the same pinned Adhan release as the LG
shell in JavaScriptCore. The Kotlin core handles rounding and prayer selection.

## Build on Apple silicon

To use a local Git bundle, start from a Miqat clone that contains the port base
`e0c46d3`. Fetch the committed feature branch into that clone, then create its
worktree. Use the path to the bundle you received.

```sh
git bundle verify /path/to/miqat-ios.bundle
git fetch /path/to/miqat-ios.bundle refs/heads/feature/ios-app-port:refs/heads/feature/ios-app-port
git worktree add ../Miqat-iOS feature/ios-app-port
cd ../Miqat-iOS
```

Use Xcode 16.3 with the iOS 18.4 simulator runtime, plus CMake, JDK 17 and
Node 24. Xcode needs the runtime to compile the asset catalog for either
target, even for an unsigned build. If it is missing, install it with
`xcodebuild -downloadPlatform iOS -buildVersion 18.4`. From the repo root:

```sh
npm --prefix companion/ios ci --ignore-scripts
node companion/ios/astronomy.test.mjs
sh companion/ios/prepare-ssh.sh iphonesimulator
sh companion/ios/prepare-adb-crypto.sh iphonesimulator
xcodebuild -project companion/ios/Miqat.xcodeproj -scheme Miqat \
  -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath state/ios-build CODE_SIGNING_ALLOWED=NO build
```

For a local simulator run, open `companion/ios/Miqat.xcodeproj` and choose an
iPhone simulator. The Xcode process needs Node and JDK 17 on its PATH. The build
phase compiles the framework and copies canonical data and license notices into
the application bundle. Run both transport dependency preparation scripts
with `iphoneos` before building for an iPhone.

After a successful [iOS development build](../../.github/workflows/ios-development.yml),
download and extract `Miqat-iOS-Simulator.zip` on an Apple silicon Mac. Boot
an iOS 17 or later iPhone simulator. From the folder containing `Miqat.app`, run:

```sh
xcrun simctl install booted Miqat.app
xcrun simctl launch booted tv.miqat.ios
```

The workflow builds both the Simulator and iPhone SDK targets without signing.
It uploads the simulator app for local testing. Use the local Xcode targets
and development signing for iPhone and signed App Group checks.

For a targeted first-launch check, dispatch the development workflow with
`simulator_launch=true`. The check uses a fresh iPhone 16 simulator to install
the built app and confirm the process stays alive. Follow the
[first-use steps](../../docs/IOS-WORK.md#first-use-regression-on-iphone) for
onboarding, offline prayer times, and permission behavior. Dispatch once with
`simulator_first_use=true` to run all simulator journeys alongside the unsigned
builds and portable checks. The journeys cover onboarding, Cairo timetable parity,
the simulated Qibla position, fasting choice persistence and reset, and the local
TV demo. Each journey starts with fresh simulator data.
Use `simulator_first_use_case` to rerun a specific case.

The app and widget targets use the `group.tv.miqat.ios` App Group to share
an offline prayer timetable. A signed widget check needs both targets in a
development team with that group enabled. Signing identities stay local.

The app privacy manifest declares app-local UserDefaults use for TV setup and
demo preferences. Before App Store submission, review the linked dependencies
and archive privacy report. The development workflow uses Xcode 16.3. Apple
currently requires Xcode 26 or later with a supported SDK for App Store Connect.
[Apple SDK requirements](https://developer.apple.com/news/upcoming-requirements/)

In **Your TVs → Download latest TV build**, choose Android/Google TV or LG and
select Production or Staging. Miqat checks the release's SHA-256 and file size
before offering the package to share or save to Files. Use the existing
[Android TV setup](../../docs/ANDROID-SETUP.md) or [LG setup](../../docs/WEBOS.md)
to install the package on a TV.

**Choose a local package** accepts an Android TV APK or an LG IPK from Files.
Miqat checks the package identifier and version before sharing it.

For direct LG installation, open **Your TVs → Install Miqat on LG webOS**.
Enable Developer Mode and Key Server on the TV. Enter the TV's address and
passphrase, then confirm its SSH fingerprint. Choose a public build or a local
IPK and compare its version with the one on the TV before installing. The app
verifies the installed version and offers to launch Miqat and pair the phone.
You can remember the passphrase on this iPhone and forget the installation
identity from the same screen.

In **Your TVs → Find Android TV**, start a search to see the local addresses
and ports advertised by Android TV for network debugging and wireless pairing.
Allow Miqat to use the local network when iOS prompts. Select a result to
fill its connection details, or open **Your TVs → Install Miqat on Android TV**
and enter the TV's private IPv4 address. With classic debugging, enter its
port and approve the request on the TV. With **Wireless debugging**, enter
the six-digit code and port from the TV's pairing screen, then connect using
the separate debugging port. Confirm that the address and debugging port
match the TV. After connecting, select a build or local APK, confirm installation
and check the installed version before launching Miqat. To set Miqat as Home and enable
its remote-button service, choose **Set up Home and remote buttons**. The
iPhone saves the prior configuration before applying changes. Reconnect to the
same TV and choose **Restore saved TV setup** to undo those changes.
Choose **Save recovery backup** to export `recovery.json` to Files. With
this version of the desktop tools, put the export in a private folder and
use `python -S -B tools/device.py --backup-dir <folder> restore` against the same TV.
Under **Optional TV setup**, you can enable the TV app's radio level and
sleep-timer permissions, startup on supported models, or Wi-Fi name display.
Miqat checks each option after applying it. Once Miqat is the active Home,
**Review TV apps** uses the bundled cleanup catalogue to offer optional apps.
Review the selection before turning apps off. Restore or export the saved
setup to re-enable apps turned off by this iPhone. **Apps turned off earlier**
uses the TV's cleanup history to show apps still off from another Miqat
installer. **Turn these apps back on** restores those apps. The review also
suggests previously cleaned apps that the TV has turned back on.

**Personalise Android TV** sets the TV's prayer city, language, Adhan,
spoken reminders and ordered Home favourites before companion pairing.
Miqat reads back changes to verify the save.

Use the desktop installer for CVTE setup.

[Parity queue and validation status](../../docs/IOS-WORK.md).

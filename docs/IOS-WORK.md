# Miqat iPhone parity queue

Branch `feature/ios-app-port`, forked at `e0c46d3`. Baseline is Miqat Phone
0.5.23 (39), stable v0.7.7. Use the [phone guide](PHONE.md), [phone controls](../shared/data/phone-ui-contract.json),
[Android instrumentation](../companion/android/src/androidTest/java/tv/miqat/companion)
and [LAN contract](COMPANION-LAN.md) as acceptance inputs.

Work in order. Record finished code and validation here before starting the next
row. Leave an unfinished row open and record its next concrete action. Compare
new Android releases to this pinned baseline before expanding the queue.
Android 0.5.24 and subsequent in-app APK updates were integrated from main.
The iOS update action still depends on an iOS distribution channel.

| Order | User journey | Status and remaining acceptance |
| --- | --- | --- |
| 1 | Apple application, Kotlin framework, canonical bundles and unsigned build | Both unsigned Xcode 16.3 builds and the first launch on a fresh iPhone 16 simulator passed. The simulator ZIP passed digest and archive checks. Signed iPhone acceptance is open. |
| 2 | Welcome, skip/resume, permission and confirmation, offline country/city search, fresh fix, manual coordinates and timezone, background location consent/travel refresh | Simulator passed manual London selection with location denied, opt-out through Skip and relaunch, all six timetable rows, and both answers to the notification prompt. Check manual coordinates and timezone, Always permission, significant-change delivery and manual edits during travel refresh. |
| 3 | Prayer Home, greeting and exact verse, Hijri/Gregorian date, next prayer, countdown seconds and six times, next-Fajr rollover, calculated city, method/school/high-latitude rule and offsets | 570 minute-for-minute Android comparisons passed. After Isha, the countdown labels tomorrow's prayer and today's timetable stays intact. Home opens alert settings from the five prayer rows. Native rollover, offsets, polar display and row navigation remain open. |
| 4 | Hijri month/day navigation, swipes and timetable, Hijri adjustment, current/upcoming and all recurring events | Home shows today's or the nearest upcoming observance and opens the calendar on its date. Month browsing keeps the prayer day selected and navigation stays within 366 days. Run the Apple fixture and verify Home links, calendar swipes and labels in Arabic and English. |
| 5 | English/Arabic and RTL, digits, 12/24-hour clocks, type scale, palettes, backgrounds, full-page previews, Home artwork and reduce motion | Setup and settings share visual palette and scene choices, and the full-page preview is read-only. Prayer Home and Adhan use the selected artwork and palette. Verify both locales, including the system app and widget labels with iOS in Arabic, artwork visibility, reduced motion and scale extremes. |
| 6 | Prayer alerts, enablement, mode, timing previews, weekday inclusion/overrides, quiet hours, before/after, explicit tests, permission and recovery | Simulator verified that setup saves Allow as alerts on and denial as alerts off. The planner keeps matching absolute-time requests and can replenish the queue in offline Background App Refresh. Verify permission recovery, timezone-change, delivery and background expiration. |
| 7 | Fasting eve, Suhoor and Iftar, choices, exclusions and overlapping selections | Uses Kotlin fasting rules. The iOS calendar adapter restores each civil day's true start and preserves the eve reminder's minute through a skipped hour. The Apple fixture covers spring, fall, half-hour and skipped-day transitions. Check independent delivery on iOS. |
| 8 | Approved voices including Fajr, named audio library/import and CRUD, previews, per-prayer selection, level/length and playback controls, foreground Adhan screen | Packaged 13 checksum-verified voices. Named imports share Android's validation. Active Adhan shows the scheduled time and recording on its controls and publishes progress to Now Playing. Home opens the controls from a full-width shortcut. Verify Files lifecycle, playback controls, motion, system controls and headphone removal. |
| 9 | Quran Radio station picker, play/pause/stop, reconnect, metadata, lock-screen playback and Adhan interruption | Uses canonical HTTPS stations. Stream completion and terminal failures can reconnect up to four times with Android's increasing retry delays. Thirty seconds of stable playback restores the budget. Verify the radio regression below, including audio ownership during Adhan and headphone removal. |
| 10 | Independent Qibla fix, true-north compass, numeric fallback, explicit map with route/pan/zoom/recenter | Compass and map share the displayed bearing. The phone-relative arrow waits for reliable true north. Run the Qibla regression below and check sighted and VoiceOver operation on a physical iPhone. |
| 11 | Home widgets, adaptive/strip/daily/circle presentations, settings per instance, date options, countdown and ring | The app stages prayer transitions from Kotlin and can replenish an installed widget during offline background refresh. Expired data offers an app refresh, with no Circle countdown updates until there is a target. Verify the Apple fixture, signed App Group transfer, timer and date rollover, medium timetable fit and per-instance choices. |
| 12 | About, feedback, update action, independent local storage and prayer reset | About, GitHub feedback and protected prayer settings are wired. Reset retains the recordings library and paired TVs, and reopens setup. Verify the reset regression below. The iOS update action awaits a stable iOS distribution channel. |
| 13 | Your TVs, demo, QR pairing/approval, pinned identity, independent credentials, settings/conflicts, local rename, Home/inputs/radio/Adhan/forget | Pairing sends a user-selected phone name and retains the local TV nickname when a saved credential reconnects. Uncertain settings writes require refresh and explicit retry. Verify native demo, TLS, Keychain, name and QR approval, readback, cancellation and conflict behavior. |
| 14 | Android/Google TV and LG setup, channels, local APK/IPK, download/transfer progress, verification, install/recovery, separate credentials | Public/local packages, LG SSH, Android classic and wireless setup, recovery/export, optional grants and personalisation are wired. LG removes its rejected temporary upload if the connection is still usable before install. Wireless setup checks the paired TV GUID. Cleanup reads TV history and offers undo. Apple fixtures and physical setup remain open. |
| 15 | End-to-end parity audit and local handoff, focused checks, support docs and changelog | Compared the Android phone guide and setup sources against the iPhone feature paths. Continue with manual coordinates, timezone and the remaining first-use regressions. Carry forward the platform differences and deferred features below. |

## Build and evidence

- Portable Kotlin companion JVM tests passed, including future reminder batches,
  equal-time candidates and expiry handling. Canonical phone generator tests
  passed. See [evidence](EVIDENCE.md#iphone-port-development-checkpoint).
- Use native iOS UI and system integrations backed by the existing Kotlin rules
  and canonical data. Keep Android and TV clients independent of iOS.
- Local host is Windows. It can run portable Gradle and resource checks.
  Apple framework linkage, SwiftUI and device behavior require macOS/Xcode.
  A local Git bundle can transfer the committed port to an existing Mac clone
  at the documented base. [Apple build and import steps](../companion/ios/README.md#build-on-apple-silicon)
- The owner authorized the GitHub push and a normal merge after the first Apple
  builds and required checks. [Pull request #49](https://github.com/AbdelrahmanHajjaj/MiqatTV/pull/49)
- Early Apple runs found a BoringSSL tool packaging error, a missing Xcode 16.3
  runtime and an iOS 18-only WidgetKit call. The fixes are on the port branch.
  Both unsigned Apple targets then passed. [Apple build and simulator ZIP](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/runs/45474659710)

## First-use regression on iPhone

1. On a fresh install, deny location access, choose a city offline and confirm
   it. Turn Prayer alerts off, Skip and relaunch. Use **Finish setting up Miqat**
   on Home. Confirm the location and alert choice persisted and that Continue
   opens Your style without a notification prompt. Finish and check the timetable.
2. On a fresh install, leave Prayer alerts on, confirm a location and continue.
   Allow notifications and confirm Prayer alerts is enabled in Settings. Repeat
   with denial and confirm alerts are off and setup still reaches Your style.
3. On a fresh install, select **Set up later** before confirming a location.
   Finish Your style, then verify Home still offers location confirmation and
   no prayer alerts are scheduled. Verify the same flow with Arabic enabled.
4. In custom location, enter `٣٠٫٠٤٤٤` and `٣١٫٢٣٥٧` with `Africa/Cairo` while
   the phone uses `America/New_York`. Cancel and verify the old selection stays.
   Reopen, verify Apply is disabled for `30abc`, then apply the valid values.
   Confirm the location again and compare the offline Cairo timetable and civil
   date with Android at the same instant.
5. After denying location, verify the location editor reports the denial and
   offers app settings and manual city selection. Re-enable access and check
   that detection returns. Start a fix, immediately choose a manual city, and
   verify a late fix cannot replace it. On Home, a failed refresh should preserve
   the previously confirmed location. Reopen Qibla and verify it gets a new fix.
6. With a confirmed location after Isha, verify Home labels the next Fajr
   Tomorrow and keeps today's Fajr in the timetable. At local midnight verify
   the date and timetable advance. Open each prayer row to its alert editor.
   Sunrise, Hijri timetable rows and the appearance preview stay read-only.
7. On a fresh install, confirm a manual city without granting location.
   Enable Automatic location. Grant While Using, then decline Always. Confirm
   the saved city stays put and Settings reports background location is off.
   Grant Always from iOS Settings and verify automatic travel refresh begins.
   Repeat with Allow Once, turn off the Miqat toggle, and then grant Always
   in iOS Settings. Verify opening the app leaves the saved city in place.

## Alert permission regression on iPhone

With a confirmed location and an upcoming audible alert, disable notification
Sounds in iOS Settings and return to Miqat. Home and alert settings should offer
**Fix prayer alerts**. Enable Sounds again and verify the action disappears.
Repeat with all scheduled alerts silent and confirm that disabled Sounds alone
does not show the action. Disable all notifications and verify recovery remains
available on Home and under Fasting when only fasting alerts are enabled.

Start Test notification or Test Adhan, leave its screen, then reopen it before
delivery. Verify the same countdown is visible and Cancel prevents delivery.
Repeat with Reset phone prayer settings while a test is being registered or
counting down. Verify no test alert or playback appears after reset, then start
a new test and check that it can still play.

With a paired TV and a named custom Adhan selected, reset phone prayer
settings. Verify setup reopens, alerts are off, the TV is still paired and the
custom recording remains playable and selectable. With a development fixture
that fails the preferences write, check that reset leaves the current selection,
recording, alerts and TV pairing in place.

## Prayer timing editor regression on iPhone

With a confirmed location, type `−٥` in the sunrise and Fajr adjustment fields
and verify each preview moves back five minutes. Increment Fajr in the general
offset screen and reopen its alert editor to verify both show the saved value.
Paste `31` over a −30 to 30 field and verify the last valid value stays saved.
Set Before and After to zero and verify both previews disappear. Select Arabic
while iOS uses English and verify the weekday names and digits follow Miqat's
preferences. Repeat before confirming a location and check time previews stay
unavailable until confirmation.

Set Miqat to 24-hour time and Eastern digits on a phone using 12-hour time.
Edit both quiet-hour boundaries and the day-before fasting time. Save midnight
and 23:59, then relaunch and check the saved values. Switch Miqat to 12-hour
time and Western digits. Check midnight and noon show distinct periods, then
change the values and cancel. Repeat in Arabic with VoiceOver.

## Radio reconnect regression on iPhone

With a controllable HTTPS stream in a local development fixture, end the
stream normally and then fail it mid-playback. In each case check that Miqat
waits 1.5, 3, 4.5 and 6 seconds before successive reconnect attempts and shows
Station unavailable after four failed retries. Recover the
stream, keep it playing for at least 30 seconds, and interrupt it again to
check that the full retry budget is available. Pause, stop, switch stations or
start Adhan during a retry delay and verify the old stream stays closed.
Repeat an interruption while locked and verify the system playback controls.

## Adhan playback presentation on iPhone

Play a scheduled Adhan with full-screen presentation off. Verify that Home's
shortcut shows the prayer and scheduled time, VoiceOver reads its playback
state, and opening it shows the recording name. Turn on Reduce Motion in
Miqat or iOS and verify the symbol stays still. Turn off artwork and verify
the controls show a plain background. Check Back confirmation, Pause and
swipe or tap to Stop. Repeat at the largest in-app text size and in Arabic.
With a custom recording selected, use a local fixture with missing or invalid
audio. Check that scheduled playback starts the built-in voice and identifies
it in Now Playing. Separately verify that a decoder failure during playback
closes the stopped session and cannot close a newer one.

## Qibla fallback on iPhone

With a simulated Cairo fix and no heading data, verify that Qibla shows a
bearing near 136.1° from true north without an arrow. Open Map and verify the
same bearing, route, pan, zoom and recenter. At the Kaaba or its antipode,
verify that Miqat reports no reliable direction. On a physical iPhone, verify
the arrow appears with a reliable heading and disappears when it is unreliable.

## iPhone pairing camera regression

On a fresh iPhone install, choose Scan TV QR code, deny Camera access and
verify the pairing page offers Settings and still accepts a pasted code.
Grant access in Settings, return and scan a TV code. Dismiss the scanner,
then reopen it and background the app while scanning. Verify the camera
closes and a late scan cannot submit a pairing request. Repeat in Arabic.

## Platform differences to verify

- Apple local notification sounds must be under 30 seconds. The short tone is
  available for scheduled delivery. Opening a recent Adhan notification can
  start full playback once the app is active.
  iOS owns lock-screen delivery, system volume, mute and haptics. The Android
  alarm stream, notification-channel vibration, hardware volume-key Stop and
  lock-screen full-screen start remain deferred. Background prayer
  notifications cannot interrupt a running radio session to start full Adhan.
  iOS controls which Now Playing actions appear on each system surface.
- The app queues the earliest 60 future notifications across an eight-day
  horizon and refreshes them on app entry or a supported location/time change.
  iOS may also run an offline background refresh. The system chooses when to
  run it, so the queue can still run out after long periods away.
- Significant-change location updates replace Android's periodic location job.
  WidgetKit owns refresh timing and the available widget sizes. Android corner
  shapes and Android's pin flow remain gaps. iOS rendering modes can also
  affect the selected palette and background visibility.
  The Circle ring uses half-hour timeline entries and prayer boundaries.
- Text sizing uses the closest native Dynamic Type category for the selected
  percentage and keeps the system's accessibility size as a minimum.
- Android's installer probes port 5555 across the subnet and scans ports on an
  entered TV. iPhone uses the three advertised Bonjour services and manual
  private IPv4 and port entry. CVTE setup uses the desktop installer on both.
- Qibla uses Apple Maps on iPhone and OpenStreetMap on Android. The iPhone
  update action depends on an iOS distribution channel.
- App Store Connect needs a newer SDK than the development workflow uses.
  Review the linked dependency privacy declarations and the archive report
  before publication. [Apple requirements](https://developer.apple.com/news/upcoming-requirements/)

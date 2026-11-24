# Miqat iPhone parity queue

Branch `feature/ios-app-port`, forked at `e0c46d3`. Baseline is Miqat Phone
0.5.23 (39), stable v0.7.7. Use the [phone guide](PHONE.md), [phone controls](../shared/data/phone-ui-contract.json),
[Android instrumentation](../companion/android/src/androidTest/java/tv/miqat/companion)
and [LAN contract](COMPANION-LAN.md) as acceptance inputs.

Work in order. Record finished code and validation here before starting the next
row. Leave an unfinished row open and record its next concrete action. Compare
new Android releases to this pinned baseline before expanding the queue.

| Order | User journey | Status and remaining acceptance |
| --- | --- | --- |
| 1 | Apple application, Kotlin framework, canonical bundles and unsigned build | App, widget and native libraries target arm64. The Xcode 16.3 workflow checks the bundle and archives it for simulator acceptance. Windows verified workflow parsing, Kotlin task registration, resource staging and the generated icon. Apple build and launch pending. |
| 2 | Welcome, skip/resume, permission and confirmation, offline country/city search, fresh fix, manual coordinates and timezone, background location consent/travel refresh | Home resumes an unfinished setup. Location confirmation and continuation are saved independently, and continuing enables prayer alerts only with user and system consent. Location mode changes retire the old request. Run the first-use regression below and check Always permission, significant-change delivery and manual edits during travel refresh. |
| 3 | Prayer Home, greeting and exact verse, Hijri/Gregorian date, next prayer, countdown seconds and six times, next-Fajr rollover, calculated city, method/school/high-latitude rule and offsets | Coded. 570 minute-for-minute Android comparisons passed across five cities, all presets, both Asr schools and three high-latitude rules. Offset and polar fixtures passed. Native UI pending. |
| 4 | Hijri month/day navigation, swipes and timetable, Hijri adjustment, current/upcoming and all recurring events | Month browsing keeps the prayer day selected, events can select their day, and civil navigation is limited to 366 days in either direction. The Apple fixture covers Umm al-Qura month/year boundaries and DST with an offset. Verify the fixture and both Arabic and English UI journeys on Apple. |
| 5 | English/Arabic and RTL, digits, 12/24-hour clocks, type scale, palettes, backgrounds, full-page previews, Home artwork and reduce motion | Setup and settings share visual palette and scene choices, and the full-page preview is read-only. Prayer Home and Adhan use the selected artwork and palette. Verify both locales, artwork visibility, reduced motion and scale extremes on iOS. |
| 6 | Prayer alerts, enablement, mode, timing previews, weekday inclusion/overrides, quiet hours, before/after, explicit tests, permission and recovery | The planner keeps matching absolute-time requests and can replenish the queue in offline Background App Refresh. Weekday overrides follow the prayer's local date and quiet hours follow the reminder's delivery time, including across midnight. Run the Apple notification fixture, permission regression, timezone-change, delivery and background expiration checks. |
| 7 | Fasting eve, Suhoor and Iftar, choices, exclusions and overlapping selections | Coded using Kotlin fasting rules. Check day edges and independent delivery on iOS. |
| 8 | Approved voices including Fajr, named audio library/import and CRUD, previews, per-prayer selection, level/length and playback controls, foreground Adhan screen | Packaged 13 checksum-verified voices. Named imports share Android's validation and preserve an unreadable library index. Deleting a recording restores affected voice selections. Active Adhan publishes progress to Now Playing and supports swipe or tap to stop. Verify Files import/cancellation, duplicate naming, deletion across slots, reset during import, system controls and headphone removal. |
| 9 | Quran Radio station picker, play/pause/stop, reconnect, metadata, lock-screen playback and Adhan interruption | Uses canonical HTTPS stations. Stream completion and terminal failures can reconnect up to four times. Thirty seconds of stable playback restores the budget. Verify the radio regression below, including audio ownership during Adhan and headphone removal. |
| 10 | Independent Qibla fix, true-north compass, numeric fallback, explicit map with route/pan/zoom/recenter | Coded with Core Location and an explicit Apple MapKit view. Test simulator fallback and a physical iPhone heading. |
| 11 | Home widgets, adaptive/strip/daily/circle presentations, settings per instance, date options, countdown and ring | Confirmed location gates the timetable. The app stages prayer transitions from Kotlin and can replenish an installed widget during offline background refresh. Each placement retains appearance, date and 120% text choices. Verify signed App Group transfer, medium timetable fit, timer locale and rollover, and per-instance choices. |
| 12 | About, feedback, update action, independent local storage and prayer reset | About, GitHub feedback, protected prayer settings and reset coded. iOS update action remains open until there is a stable iOS distribution channel. |
| 13 | Your TVs, demo, QR pairing/approval, pinned identity, independent credentials, settings/conflicts, local rename, Home/inputs/radio/Adhan/forget | Edits retain their original snapshot and submitted values across navigation. An uncertain save requires refresh before explicit retry, and readback can confirm an edit already applied. Remote validates offers and keeps Stop available after uncertainty. Verify native demo, TLS, Keychain, rename, readback, cancellation and conflict behavior. |
| 14 | Android/Google TV and LG setup, channels, local APK/IPK, download/transfer progress, verification, install/recovery, separate credentials | Public/local packages, LG SSH, Android classic and wireless setup, recovery/export, optional grants and personalisation are wired. Wireless setup checks the paired TV GUID and asks for endpoint confirmation. Cleanup reads the TV's history, offers undo for apps still off and suggests previously cleaned apps that are back on. Native C and portable Kotlin checks passed. Apple/TV validation remains open. |
| 15 | End-to-end parity audit and local handoff, focused checks, support docs and changelog | Compared the Android phone guide and setup sources against the iPhone feature paths. The remaining acceptance starts with the unsigned Apple build, then the first-run and offline prayer journey. Carry forward the platform differences and deferred features below. |

## Build and evidence

- Portable Kotlin companion JVM tests passed, including future reminder batches,
  equal-time candidates and expiry handling. Canonical phone generator tests
  passed. See [evidence](EVIDENCE.md#iphone-port-development-checkpoint).
- Use native iOS UI and system integrations backed by the existing Kotlin rules
  and canonical data. Keep Android and TV clients independent of iOS.
- Local host is Windows. It can run portable Gradle and resource checks.
  Apple framework linkage, SwiftUI and device behavior require macOS/Xcode.
- GitHub CLI authentication returned HTTP 401. Git's credential manager passed
  a non-interactive dry run. Automatic approval review blocked the actual push
  pending explicit authorization to send the branch to the GitHub remote.

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

## Alert permission regression on iPhone

With a confirmed location and an upcoming audible alert, disable notification
Sounds in iOS Settings and return to Miqat. Home and alert settings should offer
**Fix prayer alerts**. Enable Sounds again and verify the action disappears.
Repeat with all scheduled alerts silent and confirm that disabled Sounds alone
does not show the action. Disable all notifications and verify recovery remains
available on Home and under Fasting when only fasting alerts are enabled.

## Prayer timing editor regression on iPhone

With a confirmed location, type `−٥` in the sunrise and Fajr adjustment fields
and verify each preview moves back five minutes. Increment Fajr in the general
offset screen and reopen its alert editor to verify both show the saved value.
Paste `31` over a −30 to 30 field and verify the last valid value stays saved.
Set Before and After to zero and verify both previews disappear. Select Arabic
while iOS uses English and verify the weekday names and digits follow Miqat's
preferences. Repeat before confirming a location and check time previews stay
unavailable until confirmation.

## Radio reconnect regression on iPhone

With a controllable HTTPS stream in a local development fixture, end the
stream normally and then fail it mid-playback. In each case check that Miqat
reconnects and shows Station unavailable after four failed retries. Recover the
stream, keep it playing for at least 30 seconds, and interrupt it again to
check that the full retry budget is available. Pause, switch stations or start
Adhan during a failure and verify a late callback cannot restart the old player.
Repeat an interruption while locked and verify the system playback controls.

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

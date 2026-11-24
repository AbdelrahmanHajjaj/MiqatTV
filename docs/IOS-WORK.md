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
| 1 | Apple application, Kotlin framework, canonical bundles and unsigned build | The Xcode 16.3 workflow checks independent arm64 Simulator and iPhone builds and archives the Simulator app. Windows verified workflow parsing, Kotlin task registration, resource staging and the generated icon. Apple builds and launch pending. |
| 2 | Welcome, skip/resume, permission and confirmation, offline country/city search, fresh fix, manual coordinates and timezone, background location consent/travel refresh | Home resumes an unfinished setup. Location confirmation and continuation are saved independently, and continuing enables prayer alerts only with user and system consent. Location mode changes retire the old request. Run the first-use regression below and check Always permission, significant-change delivery and manual edits during travel refresh. |
| 3 | Prayer Home, greeting and exact verse, Hijri/Gregorian date, next prayer, countdown seconds and six times, next-Fajr rollover, calculated city, method/school/high-latitude rule and offsets | 570 minute-for-minute Android comparisons passed. After Isha, the countdown labels tomorrow's prayer and today's timetable stays intact. Home opens alert settings from the five prayer rows. Native rollover, offsets, polar display and row navigation remain open. |
| 4 | Hijri month/day navigation, swipes and timetable, Hijri adjustment, current/upcoming and all recurring events | Home shows today's or the nearest upcoming observance and opens the calendar on its date. Month browsing keeps the prayer day selected and navigation stays within 366 days. Run the Apple fixture and verify Home links, calendar swipes and labels in Arabic and English. |
| 5 | English/Arabic and RTL, digits, 12/24-hour clocks, type scale, palettes, backgrounds, full-page previews, Home artwork and reduce motion | Setup and settings share visual palette and scene choices, and the full-page preview is read-only. Prayer Home and Adhan use the selected artwork and palette. Verify both locales, artwork visibility, reduced motion and scale extremes on iOS. |
| 6 | Prayer alerts, enablement, mode, timing previews, weekday inclusion/overrides, quiet hours, before/after, explicit tests, permission and recovery | The planner keeps matching absolute-time requests and can replenish the queue in offline Background App Refresh. Weekday overrides follow the prayer's local date and quiet hours follow the reminder's delivery time, including across midnight. Run the Apple notification fixture, permission regression, timezone-change, delivery and background expiration checks. |
| 7 | Fasting eve, Suhoor and Iftar, choices, exclusions and overlapping selections | Uses Kotlin fasting rules. The iOS calendar adapter restores each civil day's true start and preserves the eve reminder's minute through a skipped hour. The Apple fixture covers spring, fall, half-hour and skipped-day transitions. Check independent delivery on iOS. |
| 8 | Approved voices including Fajr, named audio library/import and CRUD, previews, per-prayer selection, level/length and playback controls, foreground Adhan screen | Packaged 13 checksum-verified voices. Named imports share Android's validation. Active Adhan shows the scheduled time and recording on its controls and publishes progress to Now Playing. Home opens the controls from a full-width shortcut. Verify Files lifecycle, playback controls, motion, system controls and headphone removal. |
| 9 | Quran Radio station picker, play/pause/stop, reconnect, metadata, lock-screen playback and Adhan interruption | Uses canonical HTTPS stations. Stream completion and terminal failures can reconnect up to four times. Thirty seconds of stable playback restores the budget. Verify the radio regression below, including audio ownership during Adhan and headphone removal. |
| 10 | Independent Qibla fix, true-north compass, numeric fallback, explicit map with route/pan/zoom/recenter | Compass and map share the displayed bearing. The phone-relative arrow waits for reliable true north. Run the Qibla regression below and check sighted and VoiceOver operation on a physical iPhone. |
| 11 | Home widgets, adaptive/strip/daily/circle presentations, settings per instance, date options, countdown and ring | The app stages prayer transitions from Kotlin and can replenish an installed widget during offline background refresh. Expired data offers an app refresh, with no Circle countdown updates until there is a target. Verify the Apple fixture, signed App Group transfer, timer and date rollover, medium timetable fit and per-instance choices. |
| 12 | About, feedback, update action, independent local storage and prayer reset | About, GitHub feedback, protected prayer settings and reset coded. iOS update action remains open until there is a stable iOS distribution channel. |
| 13 | Your TVs, demo, QR pairing/approval, pinned identity, independent credentials, settings/conflicts, local rename, Home/inputs/radio/Adhan/forget | Pairing sends a user-selected phone name and retains the local TV nickname when a saved credential reconnects. Uncertain settings writes require refresh and explicit retry. Verify native demo, TLS, Keychain, name and QR approval, readback, cancellation and conflict behavior. |
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
6. With a confirmed location after Isha, verify Home labels the next Fajr
   Tomorrow and keeps today's Fajr in the timetable. At local midnight verify
   the date and timetable advance. Open each prayer row to its alert editor.
   Sunrise, Hijri timetable rows and the appearance preview stay read-only.

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

## Adhan playback presentation on iPhone

Play a scheduled Adhan with full-screen presentation off. Verify that Home's
shortcut shows the prayer and scheduled time, VoiceOver reads its playback
state, and opening it shows the recording name. Turn on Reduce Motion in
Miqat or iOS and verify the symbol stays still. Turn off artwork and verify
the controls show a plain background. Check Back confirmation, Pause and
swipe or tap to Stop. Repeat at the largest in-app text size and in Arabic.

## Qibla fallback on iPhone

With a simulated Cairo fix and no heading data, verify that Qibla shows a
bearing near 136.1° from true north without an arrow. Open Map and verify the
same bearing, route, pan, zoom and recenter. At the Kaaba or its antipode,
verify that Miqat reports no reliable direction. On a physical iPhone, verify
the arrow appears with a reliable heading and disappears when it is unreliable.

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

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
| 1 | Apple application, Kotlin framework, canonical bundles and unsigned build | Xcode target, framework and Apple build workflow added. Windows verified Kotlin task registration and resource staging. Apple build and launch pending. |
| 2 | Welcome, skip/resume, permission and confirmation, offline country/city search, fresh fix, manual coordinates and timezone, background location consent/travel refresh | Coded. Native checks include fresh/denied, cancelled fix, Always permission, significant-change delivery, and manual changes during travel refresh. |
| 3 | Prayer Home, greeting and exact verse, Hijri/Gregorian date, next prayer, countdown seconds and six times, tomorrow's Fajr, calculated city, method/school/high-latitude rule and offsets | Coded. 570 minute-for-minute Android comparisons passed across five cities, all presets, both Asr schools and three high-latitude rules. Offset and polar fixtures passed. Native UI pending. |
| 4 | Hijri month/day navigation, swipes and timetable, Hijri adjustment, current/upcoming and all recurring events | Coded using the canonical event catalog and Kotlin event rules. Verify Umm al-Qura month boundaries against Android. |
| 5 | English/Arabic and RTL, digits, 12/24-hour clocks, type scale, palettes, backgrounds, full-page previews, Home artwork and reduce motion | Coded. Verify theme fidelity, Arabic/English UI and the scale edges on iOS. |
| 6 | Prayer alerts, enablement, mode, timing previews, weekday inclusion/overrides, quiet hours, before/after, explicit tests, permission and recovery | Local notification planner, settings and short-tone test coded. Validate permission, replacement and day edges. Full Adhan playback and platform limits belong to row 8. |
| 7 | Fasting eve, Suhoor and Iftar, choices, exclusions and overlapping selections | Coded using Kotlin fasting rules. Check day edges and independent delivery on iOS. |
| 8 | Approved voices including Fajr, named audio library/import and CRUD, previews, per-prayer selection, level/length and playback controls, foreground Adhan screen | Coded. Packaged 13 checksum-verified voices. Native playback, interruptions, imported audio and reset checks pending. See delivery differences below. |
| 9 | Quran Radio station picker, play/pause/stop, reconnect, metadata, lock-screen playback and Adhan interruption | Coded with the canonical HTTPS stations. Device streaming, audio interruption and remote transport controls pending. |
| 10 | Independent Qibla fix, true-north compass, numeric fallback, explicit map with route/pan/zoom/recenter | Coded with Core Location and an explicit Apple MapKit view. Test simulator fallback and a physical iPhone heading. |
| 11 | Home widgets, adaptive/strip/daily/circle presentations, settings per instance, date options, countdown and ring | WidgetKit target and configurable timelines coded. Each placement can follow the phone theme and background, or select a canonical palette, scene and 0–100% background opacity. Verify signed App Group transfer, each widget family, timer rollover and per-instance choices. |
| 12 | About, feedback, update action, independent local storage and prayer reset | About, GitHub feedback, protected prayer settings and reset coded. iOS update action remains open until there is a stable iOS distribution channel. |
| 13 | Your TVs, demo, QR pairing/approval, pinned identity, independent credentials, settings/conflicts, Home/inputs/radio/Adhan/forget | Added Android TV custom palette and app redirect editing. Both editors use the original TV snapshot. iPhone demos use the shared Kotlin settings endpoint and simulate failures, reconnect, inputs and media locally. Android and LG previews show representative TV layout, palette, scenes and clock. Native rendering, demo, TLS, Keychain, cancellation and conflict fixtures remain pending. |
| 14 | Android/Google TV and LG setup, channels, local APK/IPK, download/transfer progress, verification, install/recovery, separate credentials | Public Production/Staging selection, cancellable download, size and SHA-256 verification and file sharing coded. Local APK and IPK identity/version checks coded. Downloaded IPKs use the same LG metadata check. Foreground Bonjour discovery for the three Android TV debugging services coded. Test permission, expiry and service removal on iOS. APK signature and downgrade checks, a manual endpoint for TVs without advertisements, authenticated ADB/SSH, install/transfer/recovery and separate credentials remain open. |
| 15 | End-to-end parity audit and local handoff, focused checks, support docs and changelog | Host checks passed. First pending action is the unsigned Apple build. Validate the source already present before filling the remaining parity gaps. |

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

## Platform differences to verify

- Apple local notification sounds must be under 30 seconds. The short tone is
  available for scheduled delivery. Full playback starts in the active app.
  iOS owns lock-screen delivery, system volume, mute and haptics. The Android
  alarm stream, notification-channel vibration, hardware volume-key Stop and
  lock-screen full-screen start remain deferred. Background prayer
  notifications cannot interrupt a running radio session to start full Adhan.
- The app queues the earliest 60 future notifications across an eight-day
  horizon and refreshes them on app entry or a supported location/time change.
  Verify rescheduling after long periods away and permission changes.
- Significant-change location updates replace Android's periodic location job.
  WidgetKit owns refresh timing and the available widget sizes. Android corner
  shapes and Android's pin flow remain gaps. iOS rendering modes can also
  affect the selected palette and background visibility.

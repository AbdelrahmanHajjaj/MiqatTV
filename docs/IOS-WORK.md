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
| 2 | Welcome, skip/resume, permission and confirmation, offline country/city search, fresh fix, manual coordinates and timezone, background location consent/travel refresh | Foreground setup, confirmation and manual selection coded. Background opt-in and travel refresh remain. Compare fresh/denied and cancelled-fix cases on iOS. |
| 3 | Prayer Home, greeting and exact verse, Hijri/Gregorian date, next prayer, countdown seconds and six times, tomorrow's Fajr, calculated city, method/school/high-latitude rule and offsets | Coded. Frozen Android Cairo vector, all-method offset application and polar unavailability passed. More cross-method/timezone fixtures and real UI pending. |
| 4 | Hijri month/day navigation, swipes and timetable, Hijri adjustment, current/upcoming and all recurring events | Coded using the canonical event catalog and Kotlin event rules. Verify Umm al-Qura month boundaries against Android. |
| 5 | English/Arabic and RTL, digits, 12/24-hour clocks, type scale, palettes, backgrounds, full-page previews, Home artwork and reduce motion | Coded. Verify theme fidelity, Arabic/English UI and the scale edges on iOS. |
| 6 | Prayer alerts, enablement, mode, timing previews, weekday inclusion/overrides, quiet hours, before/after, explicit tests, permission and recovery | Local notification planner, settings and short-tone test coded. Validate permission, replacement and day edges. Full Adhan playback and platform limits belong to row 8. |
| 7 | Fasting eve, Suhoor and Iftar, choices, exclusions and overlapping selections | Coded using Kotlin fasting rules. Check day edges and independent delivery on iOS. |
| 8 | Approved voices including Fajr, named audio library/import and CRUD, previews, per-prayer selection, level/length and playback controls, foreground Adhan screen | Queued. Check approved media, imports, interruptions, cleanup and route behavior. Background full Adhan and Android-specific controls need platform review. |
| 9 | Quran Radio station picker, play/pause/stop, reconnect, metadata, lock-screen playback and Adhan interruption | Queued. Check explicit network requests, interruptions and failure retry. |
| 10 | Independent Qibla fix, true-north compass, numeric fallback, explicit map with route/pan/zoom/recenter | Queued. Check device permissions/sensor behavior and map attribution. |
| 11 | Home widgets, adaptive/strip/daily/circle presentations, settings per instance, date options, countdown and ring | Queued. Match user outcomes within Apple widget sizes and refresh limits. |
| 12 | About, feedback, update action, independent local storage and prayer reset | Queued. Use the iOS distribution channel for updates once defined. Verify settings and imported audio reset independently of TVs. |
| 13 | Your TVs, demo, QR pairing/approval, pinned identity, independent credentials, settings/conflicts, Home/inputs/radio/Adhan/forget | Queued. Exercise mock fixtures first. Physical TV testing needs separate authorization. |
| 14 | Android/Google TV and LG setup, channels, local APK/IPK, download/transfer progress, verification, install/recovery, separate credentials | Queued. Review available iOS transports before choosing equivalents. Physical deployment needs separate authorization. |
| 15 | End-to-end parity audit and local handoff, focused checks, support docs and changelog | Queued. Report every deferred platform behavior and unavailable acceptance check. |

## Build and evidence

- Portable Kotlin companion JVM tests passed, including future reminder batches,
  equal-time candidates and expiry handling. Canonical phone generator tests
  passed. See [evidence](EVIDENCE.md#iphone-port-development-checkpoint).
- Use native iOS UI and system integrations backed by the existing Kotlin rules
  and canonical data. Keep Android and TV clients independent of iOS.
- Local host is Windows. It can run portable Gradle and resource checks.
  Apple framework linkage, SwiftUI and device behavior require macOS/Xcode.
- GitHub CLI authentication returned HTTP 401. Git's credential manager passed
  a non-interactive dry run for the feature branch.

## Platform differences to verify

- Apple local notification sounds must be under 30 seconds. The short tone is
  available for scheduled delivery. Full recording playback requires an active
  app session. Verify the iPhone behavior before closing the audio row.
- Background location and WidgetKit have OS-controlled delivery. Verify those
  journeys on an iPhone separately from the app build.

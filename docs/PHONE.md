# Miqat on Android phones

Miqat calculates prayer times offline and schedules this phone's prayer alerts.
It also installs and controls supported TVs. A TV, account or network connection
is not needed for phone prayer times. Minimum Android version: 8 (API 26).

## First use

A fresh install opens a three-step guide: location/calculation, alert access,
and personalization with a real preview. Progress survives reopening. **Set up
later** opens Prayer with a visible **Complete Miqat setup** entry; the guide
can be resumed without a TV. Completing or revisiting it preserves your settings.

1. Tap **Use my location** to request a one-shot foreground fix, then review and
   confirm it. Approximate Android location is supported. You can choose a city
   instead; manual latitude/longitude is under **Advanced**. No background location
   permission or continuous tracking is used.
2. Select the prayer timezone. **Use phone timezone** follows Android, including
   changes while travelling; choose an explicit IANA timezone to keep a fixed
   location. City selection supplies its coordinates, method and Asr school;
   verify the timezone yourself before confirming the location.
3. Review calculation method, Asr school, high-latitude rule and adjustments.
   Confirm the location and selected timezone. Times use the same pinned offline
   astronomy source and whole-minute flooring as the TV app.
4. In the guide's alerts step, choose whether to enable prayer alerts. You can
   also use **Phone settings → Adhan & reminders** later. **Prayer alert
   permissions** opens Android notification and Alarms & reminders controls.
   Miqat reports missing access; it does not enable it silently.
5. Choose a phone theme, background, digits and clock format in personalization.
   Theme/background sheets show a real prayer-card preview and use Apply/Cancel.

Alerts default to off. Changing the city, coordinates or timezone requires location
confirmation again. Review the method and local authority for your location;
the selected high-latitude rule may still return unavailable polar times.

## Prayer and sound controls

Tap a prayer to select off, notification, adhan, vibration only or silent notification,
its voice, minute adjustment,
advance/after reminder and vibration with adhan. Sunrise remains informational.
Choose **Use default level and duration** or set them independently for that prayer.
Fresh installs use the dedicated Fajr recording for Fajr; other prayers inherit
the default voice. Saved voice choices survive updates. The phone has independent
settings; changing them never writes to a TV.

**Alert days** selects weekdays for each prayer. Advance/after reminders follow
that prayer’s date. **Quiet hours** use the selected prayer timezone, including
periods crossing midnight; equal start/end covers the full day. Choose no alert,
vibration or silent notification during quiet hours. Miqat looks a week ahead
when choosing the next alarm. Android notification-channel settings and Do Not
Disturb remain under your control.

Voice sheets keep a draft selection: tap **Listen · 12s** to audition it, Stop
or Cancel to end the preview, and Apply to save. Changing the draft does not
change an upcoming adhan. Select **Custom audio from this phone**, then
**Import or replace audio** to choose that voice's recording. An existing private
recording can be reused without importing it again. Miqat copies audio-only media into private local storage,
checks that Android can prepare it, and limits it to 20 MB / 10 minutes. Failed
imports preserve the previous selection. Original document access is not needed
after a successful copy. The app still ships only its 14 approved built-in
recordings; imported files are excluded from backup/transfer and removed by
**Reset phone prayer settings**. This reset keeps your paired TVs.

In **Location & calculation**, **Update location when opening Miqat** is opt-in.
It refreshes at most every 30 minutes while the prayer screens are open, preserves
your calculation method/adjustments and needs foreground location access. Failed
or cancelled refreshes keep the saved location and show a retry message. Device
timezone follows Android; an explicitly chosen fixed timezone stays fixed. No
continuous background tracking or automatic religious travel rulings are applied.

Settings rows show their saved values. Tap a number to edit inline with +/- and
Done/Cancel; choices open a focused sheet. Prayer / Your TVs / Phone settings stay
in the root navigation, with Remote and TV settings nested inside a selected TV. Voice previews last up to 12 seconds.
Stop is available on the Prayer screen and the playback notification. A scheduled
adhan continues when the app leaves the foreground; a preview stops. Audio-focus
loss stops playback and does not automatically restart a missed recording.

Adhan level is relative to Android's alarm volume. Miqat does not change system
volume or bypass Do Not Disturb. Zero duration means the complete selected clip (up to 10 minutes);
the service exists only while preparing/playing audio. Notification-only alerts
use Android notification channel sound settings. No idle foreground service runs.

Android may display the next prayer/reminder as an alarm clock. Alarms rebuild
after boot, clock/timezone changes, app updates and settings changes. Duplicate
claims persist; events two minutes late or older are skipped. Force-stopping the
app disables Android delivery until the app is opened again. Manufacturer battery
restrictions can affect delivery; physical-device reliability remains subject to
the owner's test. Reopen **Prayer alert permissions** if alerts are not ready; its battery/background
section opens Android app settings. Exact battery menus vary by manufacturer.

## Qibla and widgets

Prayer opens a Qibla compass for the confirmed location. Its numeric bearing is
clockwise from true north; a supported foreground orientation sensor provides
the arrow relative to the top of the phone. Keep the phone level and follow the
calibration/accuracy guidance away from magnets and metal. Without a suitable
sensor, use the numeric bearing with a separate compass. There is no background
location or sensor service, and an undefined bearing at the destination is
reported explicitly. Physical compass accuracy still needs device testing.

**Home-screen widgets** shows a real timetable preview and offers Android's
pin flow where the launcher supports it. Otherwise use the launcher's Widgets
menu. The widget follows location, calculation, language, digits, clock and theme;
Android controls its size and system text scale. It shows today's six times,
date and last update, rather than promising a continuously fresh countdown.
Settings/alarm/boot/time changes request updates, with a periodic host refresh
that may be delayed while idle. Tap the widget to open Prayer.

Quran, adhkar and other broader worship modules are visibly locked Coming soon
entries; tapping them explains that they are not available in this version.

## Appearance and TVs

Phone appearance includes real theme/background previews, shared palettes and
static artwork cropped for portrait, digits, 12/24-hour clock and 80–150% interface size, combined with Android text
scaling. The Hijri date uses Android's Umm al-Qura calendar plus the chosen day
adjustment. This is a display date, independent of prayer astronomy.

**Your TVs → Set up my TV** and **Pair a new TV** start the same platform
chooser. Android/Google TV and LG webOS lead to developer-access instructions,
then source selection: Latest by default, Stable or Preview. **Advanced** switches
to a local APK/IPK. Unsupported Samsung Tizen, Roku and Other TV entries show a
lock and Coming soon. Already installed Miqat can proceed to ordinary pairing.

Android then opens guarded ADB setup/recovery using your phone theme/language.
LG requests its Developer Mode connection and explicit SSH identity approval,
then downloads the selected public build; a local IPK is selected first in
Advanced. Downloaded bytes must match size/SHA-256, and downgrades are refused.
No public release is currently published; the app shows an unavailable state
rather than fetching private CI artifacts. Local packages remain usable.

Install permission and normal authenticated remote pairing are separate.
See [Android setup](ANDROID-SETUP.md) and [LG setup/limits](WEBOS.md).
LG requires Developer Mode and its session renewal; it does not support Android
debloat, system Home replacement or standby wake through this installer.

Phone prayer settings/claims stay in device-protected local storage so scheduled
alerts can recover before unlock. Pairing credentials remain in their separate
encrypted credential store. Neither store is included in Android backup/transfer.
**Reset phone prayer settings** leaves paired TVs intact. There is no cloud sync.

Android is the standalone deliverable. iOS still needs native UI, persistence,
astronomy/calendar and notification/audio adapters; portable Kotlin rules alone
do not establish an iPhone app or iOS background-audio behavior.

The [Apple-core CI check](https://github.com/AbdelrahmanHajjaj/MiqatTV/actions/workflows/apple-core.yml)
compiles iOS device/simulator rule libraries and runs native macOS rule tests.
It is a portability check, without launching an iOS simulator or building an app.

## Combined owner review

Use the fixed `state/builds/companion/Miqat-latest.apk` (pass-3 version 0.3.0 after export) on the Android
phone and the existing `state/builds/webos/Miqat-latest.ipk` (0.2.28) for LG.
The phone build includes both standalone prayers and TV setup/control.

- On a fresh install, complete or defer/resume the guided setup. Check theme and
  background previews before applying them.
- Open Prayer without a TV; detect/confirm location and timezone, inspect daily/tomorrow
  times, change each prayer and test numeric Done/Cancel plus voice preview/Stop.
- Try per-prayer level/duration, weekdays, quiet hours, silent/vibration and a
  private custom recording. Enable travel refresh only if you want foreground
  updates, and verify that your calculation calibration stays intact.
- Grant alert access deliberately. Check the next actual prayer with the screen
  off, Stop from its notification, and recovery after restarting the phone before
  another prayer. Preview verifies local playback, not background delivery.
- Check phone language, portrait/landscape, text size, digits, themes/backgrounds
  and that changes survive reopening. Phone changes must leave TV settings alone.
- Check Qibla calibration/heading on the physical phone and add/resize a home-screen
  widget. Quran/adhkar entries should remain locked with Coming soon messages.
- Use Your TVs to select a platform, read preparation, choose a release channel
  or Advanced local package, and continue to guarded install/ordinary pairing. Check
  retained TV settings, Remote, settings save/cancel, disconnect/reconnect and
  Developer Mode renewal on the actual TV. Physical picture/sound/remote/power
  acceptance remains an owner observation.
- Send one feedback list with screen/flow, expected result, actual result and app/
  OS version. Keep credentials, device addresses and sensitive captures private.

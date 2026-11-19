# Miqat on Android phones

Miqat calculates prayer times offline and schedules this phone's prayer alerts.
It also installs and controls supported TVs. A TV, account or network connection
is not needed for phone prayer times. Minimum Android version: 8 (API 26).

## First use

First use has three pages: Location, Prayer alerts and Appearance. Confirming
location advances directly; alert permissions stay on their page. Appearance
combines compact theme/background choices with one live prayer-card preview.

Every first-use step uses Miqat’s dedicated generated portrait signature artwork with a subtle
scrim and consistent brand controls. Theme/background choices remain live previews.

Guided actions use one fixed footer with Continue/Finish and optional Skip.
The top/system Back control returns to previous steps. Cancel appears for drafts
or operations; duplicate Next/Previous buttons are omitted.
Confirmation and Save/Cancel actions stay at the bottom, outside scrolling
content; dialogs anchor their actions at the bottom of the sheet.

A fresh install presents one decision at a time: location, prayer alerts,
notification access, precise-alarm access, theme and background. **Skip** advances
past an optional step. Back from the first step opens Prayer with a visible
**Complete Miqat setup** entry. Progress survives reopening; revisiting setup
preserves saved settings. Phone setup never requires a TV.

1. Use **Use my location**, or choose a city, then review/confirm the displayed
   timezone. Approximate foreground location is supported. No continuous or
   background location tracking is used. The city supplies its calculation
   method and Asr school. Manual coordinates and further calculation/timezone
   adjustments remain under **Phone settings → Location & calculation**.
2. Choose whether to enable prayer alerts. The next two screens request Android
   notifications and precise alarms separately; each can be skipped and repaired
   later in **Prayer alert permissions**. Miqat never grants access silently.
3. Swipe the theme and portrait background carousels. Select a preview and
   continue, or skip. Ordinary Appearance settings also offer draft previews
   with Apply/Cancel. Digits, clock format and interface size remain there.

Alerts default to off. Changing the city, coordinates or timezone requires location
confirmation again. Review the method and local authority for your location;
the selected high-latitude rule may still return unavailable polar times.

## Prayer and sound controls

Tap a prayer to select off, notification, adhan, vibration only or silent notification,
its voice, minute adjustment,
advance/after reminder and vibration with adhan. Sunrise remains informational.
Choose **Use default level and duration** or set them independently for that prayer.
Fresh installs use the dedicated Fajr recording for Fajr; other prayers inherit
the default voice. Saved voice choices survive updates, except the retired Al Afasy selection,
which changes to Al-Malki (regular) or the dedicated Chebae Fajr recording. The phone has independent
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
after a successful copy. There are 13 selectable approved built-in voices; the
retired recording is removed from distribution. Imported files
are excluded from backup/transfer and removed by
**Reset phone prayer settings**. This reset keeps your paired TVs.

In **Location & calculation**, **Update location when opening Miqat** is opt-in.
It refreshes at most every 30 minutes while the prayer screens are open, preserves
your calculation method/adjustments and needs foreground location access. Failed
or cancelled refreshes keep the saved location and show a retry message. Device
timezone follows Android; an explicitly chosen fixed timezone stays fixed. No
continuous background tracking or automatic religious travel rulings are applied.

Settings rows show their saved values. Tap a number to edit inline with +/- and
Done/Cancel; choices open a focused sheet. Prayer / Your TVs / Phone settings use
a floating main navigation bar. Detail and setup screens use Back and their own
actions, with Remote and TV settings inside a selected TV. Voice previews last up to 12 seconds.
Stop is available on the Prayer screen and the playback notification. A scheduled
adhan continues when the app leaves the foreground; a preview stops. Audio-focus
loss stops playback and does not automatically restart a missed recording.

Adhan level is relative to Android's alarm volume. Miqat does not change system
volume or bypass Do Not Disturb. Zero duration means the complete selected clip (up to 10 minutes);
the service exists only during an active adhan session, including a user pause. Notification-only alerts
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

Adhan continues while using other apps or with the screen off. Its notification
provides Play/Pause and Stop; pause keeps the recording position and remaining
configured duration. Dismissing it stops playback where Android allows active
service notifications to be dismissed. Some versions pin this notification;
Stop remains available there.

The playback notification shows the prayer name, its scheduled time in the selected
clock/digit format, and the voice actually playing. Its media artwork uses the
selected background, theme and canonical mihrab. Android controls the final
notification panel layout and colors.

**Full-screen adhan** uses that same selected scene and mihrab and is off by default. Enabling it allows Android's alarm
screen when locked; Android 14+ may require **Allow full-screen alarms**. Tapping
the notification opens that same session. Tapping the screen or switching apps
does not stop audio. Pull the bottom **Swipe up to stop** handle upward and
release past its threshold to stop and close. A tap, short pull, horizontal
pull or cancelled gesture keeps playing. TalkBack and keyboard activation offer
an explicit **Stop adhan and close** action. Back asks confirmation.
The screen closes when playback finishes. The optional volume-key shortcut
stops adhan while this screen is visible, including over the lock screen.
Android reserves the power key: locking or screen timeout does not stop audio.

Prayer Home keeps location in the next-prayer card and tomorrow Fajr directly
below the daily times. Qibla and widgets are separate actions; locked future
features follow farther down, reachable with a short scroll. Finishing first use
removes its Home resume prompt, including after reopening the app.

**Home-screen widgets** offers next-prayer, daily timetable and compact strip
layouts with live previews. Each has a launcher entry with a suitable default
size. Customize each placed widget independently: layout, app/fixed theme,
solid/transparent background, date and label size. Apply saves only that widget;
Back cancels. The launcher can reopen configuration. A resized timetable falls
back to next prayer when short and returns to the chosen layout when enlarged.

The prayer data follows phone location, calculation, language, digits and clock.
Widget appearance records use the same protected local boundary as phone prayers.
Use Android's pin flow where supported, or the launcher's Widgets menu. Settings,
alarm, boot and time changes request updates; the periodic host refresh may be
late while idle. These are timestamps/timetables, not a continuously fresh
countdown. Tap a widget to open Prayer.

Quran, adhkar and other broader worship modules are visibly locked Coming soon
entries; tapping them explains that they are not available in this version.

## Appearance and TVs

Phone appearance includes real theme/background previews, shared palettes and
static artwork composed for portrait with its full scene below an extended sky, digits, 12/24-hour clock and 80–150% interface size, combined with Android text
scaling. The Hijri date uses Android's Umm al-Qura calendar plus the chosen day
adjustment. This is a display date, independent of prayer astronomy.

**Your TVs → Set up my TV** and **Pair a new TV** start the same platform
chooser. Android/Google TV and LG webOS lead to developer-access instructions,
then source selection: Latest by default, Stable or Preview. **Advanced** switches
to a local APK/IPK. Unsupported Samsung Tizen, Roku and Other TV entries show a
lock and Coming soon. Already installed Miqat can proceed to ordinary pairing.

Android then opens guarded ADB setup/recovery using your phone theme/language.
LG asks for the TV address and passphrase on separate guided screens, with
short contextual instructions and explicit SSH identity approval,
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

Use the fixed `state/builds/companion/Miqat-latest.apk` (0.4.4, version code 14) on the Android
phone and the existing `state/builds/webos/Miqat-latest.ipk` (0.2.28) for LG.
The phone build includes both standalone prayers and TV setup/control.

- On a fresh install, complete or skip/resume the guided setup. Check theme and
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
- Check Qibla calibration/heading on the physical phone and add/resize each home-screen
  widget variant, including independent customization of two instances. Quran/adhkar entries should remain locked with Coming soon messages.
- Use Your TVs to select a platform, read preparation, choose a release channel
  or Advanced local package, and continue to guarded install/ordinary pairing. Check
  retained TV settings, Remote, settings save/cancel, disconnect/reconnect and
  Developer Mode renewal on the actual TV. Physical picture/sound/remote/power
  acceptance remains an owner observation.
- Send one feedback list with screen/flow, expected result, actual result and app/
  OS version. Keep credentials, device addresses and sensitive captures private.

The optional adhan screen keeps its swipe label in the central prayer area,
above the bottom mihrab line. Android controls media-notification alignment and
Play/Pause position; Miqat supplies localized prayer/time/voice and artwork.

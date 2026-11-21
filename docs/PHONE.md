# Miqat on Android phones

Miqat calculates prayer times offline and schedules this phone's prayer alerts.
It also installs and controls supported TVs. A TV, account or network connection
is not needed for phone prayer times. Minimum Android version: 8 (API 26).

## First use

First use opens with a centered welcome, then **Your location & alerts** and
**Your style**. Choose a country and city, or tap the compact location icon.
Detection fills the fields; the page has one location confirmation. Android's
own notification permission request follows on new setups when needed. Denial
continues setup without another prompt. Alarm timing access is not part of setup.
An existing voluntary alert opt-out remains off when repeating the guide.
Progress survives reopening; Explore first/Set up later leave a resumable entry.

Miqat Signature appears first in the background and color lists.
Style previews apply across the page, including background and control colors.
Only large visual catalogs scroll horizontally. Short choices stay visible;
long text choices such as high-latitude rules use a vertical selection stack.
Ordinary Appearance has an Apply/Cancel draft for theme, background and display
settings. Language and widgets are available directly in Phone settings.
Home uses a dimmed version of the selected scene. Turn **Background on Prayer
home** off in Appearance for plain surfaces. Existing landscape-only scenes are
composed for tall screens; the dedicated portrait Signature remains unchanged.

Approximate foreground location is supported; there is no background tracking.
Country/city selection is local. Detected coordinates remain authoritative;
a nearest-city label never replaces them. Manual coordinates and timezone are
in an Advanced dialog with Apply/Cancel. Changing location or timezone requires
confirmation again. Review the method against your local authority; high-latitude
rules can still return unavailable polar times.

## Prayer and sound controls

Tap a prayer to customize it. Its enable switch disables all of its settings
without losing them. Sound choices are **Adhan** (selected recording), **Short
tone**, and **Silent**. Vibration is independent, using a short double pulse;
Silent means no sound, while the vibration switch still applies. Sunrise is
informational. Notification permission, Android channel settings and Do Not
Disturb remain authoritative; per-prayer enabling cannot bypass them. The global
alert switch remains in permissions so an existing opt-out is recoverable.

Repeat weekdays are circular toggles on one row with full accessible names; the
row scrolls when enlarged sizing needs more space. Minute
adjustments and before/after reminders stay in their rows beside −/+. Numeric
edits retain Done/Cancel. Day overrides belong to one prayer and weekday and
change only sound/vibration. Enable a weekday override to expand its controls in
the same view; turn it off to restore prayer defaults. A disabled
prayer or excluded weekday stays off. Overrides use that prayer's local date,
including before/after reminders that cross midnight.

Choose the default adhan level/length or override them for a prayer. Length is
5–100% of the selected recording. **Preview ending** plays the last five seconds
before its effective cutoff; moving the slider never starts sound. Existing
seconds caps survive upgrades and remain visible until the matching percentage
is edited. Fajr has a dedicated default recording; other prayers inherit the
normal voice. Old retired Al Afasy selections migrate to the approved Al-Malki
or Chebae Fajr recording. Phone settings never write to a TV.

An existing quiet-hour rule is retained and labeled in Prayer alerts. It keeps
its original event-time boundaries, including overnight and equal start/end
(full day), and takes precedence over day overrides. Review or turn it off
explicitly; Miqat does not silently convert a time window into whole weekdays.

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
Done/Cancel; small choice sets stay visible and large lists open a searchable sheet. Prayer / Your TVs / Phone settings use
a floating main navigation bar. Detail and setup screens use Back and their own
actions, with Remote and TV settings inside a selected TV. Voice previews last up to 12 seconds.
Stop is available on the Prayer screen and the playback notification. A scheduled
adhan continues when the app leaves the foreground; a preview stops. Audio-focus
loss stops playback and does not automatically restart a missed recording.

Adhan level is relative to Android's alarm volume. Miqat does not change system
volume or bypass Do Not Disturb. The length slider applies a percentage to the selected clip (up to 10 minutes);
the service exists only during an active adhan session, including a user pause. Short-tone alerts use a brief bundled tone on fresh notification channels;
existing user channel sound choices remain under Android control. No idle foreground service runs.

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

Prayer Home shows location above its next-prayer card. Tap the date to open the
Hijri month calendar and browse dates. The selected day's timetable appears
beneath the month grid and updates immediately; Done closes it.
Browsing never changes scheduled alerts. There is no duplicate tomorrow
Fajr row. Qibla and widgets are separate actions; locked future
features follow farther down, reachable with a short scroll. Finishing first use
removes its Home resume prompt, including after reopening the app.

**Home-screen widgets** offers next-prayer, daily timetable and compact strip
presentations with one live preview and visible initial-presentation/shape choices
and visual Theme/Background catalogs. A placed widget adapts its content to the
allocated width and height: next prayer when narrow, a timetable strip when wide
and short, and a daily list when tall enough. Android 12+ supports exact launcher
size variants; older launchers use their resize bounds. Small sizes prioritize
the prayer name/time over date/footer details. The next-prayer card uses the
allocated height for its serif prayer name and time, without an inset outline. Remaining time is a snapshot at
the existing widget refresh, rather than a separate background ticker.
Each has a launcher entry with a suitable default
size. Customize each placed widget independently: initial presentation, app/fixed theme,
color/app/selected background, 0–100% background opacity, rounded/soft/square
corners, Hijri date by default and optional Gregorian date, and label size. Text uses the app’s
Alexandria fonts and canonical theme colors. Android’s picker has populated native
previews with bilingual image fallbacks for older launchers. Apply saves only that widget;
Back cancels. The launcher can reopen configuration. Resizing keeps saved style
and date choices while adapting the amount of prayer detail that fits.

The prayer data follows phone location, calculation, language, digits and clock.
Compact widgets prioritize the enabled Hijri date when both dates cannot fit.
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
to a local APK/IPK. Unsupported Samsung Tizen, Roku and Other TV entries are omitted. These are
implemented platform families, not a certification of every TV model. Already installed Miqat can proceed to ordinary pairing.

Android then opens guarded ADB setup/recovery using your phone theme/language.
LG shows its numbered preparation instructions, release choice, TV address and
passphrase on one page, with explicit SSH identity approval,
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

Use the fixed `state/builds/companion/Miqat-latest.apk` (0.5.2, version code 18) on the Android
phone and the existing `state/builds/webos/Miqat-latest.ipk` (0.2.28) for LG.
The phone build includes both standalone prayers and TV setup/control.

- On a fresh install, complete or skip/resume the guided setup. Check theme and
  background previews before applying them.
- Open Prayer without a TV; detect/confirm location and timezone, browse dates in the Hijri calendar and inspect their calculated
  times, change each prayer and test numeric Done/Cancel plus voice preview/Stop.
- Try per-prayer level/length, weekday overrides, preserved quiet rules, sound/vibration and a
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

## Timing and Qibla map limits

Notification denial prevents scheduled prayer output. With precise alarm access,
Miqat uses Android alarm-clock scheduling. Without it, Miqat uses approximate
idle-aware alarms; Android can delay them, and events more than two minutes late
are rejected rather than replayed unexpectedly. Background restrictions may
prevent full Adhan and leave a notification fallback. Review timing access and
app restrictions in Prayer alert permissions if delivery needs repair. This
optional access is separate from the notification decision.

Qibla has offline sensor compass and calculated true-north bearing using the
confirmed app location. Approximate coordinates do not measure orientation;
sensor availability/calibration determine compass accuracy. Map is an explicit
foreground action: **Load map** requests a bounded OpenStreetMap area and draws
the great-circle route to the Kaaba, with attribution. The provider receives your
IP and viewed area. No map prefetch, IP-geolocation service or background map
listener runs. Manual location and offline compass/bearing remain available.

TV media controls use spaced Play/Stop icons per media type. The phone refreshes
TV state on entry and after commands; it does not continuously poll or pretend
its snapshot is authoritative about playback started elsewhere.

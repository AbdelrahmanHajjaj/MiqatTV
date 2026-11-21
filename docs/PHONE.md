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
Fresh phone settings use a 12-hour clock; existing saved clock choices are retained.
Only large visual catalogs scroll horizontally. Short choices stay visible;
long text choices such as high-latitude rules use a vertical selection stack.
Appearance saves theme, background and display changes immediately. Back keeps
your changes. Language and widgets are available directly in Phone settings.
Home uses a dimmed version of the selected scene. Turn **Background on Prayer
home** off in Appearance for plain surfaces. Existing landscape-only scenes are
composed for tall screens; the dedicated portrait Signature remains unchanged.

Approximate location is supported, including optional background updates.
Country/city selection is local. Detected coordinates remain authoritative;
a nearest-city label never replaces them. Manual coordinates and timezone are
in an Advanced dialog with Apply/Cancel. Changing location or timezone requires
confirmation again. Review the method against your local authority; high-latitude
rules can still return unavailable polar times.

Phone settings omit the redundant Reduce motion toggle. Android animation settings
and earlier saved motion opt-outs remain respected.

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
steps and valid typed numbers save immediately without a per-control Done step.
Day overrides belong to one prayer and weekday and
change only sound/vibration. Enable a weekday override to expand its controls in
the same view; turn it off to restore prayer defaults. A disabled
prayer or excluded weekday stays off. Overrides use that prayer's local date,
including before/after reminders that cross midnight.

Adhan, Listen and length previews use **media volume** by default. Enable
**Use alarm sound** in Prayer alerts to use **alarm volume** instead. The selected
recording stays the same, and the app's volume percentage applies within that
system stream. Volume buttons and media controls follow the selected stream.
Changing the mode stops current playback; the next playback uses the new mode.
Short-tone notifications retain Android's notification-volume/channel behavior.
This option does not grant Android timing or Do Not Disturb access.

Choose the default adhan level/length or override them for a prayer. Length is
5–100% of the selected recording. **Preview selected length** plays from the
beginning and stops at that percentage; moving the slider never starts sound. Existing
seconds caps survive upgrades and remain visible until the matching percentage
is edited. Fajr has a dedicated default recording; other prayers inherit the
normal voice. Old retired Al Afasy selections migrate to the approved Al-Malki
or Chebae Fajr recording. Phone settings never write to a TV.

**Test Adhan** and **Test notification** in Prayer alerts schedule an explicit
one-shot test after a 30-second grace period, with a countdown and Cancel test.
They do not enable prayers or change the real schedule. Adhan uses the default
recording and its configured volume/length. Notification uses the existing short-
tone channel. Android permissions, channel edits and Do Not Disturb still apply.
Without exact timing access, Android may deliver later; the page states that timing
is approximate. Reboot/update, cancellation, expiry and duplicate callbacks cannot
replay a test.

An existing quiet-hour rule is retained and labeled in Prayer alerts. It keeps
its original event-time boundaries, including overnight and equal start/end
(full day), and takes precedence over day overrides. Review or turn it off
explicitly; Miqat does not silently convert a time window into whole weekdays.

Voice sheets keep a draft selection: tap **Listen** to play it from beginning to
end, Stop
or Cancel to end the preview, and Apply to save. Changing the draft does not
change an upcoming adhan. Tap **Choose audio from device** in the voice picker
to choose that voice's recording. The action stays visible while a built-in voice
is selected. An existing private recording can be reused without importing again.
Every import stays in the app’s private adhan library, even after switching voices.
Keep the file name or enter a recording name when importing. Saved recordings are
searchable in every voice picker and can be reused for the default or any prayer.
Existing imports are retained and given a fallback name. Miqat copies audio-only
media into private local storage,
checks that Android can prepare it, and limits it to 20 MB / 10 minutes. Failed
imports preserve the previous selection. Original document access is not needed
after a successful copy. There are 13 selectable approved built-in voices; the
retired recording is removed from distribution. Imported files
are excluded from backup/transfer and removed by
**Reset phone prayer settings**. This reset keeps your paired TVs.

In **Location**, **Update location automatically** is optional and off by default.
It explains background use before asking for Android access. Android 10 has a
separate background permission; newer versions use **Allow all the time** in
Android app settings. Declining keeps manual location usable. Previously enabled
foreground refresh remains a foreground-only preference until this new opt-in.

When enabled, Miqat requests a bounded location fix through Android's persisted
periodic scheduler, including while the app is closed. Android chooses the time
and may defer updates in Doze or restricted battery modes; this is not continuous
tracking or immediate travel detection. There is no idle foreground service.
The calculation method and prayer adjustments stay unchanged, while timezone
follows Android. Manual city/coordinate selection turns automatic updates off;
a stale background result cannot overwrite the newer selection. Revoking location
access stops refresh work. Force-stopping requires reopening Miqat.

Settings rows show their saved values. Tap a number to edit inline; +/− and valid
typed changes apply immediately. Small choice sets stay visible and large lists
open a searchable sheet. Prayer / Your TVs / Phone settings use
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
sensor, use the numeric bearing with a separate compass. The compass has no background sensor service; an undefined bearing at the destination is
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

Prayer Home keeps the greeting and exact TV Quran verse above the prayer card,
without an extra reference line. Location success/confirmation copy is omitted
from Home; setup/settings still own confirmation and location access.
The Hijri date and one city action share the card: tap the city to refresh an
already permitted location, or open Location settings when access is absent.
Tap the date to open the
Hijri month calendar and browse dates. The selected day's timetable appears
beneath the month grid and updates immediately. Done, Back or dismissal returns
to the complete live Home screen; browsing does not replace Home with another date.
Event dates have accessible markers, a selected-day label and a month event list.
The Home card shows today's event or the nearest event within three days, with
no empty event row on ordinary dates. The initial catalog includes Ramadan's
start, Eid al-Fitr, Day of Arafah, Eid al-Adha and Ashura; it follows the same
Umm al-Qura calculation, timezone and manual Hijri correction as the calendar.
This is a calculated calendar, not a local moon-sighting announcement.
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
previews with bilingual image fallbacks for older launchers. Changes save and update
only that widget immediately; Back keeps them. Add completes new widget placement.
The launcher can reopen configuration. Resizing keeps saved style
and date choices while adapting the amount of prayer detail that fits.

The prayer data follows phone location, calculation, language, digits and clock.
The prayer strip keeps both Hijri and Gregorian dates when both are enabled,
and its prayer columns fill the available height. Its next prayer has a themed
rectangular border, matching Home; after Isha the Fajr slot uses tomorrow’s time. Other compact presentations
prioritize the enabled Hijri date when both dates cannot fit.
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
  background changes across the whole page.
- Open Prayer without a TV; detect/confirm location and timezone, browse dates in the Hijri calendar and inspect their calculated
  times, change each prayer and test immediate numeric changes plus voice preview/Stop.
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

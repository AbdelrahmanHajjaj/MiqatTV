# Miqat on Android phones

Open **Settings → About Miqat** for the installed version and the GitHub
feedback form. GitHub sign-in is required and reports are public. Email feedback
is currently disabled.

For a sideloaded build, **Check for updates** compares your installed version
with the stable GitHub release. If a newer version is available, choose
**Download and install**, then confirm the update in Android. On first use,
Android may ask you to allow Miqat to install apps from this source.

Miqat is a standalone Android 8+ prayer app with optional TV companion mode in
**Your TVs**. Phone prayer times, alerts, calendar, Qibla and widgets do not need a
TV or account. Prayer calculation and the city catalog work offline.

[Current screenshots](screenshots/README.md) · [Build/install](BUILD.md#android-phone) · [TV setup and pairing](../companion/README.md)

[Download Miqat](https://github.com/AbdelrahmanHajjaj/MiqatTV/releases/tag/v0.7.7).
Replacing an older debug-signed build requires one fresh install; uninstalling
clears local settings and imported audio. Later signed releases retain the same key.

## First use and location

Welcome leads to **Your location & alerts**, then **Your style**. The location
step requests foreground location access once and fills the location when a fix
is available. Deny access to choose country/city manually; the compact location
icon can retry. Confirm the location to proceed to Android's notification
permission request when needed. Denial continues setup without repeated prompts.
Setup can be skipped and resumed.

Detected coordinates stay authoritative; a nearest-city label never replaces
them. Advanced location provides manual coordinates and timezone in a validated
Apply/Cancel dialog. Changing location or timezone requires confirmation again.
Choose a calculation method and Asr school appropriate to your local authority;
high-latitude rules can still produce unavailable polar times. Prayer offsets
remain when changing calculation/location, so review them after a move.

**Automatic location** is optional and off by default, in setup and Location.
It explains background use before requesting Android access. Android 10 has a
separate background permission; newer versions use **Allow all the time** in app
settings. Approximate access is supported. Declining retains manual setup.

With consent and a confirmed location, Android's persisted periodic job requests
one bounded fix, including while Miqat is closed. Android chooses timing and may
defer it under Doze/battery restrictions; this is not continuous tracking or
instant travel detection. Timezone follows Android; method/offsets stay intact.
Manual city/coordinate selection turns automatic updates off. Stale fixes cannot
overwrite newer manual choices. Revocation cancels work; force-stop requires
reopening. Earlier foreground refresh does not grant background consent.

## Home and Hijri calendar

Home includes the greeting, exact Quran verse and prayer card, with its Hijri
date, one city action, next prayer with a seconds countdown, and the daily timetable. The city action
refreshes an already permitted location or opens Location settings. There is no
extra tomorrow-Fajr row. Tap the date to open the Hijri month. Selecting a day shows
its calculated prayer times directly beneath the calendar. Swipe the month grid
to change months or the timetable to change days. Both sections also have
previous and next buttons. Done, Back and
cancellation return to the complete live Home. Browsing does not change alerts.

Events have calendar markers and a month list. Home shows today's observances or
the next within three days. The calendar numbers each day of Ramadan and marks its
last ten nights, all three days of Eid al-Fitr, and the Hajj observances from
Tarwiyah through the Days of Tashriq, including Eid al-Adha. It also marks Hijri New
Year, Ashura, Mawlid, Isra and Mi'raj, and Mid-Sha'ban. Dates follow the app's
Umm al-Qura calendar, prayer timezone and Hijri adjustment.
[Eid al-Fitr](https://sis.gov.eg/en/egypt/society/days-and-occasions/eid-al-fitr/) ·
[Hajj rites](https://dar-alifta.org/en/article/details/29/overview-of-the-rites-of-hajj-and-umrah)

## Fasting reminders

Open **Settings → Fasting reminders** to enable the reminders you want. All
three are off initially.

- **Day-before reminder** says **Fasting tomorrow** and shows the upcoming
  Hijri date. It defaults to 8:00 PM in the prayer location's timezone, and
  you can choose another time.
- **Suhoor reminder** defaults to 30 minutes before the phone's calculated
  Fajr, with a choice from 5 to 120 minutes.
- **Iftar reminder** arrives at the phone's calculated Maghrib.

Ramadan is selected initially. Ashura, Arafah, the White Days, and Mondays and
Thursdays are each optional. Overlapping selections send one reminder of each
enabled type. The first day of Eid al-Fitr and 10–13 Dhul-Hijjah are excluded.
Fasting uses its own notification channel and works with prayer alerts disabled.
Confirm the prayer location and allow Android notifications to receive it.
[Fasting times](https://www.dar-alifta.org/en/fatwa/details/8075/the-beginning-of-the-fasting-hours) ·
[Eid and Tashriq fasting exclusions](https://www.dar-alifta.org/en/fatwa/details/6288/fasting-on-the-days-of-tashreeq)

## Prayer alerts

Tap a prayer to configure it. Its master switch disables its controls while
retaining values. Sunrise is informational. Choose **Adhan**, **Short tone** or
**Silent**; vibration is independent and uses a short double pulse. Notification
permission, Android channel settings and Do Not Disturb remain authoritative.

Weekdays are circular, accessible toggles on one row, scrolling at enlarged sizes
when needed. Offsets and before/after reminders use inline −/number/+ controls.
Steps and valid typed values save immediately. Each control previews today's
adjusted prayer or reminder time. Location settings also show adjusted sunrise.
A prayer/weekday override expands
in place and changes only sound/vibration. Excluded weekdays and disabled prayers
stay off, including reminders crossing midnight. A retained legacy quiet-hours
rule takes precedence; review/disable it explicitly rather than silently
converting its original time window into weekdays.

## Voices, level and length

There are 13 approved built-in voices, with a separate Fajr default. Other prayers
inherit the normal voice unless overridden. Voice pickers provide search,
**Listen** from beginning to end, Stop, and selection Apply/Cancel; auditioning a
voice does not change the next scheduled Adhan.

**Choose audio from device** imports audio into a named private library. Keep the
file name or choose a name; saved recordings remain searchable/reusable for the
default or any prayer after switching voices. Imports must be audio Android can
prepare, at most 20 MB and 10 minutes. Successful imports no longer need access to
the source document; failed imports retain the previous selection. Files are
excluded from backup/transfer and removed by Reset phone prayer settings.

Adhan and previews use media volume by default. **Use alarm sound** selects the
alarm stream, independently of Android's timing permission. **Adhan level** uses
a percentage of the selected stream's maximum, rounded to its available steps.
Miqat restores its temporary stream change on pause/finish and preserves manual
volume changes. Zero silences Miqat without changing system volume. Fixed-volume
devices use player level; Android safety/volume restrictions still apply. Abrupt
process-death restoration is recovered at the next Miqat entry. Short-tone
notifications use Android's notification channel/volume.

Length is 5–100% of the selected recording. **Preview selected length** starts at
zero and stops at that percentage; moving the slider does not start playback.
Existing seconds caps remain until their corresponding percentage is edited.
**Test Adhan** and **Test notification** use an explicit 30-second grace period
with countdown/Cancel. They do not change real prayers or their claims; Android
can deliver later when exact timing is unavailable.

## Playback and Android permissions

Scheduled Adhan continues outside the app or with the screen off; audition
previews stop when leaving. Audio-focus loss stops playback without restarting a
missed recording. The Home card shows a full-width animated playback band that
opens controls. It becomes static under reduced motion. Android animation
preferences and previously saved motion opt-outs are respected.

The status bar/header keep the Miqat mihrab. The expanded notification shows the
prayer icon/name, scheduled time, actual recording name, Pause/Play and Stop icons,
and one progress bar. Its layout/colors are Android-owned; decorative background
artwork is omitted. Dismissing the notification stops playback on supported
versions; older foreground-service implementations may keep it pinned, with Stop
still available. OEM dismissal gestures vary.

**Full-screen adhan**, off by default, opens the Adhan screen directly while Miqat
is active. In the background, Android chooses lock-screen or heads-up
presentation. **Allow adhan on the lock screen** requests that separate access.
Tapping the notification opens the same session. The bottom swipe handle, its
accessible Stop action or an optional visible-screen volume-key shortcut stops
Adhan. Back asks confirmation; the power key/screen timeout does not stop audio.

Notification denial prevents scheduled output. Core prayers use alarm-clock
scheduling: Android 13+ grants its core timing permission automatically; Android
12 uses special timing access and falls back to approximate delivery without it.
This is separate from Use alarm sound. Schedules recover after boot, time/timezone
changes, updates and settings changes. Persisted claims reject duplicates and
events at least two minutes late. Manufacturer battery restrictions still apply;
Prayer alert permissions links to Android settings. No idle foreground service or
background TV connection runs.

## Quran Radio

Tap the radio icon on the prayer home toolbar to open the **Quran Radio**
popup. Choose a station and use the **Play** icon. While a radio session is
active, Home also shows a Play/Pause shortcut. The stream uses the internet
and continues while you use other apps or lock the screen.

Android's media player offers Play/Pause and **Stop radio**. Tap the player
to reopen the Home popup. Pause closes the stream and keeps the station
selected. Play reconnects to the live broadcast. Android may keep the
player pinned while it is playing. Use **Stop radio** to end the session,
or pause and dismiss the player where Android offers that action.
The player uses the current phone theme and background as its artwork. Changes
in **Settings → Appearance** refresh the artwork during playback. Android
chooses its crop, tint and placement.

Radio tries to reconnect after a stall. If the popup shows **Station unavailable**,
the stream is closed. Tap **Play** to try the same station again.

Adhan pauses the stream, including during reconnection. Tap **Play** after Adhan
ends to listen again. The phone station is independent of any paired TV.

## Qibla

Allow precise Android location to use Qibla. The compass and map use a fresh
device location independently of the city set for prayer times. The compass works
offline and uses a foreground orientation sensor. Bearing is clockwise from true
north. Keep the phone level, calibrate it and avoid magnets/metal.
Without a suitable sensor, use its numeric bearing with another compass. A bearing
at the destination is unavailable.

Select **Map** to open OpenStreetMap zoomed in on your current location.
Drag to pan and pinch or use + and − to zoom. The location button restores the
starting view. The map draws the great-circle route to the Kaaba and loads tiles
only for the area in view. OpenStreetMap receives your IP and viewed area.

## Home-screen widgets

**Phone settings → Home-screen widgets** customizes and adds widgets using
Android's pin flow or the launcher's Widgets menu. Next prayer starts at **1×1**;
the adaptive widgets allow one-cell resize bounds on compatible launchers. They
adapt to their allocated dimensions: next prayer when narrow, a timetable strip
when wide/short, and a daily list when tall enough. Android 12+ supports exact size
variants; older launchers use their resize bounds. Some launchers need a widget
removed/re-added to refresh changed minimum limits.

Choose **Circle** in Miqat or **Countdown circle** in the launcher. It starts in a
fixed **2×2** slot on compatible launchers. The prayer name and icon are the
largest row, followed by the scheduled time and a smaller countdown. The
selected dates sit inside the ring, which shows the fraction of the prayer
interval remaining. Select **120%** under **Text size** for larger Circle text.

Each instance retains its own theme/background/opacity, text size,
Hijri/Gregorian date choices and initial presentation. Changes update that widget
immediately. Adaptive widgets also offer corner shapes.
**Add prayer widget** completes new placement; reopening uses
**Update prayer widget**. Back keeps changes. Prayer data follows the phone's
location/calculation/language/clock, independently of TV settings.

Small tiles prioritize prayer/time and restore details as they grow. Wide one-row
cards retain the enabled Hijri date, more prominent than the countdown. The strip
can show both dates, fills its height and outlines the next prayer; after Isha its
Fajr slot uses tomorrow's timestamp. Text, icons and colors follow the app.
Android ticks the visible countdown each second using the system's digit style.
The circle's ring refreshes about once a minute while the phone is awake.
Android can delay ring updates and prayer rollover while idle or when exact
timing access is unavailable.
Tap a widget to open Prayer.

## Appearance

Miqat and Signature lead theme/background catalogs. Previews cover the whole page;
ordinary appearance, display and widget choices save immediately, including Back.
Specialized dialogs still validate a complete value before committing. Home's
artwork is dimmed and can be turned off. Shared scenes are composed for portrait;
Signature also has dedicated portrait artwork. Arabic/English, Western/Eastern
digits, 12/24-hour clocks and 80–150% sizing work with Android text scaling. New
installs default to 12-hour time; saved explicit choices are retained.

## Your TVs

**Set up my TV** and **Pair a new TV** use Android/Google TV or LG webOS preparation,
then choose **Production** or **Staging** for public downloads. Use **Advanced**
to install a local APK or IPK, including a Development build.

Android reuses the guarded ADB installer/recovery runtime. LG uses Developer Mode,
explicit SSH host-key approval, package size/SHA-256 checks and downgrade refusal;
it can remember the address and encrypt the passphrase on the phone. Setup shows
download and transfer percentages and the current install or verification stage.
Installation and everyday remote pairing
are separate. LG sessions need renewal; it has no Android debloat, Home replacement
or standby wake. [Android setup](ANDROID-SETUP.md) · [LG setup](WEBOS.md).

Pair on the same home network, scan the TV's QR and approve with its physical
remote. Each TV has pinned HTTPS identity and revocable phone credentials.
Supported settings save immediately with baseline conflict checks; explicit
settings entry reads current values, and reconnect never uploads cached changes.
Remote has Home, inputs, radio and a bounded Adhan test. Spaced Play/Stop controls
use refreshed state on entry/after commands, without continuous polling.
**Your TVs → ⋮ → Forget TV** removes its phone-side credentials/cache; revocation
on the TV still requires its remote. [Companion guide](../companion/README.md).

## Storage

Phone prayers use protected local storage for pre-unlock recovery; paired TVs use
a separate encrypted store. Neither is in Android backup/transfer. **Reset phone
prayer settings** removes private prayer/audio data and leaves paired TVs intact.
There is no cloud sync.

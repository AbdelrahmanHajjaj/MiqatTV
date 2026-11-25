# Miqat on Android phones

Miqat runs on Android 8 or later. Prayer times and the city catalog work offline.
[Download the Android app](https://github.com/AbdelrahmanHajjaj/MiqatTV/releases/latest) · [Build from source](BUILD.md#android-phone) · [Screenshots](screenshots/README.md)

Replacing an old debug-signed build requires uninstalling it first. Uninstalling
clears local settings and imported audio. Updates between releases signed with
the same key keep your data.

## First use and location

Allow location access to fill your coordinates, or choose a country and city
manually. Confirm the location, then allow Android notifications if you want
alerts. You can skip setup and resume it from Home.

Under **Location**, use the location icon to retry detection or **Advanced** to
enter coordinates and a timezone. The nearest-city name is a label. Calculations
use your detected coordinates. Review your calculation method, Asr school and
existing offsets when you move. Some polar prayer times may be unavailable.

Turn on **Automatic location** if you want Miqat to update while closed. Android
10 asks for separate background permission. On newer versions, choose **Allow all
the time** in Android settings. Approximate access works. Android schedules the
updates and may delay them to save battery. The timezone follows Android.
Choosing a city or coordinates manually turns off automatic updates. After
force-stopping Miqat, reopen it to resume updates.

## Home and Hijri calendar

Home shows today's timetable and a countdown to the next prayer. Tap the city
to refresh your location or open Location settings. Tap the date to open the
Hijri calendar. Select a day to see its prayer times. Swipe the calendar to
change months or the timetable to change days. Browsing leaves your alerts
as scheduled.

The calendar marks Ramadan, Eid, the Hajj days and other recurring observances.
Home shows today's observance or the next one within three days. Dates use
Umm al-Qura, your prayer timezone and your Hijri adjustment.

## Fasting reminders

Under **Phone settings → Fasting reminders**, turn on the reminders you want. All
three start off. The day-before reminder defaults to 8:00 PM in your prayer
timezone. Suhoor defaults to 30 minutes before Fajr and supports 5–120 minutes.
Iftar follows Maghrib.

Ramadan is selected by default. You can also select Ashura, Arafah, the White
Days, and Mondays and Thursdays. Overlapping days send one reminder of each
enabled type. Miqat excludes 1 Shawwal and 10–13 Dhul-Hijjah. Confirm your
location and allow notifications. Fasting reminders have their own Android
notification channel and work independently of prayer alerts.
[Fasting times](https://www.dar-alifta.org/en/fatwa/details/8075/the-beginning-of-the-fasting-hours) · [Fasting exclusions](https://www.dar-alifta.org/en/fatwa/details/6288/fasting-on-the-days-of-tashreeq)

## Prayer alerts

Tap a prayer and choose **Adhan**, **Short tone** or **Silent**. Set vibration
and weekdays separately. Turning a prayer off keeps its choices for later.
Sunrise is a time display only.

Adjust prayer offsets and before/after reminders with the inline controls.
Valid changes save as you make them. Each control previews today's adjusted
time. Weekday overrides change sound and vibration for that prayer. Disabled
prayers and excluded weekdays stay off when reminders cross midnight. If you
have a quiet-hours rule from an older version, it still takes precedence.

## Voices, level and length

Choose a built-in voice or use **Choose audio from device** to import and name a
recording. Files must play on Android and be at most 20 MB and 10 minutes. Use
**Listen** to hear the whole recording, and **Apply** to save the voice. Fajr
has its own default. You can reuse or delete recordings in the private library.
A failed import keeps the old selection.

**Adhan level** sets a percentage of the Android media stream's maximum.
**Use alarm sound** switches to the alarm stream. Miqat restores temporary
volume changes when you pause or stop and preserves any manual adjustment
you make. Zero plays silently without changing system volume. On fixed-volume
devices, Miqat adjusts the player level. If Android kills the process, reopen
Miqat to recover the previous level. Short tones use the notification channel.

Set length from 5–100%. **Preview selected length** plays from the beginning.
Caps saved in seconds remain in effect until you edit the corresponding
percentage. **Test Adhan** and **Test notification** start a 30-second countdown
you can cancel. They leave the prayer schedule intact. Android may deliver the
test later without exact timing access.

## Playback and Android permissions

Scheduled Adhan can play while Miqat is closed or the screen is off. Auditions
stop when you leave. Tap the playback band on Home or the notification to open
controls. Use **Stop** or dismiss the notification where Android allows it.

**Full-screen adhan** opens the Adhan screen when Miqat is active. To show it on
the lock screen, enable **Allow adhan on the lock screen**. Android controls
background presentation. Swipe the bottom handle or use its accessible Stop
action to stop playback. Back asks for confirmation. Turning off the screen
keeps the audio playing.

Allow Android notifications for scheduled alerts. Android 13 and later grant
Miqat's core timing permission. On Android 12, use **Prayer alert permissions**
to allow exact timing. Without it, delivery is approximate. Channel settings,
Do Not Disturb and manufacturer battery limits still apply. Miqat reschedules
after restart, update or time changes and skips duplicates and alerts at least
two minutes late.

## Quran Radio

Open the radio icon on Home, select a station and tap **Play**. Radio uses the
internet and can play while the screen is locked. Use Android's media player
to pause or stop. Pause keeps the station selected. Play reconnects to the live
stream. If you see **Station unavailable**, tap **Play** to retry. Adhan pauses
radio. Tap **Play** again when Adhan ends.

## Qibla

Allow precise location for Qibla. The compass uses your current location
independently of the city selected for prayer times. Keep the phone level and
away from magnets or metal. If your phone lacks a compass sensor, use the
displayed bearing clockwise from true north with another compass.

Tap **Map** to load OpenStreetMap at your current location. Pan or zoom to see
the route to the Kaaba. Use the location button to recenter. OpenStreetMap
receives your IP address and the area you view.

## Home-screen widgets

Open **Phone settings → Home-screen widgets** or the launcher's Widgets menu.
**Next prayer** starts at 1×1 on compatible launchers. Resize to see a timetable
strip or daily list. **Circle** (called **Countdown circle** in the launcher)
starts at 2×2 and can grow in either direction. Choose 120% under **Text size**
for larger Circle text.

Each widget keeps its own theme, date and text size. Tap **Add prayer widget**
to place it or **Update prayer widget** on re-entry. Back keeps your changes.
Tap the widget to open Prayer. If your launcher keeps an old size limit,
remove and add the widget again.

The countdown ticks each second. The circle ring updates about once a minute
while the phone is awake. Android can delay ring updates and the next-prayer
rollover while idle.

## Appearance

Under **Phone settings → Appearance & display**, change the palette,
background, digits, clock format or text size (80–150%). You can turn off the
Home artwork. Change language from the main Phone settings page. Choices save
as you make them.

## Your TVs

Use **Your TVs** to install Miqat on Android TV or LG webOS, pair with your TV,
and control settings, inputs and radio. **Pair a TV** goes straight to QR pairing.
Open a paired TV and choose **Check for updates** to update Miqat using the TV's
saved installation connection. [TV installation and pairing](../companion/README.md)

## Storage and updates

**Reset phone prayer settings** clears prayer and appearance preferences and
turns off alerts. Imported recordings and paired TVs stay available. Prayer
settings, private recordings and TV credentials stay on the phone and are
excluded from Android backup and transfer.

Under **Phone settings → About Miqat**, check the installed version or open
the public GitHub feedback form, which requires sign-in. For sideloaded builds,
select **Check for updates**, then **Download and install** if available.
Confirm in Android. You may need to allow installs from Miqat the first time.
Turn on **Check for updates automatically** to check in the background when
Miqat opens or comes back to the foreground. A dialog appears when a newer
Production version is available.

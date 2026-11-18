# Miqat on Android phones

Miqat calculates prayer times offline and schedules this phone's prayer alerts.
It also installs and controls supported TVs. A TV, account or network connection
is not needed for phone prayer times. Minimum Android version: 8 (API 26).

## First use

1. Open **Prayer → location**, choose a city or save custom latitude/longitude.
2. Select the prayer timezone. **Use phone timezone** follows Android, including
   changes while travelling; choose an explicit IANA timezone to keep a fixed
   location. City selection supplies its coordinates, method and Asr school;
   verify the timezone yourself before confirming the location.
3. Review calculation method, Asr school, high-latitude rule and adjustments.
   Confirm the location and selected timezone. Times use the same pinned offline
   astronomy source and whole-minute flooring as the TV app.
4. In **Phone settings → Adhan & reminders**, enable phone prayer alerts.
   **Prayer alert permissions** opens Android notification and Alarms & reminders
   controls. Miqat reports missing access; it does not enable it silently.

Alerts default to off. Changing the city, coordinates or timezone requires location
confirmation again. Review the method and local authority for your location;
the selected high-latitude rule may still return unavailable polar times.

## Prayer and sound controls

Tap a prayer to select off, notification or adhan, its voice, minute adjustment,
advance/after reminder and vibration with adhan. Sunrise remains informational.
The default voice applies unless a prayer overrides it. The phone has independent
settings; changing them never writes to a TV.

Numbers edit inline with +/- and Done/Cancel. Voice previews last up to 12 seconds.
Stop is available on the Prayer screen and the playback notification. A scheduled
adhan continues when the app leaves the foreground; a preview stops. Audio-focus
loss stops playback and does not automatically restart a missed recording.

Adhan level is relative to Android's alarm volume. Miqat does not change system
volume or bypass Do Not Disturb. Zero duration means the complete approved clip;
the service exists only while preparing/playing audio. Notification-only alerts
use Android notification channel sound settings. No idle foreground service runs.

Android may display the next prayer/reminder as an alarm clock. Alarms rebuild
after boot, clock/timezone changes, app updates and settings changes. Duplicate
claims persist; events two minutes late or older are skipped. Force-stopping the
app disables Android delivery until the app is opened again. Manufacturer battery
restrictions can affect delivery; physical-device reliability remains subject to
the owner's test. Reopen **Prayer alert permissions** if alerts are not ready.

## Appearance and TVs

Phone appearance includes shared palettes and static artwork cropped for portrait,
digits, 12/24-hour clock and 80–150% interface size, combined with Android text
scaling. The Hijri date uses Android's Umm al-Qura calendar plus the chosen day
adjustment. This is a display date, independent of prayer astronomy.

**Your TVs** retains pairing, Android TV setup/recovery and LG Developer Mode
setup. Install permission and normal authenticated remote pairing are separate.
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

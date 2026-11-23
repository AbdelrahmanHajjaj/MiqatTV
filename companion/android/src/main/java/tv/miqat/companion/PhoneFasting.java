package tv.miqat.companion;

import android.icu.util.IslamicCalendar;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import tv.miqat.companion.core.FastingPreferences;
import tv.miqat.companion.core.FastingRules;
import tv.miqat.companion.core.PhonePrayerRules;
import tv.miqat.core.ReminderEvent;

/** Supplies corrected local dates and calculated phone prayer times to the fasting rules. */
final class PhoneFasting {
    private PhoneFasting() {}

    static FastingPreferences preferences(PhoneStore settings) {
        return new FastingPreferences(
                selected(settings, "ramadan"), selected(settings, "ashura"),
                selected(settings, "arafah"), selected(settings, "white-days"),
                selected(settings, "monday-thursday"),
                selected(settings, "suhoor") ? settings.number("fasting.suhoor-minutes") : 0,
                selected(settings, "iftar"),
                selected(settings, "eve") ? settings.number("fasting.eve-time") : null);
    }

    private static boolean selected(PhoneStore settings, String id) {
        return settings.value("fasting." + id).equals("true");
    }

    static boolean kind(String kind) {
        return FastingRules.SUHOOR.equals(kind) || FastingRules.IFTAR.equals(kind)
                || FastingRules.EVE.equals(kind);
    }

    static boolean stillTimely(ReminderEvent event, long now) {
        return !FastingRules.EVE.equals(event.getKind()) || now < event.getPrayerTime();
    }

    static ReminderEvent next(PhoneStore settings, long now, String[] handled) {
        FastingPreferences preferences = preferences(settings);
        List<String> claimed = Arrays.asList(handled);
        ReminderEvent next = null;
        for (int offset = -1; offset <= 7; offset++) {
            for (ReminderEvent event : events(settings, preferences, now, offset)) {
                if (event.getTime() > now - PhonePrayerRules.LATE_WINDOW_MS
                        && stillTimely(event, now)
                        && !claimed.contains(event.getId())
                        && (next == null || event.getTime() < next.getTime()))
                    next = event;
            }
        }
        return next;
    }

    static boolean matches(PhoneStore settings, ReminderEvent requested) {
        FastingPreferences preferences = preferences(settings);
        if (!settings.locationReady || !preferences.getEnabled()) return false;
        for (int offset = -1; offset <= 1; offset++) {
            for (ReminderEvent event : events(settings, preferences, (long) requested.getPrayerTime(), offset)) {
                if (event.getId().equals(requested.getId()) && event.getTime() == requested.getTime()
                        && event.getPrayerTime() == requested.getPrayerTime()
                        && event.getKind().equals(requested.getKind()) && event.getPrayer() == requested.getPrayer())
                    return true;
            }
        }
        return false;
    }

    private static List<ReminderEvent> events(
            PhoneStore settings, FastingPreferences preferences, long now, int offset) {
        PhoneSchedule.Day day = settings.day(now, offset);
        IslamicCalendar date = PhoneCalendar.calendar(settings, day.start, false);
        Calendar civil = new GregorianCalendar(settings.zone());
        civil.setTimeInMillis(day.start);
        return FastingRules.INSTANCE.events(
                date.get(android.icu.util.Calendar.MONTH) + 1,
                date.get(android.icu.util.Calendar.DATE),
                civil.get(Calendar.DAY_OF_WEEK) - 1,
                day.times == null ? Double.NaN : day.times[0],
                day.times == null ? Double.NaN : day.times[4], preferences, day.start,
                eveTime(settings, day.start, preferences.getEveMinutes()));
    }

    private static Long eveTime(PhoneStore settings, long fastDayStart, Integer minutes) {
        if (minutes == null) return null;
        ZoneId zone = settings.zone().toZoneId();
        LocalDate previous = Instant.ofEpochMilli(fastDayStart).atZone(zone).toLocalDate().minusDays(1);
        ZonedDateTime time = previous.atTime(minutes / 60, minutes % 60).atZone(zone);
        return time.toLocalDate().equals(previous) ? time.toInstant().toEpochMilli() : null;
    }
}

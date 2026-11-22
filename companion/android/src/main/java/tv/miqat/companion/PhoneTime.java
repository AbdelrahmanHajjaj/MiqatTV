package tv.miqat.companion;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Native clock/date formatting for phone views, widgets and playback metadata. */
final class PhoneTime {
    private PhoneTime() {}

    static SimpleDateFormat formatter(PhoneStore settings, Locale locale) {
        SimpleDateFormat format =
                new SimpleDateFormat(
                        settings.value("display.clock").equals("12") ? "h:mm a" : "HH:mm", locale);
        format.setTimeZone(settings.zone());
        return format;
    }

    static String hijri(PhoneStore settings, long stamp, boolean ar) {
        android.icu.util.IslamicCalendar calendar =
                new android.icu.util.IslamicCalendar(
                        android.icu.util.TimeZone.getTimeZone(settings.zone().getID()),
                        new Locale(ar ? "ar" : "en"));
        calendar.setCalculationType(
                android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        calendar.setTimeInMillis(stamp);
        calendar.add(android.icu.util.Calendar.DATE, settings.number("prayer.hijri-offset"));
        android.icu.text.DateFormat format =
                android.icu.text.DateFormat.getDateInstance(
                        android.icu.text.DateFormat.LONG,
                        new android.icu.util.ULocale(
                                (ar ? "ar" : "en") + "@calendar=islamic-umalqura"));
        format.setCalendar(calendar);
        return format.format(calendar.getTime());
    }

    static String at(PhoneStore settings, double stamp, boolean ar) {
        String time =
                formatter(settings, new Locale(ar ? "ar" : "en")).format(new Date((long) stamp));
        String mode = settings.value("display.digits");
        return tv.miqat.core.DisplayText.INSTANCE.digits(
                time, mode.equals("eastern") || mode.equals("auto") && ar);
    }
}

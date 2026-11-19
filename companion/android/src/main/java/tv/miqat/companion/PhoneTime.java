package tv.miqat.companion;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Native clock formatting for phone views, widgets and playback metadata. */
final class PhoneTime {
    private PhoneTime() {}

    static SimpleDateFormat formatter(PhoneStore settings, Locale locale) {
        SimpleDateFormat format =
                new SimpleDateFormat(
                        settings.value("display.clock").equals("12") ? "h:mm a" : "HH:mm", locale);
        format.setTimeZone(settings.zone());
        return format;
    }

    static String at(PhoneStore settings, double stamp, boolean ar) {
        String time =
                formatter(settings, new Locale(ar ? "ar" : "en")).format(new Date((long) stamp));
        String mode = settings.value("display.digits");
        return tv.miqat.core.DisplayText.INSTANCE.digits(
                time, mode.equals("eastern") || mode.equals("auto") && ar);
    }
}

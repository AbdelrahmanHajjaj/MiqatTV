package tv.miqat.companion;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import tv.miqat.core.PrayerSelection;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** One prayer/settings snapshot rendered as native launcher views. */
final class WidgetRenderer {
    private final Context context;
    private final RemoteViews view;
    private final WidgetOptions options;
    private final int width, height, ink, accent;
    private final PhoneStore settings;
    private final PhoneData data;
    private final boolean ar, eastern;
    private final Locale locale;
    private final long now = System.currentTimeMillis();
    private final SimpleDateFormat time;
    private final JSONObject colors;

    static RemoteViews create(Context context, WidgetOptions options, int width, int height) {
        RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.prayer_widget);
        view.setOnClickPendingIntent(R.id.widget_root, PhoneAlarms.open(context));
        try {
            new WidgetRenderer(context, view, options, width, height).render();
        } catch (Exception unavailable) {
            view.setInt(
                    R.id.widget_backdrop,
                    "setColorFilter",
                    context.getColor(R.color.companion_lapis_900));
            view.setTextColor(R.id.widget_title, context.getColor(R.color.companion_gold_400));
            view.setTextColor(R.id.widget_times, context.getColor(R.color.companion_plaster_100));
            view.setTextViewText(R.id.widget_title, "Miqat");
            view.setViewVisibility(R.id.widget_times, View.VISIBLE);
            view.setTextViewText(
                    R.id.widget_times, PhoneAlarms.text(context, "phone_settings_failed"));
            for (int id : new int[] {R.id.widget_next, R.id.widget_prayers, R.id.widget_strip})
                view.setViewVisibility(id, View.GONE);
            view.setTextViewText(R.id.widget_updated, "");
        }
        return view;
    }

    private WidgetRenderer(
            Context context, RemoteViews view, WidgetOptions options, int width, int height)
            throws Exception {
        this.context = context;
        this.view = view;
        this.options = options;
        this.width = width;
        this.height = height;
        settings = PhoneStore.read(context);
        data = PhoneData.get(context);
        ar = PhoneAlarms.arabic(context);
        locale = new Locale(ar ? "ar" : "en");
        String digits = settings.value("display.digits");
        eastern = digits.equals("eastern") || digits.equals("auto") && ar;
        colors =
                data.colors(
                        options.theme.equals("app")
                                ? settings.value("appearance.theme")
                                : options.theme);
        ink = Color.parseColor(colors.getString("plaster-100"));
        accent = Color.parseColor(colors.getString("gold-400"));
        time = PhoneTime.formatter(settings, locale);
    }

    private void render() throws Exception {
        view.setImageViewBitmap(
                R.id.widget_backdrop,
                WidgetBackground.create(context, settings, options, colors, width, height));
        view.setInt(R.id.widget_backdrop, "setImageAlpha", options.opacity * 255 / 100);
        view.setInt(
                R.id.widget_root,
                "setLayoutDirection",
                ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        for (int id :
                new int[] {
                    R.id.widget_title,
                    R.id.widget_updated,
                    R.id.widget_times,
                    R.id.widget_next_label
                }) view.setTextColor(id, ink);
        view.setTextColor(R.id.widget_next_label, accent);
        view.setTextColor(R.id.widget_next_time, ink);
        view.setTextColor(R.id.widget_title, Color.parseColor(colors.getString("mist-400")));
        view.setTextColor(R.id.widget_updated, Color.parseColor(colors.getString("mist-400")));
        SimpleDateFormat date = new SimpleDateFormat("EEE d MMM", locale);
        date.setTimeZone(settings.zone());
        String title =
                (ar ? "ميقات" : "Miqat") + (options.date ? " · " + date.format(new Date(now)) : "");
        boolean compact =
                height < (options.layout.equals("strip") ? 130 : 180)
                        && !options.layout.equals("day");
        if (options.hijri) {
            String hijri = PhoneTime.hijri(settings, now, ar);
            title = compact ? hijri : title + "\n" + hijri;
        }
        view.setInt(R.id.widget_title, "setMaxLines", options.hijri && !compact ? 2 : 1);
        view.setTextViewText(R.id.widget_title, text(title));
        prayers();
        SimpleDateFormat updated = new SimpleDateFormat("HH:mm", locale);
        updated.setTimeZone(settings.zone());
        view.setTextViewText(
                R.id.widget_updated,
                text(data.text("widget_updated", ar) + " · " + updated.format(new Date(now))));
        view.setViewVisibility(
                R.id.widget_updated,
                options.layout.equals("strip") || compact ? View.GONE : View.VISIBLE);
    }

    private void prayers() throws Exception {
        view.removeAllViews(R.id.widget_prayers);
        view.removeAllViews(R.id.widget_strip);
        PhoneSchedule.Day day = settings.day(now, 0), tomorrow = settings.day(now, 1);
        boolean ready = settings.locationReady && day.times != null;
        boolean nextOnly = options.layout.equals("next"), strip = options.layout.equals("strip");
        view.setViewVisibility(R.id.widget_times, ready ? View.GONE : View.VISIBLE);
        view.setTextViewText(
                R.id.widget_times,
                ready
                        ? ""
                        : data.text(
                                !settings.locationReady
                                        ? "phone_location_needed"
                                        : "phone_unavailable",
                                ar));
        view.setViewVisibility(R.id.widget_next, ready && nextOnly ? View.VISIBLE : View.GONE);
        view.setViewVisibility(
                R.id.widget_title,
                nextOnly && height < 180 && !options.hijri ? View.GONE : View.VISIBLE);
        view.setViewVisibility(
                R.id.widget_prayers, ready && !nextOnly && !strip ? View.VISIBLE : View.GONE);
        view.setViewVisibility(R.id.widget_strip, ready && strip ? View.VISIBLE : View.GONE);
        if (!ready) return;
        PrayerSelection next =
                tv.miqat.companion.core.PhonePrayerRules.INSTANCE.nextPrayer(
                        day.times, tomorrow.times, now);
        view.setTextViewText(
                R.id.widget_next_label,
                next == null
                        ? data.text("phone_unavailable", ar)
                        : data.prayerName(next.getNext(), ar));
        view.setTextViewText(
                R.id.widget_next_time,
                next == null ? "—" : text(time.format(new Date((long) next.getNextTime()))));
        view.setTextViewTextSize(
                R.id.widget_next_label,
                android.util.TypedValue.COMPLEX_UNIT_SP,
                12 * options.size / 100f);
        if (!nextOnly)
            for (int i = 0; i < 6; i++)
                row(
                        i,
                        day,
                        next != null && next.getNextTime() < tomorrow.start && next.getNext() == i,
                        strip);
    }

    private void row(int index, PhoneSchedule.Day day, boolean next, boolean strip)
            throws Exception {
        RemoteViews prayer =
                new RemoteViews(
                        context.getPackageName(),
                        strip ? R.layout.widget_prayer_cell : R.layout.widget_prayer_row);
        prayer.setTextViewText(R.id.widget_prayer_name, data.prayerName(index, ar));
        prayer.setTextViewText(
                R.id.widget_prayer_time, text(time.format(new Date((long) day.times[index]))));
        prayer.setTextColor(R.id.widget_prayer_name, ink);
        prayer.setTextColor(R.id.widget_prayer_time, next ? accent : ink);
        for (int id : new int[] {R.id.widget_prayer_name, R.id.widget_prayer_time})
            prayer.setTextViewTextSize(
                    id,
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    (strip ? 11 : 13) * options.size / 100f);
        view.addView(strip ? R.id.widget_strip : R.id.widget_prayers, prayer);
    }

    private String text(String value) {
        return tv.miqat.core.DisplayText.INSTANCE.digits(value, eastern);
    }
}

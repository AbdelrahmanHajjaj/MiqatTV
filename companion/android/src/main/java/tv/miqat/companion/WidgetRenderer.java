package tv.miqat.companion;

import android.content.Context;
import android.graphics.Color;
import android.os.SystemClock;
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
    private final String layout;
    private final boolean minimal, wide;
    private final PhoneStore settings;
    private final PhoneData data;
    private final boolean ar, eastern;
    private final Locale locale;
    private final long now;
    private final SimpleDateFormat time;
    private final JSONObject colors;

    static RemoteViews create(Context context, WidgetOptions options, int width, int height) {
        return create(context, options, width, height, null, System.currentTimeMillis());
    }

    static RemoteViews initial(Context context, WidgetOptions options, int width, int height) {
        return create(context, options, width, height, options.layout, System.currentTimeMillis());
    }

    static RemoteViews create(Context context, WidgetOptions options, int width, int height, long now) {
        return create(context, options, width, height, null, now);
    }

    private static RemoteViews create(Context context, WidgetOptions options, int width, int height, String initialLayout, long now) {
        String presentation = initialLayout == null ? options.layoutFor(width, height, context.getResources().getConfiguration().fontScale) : initialLayout;
        RemoteViews view = new RemoteViews(context.getPackageName(), presentation.equals("next")
                ? PhoneAlarms.arabic(context) ? R.layout.widget_next_card_ar : R.layout.widget_next_card
                : presentation.equals("circle") ? R.layout.widget_circle : R.layout.prayer_widget);
        view.setOnClickPendingIntent(R.id.widget_root, PhoneAlarms.open(context));
        try {
            new WidgetRenderer(context, view, options, width, height, initialLayout, now).render();
        } catch (Exception unavailable) {
            if (presentation.equals("circle")) {
                view.setTextViewText(R.id.widget_next_label, PhoneAlarms.text(context, "phone_settings_failed"));
                view.setViewVisibility(R.id.widget_next_remaining, View.GONE);
                view.setTextViewText(R.id.widget_title, "");
                return view;
            }
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
            view.setViewVisibility(R.id.widget_next_remaining, View.GONE);
        }
        return view;
    }

    private WidgetRenderer(
            Context context, RemoteViews view, WidgetOptions options, int width, int height, String initialLayout, long now)
            throws Exception {
        this.context = context;
        this.now = now;
        this.view = view;
        this.options = options;
        layout = initialLayout == null ? options.layoutFor(width, height, context.getResources().getConfiguration().fontScale) : initialLayout;
        this.width = width > 0 ? width : options.layout.equals("strip") ? 400
                : options.layout.equals("next") ? 80 : options.layout.equals("circle") ? options.height() : 280;
        this.height = height > 0 ? height : options.height();
        float scale = Math.max(1f, context.getResources().getConfiguration().fontScale) * options.size / 100f;
        wide = !layout.equals("strip") && (this.width >= 260 * scale
                || layout.equals("next") && this.width >= 240 && this.width >= this.height * 1.5f);
        minimal = layout.equals("next") && !wide && this.width < 180 * scale && this.height < 140 * scale;
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
        if (layout.equals("circle")) {
            circle();
            return;
        }
        if (wide || layout.equals("next")) {
            view.removeAllViews(R.id.widget_next);
            int content = ar ? R.layout.widget_next_wide_ar : R.layout.widget_next_wide;
            if (layout.equals("next")) {
                content = ar ? R.layout.widget_next_card_stacked_ar : R.layout.widget_next_card_stacked;
                if (wide) content = ar ? R.layout.widget_next_card_wide_ar : R.layout.widget_next_card_wide;
            }
            view.addView(R.id.widget_next, new RemoteViews(context.getPackageName(), content));
        }
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
        view.setTextColor(R.id.widget_next_remaining, Color.parseColor(colors.getString("mist-400")));
        SimpleDateFormat date = new SimpleDateFormat("EEE d MMM", locale);
        date.setTimeZone(settings.zone());
        String title =
                (ar ? "ميقات" : "Miqat") + (options.date ? " · " + date.format(new Date(now)) : "");
        boolean compact = height < 180 && !layout.equals("day");
        if (compact) view.setViewPadding(R.id.widget_content, dp(8), dp(6), dp(8), dp(6));
        view.setInt(R.id.widget_next_highlight, "setColorFilter", accent);
        view.setViewVisibility(R.id.widget_next_highlight, layout.equals("next") ? View.GONE : View.VISIBLE);
        if (options.hijri) {
            String hijri = PhoneTime.hijri(settings, now, ar);
            title = compact ? hijri : title + "\n" + hijri;
        }
        view.setInt(R.id.widget_title, "setMaxLines", options.hijri && !compact ? 2 : 1);
        if (layout.equals("strip")) {
            title = options.hijri ? PhoneTime.hijri(settings, now, ar) : "";
            if (options.date) title += (title.isEmpty() ? "" : " · ") + date.format(new Date(now));
            if (title.isEmpty()) title = ar ? "ميقات" : "Miqat";
            view.setInt(R.id.widget_title, "setMaxLines", 1);
            view.setTextViewTextSize(R.id.widget_title, android.util.TypedValue.COMPLEX_UNIT_SP, 11 * options.size / 100f);
        }
        if (wide && !layout.equals("next")) {
            String metadata = options.hijri ? PhoneTime.hijri(settings, now, ar) : options.date ? date.format(new Date(now)) : "";
            title = (metadata.isEmpty() ? "" : metadata + " · ") + place();
            if (options.hijri && options.date) title += "\n" + date.format(new Date(now));
            view.setInt(R.id.widget_title, "setMaxLines", options.hijri && options.date ? 2 : 1);
            view.setTextViewTextSize(R.id.widget_title, android.util.TypedValue.COMPLEX_UNIT_SP, 11 * options.size / 100f);
        }
        if (layout.equals("next")) {
            // Reserve real text height inside the host's rounded outline, including transparent widgets.
            int inset = Math.min(12, Math.max(4, Math.min(width, height) / 10));
            int verticalInset = height < 56 ? 1 : inset;
            view.setViewPadding(R.id.widget_content, dp(inset), dp(verticalInset), dp(inset), dp(verticalInset));
            view.setViewPadding(R.id.widget_next_content, 0, 0, 0, 0);
            boolean narrow = width < 180;
            title = options.hijri ? PhoneTime.hijri(settings, now, ar) : "";
            if (options.date) title += (title.isEmpty() ? "" : "\n") + date.format(new Date(now));
            view.setInt(R.id.widget_title, "setMaxLines", narrow ? 4 : options.hijri && options.date ? 2 : 1);
            int dateHeight = narrow ? 96 : options.hijri && options.date ? 56 : 28;
            view.setInt(R.id.widget_title, "setHeight", dp(Math.min(dateHeight, Math.round(height * (narrow ? .4f : .27f)))));
            view.setInt(R.id.widget_next_remaining, "setHeight", dp(Math.max(9, Math.min(24, height / 5))));
            view.setTextColor(R.id.widget_title, ink);
            if (narrow) {
                view.setViewVisibility(R.id.widget_next_icon, View.GONE);
                view.setInt(R.id.widget_next_label, "setGravity", android.view.Gravity.CENTER);
                view.setInt(R.id.widget_next_time, "setGravity", android.view.Gravity.CENTER);
            }
        }
        view.setTextViewText(R.id.widget_title, text(title));
        prayers();
        SimpleDateFormat updated = new SimpleDateFormat("HH:mm", locale);
        updated.setTimeZone(settings.zone());
        view.setTextViewText(
                R.id.widget_updated,
                text(data.text("widget_updated", ar) + " · " + updated.format(new Date(now))));
        view.setViewVisibility(
                R.id.widget_updated,
                layout.equals("next") || layout.equals("strip") || compact || minimal || layout.equals("day") && height < 360 * context.getResources().getConfiguration().fontScale ? View.GONE : View.VISIBLE);
    }

    private void circle() throws Exception {
        int side = Math.min(width, height);
        view.setImageViewBitmap(R.id.widget_backdrop,
                WidgetBackground.create(context, settings, options, colors, side, side));
        view.setInt(R.id.widget_backdrop, "setImageAlpha", options.opacity * 255 / 100);
        int inset = Math.round(side * (options.size == 120 ? .14f : .18f));
        view.setViewPadding(R.id.widget_content, dp((width - side) / 2 + inset),
                dp((height - side) / 2 + inset), dp((width - side + 1) / 2 + inset),
                dp((height - side + 1) / 2 + inset));
        view.setTextColor(R.id.widget_next_label, accent);
        view.setTextColor(R.id.widget_next_remaining, ink);
        view.setTextColor(R.id.widget_title, ink);

        PhoneSchedule.Day day = settings.day(now, 0), tomorrow = settings.day(now, 1);
        PrayerSelection next = settings.locationReady
                ? tv.miqat.companion.core.PhonePrayerRules.INSTANCE.nextPrayer(day.times, tomorrow.times, now)
                : null;
        String prayer = data.text(settings.locationReady ? "phone_unavailable" : "phone_location_needed", ar);
        double fraction = 0;
        if (next != null) {
            prayer = data.prayerName(next.getNext(), ar);
            fraction = circleRemaining(day, tomorrow, next);
        }
        view.setTextViewText(R.id.widget_next_label, prayer);
        view.setInt(R.id.widget_next_label, "setMaxLines", next == null ? 2 : 1);
        countdown(next);
        SimpleDateFormat civil = new SimpleDateFormat("d MMM", locale);
        civil.setTimeZone(settings.zone());
        String date = options.hijri ? PhoneTime.hijri(settings, now, ar) : "";
        if (options.date) date += (date.isEmpty() ? "" : "\n") + civil.format(new Date(now));
        view.setTextViewText(R.id.widget_title, text(date));
        view.setViewVisibility(R.id.widget_title, date.isEmpty() ? View.GONE : View.VISIBLE);
        view.setImageViewBitmap(R.id.widget_circle_ring, ring(side, fraction));
    }

    private double circleRemaining(PhoneSchedule.Day day, PhoneSchedule.Day tomorrow, PrayerSelection next) {
        int prayer = next.getNext();
        double target = next.getNextTime();
        double[] previousDay = prayer == 0 && (tomorrow.times == null || target != tomorrow.times[0])
                ? settings.day(now, -1).times : day.times;
        if (previousDay == null) return 0;
        double previous = previousDay[prayer == 0 ? 5 : prayer == 2 ? 0 : prayer - 1];
        return target <= previous ? 0 : Math.max(0, Math.min(1, (target - now) / (target - previous)));
    }

    private android.graphics.Bitmap ring(int side, double fraction) {
        int pixels = Math.max(1, Math.min(320, dp(side)));
        android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(
                pixels, pixels, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(image);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(pixels * .0375f);
        paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        paint.setColor((accent & 0xffffff) | 0x55000000);
        float inset = pixels * .06f;
        android.graphics.RectF track = new android.graphics.RectF(inset, inset, pixels - inset, pixels - inset);
        canvas.drawOval(track, paint);
        paint.setColor(accent);
        canvas.drawArc(track, -90, (float) (360 * fraction), false, paint);
        return image;
    }

    private void prayers() throws Exception {
        view.removeAllViews(R.id.widget_prayers);
        view.removeAllViews(R.id.widget_strip);
        PhoneSchedule.Day day = settings.day(now, 0), tomorrow = settings.day(now, 1);
        boolean ready = settings.locationReady && day.times != null;
        boolean nextOnly = layout.equals("next"), strip = layout.equals("strip");
        view.setViewVisibility(R.id.widget_times, ready ? View.GONE : View.VISIBLE);
        view.setTextViewText(
                R.id.widget_times,
                ready
                        ? ""
                        : data.text(
                                minimal && !settings.locationReady ? "phone_location" : !settings.locationReady
                                        ? "phone_location_needed"
                                        : "phone_unavailable",
                                ar));
        view.setViewVisibility(R.id.widget_next, ready && !strip ? View.VISIBLE : View.GONE);
        view.setViewVisibility(
                R.id.widget_title,
                minimal || nextOnly && !options.hijri && !options.date ? View.GONE : View.VISIBLE);
        view.setViewVisibility(
                R.id.widget_prayers, ready && !nextOnly && !strip ? View.VISIBLE : View.GONE);
        view.setViewVisibility(R.id.widget_strip, ready && strip ? View.VISIBLE : View.GONE);
        if (!ready) {
            countdown(null);
            if (minimal) view.setContentDescription(R.id.widget_root,
                    data.text(!settings.locationReady ? "phone_location_needed" : "phone_unavailable", ar));
            return;
        }
        PrayerSelection next =
                tv.miqat.companion.core.PhonePrayerRules.INSTANCE.nextPrayer(
                        day.times, tomorrow.times, now);
        view.setTextViewText(
                R.id.widget_next_label,
                next == null
                        ? data.text("phone_unavailable", ar)
                        : data.prayerName(next.getNext(), ar));
        if (next != null) {
            view.setImageViewResource(R.id.widget_next_icon, prayerIcon(next.getNext()));
            view.setInt(R.id.widget_next_icon, "setColorFilter", accent);
        }
        String fullTime=next == null ? "—" : text(time.format(new Date((long) next.getNextTime())));
        if (minimal) view.setContentDescription(R.id.widget_root,
                data.text("phone_next", ar) + ", " + (next == null ? data.text("phone_unavailable", ar)
                        : data.prayerName(next.getNext(), ar) + ", " + fullTime));
        if (minimal && width < 72 * Math.max(1f, context.getResources().getConfiguration().fontScale))
            time.applyPattern(settings.value("display.clock").equals("12") ? "h:mm" : "HH:mm");
        view.setTextViewText(
                R.id.widget_next_time,
                next == null ? "—" : text(time.format(new Date((long) next.getNextTime()))));
        if (!wide && !layout.equals("next")) view.setTextViewTextSize(
                R.id.widget_next_label,
                android.util.TypedValue.COMPLEX_UNIT_SP,
                12 * options.size / 100f);
        countdown(next);
        if (!nextOnly) {
            for (int i = 0; i < 6; i++) {
                row(
                        i,
                        strip && i == 0 && next != null && next.getNextTime() >= tomorrow.start ? tomorrow : day,
                        next != null && (strip || next.getNextTime() < tomorrow.start) && next.getNext() == i,
                        strip);
            }
        }
    }

    private void countdown(PrayerSelection next) throws Exception {
        int id = R.id.widget_next_remaining;
        view.setViewVisibility(id, next == null ? View.GONE : View.VISIBLE);
        if (next == null) return;
        String format = layout.equals("next") && width >= 240
                ? data.text("phone_remaining", ar).replace("%s", "\u2066%s\u2069") : null;
        // RemoteViews sets the base before the format, so a detached host could render the old format.
        view.setString(id, "setFormat", format);
        view.setChronometerCountDown(id, true);
        view.setChronometer(id, SystemClock.elapsedRealtime() + (long) next.getNextTime() - now, format, true);
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
        prayer.setImageViewResource(R.id.widget_prayer_icon, prayerIcon(index));
        prayer.setInt(R.id.widget_prayer_icon, "setColorFilter", next ? accent : ink);
        if (strip) prayer.setImageViewResource(R.id.widget_prayer_highlight, R.drawable.widget_strip_highlight);
        prayer.setViewVisibility(R.id.widget_prayer_highlight, next ? View.VISIBLE : View.GONE);
        if (strip && next) prayer.setContentDescription(R.id.widget_prayer_name,
                data.prayerName(index, ar) + ", " + data.text("phone_next", ar)
                        + (day.start > now ? ", " + data.text("phone_tomorrow", ar) : ""));
        prayer.setInt(R.id.widget_prayer_highlight, "setColorFilter", accent);
        prayer.setTextColor(R.id.widget_prayer_name, next ? accent : ink);
        prayer.setTextColor(R.id.widget_prayer_time, next ? accent : ink);
        for (int id : new int[] {R.id.widget_prayer_name, R.id.widget_prayer_time})
            prayer.setTextViewTextSize(
                    id,
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    (strip ? 11 : 12) * options.size / 100f);
        view.addView(strip ? R.id.widget_strip : R.id.widget_prayers, prayer);
    }

    private String place() throws Exception {
        String id = settings.value("prayer.location");
        if (id.equals("custom")) return data.text("phone_custom_location", ar);
        if (id.equals("detected")) return data.text("phone_current_location", ar);
        boolean near = id.startsWith("detected:");
        String name = data.city(near ? id.substring(9) : id).getString(ar ? "ar" : "en");
        return near ? String.format(locale, data.text("phone_near_city", ar), name) : name;
    }

    private int prayerIcon(int prayer) {
        switch (prayer) {
            case 0: return R.drawable.companion_icon_fajr;
            case 1: return R.drawable.companion_icon_sunrise;
            case 2: return R.drawable.companion_icon_dhuhr;
            case 3: return R.drawable.companion_icon_asr;
            case 4: return R.drawable.companion_icon_maghrib;
            case 5: return R.drawable.companion_icon_isha;
            default: throw new IllegalArgumentException("Invalid prayer icon");
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private String text(String value) {
        return tv.miqat.core.DisplayText.INSTANCE.digits(value, eastern);
    }
}

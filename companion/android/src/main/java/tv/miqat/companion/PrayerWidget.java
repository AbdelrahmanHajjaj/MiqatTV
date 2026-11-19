package tv.miqat.companion;

import android.appwidget.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

/** Offline daily timetable; host refreshes it without another service or exact alarm. */
public final class PrayerWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        if (ids.length > 0) manager.updateAppWidget(ids, views(context));
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context, AppWidgetManager manager, int id, Bundle options) {
        manager.updateAppWidget(id, views(context));
    }

    static void updateAll(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, PrayerWidget.class));
            if (ids.length > 0) manager.updateAppWidget(ids, views(context));
        } catch (RuntimeException unavailable) {
            android.util.Log.w(
                    "MiqatWidget", "Widget host update failed; the host may retry later");
        }
    }

    static RemoteViews views(Context context) {
        RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.prayer_widget);
        view.setOnClickPendingIntent(R.id.widget_root, PhoneAlarms.open(context));
        try {
            PhoneStore settings = PhoneStore.read(context);
            PhoneData data = PhoneData.get(context);
            Catalog catalog = new Catalog(context);
            boolean ar = PhoneAlarms.arabic(context);
            Locale locale = new Locale(ar ? "ar" : "en");
            long now = System.currentTimeMillis();
            JSONObject colors =
                    catalog.data
                            .getJSONObject("themes")
                            .getJSONObject(settings.value("appearance.theme"))
                            .getJSONObject("colors");
            int ink = Color.parseColor(colors.getString("plaster-100")),
                    accent = Color.parseColor(colors.getString("gold-400"));
            view.setInt(
                    R.id.widget_root,
                    "setBackgroundColor",
                    Color.parseColor(colors.getString("lapis-900")));
            view.setInt(
                    R.id.widget_root,
                    "setLayoutDirection",
                    ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
            view.setTextColor(R.id.widget_title, accent);
            view.setTextColor(R.id.widget_times, ink);
            view.setTextColor(R.id.widget_updated, ink);
            String digits = settings.value("display.digits");
            boolean eastern = digits.equals("eastern") || digits.equals("auto") && ar;
            SimpleDateFormat date = new SimpleDateFormat("EEE d MMM", locale);
            date.setTimeZone(settings.zone());
            view.setTextViewText(
                    R.id.widget_title,
                    tv.miqat.core.DisplayText.INSTANCE.digits(
                            (ar ? "ميقات" : "Miqat") + " · " + date.format(new Date(now)),
                            eastern));
            StringBuilder lines = new StringBuilder();
            PhoneSchedule.Day day = settings.day(now, 0);
            if (!settings.locationReady) lines.append(data.text("phone_location_needed", ar));
            else if (day.times == null) lines.append(data.text("phone_unavailable", ar));
            else {
                SimpleDateFormat time =
                        new SimpleDateFormat(
                                settings.value("display.clock").equals("12") ? "h:mm a" : "HH:mm",
                                locale);
                time.setTimeZone(settings.zone());
                for (int i = 0; i < 6; i++) {
                    if (i > 0) lines.append('\n');
                    lines.append(data.prayerName(i, ar))
                            .append("   ")
                            .append(time.format(new Date((long) day.times[i])));
                }
            }
            view.setTextViewText(
                    R.id.widget_times,
                    tv.miqat.core.DisplayText.INSTANCE.digits(lines.toString(), eastern));
            SimpleDateFormat updated = new SimpleDateFormat("HH:mm", locale);
            updated.setTimeZone(settings.zone());
            view.setTextViewText(
                    R.id.widget_updated,
                    tv.miqat.core.DisplayText.INSTANCE.digits(
                            data.text("widget_updated", ar) + " · " + updated.format(new Date(now)),
                            eastern));
        } catch (Exception unavailable) {
            view.setTextViewText(R.id.widget_title, "Miqat");
            view.setTextViewText(
                    R.id.widget_times, PhoneAlarms.text(context, "phone_settings_failed"));
            view.setTextViewText(R.id.widget_updated, "");
        }
        return view;
    }

    static void guide(MainActivity a) {
        a.body.addView(a.views.text(a.t("widget_hint"), 15, a.muted));
        LinearLayout preview = a.views.card(a.body);
        preview.addView(
                views(a).apply(a, preview), new LinearLayout.LayoutParams(-1, a.views.dp(300)));
        preview.setTag("widget.preview");
        AppWidgetManager manager = AppWidgetManager.getInstance(a);
        if (manager.isRequestPinAppWidgetSupported())
            a.views
                    .button(
                            a.body,
                            a.t("widget_add"),
                            () ->
                                    manager.requestPinAppWidget(
                                            new ComponentName(a, PrayerWidget.class), null, null))
                    .setTag("widget.add");
        a.body.addView(a.views.text(a.t("widget_manual"), 15, a.muted));
    }
}

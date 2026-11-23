package tv.miqat.companion;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.os.Bundle;
import android.os.Build;
import android.util.SizeF;
import android.widget.RemoteViews;

import java.util.*;
import tv.miqat.companion.core.PhonePrayerRules;
import tv.miqat.core.PrayerSelection;

/** Offline widget rendering, refreshed by the existing prayer lifecycle and launcher. */
public class PrayerWidget extends AppWidgetProvider {
    // Android needs a receiver component per default size in the launcher picker.
    public static final class Next extends PrayerWidget {}

    public static final class Strip extends PrayerWidget {}

    public static final class Circle extends PrayerWidget {}

    static ComponentName component(Context context, String layout) {
        return new ComponentName(
                context,
                layout.equals("next")
                        ? Next.class
                        : layout.equals("strip") ? Strip.class
                        : layout.equals("circle") ? Circle.class : PrayerWidget.class);
    }

    static int[] ids(Context context) {
        java.util.List<Integer> all = new java.util.ArrayList<>();
        for (String layout : WidgetOptions.LAYOUTS)
            for (int id :
                    AppWidgetManager.getInstance(context)
                            .getAppWidgetIds(component(context, layout))) all.add(id);
        int[] ids = new int[all.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = all.get(i);
        return ids;
    }

    static String initialLayout(Context context, int id) {
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id);
        if (info != null)
            for (String layout : WidgetOptions.LAYOUTS)
                if (component(context, layout).equals(info.provider)) return layout;
        return "day";
    }

    static final String PINNED = "tv.miqat.companion.WIDGET_PINNED";
    private static final String ROLLOVER = "tv.miqat.companion.WIDGET_ROLLOVER";

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ROLLOVER.equals(intent.getAction())) updateAll(context);
        if (PINNED.equals(intent.getAction())) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (owns(context, id)
                    && WidgetOptions.from(context, intent.getStringExtra("options"))
                            .save(context, id)) update(context, id);
        }
    }

    static boolean owns(Context context, int id) {
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id);
        if (info != null)
            for (String layout : WidgetOptions.LAYOUTS)
                if (component(context, layout).equals(info.provider)) return true;
        return false;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, id);
        scheduleRollover(context, true);
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context, AppWidgetManager manager, int id, Bundle options) {
        update(context, id);
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        android.content.SharedPreferences.Editor editor = PhoneStore.preferences(context).edit();
        for (int id : ids) editor.remove("widget-" + id);
        editor.apply();
    }

    @Override
    public void onDisabled(Context context) {
        if (ids(context).length == 0) scheduleRollover(context, false);
    }

    static void update(Context context, int id) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        manager.updateAppWidget(id, responsive(context, WidgetOptions.read(context, id), manager.getAppWidgetOptions(id)));
    }

    static RemoteViews responsive(Context context, WidgetOptions options, Bundle bounds) {
        if (Build.VERSION.SDK_INT >= 31) {
            ArrayList<SizeF> sizes = bounds.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            if (sizes != null && !sizes.isEmpty()) {
                Map<SizeF, RemoteViews> layouts = new LinkedHashMap<>();
                for (Object item : sizes) {
                    if (!(item instanceof SizeF)) continue;
                    SizeF size = (SizeF) item;
                    if (!Float.isFinite(size.getWidth()) || !Float.isFinite(size.getHeight())
                            || size.getWidth() <= 0 || size.getHeight() <= 0) continue;
                    if (layouts.size() == 16) break;
                    layouts.put(size, WidgetRenderer.create(context, options, Math.round(size.getWidth()), Math.round(size.getHeight())));
                }
                if (!layouts.isEmpty()) return new RemoteViews(layouts);
            }
        }
        int minWidth = bounds.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                minHeight = bounds.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                maxWidth = bounds.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth),
                maxHeight = bounds.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight);
        if (minWidth <= 0 || minHeight <= 0)
            return WidgetRenderer.create(context, options, 0, 0);
        if (maxWidth <= 0) maxWidth = minWidth;
        if (maxHeight <= 0) maxHeight = minHeight;
        RemoteViews portrait = WidgetRenderer.create(context, options, minWidth, maxHeight);
        if (minWidth == maxWidth && minHeight == maxHeight) return portrait;
        return new RemoteViews(WidgetRenderer.create(context, options, maxWidth, minHeight), portrait);
    }

    static void updateAll(Context context) {
        try {
            int[] widgets = ids(context);
            scheduleRollover(context, widgets.length > 0);
            for (int id : widgets) update(context, id);
        } catch (RuntimeException unavailable) {
            android.util.Log.w(
                    "MiqatWidget", "Widget host update failed; the host may retry later");
        }
    }

    private static synchronized void scheduleRollover(Context context, boolean placed) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        int flags = PendingIntent.FLAG_IMMUTABLE | (placed ? 0 : PendingIntent.FLAG_NO_CREATE);
        PendingIntent refresh = PendingIntent.getBroadcast(context, 0,
                new Intent(context, PrayerWidget.class).setAction(ROLLOVER), flags);
        if (refresh == null) return;
        try {
            if (placed) {
                PhoneStore settings = PhoneStore.read(context);
                long now = System.currentTimeMillis();
                PrayerSelection next = settings.locationReady
                        ? PhonePrayerRules.INSTANCE.nextPrayer(settings.day(now, 0).times, settings.day(now, 1).times, now)
                        : null;
                if (next != null) {
                    long target = (long) next.getNextTime();
                    if (PhoneAlarms.exact(context)) {
                        try {
                            alarms.setExact(AlarmManager.RTC, target, refresh);
                            return;
                        } catch (SecurityException revoked) {
                            // Timing access can be revoked while settings are being applied.
                        }
                    }
                    alarms.set(AlarmManager.RTC, target, refresh);
                    return;
                }
            }
        } catch (Exception unavailable) {
            android.util.Log.w("MiqatWidget", "Cannot schedule widget prayer rollover");
        }
        alarms.cancel(refresh);
        refresh.cancel();
    }
}

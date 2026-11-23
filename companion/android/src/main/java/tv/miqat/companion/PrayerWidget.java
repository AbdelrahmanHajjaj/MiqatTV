package tv.miqat.companion;

import android.appwidget.*;
import android.content.*;
import android.os.Bundle;
import android.os.Build;
import android.util.SizeF;
import android.widget.RemoteViews;

import java.util.*;

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

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
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
            for (int id : ids(context)) update(context, id);
        } catch (RuntimeException unavailable) {
            android.util.Log.w(
                    "MiqatWidget", "Widget host update failed; the host may retry later");
        }
    }
}

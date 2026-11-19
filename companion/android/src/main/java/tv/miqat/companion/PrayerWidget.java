package tv.miqat.companion;

import android.appwidget.*;
import android.content.*;
import android.os.Bundle;

import java.util.*;

/** Offline widget rendering, refreshed by the existing prayer lifecycle and launcher. */
public class PrayerWidget extends AppWidgetProvider {
    // Android needs a receiver component per default size in the launcher picker.
    public static final class Next extends PrayerWidget {}

    public static final class Strip extends PrayerWidget {}

    static ComponentName component(Context context, String layout) {
        return new ComponentName(
                context,
                layout.equals("next")
                        ? Next.class
                        : layout.equals("strip") ? Strip.class : PrayerWidget.class);
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
        Bundle bounds = AppWidgetManager.getInstance(context).getAppWidgetOptions(id);
        WidgetOptions options = WidgetOptions.read(context, id);
        int height = bounds.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 260);
        if (height > 0 && height < 200 && options.layout.equals("day")) options.layout = "next";
        AppWidgetManager.getInstance(context)
                .updateAppWidget(id, WidgetRenderer.create(context, options, height));
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

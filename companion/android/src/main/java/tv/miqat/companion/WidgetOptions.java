package tv.miqat.companion;

import android.content.Context;

import org.json.JSONObject;

import java.util.Arrays;

/** Per-instance presentation only; prayer settings always come from PhoneStore. */
final class WidgetOptions {
    static final String[] LAYOUTS = {"next", "day", "strip"};
    String layout = "day", theme = "app";
    boolean background = true, date = true;
    int size = 100;

    static WidgetOptions read(Context context, int id) {
        String stored = PhoneStore.preferences(context).getString("widget-" + id, null);
        WidgetOptions options = from(context, stored == null ? "{}" : stored);
        if (stored == null) options.layout = PrayerWidget.initialLayout(context, id);
        return options;
    }

    static WidgetOptions from(Context context, String encoded) {
        WidgetOptions options = new WidgetOptions();
        try {
            JSONObject data = new JSONObject(encoded);
            String layout = data.optString("layout", "day"), theme = data.optString("theme", "app");
            if (Arrays.asList(LAYOUTS).contains(layout)) options.layout = layout;
            if (theme.equals("app") || new Catalog(context).data.getJSONObject("themes").has(theme))
                options.theme = theme;
            options.background = data.optBoolean("background", true);
            options.date = data.optBoolean("date", true);
            options.size = data.optInt("size") == 120 ? 120 : 100;
        } catch (Exception invalid) {
            /* Invalid presentation falls back without losing prayer settings. */
        }
        return options;
    }

    String encode() {
        try {
            return new JSONObject()
                    .put("layout", layout)
                    .put("theme", theme)
                    .put("background", background)
                    .put("date", date)
                    .put("size", size)
                    .toString();
        } catch (org.json.JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    boolean save(Context context, int id) {
        return PhoneStore.preferences(context).edit().putString("widget-" + id, encode()).commit();
    }
}

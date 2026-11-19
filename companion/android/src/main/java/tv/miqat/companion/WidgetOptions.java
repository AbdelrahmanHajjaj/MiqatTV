package tv.miqat.companion;

import android.content.Context;

import org.json.JSONObject;

import java.util.Arrays;

/** Per-instance presentation only; prayer settings always come from PhoneStore. */
final class WidgetOptions {
    static final String[] LAYOUTS = {"next", "day", "strip"};
    static final String[] SHAPES = {"rounded", "soft", "square"};
    String layout = "day", theme = "app", scene = "plain", shape = "rounded";
    boolean date = true, hijri;
    int size = 100, opacity = 100;

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
            String scene = data.optString("scene", "plain"),
                    shape = data.optString("shape", "rounded");
            PhoneData phone = PhoneData.get(context);
            if (scene.equals("app")
                    || phone.options(phone.controls.get("appearance.background")).contains(scene))
                options.scene = scene;
            if (Arrays.asList(SHAPES).contains(shape)) options.shape = shape;
            options.opacity =
                    Math.max(
                            0,
                            Math.min(
                                    100,
                                    data.optInt(
                                            "opacity",
                                            data.optBoolean("background", true) ? 100 : 0)));
            options.hijri = data.optBoolean("hijri", false);
            options.date = data.optBoolean("date", true);
            options.size = data.optInt("size") == 120 ? 120 : 100;
        } catch (Exception invalid) {
            /* Invalid presentation falls back without losing prayer settings. */
        }
        return options;
    }

    int height() {
        return layout.equals("day") ? 260 : layout.equals("next") ? 160 : 120;
    }

    String encode() {
        try {
            return new JSONObject()
                    .put("layout", layout)
                    .put("theme", theme)
                    .put("scene", scene)
                    .put("shape", shape)
                    .put("opacity", opacity)
                    .put("hijri", hijri)
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

package tv.miqat.companion;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.*;

import org.json.JSONObject;

/** Shared real artwork composition for the live prayer hero and appearance preview. */
final class PhoneAppearance {
    private final MainActivity a;
    private String artworkId = "";
    private PhoneBackdrop.Artwork artwork;

    PhoneAppearance(MainActivity activity) {
        a = activity;
    }

    LinearLayout hero(LinearLayout parent, PhoneStore settings) throws Exception {
        FrameLayout frame = new FrameLayout(a);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.bottomMargin = a.views.dp(16);
        parent.addView(frame, size);
        JSONObject palette =
                a.catalog
                        .data
                        .getJSONObject("themes")
                        .getJSONObject(settings.value("appearance.theme"))
                        .getJSONObject("colors");
        int ground = Color.parseColor(palette.getString("lapis-900")),
                surface = Color.parseColor(palette.getString("lapis-800"));
        String id = settings.value("appearance.background");
        if (a.page.equals("prayers") && settings.value("appearance.home-background").equals("false"))
            id = "plain";
        if (!id.equals("plain")) {
            if (!id.equals(artworkId)) {
                artwork = PhoneBackdrop.read(a, id);
                artworkId = id;
            }
        }
        LinearLayout content = a.views.column();
        content.setPadding(a.views.dp(20), a.views.dp(20), a.views.dp(20), a.views.dp(20));
        content.setBackground(
                new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] {
                            (ground & 0xffffff) | 0x99000000, (ground & 0xffffff) | 0xe5000000
                        }));
        frame.addView(content, new FrameLayout.LayoutParams(-1, -2));
        Drawable shape = a.views.background(surface, 28);
        frame.setBackground(
                id.equals("plain")
                        ? shape
                        : new LayerDrawable(new Drawable[] {shape, new PhoneBackdrop(artwork)}));
        frame.setClipToOutline(true);
        return content;
    }

    void background(LinearLayout shell, PhoneStore settings) {
        try {
            if (a.page.equals("phone-settings") && a.section.equals("appearance"))
                settings = candidate(settings);
            String id = settings.value("appearance.background");
            if (id.equals("plain")) {
                shell.setBackgroundColor(a.ground);
                return;
            }
            if (!id.equals(artworkId)) {
                artwork = PhoneBackdrop.read(a, id);
                artworkId = id;
            }
            shell.setBackground(
                    new LayerDrawable(
                            new Drawable[] {
                                new PhoneBackdrop(artwork),
                                new android.graphics.drawable.ColorDrawable(
                                        (a.ground & 0xffffff) | 0x66000000)
                            }));
        } catch (Exception unavailable) {
            android.util.Log.w("MiqatStyle", "Background unavailable", unavailable);
        }
    }

    void homeBackground(LinearLayout shell, PhoneStore settings) {
        background(shell, settings);
        if (!settings.value("appearance.background").equals("plain"))
            shell.setBackground(new LayerDrawable(new Drawable[] {
                    shell.getBackground(),
                    new android.graphics.drawable.ColorDrawable((a.ground & 0xffffff) | 0xb3000000)
            }));
    }

    void preview(LinearLayout parent, PhoneStore settings) throws Exception {
        LinearLayout content = hero(parent, settings);
        content.setTag("phone.appearance.preview");
        a.phoneUi.home.nextPrayer(content, System.currentTimeMillis(), false);
        JSONObject colors =
                a.catalog
                        .data
                        .getJSONObject("themes")
                        .getJSONObject(settings.value("appearance.theme"))
                        .getJSONObject("colors");
        for (String[] highlight :
                new String[][] {
                    {"label", "mist-400"}, {"name", "gold-400"},
                    {"time", "plaster-100"}, {"remaining", "gold-400"}
                }) {
            TextView text = content.findViewWithTag("phone.next." + highlight[0]);
            if (text != null) text.setTextColor(Color.parseColor(colors.getString(highlight[1])));
        }
    }

    private final java.util.Map<String, String> draft = new java.util.LinkedHashMap<>();
    private String tab = "appearance.theme";

    void save(android.os.Bundle state) {
        android.os.Bundle values = new android.os.Bundle();
        for (java.util.Map.Entry<String, String> entry : draft.entrySet())
            values.putString(entry.getKey(), entry.getValue());
        state.putBundle("style-draft", values);
        state.putString("style-tab", tab);
    }

    void restore(android.os.Bundle state) {
        android.os.Bundle values = state.getBundle("style-draft");
        if (values != null)
            for (String id : values.keySet())
                if (id.startsWith("appearance.") || id.startsWith("display."))
                    draft.put(id, values.getString(id));
        tab = state.getString("style-tab", "appearance.theme");
    }

    PhoneStore candidate(PhoneStore saved) throws Exception {
        return saved.changed(draft);
    }

    void stage(String id, String value) {
        java.util.Map<String, String> changed = new java.util.LinkedHashMap<>(draft);
        changed.put(id, value);
        try {
            a.phoneUi.settings.changed(changed);
            draft.put(id, value);
            a.phoneUi.pending = "";
            a.render();
        } catch (Exception invalid) {
            a.notice("invalid");
        }
    }

    void cancel() {
        draft.clear();
    }

    void editor(LinearLayout parent, boolean guided) throws Exception {
        PhoneStore candidate = a.phoneUi.settings;
        preview(parent, candidate);
        a.views.segmentedChoices(
                parent,
                "appearance.tab",
                java.util.Arrays.asList(
                        new Choices.Option("appearance.theme", a.t("widget_theme")),
                        new Choices.Option("appearance.background", a.t("widget_background"))),
                tab,
                selected -> {
                    tab = selected;
                    a.render();
                });
        rail(
                parent,
                tab,
                candidate.value(tab),
                false,
                selected -> {
                    if (guided) a.phoneUi.save(tab, selected);
                    else stage(tab, selected);
                });
        if (!guided) a.phoneUi.control(a.phoneUi.data.controls.get("appearance.home-background"));
        if (!guided && a.phoneUi.pending.isEmpty()) {
            LinearLayout actions = a.views.actionBar(a.actionHost);
            a.views
                    .secondaryAction(a.views.secondaryActions(actions), a.t("cancel"), a::back)
                    .setTag("appearance.cancel");
            a.views
                    .primaryAction(
                            actions,
                            a.t("apply"),
                            () -> {
                                boolean resize = draft.containsKey("display.size");
                                if (a.phoneUi.save(new java.util.LinkedHashMap<>(draft), false)) {
                                    a.back();
                                    if (resize) a.recreate();
                                }
                            })
                    .setTag("appearance.apply");
        }
    }

    void rail(
            LinearLayout parent,
            String id,
            String selected,
            boolean matchApp,
            java.util.function.Consumer<String> changed)
            throws Exception {
        java.util.List<String> options =
                new java.util.ArrayList<>(a.phoneUi.data.options(a.phoneUi.data.controls.get(id)));
        if (matchApp) options.add(0, "app");
        HorizontalScrollView scroll = new HorizontalScrollView(a);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setTag("phone." + id);
        LinearLayout track = new LinearLayout(a);
        track.setPadding(a.views.dp(2), a.views.dp(4), a.views.dp(2), a.views.dp(8));
        scroll.addView(track);
        parent.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        for (String option : options) {
            PhoneStore candidate =
                    option.equals("app")
                            ? a.phoneUi.settings
                            : a.phoneUi.settings.changed(
                                    java.util.Collections.singletonMap(id, option));
            LinearLayout tile = a.views.column();
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(a.views.dp(100), -2);
            size.setMarginEnd(a.views.dp(8));
            track.addView(tile, size);
            LinearLayout art = hero(tile, candidate);
            art.setMinimumHeight(a.views.dp(id.equals("appearance.theme") ? 42 : 60));
            art.setPadding(a.views.dp(6), a.views.dp(6), a.views.dp(6), a.views.dp(6));
            if (id.equals("appearance.theme")) {
                JSONObject colors = a.phoneUi.data.colors(candidate.value(id));
                LinearLayout swatches = a.views.horizontal(art);
                for (String color : new String[] {"gold-400", "plaster-100", "mist-400"}) {
                    View swatch = new View(a);
                    swatch.setBackground(
                            a.views.background(Color.parseColor(colors.getString(color)), 6));
                    LinearLayout.LayoutParams block =
                            new LinearLayout.LayoutParams(0, a.views.dp(18), 1);
                    block.setMarginEnd(a.views.dp(4));
                    swatches.addView(swatch, block);
                }
            }
            String label =
                    option.equals("app") ? a.t("widget_match_app") : a.phoneUi.value(id, option);
            TextView name = a.views.text(label, 12, a.ink);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tile.addView(name);
            tile.setBackground(a.views.background(a.surface, 16));
            tile.setPadding(a.views.dp(4), a.views.dp(4), a.views.dp(4), a.views.dp(6));
            tile.setTag("phone." + id + ".option." + option);
            tile.setContentDescription(label);
            tile.setFocusable(true);
            tile.setSelected(option.equals(selected));
            if (tile.isSelected()) {
                GradientDrawable border = a.views.background(Color.TRANSPARENT, 16);
                border.setStroke(a.views.dp(2), a.accent);
                tile.setForeground(border);
                scroll.post(() ->
                        scroll.scrollTo(
                                Math.max(
                                        0,
                                        tile.getLeft() - (scroll.getWidth() - tile.getWidth()) / 2),
                                0));
            }
            tile.setOnClickListener(v -> changed.accept(option));
        }
    }
}

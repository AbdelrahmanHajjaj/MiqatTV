package tv.miqat.companion;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.os.*;
import android.widget.*;

import java.util.*;

/** The same preview/customization for launcher configuration and the in-app widget gallery. */
final class WidgetUi {
    private final MainActivity a;
    private WidgetOptions draft;
    private int id = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean configuring;
    private String style = "appearance.theme";

    WidgetUi(MainActivity activity) {
        a = activity;
    }

    boolean configure(Intent intent) {
        if (!AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(intent.getAction())) return false;
        id =
                intent.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        configuring = true;
        a.setResult(android.app.Activity.RESULT_CANCELED);
        if (!PrayerWidget.owns(a, id)) {
            a.finish();
            return true;
        }
        draft = WidgetOptions.read(a, id);
        a.page = "widgets";
        return true;
    }

    boolean cancel() {
        draft = null;
        if (!configuring) {
            id = AppWidgetManager.INVALID_APPWIDGET_ID;
            return false;
        }
        a.finish();
        return true;
    }

    void save(Bundle state) {
        if (draft != null) state.putString("widget-draft", draft.encode());
        state.putInt("widget-edit-id", id);
        state.putString("widget-style-tab", style);
    }

    void restore(Bundle state) {
        style = state.getString("widget-style-tab", "appearance.theme");
        if (state.containsKey("widget-draft"))
            draft = WidgetOptions.from(a, state.getString("widget-draft"));
        int saved = state.getInt("widget-edit-id", AppWidgetManager.INVALID_APPWIDGET_ID);
        if (saved == AppWidgetManager.INVALID_APPWIDGET_ID || PrayerWidget.owns(a, saved))
            id = saved;
    }

    void render() {
        if (draft == null) draft = defaults();
        layouts();
        android.view.View preview = preview();
        appearance(preview);
        LinearLayout dates = a.views.horizontal(a.body);
        for (String key : new String[] {"widget_hijri", "widget_date"}) {
            Switch date =
                    a.views.toggle(
                            dates,
                            a.t(key),
                            key.equals("widget_date") ? draft.date : draft.hijri,
                            checked -> {
                                if (key.equals("widget_date")) draft.date = checked;
                                else draft.hijri = checked;
                                changed();
                            });
            date.setTag("widget." + key);
            date.setTextSize(13);
            a.views.flex(date);
        }
        a.body.addView(a.views.text(a.t("widget_text_size"), 13, a.muted));
        a.views.segmentedChoices(
                a.body,
                "widget.size",
                Arrays.asList(
                        new Choices.Option("100", a.phoneUi.digits("100%")),
                        new Choices.Option("120", a.phoneUi.digits("120%"))),
                Integer.toString(draft.size),
                value -> {
                    draft.size = Integer.parseInt(value);
                    changed();
                });
        if (!configuring) placed();
    }

    private void layouts() {
        a.body.addView(a.views.text(a.t("widget_initial_layout"), 13, a.muted));
        List<Choices.Option> layouts = new ArrayList<>();
        for (String layout : WidgetOptions.LAYOUTS)
            layouts.add(
                    new Choices.Option(
                            layout,
                            a.t("widget_" + (layout.equals("strip") ? "compact" : layout))));
        a.views.segmentedChoices(
                a.body,
                "widget.layout",
                layouts,
                draft.layout,
                selected -> {
                    draft.layout = selected;
                    changed();
                });
    }

    private int width() {
        return Math.max(
                140,
                Math.round(
                                a.getResources().getDisplayMetrics().widthPixels
                                        / a.getResources().getDisplayMetrics().density)
                        - 48);
    }

    private android.view.View preview() {
        LinearLayout preview = a.views.column();
        preview.setTag("widget.preview");
        preview.setPadding(a.views.dp(4), a.views.dp(8), a.views.dp(4), a.views.dp(8));
        int height =
                Math.round(
                        draft.height() * draft.size / 100f
                                * Math.max(
                                        1f,
                                        a.getApplicationContext()
                                                .getResources()
                                                .getConfiguration()
                                                .fontScale));
        android.view.View rendered =
                WidgetRenderer.initial(a.getApplicationContext(), draft, width(), height)
                        .apply(a.getApplicationContext(), preview);
        preview.addView(rendered, new LinearLayout.LayoutParams(-1, a.views.dp(height)));
        a.body.addView(preview);
        return rendered;
    }

    private void appearance(android.view.View preview) {
        a.views.segmentedChoices(
                a.body,
                "widget.style",
                Arrays.asList(
                        new Choices.Option("appearance.theme", a.t("widget_theme")),
                        new Choices.Option("appearance.background", a.t("widget_background")),
                        new Choices.Option("shape", a.t("widget_shape"))),
                style,
                selected -> {
                    style = selected;
                    a.render();
                });
        try {
            if (style.equals("shape")) {
                List<Choices.Option> shapes = new ArrayList<>();
                for (String shape : WidgetOptions.SHAPES)
                    shapes.add(new Choices.Option(shape, a.t("widget_shape_" + shape)));
                a.views.segmentedChoices(
                        a.body,
                        "widget.shape",
                        shapes,
                        draft.shape,
                        value -> {
                            draft.shape = value;
                            changed();
                        });
            } else
                a.phoneUi.appearance.rail(
                        a.body,
                        style,
                        style.equals("appearance.theme") ? draft.theme : draft.scene,
                        true,
                        selected -> {
                            if (style.equals("appearance.theme")) draft.theme = selected;
                            else draft.scene = selected;
                            changed();
                        });
        } catch (Exception unavailable) {
            a.notice("phone_settings_failed");
        }
        opacity(preview);
    }

    private void opacity(android.view.View preview) {
        TextView opacity =
                a.views.text(
                        String.format(
                                a.phoneUi.locale(),
                                a.t("widget_opacity_value"),
                                a.phoneUi.digits(Integer.toString(draft.opacity))),
                        14,
                        a.muted);
        a.body.addView(opacity);
        SeekBar slider = new SeekBar(a);
        slider.setId(android.view.View.generateViewId());
        opacity.setLabelFor(slider.getId());
        slider.setContentDescription(a.t("widget_opacity"));
        slider.setMax(100);
        slider.setProgress(draft.opacity);
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(a.accent));
        slider.setThumbTintList(android.content.res.ColorStateList.valueOf(a.accent));
        slider.setTag("widget.opacity");
        a.body.addView(slider, new LinearLayout.LayoutParams(-1, a.views.dp(48)));
        slider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    private boolean tracking;

                    public void onStartTrackingTouch(SeekBar bar) { tracking = true; }

                    public void onStopTrackingTouch(SeekBar bar) {
                        tracking = false;
                        if (!persist()) a.render();
                    }

                    public void onProgressChanged(SeekBar bar, int value, boolean user) {
                        draft.opacity = value;
                        opacity.setText(
                                String.format(
                                        a.phoneUi.locale(),
                                        a.t("widget_opacity_value"),
                                        a.phoneUi.digits(Integer.toString(value))));
                        ((ImageView) preview.findViewById(R.id.widget_backdrop))
                                .setImageAlpha(value * 255 / 100);
                        if (!tracking && !persist()) a.render();
                    }
                });
    }

    private void placed() {
        int[] ids = PrayerWidget.ids(a);
        if (ids.length == 0) return;
        a.views.button(
                a.body,
                a.t("widget_configure"),
                () -> {
                    List<Choices.Option> choices = new ArrayList<>();
                    for (int placed : ids)
                        choices.add(
                                new Choices.Option(
                                        Integer.toString(placed),
                                        a.t("phone_widgets") + " · " + placed));
                    a.phoneUi.picker(
                            a.t("widget_configure"),
                            choices,
                            selected -> {
                                id = Integer.parseInt(selected);
                                draft = WidgetOptions.read(a, id);
                                a.render();
                            });
                });
    }

    private WidgetOptions defaults() {
        return WidgetOptions.from(a, PhoneStore.preferences(a).getString("widget-default", "{}"));
    }

    // Confirm storage before updating a widget or completing Android Add.
    @android.annotation.SuppressLint("ApplySharedPref")
    private boolean persist() {
        boolean placed = id != AppWidgetManager.INVALID_APPWIDGET_ID;
        if (placed && !PrayerWidget.owns(a, id)) {
            a.notice("phone_save_failed");
            return false;
        }
        boolean saved = placed ? draft.save(a, id)
                : PhoneStore.preferences(a).edit().putString("widget-default", draft.encode()).commit();
        if (!saved) {
            draft = placed ? WidgetOptions.read(a, id) : defaults();
            a.notice("phone_save_failed");
            return false;
        }
        if (placed) PrayerWidget.update(a, id);
        return true;
    }

    private void changed() {
        persist();
        a.render();
    }

    void footer(LinearLayout shell) {
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && !configuring) return;
        LinearLayout actions = a.views.actionBar(shell);
        a.views.primaryAction(actions, a.t("widget_add"), this::add).setTag("widget.add");
    }

    private void add() {
        if (!persist()) return;
        if (configuring) {
            a.setResult(android.app.Activity.RESULT_OK,
                    new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
            a.finish();
            return;
        }
        AppWidgetManager manager = AppWidgetManager.getInstance(a);
        if (!manager.isRequestPinAppWidgetSupported()) {
            a.info(a.t("phone_widgets"), a.t("widget_manual"));
            return;
        }
        Intent pinned =
                new Intent(a, PrayerWidget.class)
                        .setAction(PrayerWidget.PINNED)
                        .putExtra("options", draft.encode());
        int flags =
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        PendingIntent callback =
                PendingIntent.getBroadcast(a, (int) SystemClock.uptimeMillis(), pinned, flags);
        Bundle extras = new Bundle();
        extras.putParcelable(
                AppWidgetManager.EXTRA_APPWIDGET_PREVIEW,
                WidgetRenderer.initial(a, draft, width(), draft.height()));
        manager.requestPinAppWidget(PrayerWidget.component(a, draft.layout), extras, callback);
    }
}

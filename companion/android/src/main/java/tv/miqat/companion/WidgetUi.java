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
    }

    void restore(Bundle state) {
        if (state.containsKey("widget-draft"))
            draft = WidgetOptions.from(a, state.getString("widget-draft"));
        int saved = state.getInt("widget-edit-id", -1);
        if (saved == -1 || PrayerWidget.owns(a, saved)) id = saved;
    }

    void render() {
        if (draft == null) draft = new WidgetOptions();
        layouts();
        android.view.View preview = preview();
        appearance(preview);
        toggle(
                "widget_date",
                draft.date,
                () -> {
                    draft.date = !draft.date;
                    a.render();
                });
        toggle(
                "widget_hijri",
                draft.hijri,
                () -> {
                    draft.hijri = !draft.hijri;
                    a.render();
                });
        choice(
                        "widget_text_size",
                        a.phoneUi.digits(draft.size + "%"),
                        () -> {
                            draft.size = draft.size == 100 ? 120 : 100;
                            a.render();
                        })
                .setTag("widget.size");
        if (!configuring) placed();
    }

    private void layouts() {
        LinearLayout variants = a.views.horizontal(a.body);
        for (String layout : WidgetOptions.LAYOUTS) {
            Button button =
                    a.views.button(
                            variants,
                            a.t("widget_" + (layout.equals("strip") ? "compact" : layout)),
                            () -> {
                                draft.layout = layout;
                                a.render();
                            });
            a.views.flex(button);
            button.setTextSize(12);
            button.setSelected(draft.layout.equals(layout));
            button.setAlpha(button.isSelected() ? 1f : .5f);
            button.setTag("widget.layout." + layout);
        }
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
                        draft.height()
                                * Math.max(
                                        1f,
                                        a.getApplicationContext()
                                                .getResources()
                                                .getConfiguration()
                                                .fontScale));
        android.view.View rendered =
                WidgetRenderer.create(a.getApplicationContext(), draft, width(), height)
                        .apply(a.getApplicationContext(), preview);
        preview.addView(rendered, new LinearLayout.LayoutParams(-1, a.views.dp(height)));
        a.body.addView(preview);
        return rendered;
    }

    private void appearance(android.view.View preview) {
        String themeLabel = a.t("widget_match_app"), backgroundLabel = a.t("widget_color");
        try {
            if (!draft.theme.equals("app"))
                themeLabel = a.phoneUi.value("appearance.theme", draft.theme);
            backgroundLabel =
                    draft.scene.equals("app")
                            ? a.t("widget_app_background")
                            : draft.scene.equals("plain")
                                    ? a.t("widget_color")
                                    : a.phoneUi.value("appearance.background", draft.scene);
        } catch (Exception unavailable) {
            a.notice("phone_settings_failed");
        }
        choice("widget_theme", themeLabel, this::chooseTheme).setTag("widget.theme");
        choice("widget_background", backgroundLabel, this::chooseBackground)
                .setTag("widget.background");
        choice("widget_shape", a.t("widget_shape_" + draft.shape), this::chooseShape)
                .setTag("widget.shape");
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
                    public void onStartTrackingTouch(SeekBar bar) {}

                    public void onStopTrackingTouch(SeekBar bar) {}

                    public void onProgressChanged(SeekBar bar, int value, boolean user) {
                        draft.opacity = value;
                        opacity.setText(
                                String.format(
                                        a.phoneUi.locale(),
                                        a.t("widget_opacity_value"),
                                        a.phoneUi.digits(Integer.toString(value))));
                        ((ImageView) preview.findViewById(R.id.widget_backdrop))
                                .setImageAlpha(value * 255 / 100);
                    }
                });
    }

    private Button choice(String labelKey, String value, Runnable edit) {
        LinearLayout row = a.views.horizontal(a.body);
        row.setPadding(0, a.views.dp(3), 0, a.views.dp(3));
        TextView label = a.views.text(a.t(labelKey), 14, a.ink);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        Button button = a.views.button(row, value + " ⌄", edit);
        a.views.flex(button);
        button.setTextSize(14);
        button.setMaxLines(2);
        button.setId(android.view.View.generateViewId());
        label.setLabelFor(button.getId());
        button.setContentDescription(a.t(labelKey) + ", " + value);
        return button;
    }

    private void chooseShape() {
        List<Choices.Option> choices = new ArrayList<>();
        for (String shape : WidgetOptions.SHAPES)
            choices.add(new Choices.Option(shape, a.t("widget_shape_" + shape)));
        a.phoneUi.picker(
                a.t("widget_shape"),
                choices,
                draft.shape,
                value -> {
                    draft.shape = value;
                    a.render();
                });
    }

    private void chooseTheme() {
        try {
            List<Choices.Option> choices = new ArrayList<>();
            choices.add(new Choices.Option("app", a.t("widget_match_app")));
            for (String theme :
                    a.phoneUi.data.options(a.phoneUi.data.controls.get("appearance.theme")))
                choices.add(new Choices.Option(theme, a.phoneUi.value("appearance.theme", theme)));
            a.phoneUi.picker(
                    a.t("widget_theme"),
                    choices,
                    draft.theme,
                    value -> {
                        draft.theme = value;
                        a.render();
                    });
        } catch (Exception invalid) {
            a.notice("invalid");
        }
    }

    private void chooseBackground() {
        try {
            List<Choices.Option> choices = new ArrayList<>();
            choices.add(new Choices.Option("app", a.t("widget_app_background")));
            for (String scene :
                    a.phoneUi.data.options(a.phoneUi.data.controls.get("appearance.background")))
                choices.add(
                        new Choices.Option(
                                scene,
                                scene.equals("plain")
                                        ? a.t("widget_color")
                                        : a.phoneUi.value("appearance.background", scene)));
            a.phoneUi.picker(
                    a.t("widget_background"),
                    choices,
                    draft.scene,
                    value -> {
                        draft.scene = value;
                        a.render();
                    });
        } catch (Exception invalid) {
            a.notice("invalid");
        }
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

    void footer(LinearLayout shell) {
        LinearLayout actions = a.views.actionBar(shell);
        a.views
                .secondaryAction(
                        a.views.secondaryActions(actions),
                        a.t("cancel"),
                        () -> {
                            if (!cancel()) a.tab("prayers");
                        })
                .setTag("widget.cancel");
        a.views
                .primaryAction(actions, a.t(id == -1 ? "widget_add" : "apply"), this::apply)
                .setTag("widget.apply");
    }

    private void toggle(String label, boolean checked, Runnable changed) {
        a.views
                .toggle(a.body, a.t(label), checked, value -> changed.run())
                .setTag("widget." + label);
    }

    private void apply() {
        if (id != -1) {
            if (!PrayerWidget.owns(a, id) || !draft.save(a, id)) {
                a.notice("phone_save_failed");
                return;
            }
            PrayerWidget.update(a, id);
            if (configuring) {
                a.setResult(
                        android.app.Activity.RESULT_OK,
                        new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
                a.finish();
            } else a.notice("widget_saved");
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
                WidgetRenderer.create(a, draft, width(), draft.height()));
        manager.requestPinAppWidget(PrayerWidget.component(a, draft.layout), extras, callback);
    }
}

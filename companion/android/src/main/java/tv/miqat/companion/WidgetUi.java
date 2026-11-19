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
        if (!configuring) return false;
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
        LinearLayout preview = a.views.column();
        preview.setTag("widget.preview");
        preview.setPadding(a.views.dp(4), a.views.dp(8), a.views.dp(4), a.views.dp(8));
        int height = draft.layout.equals("day") ? 260 : draft.layout.equals("next") ? 160 : 110;
        preview.addView(
                WidgetRenderer.create(a, draft, Integer.MAX_VALUE).apply(a, preview),
                new LinearLayout.LayoutParams(
                        -1,
                        a.views.dp(
                                height
                                        * Math.max(
                                                1f,
                                                a.getResources().getConfiguration().fontScale))));
        a.body.addView(preview);
        String themeLabel = a.t("phone_theme");
        try {
            if (!draft.theme.equals("app"))
                themeLabel = a.phoneUi.value("appearance.theme", draft.theme);
        } catch (Exception unavailable) {
            themeLabel = a.t("phone_theme");
        }
        a.views
                .navigationRow(
                        a.body,
                        "mihrab",
                        a.t("widget_theme"),
                        themeLabel,
                        () -> {
                            try {
                                List<Choices.Option> choices = new ArrayList<>();
                                choices.add(new Choices.Option("app", a.t("phone_theme")));
                                for (String theme :
                                        a.phoneUi.data.options(
                                                a.phoneUi.data.controls.get("appearance.theme")))
                                    choices.add(
                                            new Choices.Option(
                                                    theme,
                                                    a.phoneUi.value("appearance.theme", theme)));
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
                        })
                .setTag("widget.theme");
        toggle(
                "widget_opacity",
                draft.background,
                () -> {
                    draft.background = !draft.background;
                    a.render();
                });
        toggle(
                "widget_date",
                draft.date,
                () -> {
                    draft.date = !draft.date;
                    a.render();
                });
        a.views
                .button(
                        a.body,
                        a.t("widget_text_size") + " · " + a.phoneUi.digits(draft.size + "%"),
                        () -> {
                            draft.size = draft.size == 100 ? 120 : 100;
                            a.render();
                        })
                .setTag("widget.size");
        if (!configuring) {
            int[] ids = PrayerWidget.ids(a);
            if (ids.length > 0)
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
        a.views
                .button(a.body, a.t(id == -1 ? "widget_add" : "apply"), this::apply)
                .setTag("widget.apply");
        if (id == -1) a.body.addView(a.views.text(a.t("widget_manual"), 12, a.muted));
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
                WidgetRenderer.create(a, draft, Integer.MAX_VALUE));
        manager.requestPinAppWidget(PrayerWidget.component(a, draft.layout), extras, callback);
    }
}

package tv.miqat.companion;

import android.Manifest;
import android.app.Instrumentation;
import android.appwidget.*;
import android.content.*;
import android.os.Build;
import android.view.*;
import android.widget.*;

import java.util.Collections;

/** Real launcher binding, per-instance configuration/cancel and update callbacks. */
final class WidgetHostCheck {
    static void run(Instrumentation test, MainActivity a, boolean capture) throws Exception {
        if (Build.VERSION.SDK_INT < 29)
            throw new IllegalStateException("Widget host fixture requires API 29+");
        PhoneStore original = PhoneStore.read(a);
        AppWidgetHost[] host = {null};
        AppWidgetHostView[] views = new AppWidgetHostView[4];
        int[] ids = new int[4];
        MainActivity[] editor = {null};
        test.getUiAutomation().adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET);
        try {
            PhoneUiCheck.on(
                    test,
                    () -> {
                        a.body.removeAllViews();
                        host[0] = new AppWidgetHost(a, 41939);
                        host[0].startListening();
                        AppWidgetManager manager = AppWidgetManager.getInstance(a);
                        for (int i = 0; i < ids.length; i++) {
                            ids[i] = host[0].allocateAppWidgetId();
                            PhoneUiCheck.check(
                                    manager.bindAppWidgetIdIfAllowed(
                                            ids[i],
                                            PrayerWidget.component(
                                                    a, i == 2 ? "next" : i == 3 ? "strip" : "day")),
                                    "System rejected test-owned widget binding");
                            views[i] =
                                    host[0].createView(a, ids[i], manager.getAppWidgetInfo(ids[i]));
                            a.body.addView(
                                    views[i], new LinearLayout.LayoutParams(-1, a.views.dp(340)));
                            PrayerWidget.update(a, ids[i]);
                        }
                    });
            LiveUiCheck.untilUi(
                    test,
                    () -> {
                        LinearLayout rows = views[0].findViewById(R.id.widget_prayers);
                        return rows != null && rows.getChildCount() == 6;
                    });
            LiveUiCheck.untilUi(
                    test,
                    () ->
                            views[2].findViewById(R.id.widget_next).getVisibility() == View.VISIBLE
                                    && views[2].findViewById(R.id.widget_prayers).getVisibility()
                                            == View.GONE);
            LiveUiCheck.untilUi(
                    test,
                    () -> {
                        LinearLayout strip = views[3].findViewById(R.id.widget_strip);
                        return strip != null
                                && strip.getVisibility() == View.VISIBLE
                                && strip.getChildCount() == 6;
                    });
            pickerPreviews(test, a, ids, capture);
            WidgetOptions legacy = WidgetOptions.from(a, "{\"background\":false}");
            WidgetOptions invalid = WidgetOptions.from(a, "{\"opacity\":999,\"shape\":\"unknown\",\"scene\":\"../private\"}");
            PhoneUiCheck.check(legacy.opacity == 0 && invalid.opacity == 100
                            && invalid.shape.equals("rounded") && invalid.scene.equals("plain"),
                    "Widget presentation migration/bounds accepted invalid options");
            MainActivity cancelled =
                    (MainActivity)
                            test.startActivitySync(
                                    new Intent(a, MainActivity.class)
                                            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ids[0])
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            editor[0] = cancelled;
            PhoneUiCheck.on(
                    test,
                    () -> {
                        cancelled.body.findViewWithTag("widget.layout.next").performClick();
                        cancelled.back();
                    });
            LiveUiCheck.untilUi(test, cancelled::isDestroyed);
            editor[0] = null;
            PhoneUiCheck.check(
                    WidgetOptions.read(a, ids[0]).layout.equals("day"),
                    "Cancelled widget draft persisted");
            MainActivity config =
                    (MainActivity)
                            test.startActivitySync(
                                    new Intent(a, MainActivity.class)
                                            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ids[0])
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            editor[0] = config;
            PhoneUiCheck.on(
                    test,
                    () -> {
                        config.body.findViewWithTag("widget.layout.next").performClick();
                        config.body.findViewWithTag("widget.size").performClick();
                        ((SeekBar) config.body.findViewWithTag("widget.opacity")).setProgress(42);
                        PhoneUiCheck.check(((ImageView) config.body.findViewById(R.id.widget_backdrop)).getImageAlpha() == 107
                                        && config.body.findViewById(R.id.widget_root).getAlpha() == 1f,
                                "Opacity must affect only the widget background");
                        config.body.findViewWithTag("widget.widget_hijri").performClick();
                        config.body.findViewWithTag("widget.shape").performClick();
                        config.phoneUi.choiceDialog.getWindow().getDecorView().<ListView>findViewWithTag("phone.choice.list").performItemClick(null, 1, 1);
                        config.body.findViewWithTag("widget.background").performClick();
                        config.phoneUi.choiceDialog.getWindow().getDecorView().<ListView>findViewWithTag("phone.choice.list").performItemClick(null, 0, 0);
                        config.getWindow().getDecorView().findViewWithTag("widget.apply").performClick();
                    });
            LiveUiCheck.untilUi(test, config::isDestroyed);
            editor[0] = null;
            LiveUiCheck.untilUi(
                    test,
                    () -> views[0].findViewById(R.id.widget_prayers).getVisibility() == View.GONE);
            PhoneUiCheck.on(
                    test,
                    () -> {
                        TextView time = views[0].findViewById(R.id.widget_next_time);
                        PhoneUiCheck.check(
                                !time.getText().toString().isEmpty(),
                                "Configured next-prayer widget has no time");
                        timetable(views[1], a.phoneUi.data);
                    });
            PhoneUiCheck.capture(
                    test, a, (a.ar ? "ar" : "en") + "-widget-host-independent", capture);
            WidgetOptions saved = WidgetOptions.read(a, ids[0]);
            PhoneUiCheck.check(
                    saved.layout.equals("next")
                            && saved.size == 120 && saved.opacity == 42 && saved.hijri
                            && saved.shape.equals("soft") && saved.scene.equals("app")
                            && WidgetOptions.read(a, ids[1]).layout.equals("day")
                            && WidgetOptions.read(a, ids[1]).opacity == 100
                            && !WidgetOptions.read(a, ids[1]).hijri,
                    "Widget instances did not retain independent configuration");
            String hijri = a.phoneUi.digits(PhoneTime.hijri(PhoneStore.read(a), System.currentTimeMillis(), a.ar));
            PhoneUiCheck.on(test, () -> {
                PhoneUiCheck.check(((TextView) views[0].findViewById(R.id.widget_title)).getText().toString().contains(hijri),
                        "Optional Hijri date is missing from the actual hosted widget");
                ImageView background = views[0].findViewById(R.id.widget_backdrop);
                android.graphics.Bitmap bitmap = ((android.graphics.drawable.BitmapDrawable) background.getDrawable()).getBitmap();
                PhoneUiCheck.check(background.getImageAlpha() == 107
                                && android.graphics.Color.alpha(bitmap.getPixel(0, 0)) == 0
                                && bitmap.getByteCount() < 1024 * 1024,
                        "Hosted widget lost opacity/shape or exceeded a bounded image parcel");
                TextView name = views[1].findViewById(R.id.widget_prayer_name);
                TextView prayerTime = views[1].findViewById(R.id.widget_prayer_time);
                PhoneUiCheck.check(name.getTypeface().equals(a.getResources().getFont(R.font.alexandria_regular))
                                && prayerTime.getTypeface().equals(a.getResources().getFont(R.font.alexandria_semibold)),
                        "Hosted widget did not retain app typography");
            });
            PhoneUiCheck.on(
                    test,
                    () -> {
                        android.os.Bundle small =
                                AppWidgetManager.getInstance(a).getAppWidgetOptions(ids[1]);
                        small.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160);
                        AppWidgetManager.getInstance(a).updateAppWidgetOptions(ids[1], small);
                    });
            LiveUiCheck.untilUi(
                    test,
                    () -> views[1].findViewById(R.id.widget_prayers).getVisibility() == View.GONE);
            PhoneUiCheck.check(
                    WidgetOptions.read(a, ids[1]).layout.equals("day"),
                    "Resize overwrote chosen widget layout");
            PhoneUiCheck.on(
                    test,
                    () -> {
                        android.os.Bundle large =
                                AppWidgetManager.getInstance(a).getAppWidgetOptions(ids[1]);
                        large.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 330);
                        AppWidgetManager.getInstance(a).updateAppWidgetOptions(ids[1], large);
                    });
            LiveUiCheck.untilUi(
                    test,
                    () ->
                            views[1].findViewById(R.id.widget_prayers).getVisibility()
                                    == View.VISIBLE);
            MainActivity strip =
                    (MainActivity)
                            test.startActivitySync(
                                    new Intent(a, MainActivity.class)
                                            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ids[1])
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            editor[0] = strip;
            PhoneUiCheck.on(
                    test,
                    () -> {
                        strip.body.findViewWithTag("widget.layout.strip").performClick();
                        strip.getWindow().getDecorView().findViewWithTag("widget.apply").performClick();
                    });
            LiveUiCheck.untilUi(test, strip::isDestroyed);
            editor[0] = null;
            LiveUiCheck.untilUi(
                    test,
                    () -> {
                        LinearLayout cells = views[1].findViewById(R.id.widget_strip);
                        return cells != null
                                && cells.getVisibility() == View.VISIBLE
                                && cells.getChildCount() == 6;
                    });
            PhoneUiCheck.check(
                    WidgetOptions.read(a, ids[0]).layout.equals("next"),
                    "Editing the second widget changed the first");
            PhoneUiCheck.capture(test, a, (a.ar ? "ar" : "en") + "-widget-host-strip", capture);
            original.changed(Collections.singletonMap("prayer.location-ready", "false")).save(a);
            PrayerWidget.updateAll(a);
            for (AppWidgetHostView view : views)
                LiveUiCheck.untilUi(
                        test,
                        () -> {
                            TextView text = view.findViewById(R.id.widget_times);
                            return text != null
                                    && text.getVisibility() == View.VISIBLE
                                    && text.getText()
                                            .toString()
                                            .equals(a.t("phone_location_needed"));
                        });
        } finally {
            if (editor[0] != null) PhoneUiCheck.on(test, editor[0]::finish);
            PhoneUiCheck.on(
                    test,
                    () -> {
                        if (host[0] != null) {
                            for (int id : ids) if (id != 0) host[0].deleteAppWidgetId(id);
                            host[0].stopListening();
                        }
                        for (AppWidgetHostView view : views)
                            if (view != null) a.body.removeView(view);
                    });
            test.getUiAutomation().dropShellPermissionIdentity();
            original.save(a);
            PrayerWidget.updateAll(a);
        }
    }

    private static void pickerPreviews(Instrumentation test, MainActivity a, int[] ids, boolean capture) throws Exception {
        for (int index : new int[] {0, 2, 3}) {
            String layout = index == 0 ? "day" : index == 2 ? "next" : "strip";
            AppWidgetProviderInfo info = AppWidgetManager.getInstance(a).getAppWidgetInfo(ids[index]);
            android.graphics.Bitmap[] image = {null};
            PhoneUiCheck.on(test, () -> {
                android.view.View preview = new RemoteViews(a.getPackageName(), info.previewLayout).apply(a, new FrameLayout(a));
                TextView prayer = preview.findViewById(layout.equals("next") ? R.id.widget_next_label : R.id.widget_prayer_name);
                TextView time = preview.findViewById(layout.equals("next") ? R.id.widget_next_time : R.id.widget_prayer_time);
                PhoneUiCheck.check(prayer != null && !prayer.getText().toString().isEmpty()
                                && time != null && !time.getText().toString().isEmpty(),
                        "Android picker preview is empty for " + layout);
                if (!capture) return;
                preview.setLayoutDirection(a.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
                int width = a.views.dp(layout.equals("strip") ? 320 : layout.equals("next") ? 160 : 280);
                int height = a.views.dp(layout.equals("day") ? 260 : layout.equals("next") ? 140 : 120);
                preview.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY));
                preview.layout(0, 0, width, height);
                image[0] = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
                preview.draw(new android.graphics.Canvas(image[0]));
            });
            if (image[0] != null) {
                try (java.io.FileOutputStream file = new java.io.FileOutputStream(new java.io.File(a.getExternalFilesDir(null),
                        (a.ar ? "ar" : "en") + "-picker-" + layout + ".png"))) {
                    image[0].compress(android.graphics.Bitmap.CompressFormat.PNG, 100, file);
                } finally { image[0].recycle(); }
            }
        }
    }

    static void timetable(View root, PhoneData data) {
        LinearLayout rows = root.findViewById(R.id.widget_prayers);
        PhoneUiCheck.check(
                rows != null && rows.getVisibility() == View.VISIBLE && rows.getChildCount() == 6,
                "Widget timetable must show six prayer rows");
        for (int i = 0; i < 6; i++) {
            View row = rows.getChildAt(i);
            for (int id : new int[] {R.id.widget_prayer_name, R.id.widget_prayer_time}) {
                TextView text = row.findViewById(id);
                PhoneUiCheck.check(
                        text != null && !text.getText().toString().isEmpty(),
                        "Widget row missing name/time");
                if (text.getLayout() != null)
                    PhoneUiCheck.check(
                            text.getLayout().getHeight()
                                    <= text.getHeight()
                                            - text.getCompoundPaddingTop()
                                            - text.getCompoundPaddingBottom(),
                            "Widget row text clipped");
            }
        }
    }
}

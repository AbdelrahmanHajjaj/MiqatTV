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
                            android.os.Bundle initial = new android.os.Bundle();
                            setBounds(initial, i == 2 ? 160 : i == 3 ? 400 : 280,
                                    i == 2 ? 160 : i == 3 ? 140 : 330);
                            manager.updateAppWidgetOptions(ids[i], initial);
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
            for (int id : ids) {
                WidgetOptions defaults = WidgetOptions.read(a, id);
                PhoneUiCheck.check(defaults.hijri && !defaults.date,
                        "New widgets must default to Hijri without Gregorian date");
            }
            responsivePreviews(test, a, capture);
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
                        for (String layout : WidgetOptions.LAYOUTS) {
                            cancelled.body.findViewWithTag("widget.layout." + layout).performClick();
                            PhoneUiCheck.check((cancelled.body.findViewById(R.id.widget_strip).getVisibility()==View.VISIBLE)==layout.equals("strip")
                                    && (cancelled.body.findViewById(R.id.widget_prayers).getVisibility()==View.VISIBLE)==layout.equals("day"),
                                    "Initial presentation selector previewed another widget: "+layout);
                        }
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
                        config.body.findViewWithTag("widget.size.120").performClick();
                        ((SeekBar) config.body.findViewWithTag("widget.opacity")).setProgress(42);
                        PhoneUiCheck.check(((ImageView) config.body.findViewById(R.id.widget_backdrop)).getImageAlpha() == 107
                                        && config.body.findViewById(R.id.widget_root).getAlpha() == 1f,
                                "Opacity must affect only the widget background");
                        config.body.findViewWithTag("widget.widget_hijri").performClick();
                        config.body.findViewWithTag("widget.widget_date").performClick();
                        config.body.findViewWithTag("widget.style.shape").performClick();
                        config.body.findViewWithTag("widget.shape.soft").performClick();
                        config.body.findViewWithTag("widget.style.appearance.background").performClick();
                        config.body.findViewWithTag("phone.appearance.background.option.app").performClick();
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
                            && saved.size == 120 && saved.opacity == 42 && !saved.hijri && saved.date
                            && saved.shape.equals("soft") && saved.scene.equals("app")
                            && WidgetOptions.read(a, ids[1]).layout.equals("day")
                            && WidgetOptions.read(a, ids[1]).opacity == 100
                            && WidgetOptions.read(a, ids[1]).hijri && !WidgetOptions.read(a, ids[1]).date,
                    "Widget instances did not retain independent configuration");
            String hijri = a.phoneUi.digits(PhoneTime.hijri(PhoneStore.read(a), System.currentTimeMillis(), a.ar));
            PhoneUiCheck.on(test, () -> {
                PhoneUiCheck.check(((TextView) views[1].findViewById(R.id.widget_title)).getText().toString().contains(hijri),
                        "Default Hijri date is missing from the actual hosted widget");
                java.text.SimpleDateFormat date = new java.text.SimpleDateFormat(
                        "EEE d MMM", new java.util.Locale(a.ar ? "ar" : "en"));
                date.setTimeZone(original.zone());
                String gregorian = a.phoneUi.digits(date.format(new java.util.Date()));
                PhoneUiCheck.check(((TextView) views[0].findViewById(R.id.widget_title))
                                .getText().toString().contains(gregorian),
                        "Opting into Gregorian did not update the actual hosted widget");
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
                        setBounds(small, 280, 160);
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
                        setBounds(large, 280, 330);
                        AppWidgetManager.getInstance(a).updateAppWidgetOptions(ids[1], large);
                    });
            LiveUiCheck.untilUi(
                    test,
                    () ->
                            views[1].findViewById(R.id.widget_prayers).getVisibility()
                                    == View.VISIBLE);
            PhoneUiCheck.on(test, () -> {
                android.os.Bundle wide = AppWidgetManager.getInstance(a).getAppWidgetOptions(ids[1]);
                setBounds(wide, 400, 140);
                AppWidgetManager.getInstance(a).updateAppWidgetOptions(ids[1], wide);
            });
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

    private static void setBounds(android.os.Bundle bounds, int width, int height) {
        bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width);
        bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width);
        bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height);
        bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height);
        if (Build.VERSION.SDK_INT >= 31) {
            java.util.ArrayList<android.util.SizeF> sizes = new java.util.ArrayList<>();
            sizes.add(new android.util.SizeF(width, height));
            bounds.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, sizes);
        }
    }

    private static void responsivePreviews(Instrumentation test, MainActivity a, boolean capture) throws Exception {
        android.graphics.Bitmap[] stripImage = {null};
        PhoneUiCheck.on(test, () -> {
            WidgetOptions style = new WidgetOptions();
            style.layout="next";style.opacity=42;style.shape="soft";style.theme="frost";
            String saved = style.encode();
            android.content.Context context = a.getApplicationContext();
            float font = Math.max(1f,context.getResources().getConfiguration().fontScale);
            for (int[] size : new int[][] {{140,500,0},{Math.round(500*font),Math.round(150*font),1},
                    {Math.round(500*font),Math.round(250*font),0},
                    {Math.round(300*font),Math.round(400*font),2}}) {
                android.os.Bundle bounds = new android.os.Bundle();
                setBounds(bounds,size[0],size[1]);
                View rendered = PrayerWidget.responsive(context,style,bounds).apply(context,new FrameLayout(a));
                int width = a.views.dp(size[0]), height = a.views.dp(size[1]);
                rendered.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
                rendered.layout(0,0,width,height);
                boolean strip = size[2] == 1, daily = size[2] == 2;
                PhoneUiCheck.check((rendered.findViewById(R.id.widget_strip).getVisibility()==View.VISIBLE)==strip
                        && (rendered.findViewById(R.id.widget_prayers).getVisibility()==View.VISIBLE)==daily,
                        "Allocated widget dimensions did not select its presentation: "+size[0]+"x"+size[1]);
                if(daily||strip) {
                    LinearLayout prayers=rendered.findViewById(strip?R.id.widget_strip:R.id.widget_prayers);
                    PhoneUiCheck.check(prayers.getChildCount()==6,"Responsive timetable lost prayer rows");
                    int outlined=0;
                    for(int i=0;i<6;i++) {
                        View row=prayers.getChildAt(i);
                        PhoneUiCheck.check(((ImageView)row.findViewById(R.id.widget_prayer_icon)).getDrawable()!=null,
                                "Responsive prayer row lacks canonical icon");
                        if(row.findViewById(R.id.widget_prayer_highlight).getVisibility()==View.VISIBLE)outlined++;
                    }
                    PhoneUiCheck.check(outlined<=1,"Widget highlighted more than one next prayer");
                } else PhoneUiCheck.check(((ImageView)rendered.findViewById(R.id.widget_next_icon)).getDrawable()!=null,
                        "Next-prayer widget lacks its canonical prayer icon");
                PhoneUiCheck.check(((ImageView)rendered.findViewById(R.id.widget_backdrop)).getImageAlpha()==107
                        && style.encode().equals(saved),"Responsive rendering overwrote per-instance style or initial presentation");
                if(Build.VERSION.SDK_INT>=31)bounds.remove(AppWidgetManager.OPTION_APPWIDGET_SIZES);
                View legacy=PrayerWidget.responsive(context,style,bounds).apply(context,new FrameLayout(a));
                PhoneUiCheck.check((legacy.findViewById(R.id.widget_strip).getVisibility()==View.VISIBLE)==strip
                        && (legacy.findViewById(R.id.widget_prayers).getVisibility()==View.VISIBLE)==daily,
                        "Legacy min/max widget bounds did not select the same presentation");
            }
            try { stripImage[0] = stripDatesAndHeight(a, capture); minimumNext(a); } catch (Exception invalid) { throw new AssertionError(invalid); }
            View wide=WidgetRenderer.create(context,new WidgetOptions(),Math.round(363*font),Math.round(200*font)).apply(context,new FrameLayout(a));
            int wideWidth=a.views.dp(Math.round(363*font)),wideHeight=a.views.dp(Math.round(200*font));
            wide.measure(View.MeasureSpec.makeMeasureSpec(wideWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(wideHeight,View.MeasureSpec.EXACTLY));
            wide.layout(0,0,wideWidth,wideHeight);
            TextView wideName=wide.findViewById(R.id.widget_next_label),wideTime=wide.findViewById(R.id.widget_next_time);
            android.graphics.Rect nameBounds=new android.graphics.Rect(),timeBounds=new android.graphics.Rect();
            wideName.getDrawingRect(nameBounds);wideTime.getDrawingRect(timeBounds);
            ((ViewGroup)wide).offsetDescendantRectToMyCoords(wideName,nameBounds);
            ((ViewGroup)wide).offsetDescendantRectToMyCoords(wideTime,timeBounds);
            PhoneUiCheck.check(wide.findViewById(R.id.widget_next_wide_row)!=null
                    && nameBounds.centerY()==timeBounds.centerY()&&!android.graphics.Rect.intersects(nameBounds,timeBounds),
                    "Wide next card must put the prominent prayer name and time on one row");
            View nextCard = wide.findViewById(R.id.widget_next);
            PhoneUiCheck.check(wide.findViewById(R.id.widget_next_highlight).getVisibility()==View.GONE
                    && nextCard.getHeight()>wideHeight*0.7f
                    && wide.findViewById(R.id.widget_next_wide_row).getHeight()>a.views.dp(70),
                    "Next-prayer card must use allocated height without an inset golden outline");
            android.graphics.Typeface heading=context.getResources().getFont(a.ar?R.font.amiri_bold:R.font.marcellus_regular);
            PhoneUiCheck.check(wideName.getTypeface().equals(heading)&&wideTime.getTypeface().equals(heading)
                    && !((TextView)wide.findViewById(R.id.widget_next_remaining)).getText().toString().isEmpty(),
                    "Wide next card lost canonical heading fonts or real remaining time");
            for(String initial : WidgetOptions.LAYOUTS) {
                style.layout=initial;
                View selected=WidgetRenderer.initial(context,style,363,style.height()).apply(context,new FrameLayout(a));
                PhoneUiCheck.check((selected.findViewById(R.id.widget_strip).getVisibility()==View.VISIBLE)==initial.equals("strip")
                        && (selected.findViewById(R.id.widget_prayers).getVisibility()==View.VISIBLE)==initial.equals("day"),
                        "Initial picker preview adapted away from selected presentation "+initial);
            }
            if(Build.VERSION.SDK_INT>=31) {
                android.os.Bundle bounds=new android.os.Bundle();setBounds(bounds,300,400);
                java.util.ArrayList<android.util.SizeF> sizes=new java.util.ArrayList<>();
                sizes.add(new android.util.SizeF(140,100));sizes.add(new android.util.SizeF(300,400));
                bounds.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,sizes);
                View smallest=PrayerWidget.responsive(context,style,bounds).apply(context,new FrameLayout(a));
                PhoneUiCheck.check(smallest.findViewById(R.id.widget_prayers).getVisibility()==View.GONE,
                        "Modern RemoteViews ignored exact host sizes and their smallest-size fallback");
            }
        });
        if (stripImage[0] != null) {
            try (java.io.FileOutputStream file = new java.io.FileOutputStream(
                    new java.io.File(a.getExternalFilesDir(null),
                            (a.ar ? "ar" : "en") + "-strip-both-dates.png"))) {
                stripImage[0].compress(android.graphics.Bitmap.CompressFormat.PNG, 100, file);
            } finally { stripImage[0].recycle(); }
        }
    }

    private static android.graphics.Bitmap stripDatesAndHeight(MainActivity a, boolean capture) throws Exception {
        android.graphics.Bitmap image = null;
        for (float font : new float[] {1f, 1.5f, 2f}) {
            android.content.res.Configuration config = new android.content.res.Configuration(
                    a.getApplicationContext().getResources().getConfiguration());
            config.fontScale = font;
            android.content.Context context = a.getApplicationContext().createConfigurationContext(config);
            PhoneStore settings = PhoneStore.read(context);
            boolean ar = PhoneAlarms.arabic(context);
            java.util.Locale locale = new java.util.Locale(ar ? "ar" : "en");
            java.text.SimpleDateFormat date = new java.text.SimpleDateFormat("EEE d MMM", locale);
            date.setTimeZone(settings.zone());
            long now = System.currentTimeMillis();
            String hijri = a.phoneUi.digits(PhoneTime.hijri(settings, now, ar));
            String gregorian = a.phoneUi.digits(date.format(new java.util.Date(now)));
            for (int size : new int[] {100, 120}) for (int height : new int[] {110, 140, 179}) {
                WidgetOptions options = new WidgetOptions();
                options.layout = "strip";
                options.size = size;
                float scale = font * size / 100f;
                int widthDp = (int) Math.ceil(360 * scale), heightDp = (int) Math.ceil(height * scale);
                for (int dates = 0; dates < 4; dates++) {
                    options.hijri = (dates & 1) != 0;
                    options.date = (dates & 2) != 0;
                    android.os.Bundle bounds = new android.os.Bundle();
                    setBounds(bounds, widthDp, heightDp);
                    for (boolean legacy : new boolean[] {false, true}) {
                        if (legacy) bounds.remove(AppWidgetManager.OPTION_APPWIDGET_SIZES);
                        View rendered = PrayerWidget.responsive(context, options, bounds)
                                .apply(context, new FrameLayout(context));
                        int width = a.views.dp(widthDp), allocatedHeight = a.views.dp(heightDp);
                        rendered.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(allocatedHeight, View.MeasureSpec.EXACTLY));
                        rendered.layout(0, 0, width, allocatedHeight);
                        TextView title = rendered.findViewById(R.id.widget_title);
                        String metadata = title.getText().toString();
                        PhoneUiCheck.check(metadata.contains(hijri) == options.hijri
                                        && metadata.contains(gregorian) == options.date,
                                "Strip date toggles lost independent visibility: " + metadata);
                        String fixture = " ar=" + ar + " font=" + font + " size=" + size
                                + " bounds=" + widthDp + "x" + heightDp + " dates=" + dates
                                + " legacy=" + legacy;
                        textInside(rendered, title, fixture);
                        LinearLayout strip = rendered.findViewById(R.id.widget_strip);
                        LinearLayout content = rendered.findViewById(R.id.widget_content);
                        PhoneUiCheck.check(strip.getVisibility() == View.VISIBLE && strip.getChildCount() == 6
                                        && strip.getBottom() == content.getHeight() - content.getPaddingBottom(),
                                "Prayer strip left unused space below its columns");
                        for (int i = 0; i < 6; i++) {
                            View column = strip.getChildAt(i);
                            PhoneUiCheck.check(column.getHeight() == strip.getHeight(),
                                    "Prayer column did not fill the available strip height");
                            textInside(rendered, column.findViewById(R.id.widget_prayer_name), fixture);
                            textInside(rendered, column.findViewById(R.id.widget_prayer_time), fixture);
                        }
                        PhoneUiCheck.check(rendered.findViewById(R.id.widget_next).getVisibility() == View.GONE
                                        && rendered.findViewById(R.id.widget_updated).getVisibility() == View.GONE,
                                "Prayer strip gained a timer or update footer");
                        if (capture && font == 1f && size == 100 && height == 140 && dates == 3 && !legacy) {
                            image = android.graphics.Bitmap.createBitmap(width, allocatedHeight,
                                    android.graphics.Bitmap.Config.ARGB_8888);
                            rendered.draw(new android.graphics.Canvas(image));
                        }
                    }
                }
            }
        }
        return image;
    }

    private static void textInside(View root, TextView text, String fixture) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        text.getDrawingRect(bounds);
        ((ViewGroup) root).offsetDescendantRectToMyCoords(text, bounds);
        PhoneUiCheck.check(text.getWidth() > 0 && text.getHeight() > 0
                        && new android.graphics.Rect(0, 0, root.getWidth(), root.getHeight()).contains(bounds)
                        && text.getLayout() != null
                        && text.getLayout().getHeight() <= text.getHeight()
                                - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom(),
                "Strip clips date or prayer text: " + text.getText() + fixture
                        + " bounds=" + bounds + " textHeight=" + text.getHeight()
                        + " layoutHeight=" + (text.getLayout() == null ? -1 : text.getLayout().getHeight()));
        for (int line = 0; line < text.getLayout().getLineCount(); line++)
            PhoneUiCheck.check(text.getLayout().getEllipsisCount(line) == 0
                            && text.getLayout().getLineWidth(line) <= text.getWidth()
                                    - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight() + 1,
                    "Strip truncates date or prayer text: " + text.getText() + fixture);
    }

    private static void minimumNext(MainActivity a) throws Exception {
        for(float font : new float[] {1f,1.5f,2f}) {
            android.content.res.Configuration config=new android.content.res.Configuration(a.getApplicationContext().getResources().getConfiguration());
            config.fontScale=font;
            android.content.Context context=a.getApplicationContext().createConfigurationContext(config);
            WidgetOptions options=new WidgetOptions();options.size=120;options.date=true;
            RemoteViews content=WidgetRenderer.create(context,options,140,100);
            content.setTextViewText(R.id.widget_next_label,a.phoneUi.data.prayerName(4,a.ar));
            content.setTextViewText(R.id.widget_next_time,a.phoneUi.digits("11:48 PM"));
            View rendered=content.apply(context,new FrameLayout(context));
            int width=Math.round(140*context.getResources().getDisplayMetrics().density),
                    height=Math.round(100*context.getResources().getDisplayMetrics().density);
            rendered.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            rendered.layout(0,0,width,height);
            PhoneUiCheck.check(rendered.findViewById(R.id.widget_title).getVisibility()==View.GONE
                    && rendered.findViewById(R.id.widget_updated).getVisibility()==View.GONE,
                    "Minimum widget did not give prayer name/time priority over metadata");
            for(int id : new int[]{R.id.widget_next_label,R.id.widget_next_time}) {
                TextView text=rendered.findViewById(id);
                PhoneUiCheck.check(text.getTypeface().equals(context.getResources().getFont(PhoneAlarms.arabic(context)?R.font.amiri_bold:R.font.marcellus_regular)),
                        "Minimum next widget lost canonical app-language heading font at scale "+font);
                android.graphics.Rect visible=new android.graphics.Rect();text.getDrawingRect(visible);
                ((ViewGroup)rendered).offsetDescendantRectToMyCoords(text,visible);
                PhoneUiCheck.check(text.getVisibility()==View.VISIBLE&&text.getWidth()>0&&text.getHeight()>0
                        && new android.graphics.Rect(0,0,width,height).contains(visible),
                        "Minimum widget core text leaves allocated bounds at font "+font+": "+visible);
                PhoneUiCheck.check(text.getLayout()!=null&&text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom(),
                        "Minimum widget clips core text id="+id+" arabic="+a.ar+" font="+font+" lineHeight="+text.getLineHeight()+" layoutHeight="+(text.getLayout()==null?-1:text.getLayout().getHeight())+" height="+text.getHeight());
                for(int line=0;line<text.getLayout().getLineCount();line++)
                    PhoneUiCheck.check(text.getLayout().getEllipsisCount(line)==0
                            && text.getLayout().getLineWidth(line)<=text.getWidth()-text.getCompoundPaddingLeft()-text.getCompoundPaddingRight()+1,
                            "Minimum widget truncates prayer name/time at font "+font);
            }
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
                int width = a.views.dp(layout.equals("strip") ? 400 : 280);
                int height = a.views.dp(layout.equals("day") ? 300 : layout.equals("next") ? 160 : 140);
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

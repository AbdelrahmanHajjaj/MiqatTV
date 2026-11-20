package tv.miqat.companion;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.Rect;
import android.view.*;
import android.widget.TextView;

import java.util.*;

/** Run in each orientation of the owned emulator; app scale combines with OS font scale. */
final class PhoneSizeCheck {
    static void run(Instrumentation test, boolean capture, String expectedOrientation)
            throws Exception {
        Context c = test.getTargetContext();
        PhoneStore original = PhoneStore.read(c);
        String language = c.getSharedPreferences("companion", 0).getString("language", "system");
        MainActivity[] active = {null};
        float systemScale = c.getResources().getConfiguration().fontScale;
        try {
            for (String lang : new String[] {"en", "ar"})
                for (int size = 80; size <= 150; size += 10) {
                    original.changed(
                                    Collections.singletonMap(
                                            "display.size", Integer.toString(size)))
                            .save(c);
                    c.getSharedPreferences("companion", 0)
                            .edit()
                            .putString("language", lang)
                            .commit();
                    MainActivity a =
                            (MainActivity)
                                    test.startActivitySync(
                                            new Intent(c, MainActivity.class)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    active[0] = a;
                    final int expected = size;
                    PhoneUiCheck.on(test, () -> a.tab("prayers"));
                    PhoneUiCheck.on(
                            test,
                            () -> {
                                android.content.res.Configuration config =
                                        a.getResources().getConfiguration();
                                PhoneUiCheck.check(
                                        expectedOrientation.isEmpty()
                                                || expectedOrientation.equals(
                                                        config.orientation
                                                                        == android.content.res
                                                                                .Configuration
                                                                                .ORIENTATION_LANDSCAPE
                                                                ? "landscape"
                                                                : "portrait"),
                                        "Emulator did not enter requested orientation: "
                                                + expectedOrientation);
                                PhoneUiCheck.check(
                                        Math.abs(
                                                        a.getResources()
                                                                        .getConfiguration()
                                                                        .fontScale
                                                                - systemScale * expected / 100f)
                                                < 0.02,
                                        "App scale did not combine with Android text scale");
                                View nav =
                                        a.getWindow().getDecorView().findViewWithTag("navigation");
                                Rect visible = new Rect();
                                PhoneUiCheck.check(
                                        nav.getGlobalVisibleRect(visible)
                                                && visible.height() == nav.getHeight(),
                                        "Navigation clipped at " + expected);
                                for (String destination :
                                        new String[] {"prayers", "tvs", "phone-settings"}) {
                                    TextView button =
                                            a.getWindow()
                                                    .getDecorView()
                                                    .findViewWithTag("nav." + destination);
                                    PhoneUiCheck.check(
                                            button != null && button.getHeight() >= a.views.dp(48),
                                            "Small navigation touch target");
                                    if (button.getLayout() != null)
                                        for (int line = 0;
                                                line < button.getLayout().getLineCount();
                                                line++)
                                            PhoneUiCheck.check(
                                                    button.getLayout().getEllipsisCount(line) == 0,
                                                    "Navigation label truncated");
                                }
                                for (String prayer : PhoneStore.PRAYERS) {
                                    ViewGroup cell =
                                            a.body.findViewWithTag("phone.prayer." + prayer);
                                    PhoneUiCheck.check(
                                            cell != null && cell.getHeight() >= a.views.dp(48),
                                            "Prayer card touch target");
                                    PhoneUiCheck.check(
                                            cell.getTop() >= 0
                                                    && cell.getBottom()
                                                            <= ((View) cell.getParent())
                                                                    .getHeight(),
                                            "Prayer card outline clipped by its row at "
                                                    + expected);
                                    int content = 0;
                                    for (int i = 0; i < cell.getChildCount(); i++)
                                        content += cell.getChildAt(i).getHeight();
                                    PhoneUiCheck.check(
                                            content
                                                    <= cell.getHeight()
                                                            - cell.getPaddingTop()
                                                            - cell.getPaddingBottom(),
                                            "Prayer card text clipped at " + expected);
                                }
                                View tomorrow = a.body.findViewWithTag("phone.tomorrow");
                                View tools =
                                        (View) a.body.findViewWithTag("qibla.open").getParent();
                                PhoneUiCheck.check(
                                        tomorrow != null && tomorrow.getBottom() <= tools.getTop(),
                                        "Tomorrow Fajr must precede secondary actions");
                                View qibla = a.body.findViewWithTag("qibla.open");
                                View widgets = a.body.findViewWithTag("widgets.open");
                                int gap =
                                        Math.max(qibla.getLeft(), widgets.getLeft())
                                                - Math.min(qibla.getRight(), widgets.getRight());
                                PhoneUiCheck.check(
                                        gap >= a.views.dp(12), "Home actions fuse together");
                                PhoneUiCheck.check(
                                        a.body.findViewWithTag("phone.coming-soon").getTop()
                                                >= tools.getBottom(),
                                        "Locked features must follow prayer tools");
                                PhoneUiCheck.check(
                                        ((ViewGroup) a.body.findViewWithTag("phone.hero"))
                                                        .findViewWithTag("phone.location")
                                                != null,
                                        "Location must belong to the prayer card");
                                TextView name = a.body.findViewWithTag("phone.next.name");
                                TextView time = a.body.findViewWithTag("phone.next.time");
                                PhoneUiCheck.check(
                                        name.getCurrentTextColor() == a.accent
                                                && time.getCurrentTextColor() == a.ink,
                                        "Prayer card lost its theme highlights");
                                PhoneUiCheck.check(
                                        name.getWidth() > 0
                                                && Math.max(name.getLeft(), time.getLeft())
                                                                - Math.min(
                                                                        name.getRight(),
                                                                        time.getRight())
                                                        >= a.views.dp(12),
                                        "Prayer name and time overlap");
                                a.phoneUi.open("audio");
                                a.body.findViewWithTag("phone.reminders.volume.edit")
                                        .performClick();
                                View save =
                                        a.getWindow()
                                                .getDecorView()
                                                .findViewWithTag("phone.reminders.volume.save");
                                PhoneUiCheck.check(
                                        save != null && save.isEnabled(),
                                        "Numeric save missing at scale");
                            });
                    PhoneUiCheck.on(
                            test,
                            () -> {
                                View save =
                                        a.getWindow()
                                                .getDecorView()
                                                .findViewWithTag("phone.reminders.volume.save");
                                visible(save, "Bottom numeric confirmation clipped at scale");
                                PhoneUiCheck.check(
                                        a.body.findViewWithTag("phone.reminders.volume.save")
                                                == null,
                                        "Confirmation must stay outside scrolling content");
                            });
                    PhoneUiCheck.on(
                            test,
                            () -> {
                                a.tab("prayers");
                                a.phoneUi.save("display.digits", "western");
                                PhoneUiCheck.check(
                                        a.phoneUi.digits("٠١٢٣").equals("0123"),
                                        "Western digits ignored in Arabic locale");
                                a.phoneUi.save("display.digits", "eastern");
                                PhoneUiCheck.check(
                                        a.phoneUi.digits("0123").equals("٠١٢٣"),
                                        "Eastern digits ignored");
                            });
                    PhoneUiCheck.on(test, () -> a.phoneUi.open("audio"));
                    if (size == 80 || size == 150) {
                        extended(test, a, expected, capture);
                        PhoneUiCheck.on(test, () -> a.phoneUi.open("audio"));
                        PhoneUiCheck.on(
                                test,
                                () ->
                                        a.body.findViewWithTag("phone.quiet.start.edit")
                                                .performClick());
                        PhoneUiCheck.on(
                                test,
                                () -> {
                                    Rect bounds = new Rect();
                                    View done = a.phoneUi.choiceDialog.getButton(-1);
                                    PhoneUiCheck.check(
                                            done.getGlobalVisibleRect(bounds)
                                                    && bounds.height() == done.getHeight(),
                                            "Clock save clipped at " + expected);
                                });
                        PhoneUiCheck.capture(test, a, lang + "-phone-clock-" + size, capture);
                        PhoneUiCheck.on(test, () -> a.phoneUi.choiceDialog.dismiss());
                        PhoneUiCheck.on(
                                test,
                                () -> {
                                    a.phoneUi.open("fajr");
                                    a.body.findViewWithTag("phone.call.fajr.weekdays.edit")
                                            .performClick();
                                });
                        PhoneUiCheck.on(
                                test,
                                () -> {
                                    Rect bounds = new Rect();
                                    View done = a.phoneUi.choiceDialog.getButton(-1);
                                    PhoneUiCheck.check(
                                            done.getGlobalVisibleRect(bounds)
                                                    && bounds.height() == done.getHeight(),
                                            "Weekday save clipped at " + expected);
                                });
                        PhoneUiCheck.capture(test, a, lang + "-phone-weekdays-" + size, capture);
                        PhoneUiCheck.on(test, () -> a.phoneUi.choiceDialog.dismiss());
                    }
                    PhoneUiCheck.on(test, () -> a.tab("prayers"));
                    if (size == 80 || size == 150)
                        PhoneUiCheck.capture(test, a, lang + "-phone-size-" + size, capture);
                    PhoneUiCheck.on(test, a::finish);
                    LiveUiCheck.untilUi(test, a::isDestroyed);
                    active[0] = null;
                }
        } finally {
            if (active[0] != null) PhoneUiCheck.on(test, active[0]::finish);
            original.save(c);
            c.getSharedPreferences("companion", 0).edit().putString("language", language).commit();
            PhoneAlarms.rebuild(c);
        }
    }

    private static void extended(Instrumentation test, MainActivity a, int size, boolean capture)
            throws Exception {
        PhoneUiCheck.on(
                test, () -> a.body.findViewWithTag("phone.reminders.voice.edit").performClick());
        PhoneUiCheck.on(
                test,
                () -> {
                    visible(a.phoneUi.choiceDialog.getButton(-1), "Voice Apply clipped at " + size);
                    visible(
                            a.phoneUi
                                    .choiceDialog
                                    .getWindow()
                                    .getDecorView()
                                    .findViewWithTag("phone.voice.preview"),
                            "Voice audition clipped at " + size);
                    View choices =
                            a.phoneUi
                                    .choiceDialog
                                    .getWindow()
                                    .getDecorView()
                                    .findViewWithTag("phone.voice.list");
                    Rect bounds = new Rect();
                    PhoneUiCheck.check(
                            choices.getGlobalVisibleRect(bounds)
                                    && bounds.height() >= a.views.dp(48),
                            "Voice list has no reachable row at " + size);
                });
        PhoneUiCheck.capture(test, a, (a.ar ? "ar" : "en") + "-journey-voice-" + size, capture);
        PhoneUiCheck.on(test, () -> a.phoneUi.choiceDialog.dismiss());
        PhoneStore beforeWidget = PhoneStore.read(a);
        try {
            beforeWidget.changed(Collections.singletonMap("prayer.location-ready", "true")).save(a);
            PhoneUiCheck.on(
                    test,
                    () -> {
                        a.tab("prayers");
                        a.body.findViewWithTag("widgets.open").performClick();
                    });
            PhoneUiCheck.on(test, () -> WidgetHostCheck.timetable(a.body, a.phoneUi.data));
            PhoneUiCheck.on(
                    test,
                    () -> {
                        android.widget.LinearLayout rows = a.body.findViewById(R.id.widget_prayers);
                        TextView label = rows.getChildAt(0).findViewById(R.id.widget_prayer_name);
                        float expectedSize =
                                android.util.TypedValue.applyDimension(
                                        android.util.TypedValue.COMPLEX_UNIT_SP,
                                        13,
                                        a.getApplicationContext()
                                                .getResources()
                                                .getDisplayMetrics());
                        PhoneUiCheck.check(
                                Math.abs(label.getTextSize() - expectedSize) < 1f,
                                "Widget preview used app text scale instead of launcher scale");
                    });
            PhoneUiCheck.capture(
                    test, a, (a.ar ? "ar" : "en") + "-journey-widget-" + size, capture);
            for (String layout : new String[] {"next", "strip"}) {
                PhoneUiCheck.on(
                        test,
                        () -> a.body.findViewWithTag("widget.layout." + layout).performClick());
                PhoneUiCheck.on(
                        test,
                        () -> {
                            View content =
                                    a.body.findViewById(
                                            layout.equals("strip")
                                                    ? R.id.widget_strip
                                                    : R.id.widget_next);
                            PhoneUiCheck.check(
                                    content != null && content.getVisibility() == View.VISIBLE,
                                    "Widget variant did not render at scale");
                        });
                PhoneUiCheck.capture(
                        test, a, (a.ar ? "ar" : "en") + "-widget-" + layout + "-" + size, capture);
            }
        } finally {
            beforeWidget.save(a);
        }
        android.content.SharedPreferences prefs = PhoneStore.preferences(a);
        boolean hadStep = prefs.contains("onboarding-step");
        int oldStep = prefs.getInt("onboarding-step", 0);
        try {
            prefs.edit().putInt("onboarding-step", 1).commit();
            PhoneUiCheck.on(test, () -> a.navigate("onboarding"));
            PhoneUiCheck.on(
                    test,
                    () -> {
                        visible(
                                a.getWindow().getDecorView().findViewWithTag("onboarding.next"),
                                "Onboarding Continue clipped at " + size);
                    });
            PhoneUiCheck.capture(
                    test, a, (a.ar ? "ar" : "en") + "-journey-appearance-" + size, capture);
            PhoneUiCheck.on(
                    test,
                    () -> {
                        a.phoneUi.open("appearance");
                        a.body.findViewWithTag("appearance.tab.appearance.background")
                                .performClick();
                    });
            PhoneUiCheck.on(
                    test,
                    () ->
                            visible(
                                    a.getWindow()
                                            .getDecorView()
                                            .findViewWithTag("appearance.apply"),
                                    "Appearance Apply clipped at " + size));
            PhoneUiCheck.capture(
                    test, a, (a.ar ? "ar" : "en") + "-journey-background-" + size, capture);
            PhoneUiCheck.on(
                    test,
                    () -> {
                        a.getWindow()
                                .getDecorView()
                                .findViewWithTag("appearance.cancel")
                                .performClick();
                        a.tab("tvs");
                        a.body.findViewWithTag("pair.open").performClick();
                        a.body.findViewWithTag("setup.platform.lg").performClick();
                    });
            PhoneUiCheck.capture(
                    test, a, (a.ar ? "ar" : "en") + "-journey-tv-guide-" + size, capture);
        } finally {
            android.content.SharedPreferences.Editor restore = prefs.edit();
            if (hadStep) restore.putInt("onboarding-step", oldStep);
            else restore.remove("onboarding-step");
            PhoneUiCheck.check(restore.commit(), "Cannot restore onboarding size fixture");
            PhoneUiCheck.on(
                    test,
                    () -> {
                        if (a.phoneUi.choiceDialog != null) a.phoneUi.choiceDialog.dismiss();
                        a.tab("prayers");
                    });
        }
    }

    private static void visible(View view, String message) {
        Rect bounds = new Rect();
        PhoneUiCheck.check(
                view != null
                        && view.getGlobalVisibleRect(bounds)
                        && bounds.height() == view.getHeight()
                        && bounds.width() == view.getWidth(),
                message);
    }
}

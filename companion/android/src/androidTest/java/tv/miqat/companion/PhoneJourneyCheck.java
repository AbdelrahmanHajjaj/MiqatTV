package tv.miqat.companion;

import android.app.Instrumentation;
import android.content.*;
import android.view.*;
import android.widget.*;

import java.util.*;

/** Fresh use, draft previews and unified setup boundaries; no physical TV or network. */
final class PhoneJourneyCheck {
    static void run(Instrumentation test, boolean captures) throws Exception {
        Context context = test.getTargetContext();
        SharedPreferences prefs = PhoneStore.preferences(context);
        Map<String, ?> original = new LinkedHashMap<>(prefs.getAll());
        String language =
                context.getSharedPreferences("companion", 0).getString("language", "system");
        MainActivity[] current = {null};
        try {
            for (String lang : new String[] {"en", "ar"}) {
                prefs.edit().clear().commit();
                context.getSharedPreferences("companion", 0)
                        .edit()
                        .putString("language", lang)
                        .commit();
                MainActivity a = launch(test, context);
                current[0] = a;
                PhoneUiCheck.on(
                        test,
                        () -> {
                            check(
                                    a.page.equals("onboarding"),
                                    "Fresh install must open guided setup");
                            check(
                                    !a.getWindow()
                                            .getDecorView()
                                            .findViewWithTag("onboarding.next")
                                            .isEnabled(),
                                    "Unconfirmed default city must not complete location setup");
                            a.body.findViewWithTag("phone.location.confirm").performClick();
                        });
                PhoneUiCheck.capture(test, a, lang + "-journey-location", captures);
                PhoneUiCheck.on(
                        test,
                        () ->
                                a.getWindow()
                                        .getDecorView()
                                        .findViewWithTag("onboarding.next")
                                        .performClick());
                PhoneUiCheck.capture(test, a, lang + "-journey-alerts", captures);
                PhoneUiCheck.on(test, a::finish);
                LiveUiCheck.untilUi(test, a::isDestroyed);
                MainActivity resumed = launch(test, context);
                current[0] = resumed;
                PhoneUiCheck.on(
                        test,
                        () -> {
                            check(
                                    resumed.page.equals("onboarding")
                                            && resumed.body.findViewWithTag(
                                                            "phone.reminders.enabled.edit")
                                                    != null,
                                    "First-use progress lost after reopening");
                            resumed.getWindow()
                                    .getDecorView()
                                    .findViewWithTag("onboarding.next")
                                    .performClick();
                            check(
                                    resumed.body.findViewWithTag(
                                                    "onboarding.permission.notifications")
                                            != null,
                                    "Notifications must have a dedicated step");
                            resumed.getWindow()
                                    .getDecorView()
                                    .findViewWithTag("onboarding.skip")
                                    .performClick();
                            check(
                                    resumed.body.findViewWithTag("onboarding.permission.exact")
                                            != null,
                                    "Precise alarms must have a dedicated step");
                            resumed.getWindow()
                                    .getDecorView()
                                    .findViewWithTag("onboarding.skip")
                                    .performClick();
                            check(
                                    resumed.body.findViewWithTag("phone.appearance.preview")
                                            != null,
                                    "First-use personalization must show a real preview");
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-personalization", captures);
                String before = PhoneStore.read(context).value("appearance.theme");
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.body
                                    .findViewWithTag("phone.appearance.theme.option.frost")
                                    .performClick();
                            check(
                                    resumed.phoneUi
                                            .settings
                                            .value("appearance.theme")
                                            .equals("frost"),
                                    "Guided carousel did not select the previewed theme");
                            resumed.getWindow()
                                    .getDecorView()
                                    .findViewWithTag("onboarding.next")
                                    .performClick();
                            check(
                                    resumed.body.findViewWithTag("phone.appearance.background")
                                            != null,
                                    "Background must have a dedicated carousel step");
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-background", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.getWindow()
                                    .getDecorView()
                                    .findViewWithTag("onboarding.next")
                                    .performClick();
                            check(
                                    resumed.page.equals("prayers") && !resumed.onboarding.needed(),
                                    "Completed first use did not reach standalone prayers");
                            resumed.onboarding.open();
                            resumed.back();
                            check(
                                    !resumed.onboarding.needed() && resumed.page.equals("prayers"),
                                    "Cancelling a repeated guide erased completed setup");
                            resumed.phoneUi.open("appearance");
                            resumed.body
                                    .findViewWithTag("phone.appearance.theme.option.dusk")
                                    .performClick();
                        });
                PhoneUiCheck.on(
                        test,
                        () -> {
                            android.widget.HorizontalScrollView carousel =
                                    resumed.phoneUi
                                            .choiceDialog
                                            .getWindow()
                                            .getDecorView()
                                            .findViewWithTag("phone.appearance.choices");
                            ((android.view.ViewGroup) carousel.getChildAt(0))
                                    .getChildAt(0)
                                    .performClick();
                            check(
                                    resumed.phoneUi
                                                    .choiceDialog
                                                    .getWindow()
                                                    .getDecorView()
                                                    .findViewWithTag("phone.appearance.preview")
                                            != null,
                                    "Theme choice preview missing");
                            resumed.phoneUi.choiceDialog.getButton(-2).performClick();
                        });
                check(
                        PhoneStore.read(context).value("appearance.theme").equals("frost"),
                        "Cancel preview changed saved theme");
                PhoneUiCheck.on(
                        test,
                        () ->
                                resumed.body
                                        .findViewWithTag("phone.appearance.background.edit")
                                        .performClick());
                PhoneUiCheck.capture(test, resumed, lang + "-journey-frost-preview", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.phoneUi.choiceDialog.getButton(-2).performClick();
                            resumed.phoneUi.save("appearance.theme", before);
                            resumed.tab("prayers");
                            resumed.body.findViewWithTag("widgets.open").performClick();
                            check(
                                    resumed.body
                                                    .findViewWithTag("widget.preview")
                                                    .findViewById(R.id.widget_times)
                                            != null,
                                    "Widget must render actual RemoteViews");
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-widget", captures);
                WidgetHostCheck.run(test, resumed, captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.tab("tvs");
                            resumed.body.findViewWithTag("pair.open").performClick();
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-platforms", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            check(
                                    resumed.page.equals("tv-setup"),
                                    "Pair and setup must start with the same platform choice");
                            resumed.body.findViewWithTag("setup.platform.roku").performClick();
                            check(
                                    resumed.page.equals("tv-setup") && !resumed.networkBusy,
                                    "Unsupported TV started setup");
                        });
                LiveUiCheck.untilUi(test, () -> !resumed.hasWindowFocus());
                test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                LiveUiCheck.untilUi(test, resumed::hasWindowFocus);
                PhoneUiCheck.on(
                        test,
                        () -> resumed.body.findViewWithTag("setup.platform.lg").performClick());
                PhoneUiCheck.capture(test, resumed, lang + "-journey-lg-guide", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            check(
                                    resumed.getWindow()
                                                    .getDecorView()
                                                    .findViewWithTag("setup.continue")
                                            == null,
                                    "Source selection must follow preparation");
                            resumed.getWindow()
                                    .getDecorView()
                                    .findViewWithTag("setup.skip")
                                    .performClick();
                            check(
                                    resumed.body
                                            .findViewWithTag("setup.channel.latest")
                                            .isSelected(),
                                    "Latest channel must be default");
                            resumed.body.findViewWithTag("setup.channel.stable").performClick();
                            check(
                                    resumed.body
                                            .findViewWithTag("setup.channel.stable")
                                            .isSelected(),
                                    "Release channel did not update");
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-channel", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.body.findViewWithTag("setup.advanced").performClick();
                            check(
                                    resumed.body.findViewWithTag("setup.local") != null
                                            && resumed.getWindow()
                                                            .getDecorView()
                                                            .findViewWithTag("setup.continue")
                                                    == null,
                                    "Advanced must switch to local package UI");
                            resumed.tab("prayers");
                            resumed.body.findViewWithTag("qibla.open").performClick();
                            check(
                                    resumed.page.equals("qibla")
                                            && resumed.body.findViewWithTag("qibla.status") != null,
                                    "Qibla screen/bearing unavailable for confirmed location");
                        });
                LiveUiCheck.untilUi(
                        test,
                        () -> {
                            View compass = resumed.body.findViewWithTag("qibla.compass");
                            TextView status = resumed.body.findViewWithTag("qibla.status");
                            return compass != null && compass.getVisibility() == View.VISIBLE
                                    || status != null
                                            && status.getText()
                                                    .toString()
                                                    .equals(resumed.t("qibla_no_sensor"));
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-qibla", captures);
                cancelledResult(test, resumed);
                PhoneUiCheck.on(test, resumed::finish);
                LiveUiCheck.untilUi(test, resumed::isDestroyed);
                current[0] = null;
            }
        } finally {
            if (current[0] != null) PhoneUiCheck.on(test, current[0]::finish);
            SharedPreferences.Editor restore = prefs.edit().clear();
            for (Map.Entry<String, ?> entry : original.entrySet()) {
                Object value = entry.getValue();
                String key = entry.getKey();
                if (value instanceof String) restore.putString(key, (String) value);
                else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else if (value instanceof Long) restore.putLong(key, (Long) value);
                else if (value instanceof Integer) restore.putInt(key, (Integer) value);
            }
            check(restore.commit(), "Cannot restore journey fixture");
            context.getSharedPreferences("companion", 0)
                    .edit()
                    .putString("language", language)
                    .commit();
            PhoneAlarms.rebuild(context);
        }
    }

    private static void cancelledResult(Instrumentation test, MainActivity a) throws Exception {
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1),
                release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean published =
                new java.util.concurrent.atomic.AtomicBoolean();
        String page = a.page;
        PhoneUiCheck.on(
                test,
                () ->
                        a.network(
                                () -> {
                                    entered.countDown();
                                    try {
                                        release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                                    } catch (InterruptedException cancelled) {
                                        /* Some providers can still return a late result after cancellation. */
                                    }
                                    return true;
                                },
                                done -> published.set(true),
                                () -> published.set(true)));
        check(
                entered.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "Cancellation fixture never started");
        PhoneUiCheck.on(
                test,
                () -> {
                    a.cancelNetwork();
                    a.render();
                });
        release.countDown();
        a.io.shutdown();
        check(
                a.io.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS),
                "Cancelled provider did not finish");
        test.waitForIdleSync();
        check(
                !published.get() && !a.networkBusy && a.page.equals(page),
                "A cancelled result changed the current journey");
    }

    private static MainActivity launch(Instrumentation test, Context context) {
        return (MainActivity)
                test.startActivitySync(
                        new Intent(context, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

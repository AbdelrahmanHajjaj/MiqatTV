package tv.miqat.companion;

import android.app.Instrumentation;
import android.content.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
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
                MainActivity a = LiveUiCheck.launch(test);
                current[0] = a;
                PhoneUiCheck.capture(test, a, lang + "-journey-location", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            check(
                                    a.page.equals("onboarding")
                                            && !a.phoneUi.settings.locationReady,
                                    "Fresh setup must require location confirmation");
                            check(
                                    a.body.findViewWithTag("phone.reminders.enabled.edit") != null
                                            && a.body.findViewWithTag("phone.location.detect")
                                                    != null,
                                    "Location and alerts must share the first page");
                            a.body.findViewWithTag("phone.reminders.enabled.edit").performClick();
                            check(
                                    !a.phoneUi.settings.locationReady
                                            && !a.phoneUi.settings.enabled,
                                    "First-use draft must not enable alerts before location"
                                            + " confirmation");
                            a.getWindow()
                                    .getDecorView()
                                    .findViewWithTag("onboarding.next")
                                    .performClick();
                            if (a.body.findViewWithTag("phone.appearance.preview") != null)
                                a.back();
                            check(
                                    a.phoneUi.settings.locationReady && a.phoneUi.settings.enabled,
                                    "Bundled choices were not saved");
                        });
                PhoneUiCheck.capture(test, a, lang + "-journey-alerts", captures);
                PhoneUiCheck.on(test, a::finish);
                LiveUiCheck.untilUi(test, a::isDestroyed);
                MainActivity resumed = LiveUiCheck.launch(test);
                current[0] = resumed;
                PhoneUiCheck.on(
                        test,
                        () -> {
                            check(
                                    resumed.page.equals("onboarding")
                                            && resumed.phoneUi.settings.locationReady
                                            && resumed.phoneUi.settings.enabled,
                                    "First-use progress lost after reopening");
                            android.widget.Button next =
                                    resumed.getWindow()
                                            .getDecorView()
                                            .findViewWithTag("onboarding.next");
                            String required =
                                    !PhoneAlarms.notifications(resumed)
                                            ? "phone_permission_notifications"
                                            : !PhoneAlarms.exact(resumed)
                                                    ? "phone_permission_exact"
                                                    : "setup_next";
                            check(
                                    next.getText().toString().equals(resumed.t(required)),
                                    "Continue must request only missing access");
                            resumed.getWindow()
                                    .getDecorView()
                                    .findViewWithTag("onboarding.skip")
                                    .performClick();
                            check(
                                    resumed.body.findViewWithTag("phone.appearance.preview") != null
                                            && resumed.body.findViewWithTag(
                                                            "appearance.tab.appearance.theme")
                                                    != null
                                            && resumed.body.findViewWithTag(
                                                            "appearance.tab.appearance.background")
                                                    != null,
                                    "Second page needs one preview and both direct visual choices");
                            check(
                                    resumed.getWindow()
                                                    .getDecorView()
                                                    .findViewWithTag("onboarding.skip")
                                            == null,
                                    "Finish needs no duplicate Skip");
                            resumed.body
                                    .findViewWithTag("appearance.tab.appearance.theme")
                                    .performClick();
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-personalization", captures);
                String before = PhoneStore.read(context).value("appearance.theme");
                int frostAccent =
                        android.graphics.Color.parseColor(
                                resumed.phoneUi.data.colors("frost").getString("gold-400"));
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
                            check(
                                    ((TextView) resumed.body.findViewWithTag("phone.next.name"))
                                                    .getCurrentTextColor()
                                            == frostAccent,
                                    "Live preview did not apply the selected theme");
                            resumed.body
                                    .findViewWithTag("appearance.tab.appearance.background")
                                    .performClick();
                            resumed.body
                                    .findViewWithTag(
                                            "phone.appearance.background.option.dusk_peaks")
                                    .performClick();
                            check(
                                    resumed.phoneUi
                                            .settings
                                            .value("appearance.background")
                                            .equals("dusk_peaks"),
                                    "Live background selection was not applied");
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
                            check(
                                    resumed.body.findViewWithTag("onboarding.resume") == null,
                                    "Completed setup still shows its Home prompt");
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-home-complete", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.body.findViewWithTag("phone.location").performClick();
                            check(
                                    resumed.section.equals("calculation"),
                                    "Prayer card location action must open location settings");
                            resumed.tab("prayers");
                            resumed.onboarding.open();
                            resumed.back();
                            check(
                                    !resumed.onboarding.needed() && resumed.page.equals("prayers"),
                                    "Cancelling a repeated guide erased completed setup");
                            check(
                                    resumed.body.findViewWithTag("onboarding.resume") == null,
                                    "Repeated setup restored a completed Home prompt");
                            resumed.phoneUi.open("appearance");
                            resumed.body
                                    .findViewWithTag("appearance.tab.appearance.theme")
                                    .performClick();
                            resumed.body
                                    .findViewWithTag("phone.appearance.theme.option.dusk")
                                    .performClick();
                            check(
                                    resumed.phoneUi
                                            .settings
                                            .value("appearance.theme")
                                            .equals("frost"),
                                    "Preview saved before Apply");
                            check(
                                    resumed.body.findViewWithTag("phone.appearance.preview")
                                            != null,
                                    "Live preview missing");
                            resumed.phoneUi.save("display.clock", "12");
                            check(
                                    resumed.phoneUi.settings.value("display.clock").equals("24"),
                                    "Display draft changed saved clock before Apply");
                            resumed.getWindow()
                                    .getDecorView()
                                    .findViewWithTag("appearance.cancel")
                                    .performClick();
                        });
                check(
                        PhoneStore.read(context).value("appearance.theme").equals("frost"),
                        "Cancel saved appearance draft");
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.phoneUi.open("appearance");
                            resumed.body
                                    .findViewWithTag("appearance.tab.appearance.theme")
                                    .performClick();
                            resumed.body
                                    .findViewWithTag("phone.appearance.theme.option.dusk")
                                    .performClick();
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-frost-preview", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.getWindow()
                                    .getDecorView()
                                    .findViewWithTag("appearance.apply")
                                    .performClick();
                            check(
                                    resumed.phoneUi
                                            .settings
                                            .value("appearance.theme")
                                            .equals("dusk"),
                                    "Apply lost appearance draft");
                            resumed.phoneUi.save("appearance.theme", before);
                            resumed.tab("prayers");
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-home-theme-complete", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.body.findViewWithTag("widgets.open").performClick();
                            android.widget.Button add =
                                    resumed.getWindow()
                                            .getDecorView()
                                            .findViewWithTag("widget.apply");
                            check(
                                    add.getText().toString().equals(resumed.t("widget_add")),
                                    "A new widget must enter Add, not update an invalid widget ID");
                            check(
                                    resumed.body
                                                    .findViewWithTag("widget.preview")
                                                    .findViewById(R.id.widget_times)
                                            != null,
                                    "Widget must render actual RemoteViews");
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-widget", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.body.findViewWithTag("widget.layout.strip").performClick();
                            resumed.getWindow()
                                    .getDecorView()
                                    .findViewWithTag("widget.cancel")
                                    .performClick();
                            check(
                                    resumed.page.equals("prayers"),
                                    "Widget Cancel did not leave the editor");
                            resumed.body.findViewWithTag("widgets.open").performClick();
                            check(
                                    ((android.view.View)
                                                    resumed.body.findViewWithTag(
                                                            "widget.layout.day"))
                                            .isSelected(),
                                    "Widget Cancel retained an unsaved layout draft");
                        });
                WidgetHostCheck.run(test, resumed, captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.tab("tvs");
                            resumed.body.findViewWithTag("pair.open").performClick();
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-platforms", captures);
                comingSoon(test, resumed);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.body.findViewWithTag("setup.platform.lg").performClick();
                            check(
                                    resumed.page.equals("lg-setup")
                                            && resumed.body.findViewWithTag("setup.instruction.5")
                                                    != null
                                            && resumed.body.findViewWithTag("lg.host") != null
                                            && resumed.body.findViewWithTag("lg.passphrase")
                                                    != null,
                                    "LG numbered guide and credentials must share one page");
                            check(
                                    resumed.body
                                            .findViewWithTag("setup.channel.latest")
                                            .isSelected(),
                                    "Latest must be default");
                        });
                PhoneUiCheck.capture(test, resumed, lang + "-journey-lg-guide", captures);
                PhoneUiCheck.on(
                        test,
                        () -> {
                            resumed.body.findViewWithTag("setup.channel.preview").performClick();
                            check(
                                    resumed.body
                                            .findViewWithTag("setup.channel.preview")
                                            .isSelected(),
                                    "Release choice did not update");
                            resumed.back();
                            resumed.body.findViewWithTag("setup.platform.android").performClick();
                            check(
                                    resumed.body.findViewWithTag("setup.instruction.1") != null
                                            && resumed.getWindow()
                                                            .getDecorView()
                                                            .findViewWithTag("setup.continue")
                                                    != null,
                                    "Android preparation and release must share one page");
                            resumed.body.findViewWithTag("setup.channel.preview").performClick();
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
                MainActivity reopened = LiveUiCheck.launch(test);
                current[0] = reopened;
                PhoneUiCheck.on(
                        test,
                        () -> {
                            check(
                                    reopened.page.equals("prayers")
                                            && reopened.body.findViewWithTag("onboarding.resume")
                                                    == null,
                                    "Completed setup prompt returned after cold launch");
                        });
                PhoneUiCheck.on(test, reopened::finish);
                LiveUiCheck.untilUi(test, reopened::isDestroyed);
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

    private static void comingSoon(Instrumentation test, MainActivity a) throws Exception {
        List<String> windows = new ArrayList<>();
        AccessibilityEvent dialog;
        try {
            dialog =
                    test.getUiAutomation()
                            .executeAndWaitForEvent(
                                    () ->
                                            PhoneUiCheck.on(
                                                    test,
                                                    () -> {
                                                        check(
                                                                a.page.equals("tv-setup"),
                                                                "Pair and setup must start with the"
                                                                        + " same platform choice");
                                                        a.body.findViewWithTag(
                                                                        "setup.platform.roku")
                                                                .performClick();
                                                        check(
                                                                a.page.equals("tv-setup")
                                                                        && !a.networkBusy,
                                                                "Unsupported TV started setup");
                                                    }),
                                    event -> {
                                        if (event.getEventType()
                                                != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                                            return false;
                                        windows.add(event.getClassName() + ": " + event.getText());
                                        return "android.app.AlertDialog"
                                                        .equals(
                                                                String.valueOf(
                                                                        event.getClassName()))
                                                && event.getText()
                                                        .toString()
                                                        .contains(a.t("coming_soon"));
                                    },
                                    10000);
        } catch (java.util.concurrent.TimeoutException timeout) {
            throw new AssertionError(
                    "Coming soon dialog window event missing; observed " + windows, timeout);
        }
        dialog.recycle();
        View[] focusedDialog = {null};
        LiveUiCheck.untilUi(
                test,
                () -> {
                    if (a.hasWindowFocus()) return false;
                    if (android.os.Build.VERSION.SDK_INT < 29) return true;
                    for (View window :
                            android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                        if (window != a.getWindow().getDecorView() && window.hasWindowFocus()) {
                            focusedDialog[0] = window;
                            return true;
                        }
                    }
                    return false;
                });
        LiveUiCheck.key(test, KeyEvent.KEYCODE_BACK);
        LiveUiCheck.untilUi(
                test,
                () ->
                        a.hasWindowFocus()
                                && (focusedDialog[0] == null
                                        || !focusedDialog[0].isAttachedToWindow()));
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

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

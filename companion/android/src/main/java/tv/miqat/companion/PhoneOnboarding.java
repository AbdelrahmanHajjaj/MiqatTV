package tv.miqat.companion;

import android.widget.LinearLayout;

import org.json.JSONObject;

/** Resumable first use. Preferences are saved by the same editors as ordinary settings. */
final class PhoneOnboarding {
    private final MainActivity a;

    PhoneOnboarding(MainActivity activity) {
        a = activity;
    }

    boolean needed() {
        return !PhoneStore.preferences(a).getBoolean("onboarding-complete", false);
    }

    boolean shouldStart() {
        return needed() && !PhoneStore.preferences(a).getBoolean("onboarding-seen", false);
    }

    private int step() {
        return Math.max(0, Math.min(2, PhoneStore.preferences(a).getInt("onboarding-step", 0)));
    }

    void open() {
        if (!needed() && !PhoneStore.preferences(a).edit().putInt("onboarding-step", 0).commit()) {
            a.notice("phone_save_failed");
            return;
        }
        a.navigate("onboarding");
    }

    void back() {
        if (step() > 0) advance(step() - 1);
        else finish(false);
    }

    String locationKey() {
        return "onboarding:" + step();
    }

    void render() {
        int step = step();
        a.body.addView(a.views.text(a.t("welcome_hint"), 16, a.muted));
        a.body.addView(a.views.text(a.phoneUi.digits((step + 1) + " / 3"), 14, a.accent));
        a.body.addView(
                a.views.heading(
                        a.t(
                                step == 0
                                        ? "setup_location"
                                        : step == 1 ? "setup_alerts" : "setup_appearance")));
        try {
            if (step == 0) {
                a.body.addView(a.views.text(a.t("setup_location_hint"), 14, a.muted));
                a.phoneUi.locationUi.locationEditor();
                control("prayer.zone");
                control("prayer.method");
                control("prayer.asr");
                a.phoneUi.locationUi.confirmLocation();
            } else if (step == 1) {
                a.body.addView(a.views.text(a.t("setup_alert_hint"), 14, a.muted));
                control("reminders.enabled");
                control("reminders.voice");
                a.phoneUi.permissions();
            } else {
                a.phoneUi.appearance.preview(a.body, a.phoneUi.settings);
                control("appearance.theme");
                control("appearance.background");
                control("display.digits");
                control("display.clock");
            }
        } catch (Exception invalid) {
            a.body.addView(a.views.text(a.t("phone_settings_failed"), 16, a.ink));
            return;
        }
    }

    void footer(LinearLayout shell) {
        int step = step();
        LinearLayout actions = a.views.horizontal(shell);
        actions.setPadding(a.views.dp(16), a.views.dp(8), a.views.dp(16), a.views.dp(12));
        android.widget.Button later =
                a.views.button(actions, a.t("setup_later"), () -> finish(false));
        a.views.flex(later);
        later.setTag("onboarding.later");
        android.widget.Button next =
                a.views.button(
                        actions,
                        a.t(step == 2 ? "setup_finish" : "setup_next"),
                        () -> {
                            if (step == 2) finish(true);
                            else advance(step + 1);
                        });
        a.views.flex(next);
        next.setTag("onboarding.next");
        next.setBackground(a.views.background(a.accent, 24));
        next.setTextColor(a.ground);
        next.setEnabled(
                a.phoneUi.settings != null
                        && (step != 0 || a.phoneUi.settings.locationReady)
                        && !a.phoneUi.locationUi.locator.running);
        next.setAlpha(next.isEnabled() ? 1f : .45f);
    }

    private void control(String id) throws Exception {
        JSONObject definition = a.phoneUi.data.controls.get(id);
        a.phoneUi.control(definition);
    }

    private void advance(int next) {
        a.phoneUi.locationUi.cancelLocation();
        if (!PhoneStore.preferences(a).edit().putInt("onboarding-step", next).commit()) {
            a.notice("phone_save_failed");
            return;
        }
        a.render();
    }

    private void finish(boolean complete) {
        if (!PhoneStore.preferences(a)
                .edit()
                .putBoolean("onboarding-complete", complete || !needed())
                .putBoolean("onboarding-seen", true)
                .commit()) {
            a.notice("phone_save_failed");
            return;
        }
        a.phoneUi.resetNavigation();
        a.navigate("prayers");
    }
}

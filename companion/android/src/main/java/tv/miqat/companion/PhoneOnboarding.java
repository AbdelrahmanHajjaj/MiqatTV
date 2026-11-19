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
        return Math.max(0, Math.min(5, PhoneStore.preferences(a).getInt("onboarding-step", 0)));
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
        a.body.addView(a.views.text(a.phoneUi.digits((step + 1) + " / 6"), 13, a.accent));
        String[] titles = {
            "setup_location",
            "phone_alerts",
            "phone_notifications",
            "phone_exact",
            "phone_theme",
            "phone_background"
        };
        a.body.addView(a.views.heading(a.t(titles[step])));
        try {
            switch (step) {
                case 0:
                    a.body.addView(a.views.text(a.t("setup_location_short"), 14, a.muted));
                    a.phoneUi.locationUi.locationEditor();
                    a.phoneUi.locationUi.confirmLocation();
                    break;
                case 1:
                    a.body.addView(a.views.text(a.t("setup_alert_short"), 14, a.muted));
                    control("reminders.enabled");
                    break;
                case 2:
                    a.body.addView(a.views.text(a.t("setup_notification_short"), 14, a.muted));
                    a.views
                            .button(
                                    a.body,
                                    a.t("phone_notifications"),
                                    a.phoneUi::requestNotifications)
                            .setTag("onboarding.permission.notifications");
                    break;
                case 3:
                    a.body.addView(a.views.text(a.t("setup_exact_short"), 14, a.muted));
                    a.views
                            .button(a.body, a.t("phone_exact"), a.phoneUi::requestExact)
                            .setTag("onboarding.permission.exact");
                    break;
                default:
                    a.phoneUi.appearance.carousel(
                            a.body, step == 4 ? "appearance.theme" : "appearance.background", true);
                    break;
            }
        } catch (Exception invalid) {
            a.body.addView(a.views.text(a.t("phone_settings_failed"), 16, a.ink));
        }
    }

    void footer(LinearLayout shell) {
        int step = step();
        LinearLayout actions = a.views.horizontal(shell);
        actions.setPadding(a.views.dp(16), a.views.dp(8), a.views.dp(16), a.views.dp(12));
        android.widget.Button skip =
                a.views.button(
                        actions,
                        a.t("setup_skip"),
                        () -> {
                            if (step == 5) finish(true);
                            else advance(step + 1);
                        });
        a.views.flex(skip);
        skip.setTag("onboarding.skip");
        android.widget.Button next =
                a.views.button(
                        actions,
                        a.t(step == 5 ? "setup_finish" : "setup_next"),
                        () -> {
                            if (step == 5) finish(true);
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

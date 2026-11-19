package tv.miqat.companion;

import android.widget.LinearLayout;

/** Resumable first use. Preferences are saved by the same editors as ordinary settings. */
final class PhoneOnboarding {
    private final MainActivity a;
    private PhoneBackdrop.Artwork artwork;

    PhoneOnboarding(MainActivity activity) {
        a = activity;
    }

    void background(LinearLayout shell) {
        try {
            if (artwork == null) artwork = PhoneBackdrop.read(a, "miqat_signature");
            shell.setBackground(
                    new android.graphics.drawable.LayerDrawable(
                            new android.graphics.drawable.Drawable[] {
                                new PhoneBackdrop(artwork),
                                new android.graphics.drawable.ColorDrawable(
                                        (a.ground & 0xffffff) | 0x66000000)
                            }));
        } catch (java.io.IOException unavailable) {
            android.util.Log.w("MiqatSetup", "Signature background unavailable", unavailable);
        }
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
        a.body.addView(a.views.text(a.phoneUi.digits((step + 1) + " / 3"), 13, a.accent));
        String[] titles = {"setup_location", "setup_alerts", "setup_appearance"};
        android.widget.TextView title = a.views.heading(a.t(titles[step]));
        title.setTag("onboarding.title");
        a.body.addView(title);
        try {
            if (step == 0) {
                a.phoneUi.locationUi.locationEditor();
                a.phoneUi.locationUi.confirmLocation();
            } else if (step == 1) {
                a.phoneUi.control(a.phoneUi.data.controls.get("reminders.enabled"));
                android.widget.Switch alerts =
                        a.body.findViewWithTag("phone.reminders.enabled.edit");
                alerts.setText(a.t(a.phoneUi.settings.enabled ? "on" : "off"));
                if (a.phoneUi.settings.enabled)
                    a.body.addView(
                            a.views.text(
                                    a.t("phone_notifications")
                                            + " · "
                                            + a.t(PhoneAlarms.notifications(a) ? "on" : "off")
                                            + "\n"
                                            + a.t("phone_exact")
                                            + " · "
                                            + a.t(PhoneAlarms.exact(a) ? "on" : "off"),
                                    14,
                                    a.muted));
            } else {
                a.phoneUi.appearance.preview(a.body, a.phoneUi.settings);
                a.phoneUi.appearance.carousel(a.body, "appearance.theme", true);
                a.phoneUi.appearance.carousel(a.body, "appearance.background", true);
            }
        } catch (Exception invalid) {
            a.body.addView(a.views.text(a.t("phone_settings_failed"), 16, a.ink));
        }
    }

    void footer(LinearLayout shell) {
        int step = step();
        boolean alerts = step == 1 && a.phoneUi.settings != null && a.phoneUi.settings.enabled;
        boolean notification = alerts && !PhoneAlarms.notifications(a);
        boolean exact = alerts && !PhoneAlarms.exact(a);
        boolean confirm =
                step == 0 && a.phoneUi.settings != null && !a.phoneUi.settings.locationReady;
        LinearLayout footer = a.views.actionBar(shell);
        footer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        if (confirm || notification || exact)
            a.views
                    .secondaryAction(
                            a.views.secondaryActions(footer),
                            a.t("setup_skip"),
                            () -> advance(step + 1))
                    .setTag("onboarding.skip");
        String label = "setup_next";
        if (notification) label = "phone_permission_notifications";
        else if (exact) label = "phone_permission_exact";
        else if (confirm) label = "phone_confirm_location";
        else if (step == 2) label = "setup_finish";
        android.widget.Button next =
                a.views.primaryAction(
                        footer,
                        a.t(label),
                        () -> {
                            if (notification) a.phoneUi.requestNotifications();
                            else if (exact) a.phoneUi.requestExact();
                            else if (confirm) {
                                if (a.phoneUi.save(
                                        java.util.Collections.singletonMap(
                                                "prayer.location-ready", "true"),
                                        false)) advance(1);
                            } else if (step == 2) finish(true);
                            else advance(step + 1);
                        });
        next.setTag("onboarding.next");
        next.setEnabled(a.phoneUi.settings != null && !a.phoneUi.locationUi.locator.running);
        next.setAlpha(next.isEnabled() ? 1f : .45f);
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

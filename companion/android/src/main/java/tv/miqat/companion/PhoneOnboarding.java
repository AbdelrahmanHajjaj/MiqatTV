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
        return Math.max(0, Math.min(1, PhoneStore.preferences(a).getInt("onboarding-step", 0)));
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
        a.body.addView(a.views.heading(a.t(step == 0 ? "setup_essentials" : "setup_appearance")));
        try {
            if (step == 0) {
                a.phoneUi.locationUi.locationEditor();
                a.phoneUi.locationUi.confirmLocation();
                a.views
                        .toggle(
                                a.body,
                                a.t(
                                        a.phoneUi
                                                .data
                                                .controls
                                                .get("reminders.enabled")
                                                .getString("label")),
                                wantsAlerts(),
                                value -> {
                                    if (!PhoneStore.preferences(a)
                                            .edit()
                                            .putBoolean("onboarding-alerts", value)
                                            .commit()) a.notice("phone_save_failed");
                                    a.render();
                                })
                        .setTag("phone.reminders.enabled.edit");
                if (wantsAlerts())
                    a.body.addView(
                            a.views.text(
                                    a.t("phone_notifications")
                                            + " · "
                                            + a.t(PhoneAlarms.notifications(a) ? "on" : "off")
                                            + "  ·  "
                                            + a.t("phone_exact")
                                            + " · "
                                            + a.t(PhoneAlarms.exact(a) ? "on" : "off"),
                                    13,
                                    a.muted));
            } else a.phoneUi.appearance.editor(a.body, true);
        } catch (Exception invalid) {
            a.body.addView(a.views.text(a.t("phone_settings_failed"), 16, a.ink));
        }
    }

    void footer(LinearLayout shell) {
        int step = step();
        LinearLayout footer = a.views.actionBar(shell);
        footer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        if (step == 0)
            a.views
                    .secondaryAction(
                            a.views.secondaryActions(footer), a.t("setup_skip"), () -> advance(1))
                    .setTag("onboarding.skip");
        boolean alerts = step == 0 && a.phoneUi.settings != null && wantsAlerts();
        boolean notifications = alerts && !PhoneAlarms.notifications(a);
        boolean exact = alerts && !PhoneAlarms.exact(a);
        String label =
                step == 1
                        ? "setup_finish"
                        : a.phoneUi.settings == null || !a.phoneUi.settings.locationReady
                                ? "phone_confirm_location"
                                : notifications
                                        ? "phone_permission_notifications"
                                        : exact ? "phone_permission_exact" : "setup_next";
        android.widget.Button next =
                a.views.primaryAction(
                        footer,
                        a.t(label),
                        () -> {
                            if (step == 1) {
                                finish(true);
                                return;
                            }
                            boolean confirming = !a.phoneUi.settings.locationReady;
                            java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
                            values.put("prayer.location-ready", "true");
                            values.put("reminders.enabled", Boolean.toString(wantsAlerts()));
                            if (!a.phoneUi.save(values, false)) return;
                            if (confirming && (notifications || exact)) return;
                            if (notifications) a.phoneUi.requestNotifications();
                            else if (exact) a.phoneUi.requestExact();
                            else advance(1);
                        });
        next.setTag("onboarding.next");
        next.setEnabled(a.phoneUi.settings != null && !a.phoneUi.locationUi.locator.running);
    }

    private boolean wantsAlerts() {
        return PhoneStore.preferences(a)
                .getBoolean("onboarding-alerts", a.phoneUi.settings.enabled);
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
        boolean done = complete || !needed();
        android.content.SharedPreferences.Editor edit =
                PhoneStore.preferences(a)
                        .edit()
                        .putBoolean("onboarding-complete", done)
                        .putBoolean("onboarding-seen", true);
        if (done) edit.remove("onboarding-alerts");
        if (!edit.commit()) {
            a.notice("phone_save_failed");
            return;
        }
        a.phoneUi.resetNavigation();
        a.navigate("prayers");
    }
}

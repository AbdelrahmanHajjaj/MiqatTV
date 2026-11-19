package tv.miqat.companion;

import android.widget.LinearLayout;

import org.json.JSONObject;

/** Resumable first use. Preferences are saved by the same editors as ordinary settings. */
final class PhoneOnboarding {
    private final MainActivity a;
    private android.graphics.Bitmap artwork;

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
            "setup_alerts",
            "phone_notifications",
            "phone_exact",
            "phone_theme",
            "phone_background"
        };
        android.widget.TextView title = a.views.heading(a.t(titles[step]));
        title.setTag("onboarding.title");
        a.body.addView(title);
        try {
            switch (step) {
                case 0:
                    a.phoneUi.locationUi.locationEditor();
                    a.phoneUi.locationUi.confirmLocation();
                    break;
                case 1:
                    control("reminders.enabled");
                    break;
                case 2:
                case 3:
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
        LinearLayout footer = a.views.actionBar(shell);
        footer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        LinearLayout secondary = a.views.secondaryActions(footer);
        a.views
                .secondaryAction(
                        secondary,
                        a.t("setup_skip"),
                        () -> {
                            if (step == 5) finish(true);
                            else advance(step + 1);
                        })
                .setTag("onboarding.skip");
        boolean notification = step == 2 && !PhoneAlarms.notifications(a);
        boolean exact = step == 3 && !PhoneAlarms.exact(a);
        boolean confirm =
                step == 0 && a.phoneUi.settings != null && !a.phoneUi.settings.locationReady;
        android.widget.Button next =
                a.views.primaryAction(
                        footer,
                        a.t(
                                notification
                                        ? "phone_permission_notifications"
                                        : exact
                                                ? "phone_permission_exact"
                                                : confirm
                                                        ? "phone_confirm_location"
                                                        : step == 5
                                                                ? "setup_finish"
                                                                : "setup_next"),
                        () -> {
                            if (notification) a.phoneUi.requestNotifications();
                            else if (exact) a.phoneUi.requestExact();
                            else if (confirm) a.phoneUi.save("prayer.location-ready", "true");
                            else if (step == 5) finish(true);
                            else advance(step + 1);
                        });
        next.setTag("onboarding.next");
        next.setEnabled(a.phoneUi.settings != null && !a.phoneUi.locationUi.locator.running);
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

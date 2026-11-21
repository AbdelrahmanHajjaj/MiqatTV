package tv.miqat.companion;

import android.os.Build;
import android.widget.*;

/** Resumable welcome, location confirmation and full-page personalization. */
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

    int step() {
        android.content.SharedPreferences prefs = PhoneStore.preferences(a);
        if (!prefs.getBoolean("onboarding-welcome", false)) return 0;
        return Math.max(0, Math.min(2, prefs.getInt("onboarding-step", 0)));
    }

    boolean style() {
        return step() == 2;
    }

    void background(LinearLayout shell) {
        if (style()) a.phoneUi.appearance.background(shell, a.phoneUi.settings);
        else {
            try {
                shell.setBackground(
                        new android.graphics.drawable.LayerDrawable(
                                new android.graphics.drawable.Drawable[] {
                                    new PhoneBackdrop(PhoneBackdrop.read(a, "miqat_signature")),
                                    new android.graphics.drawable.ColorDrawable(
                                            (a.ground & 0xffffff) | 0x55000000)
                                }));
            } catch (java.io.IOException unavailable) {
                android.util.Log.w("MiqatSetup", "Background unavailable", unavailable);
            }
        }
    }

    void open() {
        if (!needed()
                && !PhoneStore.preferences(a)
                        .edit()
                        .putBoolean("onboarding-welcome", true)
                        .putInt("onboarding-step", 0)
                        .commit()) {
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
        if (step() == 0) {
            a.body.setGravity(android.view.Gravity.CENTER);
            ImageView mark = new ImageView(a);
            mark.setImageResource(R.drawable.miqat_mark);
            mark.setContentDescription(a.ar ? "ميقات" : "Miqat");
            a.body.addView(mark, new LinearLayout.LayoutParams(-1, a.views.dp(150)));
            TextView title = a.views.heading(a.t("setup_welcome"));
            title.setTextSize(36);
            title.setGravity(android.view.Gravity.CENTER);
            title.setTextAlignment(android.view.View.TEXT_ALIGNMENT_CENTER);
            a.body.addView(title);
            for (String key :
                    new String[] {
                        "setup_welcome_tagline", "setup_welcome_body", "setup_welcome_private"
                    }) {
                TextView text =
                        a.views.text(
                                a.t(key), key.equals("setup_welcome_private") ? 12 : 16, a.ink);
                text.setGravity(android.view.Gravity.CENTER);
                text.setTextAlignment(android.view.View.TEXT_ALIGNMENT_CENTER);
                a.body.addView(text);
            }
            return;
        }
        a.body.addView(a.views.heading(a.t(style() ? "setup_appearance" : "setup_essentials")));
        try {
            if (style()) a.phoneUi.appearance.editor(a.body, true);
            else {
                a.body.addView(a.views.text(a.t("phone_location_intro"), 15, a.ink));
                a.phoneUi.locationUi.locationEditor();
                a.phoneUi.locationUi.confirmLocation();
            }
        } catch (Exception invalid) {
            a.body.addView(a.views.text(a.t("phone_settings_failed"), 16, a.ink));
        }
    }

    void footer(LinearLayout shell) {
        LinearLayout footer = a.views.actionBar(shell);
        footer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        if (!style())
            a.views
                    .secondaryAction(
                            a.views.secondaryActions(footer),
                            a.t(step() == 0 ? "setup_explore" : "setup_later"),
                            () -> {
                                if (step() == 0) finish(false);
                                else advance(2);
                            })
                    .setTag("onboarding.skip");
        a.views
                .primaryAction(
                        footer,
                        a.t(
                                step() == 0
                                        ? "setup_start"
                                        : style() ? "setup_finish" : "phone_confirm_location"),
                        () -> {
                            if (step() == 0) {
                                advance(1);
                                return;
                            }
                            if (style()) {
                                finish(true);
                                return;
                            }
                            if (a.phoneUi.settings == null || a.phoneUi.locationUi.locator.running)
                                return;
                            if (!a.phoneUi.locationUi.countryMatches()) {
                                a.notice("phone_choose_city");
                                return;
                            }
                            boolean request =
                                    needed()
                                            && PhoneStore.preferences(a)
                                                    .getBoolean(
                                                            "onboarding-enable-with-permission",
                                                            false)
                                            && Build.VERSION.SDK_INT >= 33
                                            && a.checkSelfPermission(
                                                            android.Manifest.permission
                                                                    .POST_NOTIFICATIONS)
                                                    != android.content.pm.PackageManager
                                                            .PERMISSION_GRANTED
                                            && !PhoneAlarms.notifications(a)
                                            && !PhoneStore.preferences(a)
                                                    .getBoolean(
                                                            "onboarding-notifications-asked",
                                                            false);
                            java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
                            values.put("prayer.location-ready", "true");
                            if (needed()
                                    && PhoneStore.preferences(a)
                                            .getBoolean("onboarding-enable-with-permission", false))
                                values.put(
                                        "reminders.enabled",
                                        Boolean.toString(PhoneAlarms.notifications(a)));
                            if (!a.phoneUi.save(values, false)) return;
                            if (!PhoneStore.preferences(a)
                                    .edit()
                                    .putBoolean("onboarding-notifications-asked", true)
                                    .putBoolean("onboarding-notifications-pending", request)
                                    .commit()) {
                                a.notice("phone_save_failed");
                                return;
                            }
                            advance(2);
                            if (request) a.phoneUi.requestNotifications();
                        })
                .setTag("onboarding.next");
    }

    void notificationResult() {
        if (!PhoneStore.preferences(a).getBoolean("onboarding-notifications-pending", false))
            return;
        if (!PhoneStore.preferences(a).edit().remove("onboarding-notifications-pending").commit()) {
            a.notice("phone_save_failed");
            return;
        }
        a.phoneUi.save("reminders.enabled", Boolean.toString(PhoneAlarms.notifications(a)));
    }

    private void advance(int next) {
        a.phoneUi.locationUi.cancelLocation();
        android.content.SharedPreferences prefs = PhoneStore.preferences(a);
        if (next == 1 && !prefs.contains("onboarding-enable-with-permission")) {
            boolean allow =
                    !prefs.contains("settings")
                            || a.phoneUi.settings.enabled
                            || prefs.getBoolean("onboarding-alerts", false);
            if (!prefs.edit().putBoolean("onboarding-enable-with-permission", allow).commit()) {
                a.notice("phone_save_failed");
                return;
            }
        }
        if (!PhoneStore.preferences(a)
                .edit()
                .putBoolean("onboarding-welcome", true)
                .putInt("onboarding-step", next)
                .commit()) {
            a.notice("phone_save_failed");
            return;
        }
        a.render();
    }

    private void finish(boolean complete) {
        boolean done = complete || !needed();
        android.content.SharedPreferences prefs = PhoneStore.preferences(a);
        android.content.SharedPreferences.Editor edit =
                prefs.edit()
                        .putBoolean("onboarding-complete", done)
                        .putBoolean("onboarding-seen", true);
        if (!prefs.contains("settings") && !prefs.contains("onboarding-enable-with-permission"))
            edit.putBoolean("onboarding-enable-with-permission", true);
        if (!edit.commit()) {
            a.notice("phone_save_failed");
            return;
        }
        a.phoneUi.resetNavigation();
        a.navigate("prayers");
    }
}

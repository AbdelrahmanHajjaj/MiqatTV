package tv.miqat.companion;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.Switch;

import java.util.LinkedHashMap;
import java.util.Map;

/** An explicit background opt-in, separate from older foreground travel preferences. */
final class PhoneBackgroundPermission {
    private final MainActivity a;
    private final PhoneUi ui;
    private int stage;
    private boolean leftForSettings;
    private String location = "";

    PhoneBackgroundPermission(MainActivity activity, PhoneUi phoneUi) {
        a = activity;
        ui = phoneUi;
    }

    void save(Bundle state) {
        state.putInt("background-permission-stage", stage);
        state.putBoolean("background-permission-left", leftForSettings);
        state.putString("background-permission-location", location);
    }

    void restore(Bundle state) {
        stage = state.getInt("background-permission-stage", 0);
        leftForSettings = state.getBoolean("background-permission-left");
        location = state.getString("background-permission-location", "");
    }

    void control(LinearLayout parent) {
        LinearLayout row = a.views.card(parent);
        Switch toggle = a.views.toggle(row, a.t("phone_automatic_location"),
                ui.settings.value("prayer.background-location").equals("true")
                        && PhoneBackgroundLocation.allowed(a), on -> {
                    if (on) begin();
                    else set(false);
                });
        toggle.setTag("phone.prayer.background-location.edit");
        row.setTag("phone.prayer.background-location");
        row.addView(a.views.text(a.t("phone_background_location_summary"), 11, a.muted));
        String error = PhoneStore.preferences(a).getString("background-location-error", "");
        if (!error.isEmpty()) row.addView(a.views.text(a.t(error), 12, a.accent));
    }

    private static String fingerprint(PhoneStore settings) {
        return settings.value("prayer.location") + ":" + settings.latitude + ":" + settings.longitude
                + ":" + settings.value("prayer.zone") + ":" + settings.locationReady;
    }

    void begin() {
        if (!ui.settings.locationReady) {
            a.notice("phone_location_needed");
            ui.open("calculation");
            return;
        }
        location = fingerprint(ui.settings);
        if (PhoneBackgroundLocation.allowed(a)) {
            set(true);
            return;
        }
        String option = Build.VERSION.SDK_INT >= 30
                ? a.getPackageManager().getBackgroundPermissionOptionLabel().toString()
                : a.t("phone_background_allow_always");
        AlertDialog dialog = a.views.dialog()
                .setTitle(a.t("phone_background_location_title"))
                .setMessage(a.t("phone_background_location_body") + (Build.VERSION.SDK_INT >= 29 ? "\n\n"
                        + String.format(ui.locale(), a.t("phone_background_location_grant"), option) : ""))
                .setNegativeButton(a.t("phone_not_now"), (d, which) -> a.render())
                .setPositiveButton(a.t("phone_background_continue"), (d, which) -> request())
                .create();
        dialog.setOnCancelListener(d -> a.render());
        dialog.show();
        a.views.styleDialog(dialog);
        a.render();
    }

    private void request() {
        if (!PhoneLocation.allowed(a)) {
            stage = 1;
            a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 80);
        } else requestBackground();
    }

    @android.annotation.SuppressLint("ObsoleteSdkInt") // API 29 alone has the separate runtime background prompt.
    private void requestBackground() {
        if (PhoneBackgroundLocation.allowed(a)) {
            set(true);
        } else if (Build.VERSION.SDK_INT == 29) {
            stage = 2;
            a.requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 81);
        } else {
            stage = 3;
            leftForSettings = false;
            try {
                a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + a.getPackageName())));
            } catch (android.content.ActivityNotFoundException unavailable) {
                stage = 0;
                a.notice("phone_background_location_denied");
                a.render();
            }
        }
    }

    void result(int request) {
        if (request == 80 && stage == 1) {
            stage = 0;
            if (PhoneLocation.allowed(a)) requestBackground();
            else declined();
        } else if (request == 81 && stage == 2) {
            stage = 0;
            if (PhoneBackgroundLocation.allowed(a)) set(true);
            else declined();
        }
    }

    void pause() {
        if (stage == 3) leftForSettings = true;
    }

    void resume() {
        if (stage == 3 && leftForSettings) {
            stage = 0;
            if (PhoneBackgroundLocation.allowed(a)) set(true);
            else declined();
        } else if (ui.settings != null
                && ui.settings.value("prayer.background-location").equals("true")
                && !PhoneBackgroundLocation.allowed(a)) {
            set(false);
        }
        PhoneBackgroundLocation.sync(a);
    }

    private void declined() {
        stage = 0;
        a.notice("phone_background_location_denied");
        a.render();
    }

    private void set(boolean enabled) {
        stage = 0;
        try {
            PhoneStore current = PhoneStore.read(a);
            if (enabled && (!PhoneBackgroundLocation.allowed(a) || !current.locationReady
                    || !fingerprint(current).equals(location))) {
                declined();
                return;
            }
            Map<String, String> changes = new LinkedHashMap<>();
            changes.put("prayer.background-location", Boolean.toString(enabled));
            changes.put("prayer.travel-refresh", Boolean.toString(enabled));
            if (enabled) changes.put("prayer.zone", "system");
            if (ui.save(changes, false) && enabled) {
                ui.locationUi.travelAttempt = 0;
                ui.locationUi.refreshTravel();
            }
        } catch (Exception invalid) {
            a.notice("phone_save_failed");
            a.render();
        }
    }
}

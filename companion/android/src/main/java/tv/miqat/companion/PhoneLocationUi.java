package tv.miqat.companion;

import android.app.AlertDialog;
import android.content.Intent;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.*;

import org.json.JSONObject;

import java.util.*;

/** Location editing and foreground fix session; never changes a TV's calibration. */
final class PhoneLocationUi {
    final MainActivity a;
    final PhoneUi ui;
    final PhoneLocation locator;
    boolean locationPermissionPending, advancedLocation, travelRequest;
    long travelAttempt;
    String locationMessage = "";
    AlertDialog locationDialog;
    String latitude = "", longitude = "";

    PhoneLocationUi(MainActivity activity, PhoneUi ui) {
        a = activity;
        this.ui = ui;
        locator = new PhoneLocation(a);
    }

    void cancelLocation() {
        if (locator.running)
            locationMessage = travelRequest ? "phone_travel_failed" : "phone_location_cancelled";
        locator.close();
        locationPermissionPending = false;
        if (locationDialog != null) {
            locationDialog.dismiss();
            locationDialog = null;
        }
    }

    void locationEditor() throws Exception {
        a.views
                .button(
                        a.body,
                        a.t(locator.running ? "phone_location_cancel" : "phone_use_location"),
                        () -> {
                            if (locator.running) {
                                cancelLocation();
                                a.render();
                            } else useLocation();
                        })
                .setTag("phone.location.detect");
        if (!locationMessage.isEmpty())
            a.body.addView(a.views.text(a.t(locationMessage), 14, a.accent));
        if (locationMessage.equals("phone_location_disabled"))
            a.views.button(
                    a.body,
                    a.t("phone_location_settings"),
                    () -> ui.system(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        if (locationMessage.equals("phone_location_denied"))
            a.views.button(
                    a.body,
                    a.t("phone_app_settings"),
                    () ->
                            ui.system(
                                    new Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            android.net.Uri.parse(
                                                    "package:" + a.getPackageName()))));

        a.views
                .button(
                        a.body,
                        ui.location() + "  ⌄",
                        () -> {
                            java.util.List<Choices.Option> options = new ArrayList<>();
                            try {
                                for (int i = 0; i < ui.data.cities.length(); i++) {
                                    JSONObject c = ui.data.cities.getJSONObject(i);
                                    options.add(
                                            new Choices.Option(
                                                    c.getString("id"),
                                                    a.catalog.label(c, a.ar)
                                                            + " · "
                                                            + c.optString(
                                                                    a.ar
                                                                            ? "countryAr"
                                                                            : "countryEn")));
                                }
                            } catch (Exception invalid) {
                                a.notice("invalid");
                                return;
                            }
                            ui.picker(
                                    a.t("phone_choose_location"),
                                    options,
                                    id -> {
                                        try {
                                            JSONObject city = ui.data.city(id);
                                            Map<String, String> changes = new LinkedHashMap<>();
                                            changes.put("prayer.location", id);
                                            changes.put("prayer.latitude", city.getString("lat"));
                                            changes.put("prayer.longitude", city.getString("lng"));
                                            changes.put("prayer.method", city.getString("method"));
                                            changes.put(
                                                    "prayer.asr",
                                                    city.optBoolean("hanafi") ? "hanafi" : "shafi");
                                            changes.put("prayer.location-ready", "false");
                                            latitude = "";
                                            longitude = "";
                                            ui.save(changes, false);
                                        } catch (Exception invalid) {
                                            a.notice("invalid");
                                        }
                                    });
                        })
                .setTag("phone.city");
        if (!a.page.equals("onboarding"))
            a.views
                    .button(
                            a.body,
                            a.t("phone_advanced"),
                            () -> {
                                advancedLocation = !advancedLocation;
                                a.render();
                            })
                    .setTag("phone.location.advanced");
        if (advancedLocation) {
            LinearLayout custom = a.views.card(a.body);
            custom.addView(a.views.text(a.t("phone_custom_location"), 16, a.ink));
            if (latitude.isEmpty()) {
                latitude = ui.settings.value("prayer.latitude");
                longitude = ui.settings.value("prayer.longitude");
            }
            EditText lat = coordinate(custom, "phone_latitude", latitude),
                    lng = coordinate(custom, "phone_longitude", longitude);
            lat.addTextChangedListener(MainActivity.watcher(v -> latitude = v));
            lng.addTextChangedListener(MainActivity.watcher(v -> longitude = v));
            a.actionHost.removeAllViews();
            LinearLayout actions = a.views.actionBar(a.actionHost);
            LinearLayout secondary = a.views.secondaryActions(actions);
            a.views
                    .primaryAction(
                            actions,
                            a.t("done"),
                            () -> {
                                try {
                                    double x = Double.parseDouble(PhoneUi.normalize(latitude)),
                                            y = Double.parseDouble(PhoneUi.normalize(longitude));
                                    if (!Double.isFinite(x)
                                            || !Double.isFinite(y)
                                            || Math.abs(x) > 90
                                            || Math.abs(y) > 180)
                                        throw new IllegalArgumentException();
                                    Map<String, String> values = new LinkedHashMap<>();
                                    values.put("prayer.location", "custom");
                                    values.put("prayer.latitude", Double.toString(x));
                                    values.put("prayer.longitude", Double.toString(y));
                                    values.put("prayer.location-ready", "false");
                                    advancedLocation = false;
                                    if (!ui.save(values, false)) {
                                        advancedLocation = true;
                                        a.render();
                                    }
                                } catch (Exception invalid) {
                                    lat.setError(a.t("invalid"));
                                }
                            })
                    .setTag("phone.coordinates.save");
            a.views.secondaryAction(
                    secondary,
                    a.t("cancel"),
                    () -> {
                        latitude = "";
                        longitude = "";
                        advancedLocation = false;
                        a.render();
                    });
            custom.addView(
                    a.views.text(ui.settings.latitude + ", " + ui.settings.longitude, 13, a.muted));
        }
    }

    void confirmLocation() {
        a.body.addView(a.views.text(ui.settings.zone().getID(), 14, a.muted));
        if (a.page.equals("onboarding")
                || ui.settings.locationReady
                || a.actionHost.getChildCount() > 0) return;
        a.views
                .primaryAction(
                        a.views.actionBar(a.actionHost),
                        a.t("phone_confirm_location"),
                        () -> ui.save("prayer.location-ready", "true"))
                .setTag("phone.location.confirm");
    }

    void refreshTravel() {
        if (ui.settings == null
                || !ui.settings.locationReady
                || !ui.settings.value("prayer.travel-refresh").equals("true")
                || a.networkBusy
                || locator.running
                || !a.phoneForeground
                || !a.page.equals("prayers")
                        && !(a.page.equals("phone-settings") && a.section.equals("calculation")))
            return;
        long now = System.currentTimeMillis(),
                last =
                        Math.max(
                                travelAttempt,
                                PhoneStore.preferences(a).getLong("location-refresh", 0));
        if (now >= last && now - last < 1800000) return;
        travelAttempt = now;
        if (!PhoneLocation.allowed(a)) {
            locationMessage = "phone_travel_failed";
            a.render();
            return;
        }
        travelRequest = true;
        locationMessage = "phone_location_working";
        locator.request(
                fix -> {
                    if (!a.phoneForeground) return;
                    try {
                        JSONObject city = ui.data.nearest(fix.getLatitude(), fix.getLongitude());
                        Map<String, String> values = new LinkedHashMap<>();
                        values.put(
                                "prayer.location",
                                city == null ? "detected" : "detected:" + city.getString("id"));
                        values.put("prayer.latitude", Double.toString(fix.getLatitude()));
                        values.put("prayer.longitude", Double.toString(fix.getLongitude()));
                        PhoneStore.read(a).changed(values).save(a);
                        PhoneStore.preferences(a)
                                .edit()
                                .putLong("location-refresh", System.currentTimeMillis())
                                .apply();
                        locationMessage = "";
                        PhoneAlarms.rebuild(a);
                        a.render();
                    } catch (Exception failed) {
                        locationMessage = "phone_travel_failed";
                        a.render();
                    }
                },
                failure -> {
                    locationMessage = "phone_travel_failed";
                    if (!a.isDestroyed()) a.render();
                });
        if (locator.running) a.render();
    }

    void useLocation() {
        travelRequest = false;
        if (!PhoneLocation.allowed(a)) {
            locationPermissionPending = true;
            a.requestPermissions(
                    new String[] {
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    78);
            return;
        }
        locationMessage = "phone_location_working";
        locator.request(
                value -> {
                    if (a.phoneForeground) offerLocation(value);
                },
                failure -> {
                    locationMessage = failure;
                    if (!a.isDestroyed()) a.render();
                });
        if (locator.running) a.render();
    }

    void locationPermissionResult() {
        if (!locationPermissionPending) return;
        locationPermissionPending = false;
        if (PhoneLocation.allowed(a)) useLocation();
        else {
            locationMessage = "phone_location_denied";
            a.render();
        }
    }

    void offerLocation(android.location.Location fix) {
        try {
            JSONObject city = ui.data.nearest(fix.getLatitude(), fix.getLongitude());
            Map<String, String> changes = new LinkedHashMap<>();
            changes.put(
                    "prayer.location",
                    city == null ? "detected" : "detected:" + city.getString("id"));
            changes.put("prayer.latitude", Double.toString(fix.getLatitude()));
            changes.put("prayer.longitude", Double.toString(fix.getLongitude()));
            changes.put("prayer.zone", "system");
            changes.put("prayer.location-ready", "true");
            if (!ui.settings.locationReady && city != null) {
                changes.put("prayer.method", city.getString("method"));
                changes.put("prayer.asr", city.optBoolean("hanafi") ? "hanafi" : "shafi");
            }
            String place =
                    city == null
                            ? a.t("phone_current_location")
                            : String.format(
                                    ui.locale(),
                                    a.t("phone_near_city"),
                                    a.catalog.label(city, a.ar));
            String method =
                    ui.value(
                            "prayer.method",
                            changes.getOrDefault(
                                    "prayer.method", ui.settings.value("prayer.method")));
            locationMessage = "";
            locationDialog =
                    a.views
                            .dialog()
                            .setTitle(a.t("phone_location_found"))
                            .setMessage(
                                    place + "\n" + TimeZone.getDefault().getID() + "\n" + method)
                            .setNegativeButton(
                                    a.t("cancel"),
                                    (d, w) -> {
                                        locationDialog = null;
                                        a.render();
                                    })
                            .setPositiveButton(
                                    a.t("phone_confirm_location"),
                                    (d, w) -> {
                                        locationDialog = null;
                                        if (!PhoneLocation.usable(
                                                fix,
                                                android.os.SystemClock.elapsedRealtimeNanos())) {
                                            locationMessage = "phone_location_timeout";
                                            a.render();
                                            return;
                                        }
                                        latitude = "";
                                        longitude = "";
                                        ui.save(changes, false);
                                    })
                            .show();
        } catch (Exception invalid) {
            locationMessage = "phone_location_timeout";
            a.render();
        }
    }

    EditText coordinate(LinearLayout parent, String key, String value) {
        TextView label = a.views.text(a.t(key), 14, a.muted);
        parent.addView(label);
        EditText field = new EditText(a);
        field.setImeOptions(
                field.getImeOptions() | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);

        field.setId(View.generateViewId());
        label.setLabelFor(field.getId());
        field.setTextColor(a.ink);
        field.setTypeface(a.regular);
        field.setSingleLine(true);
        field.setTextDirection(View.TEXT_DIRECTION_LTR);
        field.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED);
        field.setText(value);
        field.setTag(key);
        parent.addView(field);
        return field;
    }
}

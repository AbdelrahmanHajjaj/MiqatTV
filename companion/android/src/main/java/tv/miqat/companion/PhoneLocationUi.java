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
    String latitude = "", longitude = "", selectedCountry = "";

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

    private String country(JSONObject city, boolean arabic) {
        return city.optString(arabic ? "countryAr" : "countryEn");
    }

    private JSONObject currentCity() throws Exception {
        String id = ui.settings.value("prayer.location");
        return ui.data.city(id.startsWith("detected:") ? id.substring(9) : id);
    }

    boolean countryMatches() {
        try {
            return selectedCountry.isEmpty()
                    || country(currentCity(), false).equals(selectedCountry);
        } catch (Exception unknown) {
            return selectedCountry.isEmpty();
        }
    }

    void locationEditor() throws Exception {
        JSONObject city = null;
        try {
            city = currentCity();
        } catch (Exception unknown) {
            /* Coordinates need no catalog match. */
        }
        if (selectedCountry.isEmpty() && city != null) selectedCountry = country(city, false);
        LinearLayout panel = a.views.card(a.body);
        LinearLayout countryRow = a.views.horizontal(panel);
        LinearLayout field = a.views.column();
        countryRow.addView(field, new LinearLayout.LayoutParams(0, -2, 1));
        field.addView(a.views.text(a.t("phone_country"), 13, a.muted));
        String countryLabel = a.t("phone_choose_country");
        if (!selectedCountry.isEmpty()) {
            for (int i = 0; i < ui.data.cities.length(); i++) {
                JSONObject item = ui.data.cities.getJSONObject(i);
                if (country(item, false).equals(selectedCountry)) {
                    countryLabel = country(item, a.ar);
                    break;
                }
            }
        }
        a.views.button(field, countryLabel + "  ⌄", this::chooseCountry).setTag("phone.country");
        a.views
                .iconButton(
                        countryRow,
                        locator.running ? "close" : "compass",
                        a.t(locator.running ? "phone_location_cancel" : "phone_use_location"),
                        () -> {
                            if (locator.running) {
                                cancelLocation();
                                a.render();
                            } else useLocation();
                        })
                .setTag("phone.location.detect");
        panel.addView(a.views.text(a.t("phone_city"), 13, a.muted));
        boolean matches = city != null && country(city, false).equals(selectedCountry);
        a.views
                .button(
                        panel,
                        (matches || selectedCountry.isEmpty()
                                        ? ui.location()
                                        : a.t("phone_choose_city"))
                                + "  ⌄",
                        this::chooseCity)
                .setTag("phone.city");
        panel.addView(a.views.text(ui.settings.zone().getID(), 13, a.muted));
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
                .navigationRow(a.body, "gear", a.t("phone_advanced"), "", this::advanced)
                .setTag("phone.location.advanced");
    }

    private void chooseCountry() {
        try {
            Map<String, String> countries = new TreeMap<>();
            for (int i = 0; i < ui.data.cities.length(); i++) {
                JSONObject city = ui.data.cities.getJSONObject(i);
                countries.put(country(city, false), country(city, a.ar));
            }
            List<Choices.Option> options = new ArrayList<>();
            for (Map.Entry<String, String> entry : countries.entrySet())
                options.add(new Choices.Option(entry.getKey(), entry.getValue()));
            ui.picker(
                    a.t("phone_choose_country"),
                    options,
                    selectedCountry,
                    selected -> {
                        selectedCountry = selected;
                        a.render();
                        chooseCity();
                    });
        } catch (Exception invalid) {
            a.notice("invalid");
        }
    }

    private void chooseCity() {
        try {
            List<Choices.Option> options = new ArrayList<>();
            for (int i = 0; i < ui.data.cities.length(); i++) {
                JSONObject city = ui.data.cities.getJSONObject(i);
                if (selectedCountry.isEmpty() || country(city, false).equals(selectedCountry))
                    options.add(
                            new Choices.Option(city.getString("id"), a.catalog.label(city, a.ar)));
            }
            ui.picker(
                    a.t("phone_choose_city"),
                    options,
                    ui.settings.value("prayer.location"),
                    id -> {
                        try {
                            JSONObject city = ui.data.city(id);
                            Map<String, String> changes = new LinkedHashMap<>();
                            changes.put("prayer.location", id);
                            changes.put("prayer.latitude", city.getString("lat"));
                            changes.put("prayer.longitude", city.getString("lng"));
                            changes.put("prayer.method", city.getString("method"));
                            changes.put(
                                    "prayer.asr", city.optBoolean("hanafi") ? "hanafi" : "shafi");
                            changes.put("prayer.location-ready", "false");
                            selectedCountry = country(city, false);
                            latitude = "";
                            longitude = "";
                            ui.save(changes, false);
                        } catch (Exception invalid) {
                            a.notice("invalid");
                        }
                    });
        } catch (Exception invalid) {
            a.notice("invalid");
        }
    }

    void advanced() {
        LinearLayout custom = a.views.column();
        custom.setPadding(a.views.dp(20), a.views.dp(12), a.views.dp(20), a.views.dp(12));
        EditText lat = coordinate(custom, "phone_latitude", ui.settings.value("prayer.latitude")),
                lng = coordinate(custom, "phone_longitude", ui.settings.value("prayer.longitude"));
        String[] draftZone = {ui.settings.value("prayer.zone")};
        TextView zoneValue = a.views.text(ui.settings.zone().getID(), 13, a.muted);
        custom.addView(zoneValue);
        Button zone =
                a.views.button(
                        custom,
                        a.t("phone_zone"),
                        () -> {
                            try {
                                List<Choices.Option> options = new ArrayList<>();
                                for (String id : ui.data.options(ui.data.controls.get("prayer.zone")))
                                    options.add(new Choices.Option(id,
                                            id.equals("system") ? a.t("phone_system") : id));
                                ui.picker(
                                        a.t("phone_zone"),
                                        options,
                                        draftZone[0],
                                        selected -> {
                                            draftZone[0] = selected;
                                            zoneValue.setText(selected.equals("system")
                                                    ? TimeZone.getDefault().getID() : selected);
                                        });
                            } catch (Exception invalid) {
                                a.notice("invalid");
                            }
                        });
        zone.setTag("phone.location.zone");
        ScrollView scroll = new ScrollView(a);
        scroll.addView(custom);
        AlertDialog dialog =
                a.views
                        .dialog()
                        .setTitle(a.t("phone_advanced"))
                        .setView(scroll)
                        .setNegativeButton(a.t("cancel"), null)
                        .setPositiveButton(a.t("apply"), null)
                        .create();
        locationDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (locationDialog == dialog) locationDialog = null;
        });
        dialog.show();
        a.views.styleDialog(dialog);
        Button apply = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        apply.setTag("phone.coordinates.save");
        apply.setOnClickListener(v -> {
            try {
                double x = Double.parseDouble(PhoneUi.normalize(lat.getText().toString())),
                        y = Double.parseDouble(PhoneUi.normalize(lng.getText().toString()));
                if (!Double.isFinite(x)
                        || !Double.isFinite(y)
                        || Math.abs(x) > 90
                        || Math.abs(y) > 180) throw new IllegalArgumentException();
                Map<String, String> changes = new LinkedHashMap<>();
                changes.put("prayer.location", "custom");
                changes.put("prayer.latitude", Double.toString(x));
                changes.put("prayer.longitude", Double.toString(y));
                changes.put("prayer.zone", draftZone[0]);
                changes.put("prayer.location-ready", "false");
                if (ui.save(changes, false)) {
                    selectedCountry = "";
                    dialog.dismiss();
                    a.render();
                }
            } catch (Exception invalid) {
                lat.setError(a.t("invalid"));
            }
        });
    }

    void confirmLocation() {
        if (a.page.equals("onboarding")) return;
        if (ui.settings.locationReady || a.actionHost.getChildCount() > 0) return;
        a.views
                .primaryAction(
                        a.views.actionBar(a.actionHost),
                        a.t("phone_confirm_location"),
                        () -> {
                            if (countryMatches()) ui.save("prayer.location-ready", "true");
                            else a.notice("phone_choose_city");
                        })
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
                        selectedCountry = city == null ? "" : country(city, false);
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
            if (!PhoneLocation.usable(fix, android.os.SystemClock.elapsedRealtimeNanos()))
                throw new IllegalArgumentException();
            JSONObject city = ui.data.nearest(fix.getLatitude(), fix.getLongitude());
            Map<String, String> changes = new LinkedHashMap<>();
            changes.put(
                    "prayer.location",
                    city == null ? "detected" : "detected:" + city.getString("id"));
            changes.put("prayer.latitude", Double.toString(fix.getLatitude()));
            changes.put("prayer.longitude", Double.toString(fix.getLongitude()));
            changes.put("prayer.zone", "system");
            changes.put("prayer.location-ready", "false");
            if (!ui.settings.locationReady && city != null) {
                changes.put("prayer.method", city.getString("method"));
                changes.put("prayer.asr", city.optBoolean("hanafi") ? "hanafi" : "shafi");
            }
            latitude = "";
            longitude = "";
            selectedCountry = city == null ? "" : country(city, false);
            locationMessage = "phone_location_found";
            if (ui.save(changes, false) && a.page.equals("prayers")) ui.open("calculation");
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

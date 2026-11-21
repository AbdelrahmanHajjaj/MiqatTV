package tv.miqat.companion;

import android.app.Instrumentation;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Two phases let the owned-emulator runner end/kill the app before forcing its persisted job. */
final class PhoneBackgroundCheck {
    private PhoneBackgroundCheck() {}

    private static File fixture(Context context) {
        return new File(context.getFilesDir(), "background-location-fixture.json");
    }

    static void prepare(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        PhoneUiCheck.check(PhoneBackgroundLocation.allowed(context), "Grant foreground/background location before preparing the closed-app fixture");
        PhoneUiCheck.check(!fixture(context).exists(), "Restore the previous background-location fixture before preparing another");
        PhoneStore original = PhoneStore.read(context);
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("prayer.background-location", "true");
        changes.put("prayer.travel-refresh", "true");
        changes.put("prayer.location-ready", "true");
        changes.put("prayer.location", "cairo");
        changes.put("prayer.latitude", "30.0444");
        changes.put("prayer.longitude", "31.2357");
        changes.put("prayer.zone", "system");
        changes.put("reminders.enabled", "false");
        PhoneStore requested = original.changed(changes);
        JSONObject record = new JSONObject()
                .put("preferences", snapshot(PhoneStore.preferences(context)))
                .put("requested", new JSONObject(requested.values))
                .put("started", System.currentTimeMillis());
        Files.write(fixture(context).toPath(), record.toString().getBytes(StandardCharsets.UTF_8));
        try {
            optionalControl(test, requested, true);
            requested.save(context);
            PhoneAlarms.rebuild(context);
            PhoneUiCheck.check(PhoneBackgroundLocation.sync(context), "Background job was not scheduled");
            JobInfo job = context.getSystemService(JobScheduler.class).getPendingJob(PhoneBackgroundLocation.JOB);
            PhoneUiCheck.check(job != null && job.isPersisted(), "Background refresh must survive app-process death and reboot");
        } catch (Exception | AssertionError failure) {
            restore(context, record);
            throw failure;
        }
    }

    static void verify(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        byte[] bytes = Files.readAllBytes(fixture(context).toPath());
        PhoneUiCheck.check(bytes.length <= 1048576, "Unexpected background fixture size");
        JSONObject record = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        try {
            PhoneStore updated = PhoneStore.read(context);
            PhoneUiCheck.check(tv.miqat.core.LocationRules.INSTANCE.distanceKm(
                            updated.latitude, updated.longitude, 51.5074, -0.1278) < 20,
                    "Closed-app job did not persist the injected London coordinates: "
                            + PhoneStore.preferences(context).getString("background-location-error", ""));
            PhoneUiCheck.check(updated.value("prayer.location").startsWith("detected")
                            && updated.locationReady
                            && PhoneStore.preferences(context).getLong("location-refresh", 0) >= record.getLong("started"),
                    "Background fix lost detected-location identity, readiness or update timestamp");
            JSONObject requested = record.getJSONObject("requested");
            for (String key : updated.values.keySet())
                if (!key.equals("prayer.location") && !key.equals("prayer.latitude") && !key.equals("prayer.longitude"))
                    PhoneUiCheck.check(updated.value(key).equals(requested.getString(key)),
                            "Background refresh changed a prayer preference: " + key);
            PhoneUiCheck.check(!PhonePlayback.playing(), "Background location started prayer output");
            cancelledFixes(context);
        } finally {
            restore(context, record);
        }
    }

    static void denied(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        PhoneUiCheck.check(!PhoneBackgroundLocation.allowed(context), "Revoke background access before the denial fixture");
        PhoneStore original = PhoneStore.read(context);
        try {
            optionalControl(test, original, false);
            original.changed(java.util.Collections.singletonMap("prayer.background-location", "true")).save(context);
            PhoneBackgroundLocation.sync(context);
            PhoneUiCheck.check(context.getSystemService(JobScheduler.class).getPendingJob(PhoneBackgroundLocation.JOB) == null
                            && PhoneStore.read(context).latitude == original.latitude
                            && PhoneStore.read(context).longitude == original.longitude,
                    "Denied background access scheduled a job or changed the saved location");
        } finally {
            original.save(context);
            PhoneBackgroundLocation.sync(context);
        }
    }

    private static void optionalControl(Instrumentation test, PhoneStore fixture, boolean granted) throws Exception {
        Context context = test.getTargetContext();
        PhoneStore original = PhoneStore.read(context);
        SharedPreferences preferences = PhoneStore.preferences(context);
        boolean hadOnboarding = preferences.contains("onboarding-seen");
        boolean onboarding = preferences.getBoolean("onboarding-seen", false);
        MainActivity activity = null;
        try {
            Map<String, String> changes = new LinkedHashMap<>();
            changes.put("prayer.background-location", "false");
            changes.put("prayer.travel-refresh", "false");
            changes.put("prayer.location-ready", "true");
            changes.put("reminders.enabled", "false");
            PhoneStore manual = fixture.changed(changes);
            PhoneBackgroundLocation.cancel(context);
            manual.save(context);
            PhoneUiCheck.check(preferences.edit().putBoolean("onboarding-seen", true).commit(), "Could not prepare location UI");
            activity = LiveUiCheck.launch(test);
            MainActivity a = activity;
            PhoneUiCheck.on(test, () -> {
                a.phoneUi.open("calculation");
                android.widget.Switch toggle = a.body.findViewWithTag("phone.prayer.background-location.edit");
                PhoneUiCheck.check(toggle != null && !toggle.isChecked(), "Optional background location did not start off");
                android.os.Bundle state = new android.os.Bundle();
                a.phoneUi.locationUi.background.save(state);
                PhoneUiCheck.check(state.getInt("background-permission-stage") == 0 && a.hasWindowFocus()
                                && a.phoneUi.settings.values.equals(manual.values),
                        "Opening Location requested permission or changed manual settings");
                toggle.performClick();
            });
            if (!granted) {
                android.view.accessibility.AccessibilityNodeInfo root = test.getUiAutomation().getRootInActiveWindow();
                PhoneUiCheck.check(root != null && !root.findAccessibilityNodeInfosByText(a.t("phone_background_location_title")).isEmpty(),
                        "Optional toggle did not explain background access before requesting it");
                java.util.List<android.view.accessibility.AccessibilityNodeInfo> cancel =
                        root.findAccessibilityNodeInfosByText(a.t("phone_not_now"));
                PhoneUiCheck.check(!cancel.isEmpty() && cancel.get(0).performAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK), "Background disclosure Cancel was unavailable");
                test.waitForIdleSync();
                LiveUiCheck.untilUi(test, a::hasWindowFocus);
            }
            PhoneUiCheck.on(test, () -> {
                android.widget.Switch toggle = a.body.findViewWithTag("phone.prayer.background-location.edit");
                if (granted) {
                    PhoneUiCheck.check(toggle.isChecked()
                                    && a.phoneUi.settings.value("prayer.background-location").equals("true")
                                    && a.phoneUi.settings.value("prayer.travel-refresh").equals("true")
                                    && a.phoneUi.settings.value("prayer.zone").equals("system")
                                    && context.getSystemService(JobScheduler.class).getPendingJob(PhoneBackgroundLocation.JOB) != null,
                            "Explicit granted opt-in did not persist and schedule background updates");
                    toggle.performClick();
                } else {
                    android.os.Bundle state = new android.os.Bundle();
                    a.phoneUi.locationUi.background.save(state);
                    PhoneUiCheck.check(!toggle.isChecked() && a.phoneUi.settings.values.equals(manual.values)
                                    && state.getInt("background-permission-stage") == 0 && a.hasWindowFocus(),
                            "Cancelling background disclosure changed manual settings or opened a permission request");
                }
                PhoneUiCheck.check(a.phoneUi.settings.value("prayer.background-location").equals("false")
                                && context.getSystemService(JobScheduler.class).getPendingJob(PhoneBackgroundLocation.JOB) == null,
                        "Opt-out left background work enabled");
            });
        } finally {
            if (activity != null) {
                MainActivity a = activity;
                PhoneUiCheck.on(test, a::finish);
            }
            PhoneBackgroundLocation.cancel(context);
            original.save(context);
            SharedPreferences.Editor editor = preferences.edit();
            if (hadOnboarding) editor.putBoolean("onboarding-seen", onboarding);
            else editor.remove("onboarding-seen");
            PhoneUiCheck.check(editor.commit(), "Could not restore location UI fixture");
            PhoneBackgroundLocation.sync(context);
        }
    }

    private static void cancelledFixes(Context context) throws Exception {
        PhoneStore requested = PhoneStore.read(context);
        long generation = PhoneBackgroundLocation.generation(context);
        Location fix = new Location("background-regression");
        fix.setLatitude(51.5074);
        fix.setLongitude(-0.1278);
        fix.setAccuracy(3000);
        fix.setTime(System.currentTimeMillis());
        fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos() - 121000000000L);
        PhoneUiCheck.check(!PhoneBackgroundLocation.accept(context, requested, generation, fix), "Background refresh accepted a stale fix");
        Map<String, String> manual = new LinkedHashMap<>();
        manual.put("prayer.location", "cairo");
        manual.put("prayer.latitude", "30.0444");
        manual.put("prayer.longitude", "31.2357");
        requested.changed(manual).save(context);
        fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        PhoneUiCheck.check(!PhoneBackgroundLocation.accept(context, requested, generation, fix)
                        && PhoneStore.read(context).value("prayer.location").equals("cairo"),
                "Late background fix overwrote a newer manual city");
        requested = PhoneStore.read(context);
        generation = PhoneBackgroundLocation.generation(context);
        PhoneBackgroundLocation.cancel(context);
        requested.changed(java.util.Collections.singletonMap("prayer.background-location", "false")).save(context);
        PhoneBackgroundLocation.sync(context);
        requested.save(context);
        PhoneBackgroundLocation.sync(context);
        PhoneUiCheck.check(!PhoneBackgroundLocation.accept(context, requested, generation, fix),
                "An off/on cycle revived an old background request");
        requested.changed(java.util.Collections.singletonMap("prayer.background-location", "false")).save(context);
        PhoneBackgroundLocation.sync(context);
        PhoneUiCheck.check(context.getSystemService(JobScheduler.class).getPendingJob(PhoneBackgroundLocation.JOB) == null,
                "Disabling background updates left a scheduled job");
    }

    private static JSONObject snapshot(SharedPreferences preferences) throws Exception {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            Object value = entry.getValue();
            String type = value instanceof String ? "string" : value instanceof Boolean ? "boolean"
                    : value instanceof Integer ? "int" : value instanceof Long ? "long"
                    : value instanceof Float ? "float" : value instanceof Set ? "set" : "";
            PhoneUiCheck.check(!type.isEmpty(), "Unsupported fixture preference type");
            result.put(entry.getKey(), new JSONObject().put("type", type)
                    .put("value", value instanceof Set ? new JSONArray((Set<?>) value) : value));
        }
        return result;
    }

    private static void restore(Context context, JSONObject record) throws Exception {
        PhoneBackgroundLocation.cancel(context);
        SharedPreferences.Editor editor = PhoneStore.preferences(context).edit().clear();
        JSONObject entries = record.getJSONObject("preferences");
        Iterator<String> keys = entries.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject entry = entries.getJSONObject(key);
            switch (entry.getString("type")) {
                case "string": editor.putString(key, entry.getString("value")); break;
                case "boolean": editor.putBoolean(key, entry.getBoolean("value")); break;
                case "int": editor.putInt(key, entry.getInt("value")); break;
                case "long": editor.putLong(key, entry.getLong("value")); break;
                case "float": editor.putFloat(key, (float) entry.getDouble("value")); break;
                case "set":
                    Set<String> values = new HashSet<>();
                    JSONArray array = entry.getJSONArray("value");
                    for (int i = 0; i < array.length(); i++) values.add(array.getString(i));
                    editor.putStringSet(key, values); break;
                default: throw new IllegalStateException("Invalid fixture preference type");
            }
        }
        PhoneUiCheck.check(editor.commit(), "Cannot restore background location fixture");
        PhoneAlarms.rebuild(context);
        PhoneBackgroundLocation.sync(context);
        Files.delete(fixture(context).toPath());
    }
}

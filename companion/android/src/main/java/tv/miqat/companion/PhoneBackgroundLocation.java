package tv.miqat.companion;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Optional, OS-scheduled travel refresh; no foreground service or network transport. */
final class PhoneBackgroundLocation {
    static final int JOB = 41703;
    private static final String GENERATION = "background-location-generation";
    private static final String[] CALIBRATION = {
        "prayer.location", "prayer.latitude", "prayer.longitude", "prayer.zone",
        "prayer.location-ready", "prayer.background-location"
    };
    private static volatile PhoneLocationJob active;

    private PhoneBackgroundLocation() {}

    static boolean allowed(Context context) {
        return PhoneLocation.allowed(context)
                && (Build.VERSION.SDK_INT < 29
                        || context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                == PackageManager.PERMISSION_GRANTED);
    }

    static boolean enabled(Context context) {
        try {
            PhoneStore settings = PhoneStore.read(context);
            return settings.locationReady
                    && settings.value("prayer.background-location").equals("true")
                    && allowed(context);
        } catch (Exception unavailable) {
            return false;
        }
    }

    static boolean sync(Context context) {
        try {
            Context app = context.getApplicationContext();
            JobScheduler scheduler = app.getSystemService(JobScheduler.class);
            if (!enabled(app)) {
                return cancel(app);
            }
            if (scheduler.getPendingJob(JOB) != null) return true;
            // Android chooses the execution time and can defer it in Doze or restricted battery modes.
            int result = scheduler.schedule(new JobInfo.Builder(JOB, new ComponentName(app, PhoneLocationJob.class))
                    .setPeriodic(30 * 60 * 1000L, 10 * 60 * 1000L)
                    .setPersisted(true)
                    .build());
            PhoneStore.preferences(app).edit()
                    .putString("background-location-error", result == JobScheduler.RESULT_SUCCESS ? "" : "phone_background_unavailable")
                    .apply();
            return result == JobScheduler.RESULT_SUCCESS;
        } catch (RuntimeException unavailable) {
            unavailable(context);
            return false;
        }
    }

    static boolean cancel(Context context) {
        try {
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            PhoneLocationJob running = active;
            if (scheduler.getPendingJob(JOB) == null && running == null) return true;
            final long invalidated;
            final boolean saved;
            synchronized (PhoneStore.LOCK) {
                invalidated = generation(context);
                saved = PhoneStore.preferences(context).edit().putLong(GENERATION, invalidated + 1).commit();
            }
            scheduler.cancel(JOB);
            if (running != null) {
                if (Looper.myLooper() == Looper.getMainLooper()) running.cancelCurrent(invalidated);
                else new Handler(Looper.getMainLooper()).post(() -> running.cancelCurrent(invalidated));
            }
            if (!saved) PhoneStore.preferences(context).edit().putString("background-location-error", "phone_save_failed").apply();
            return saved;
        } catch (RuntimeException unavailable) {
            unavailable(context);
            return false;
        }
    }

    private static void unavailable(Context context) {
        try {
            PhoneStore.preferences(context).edit().putString("background-location-error", "phone_background_unavailable").apply();
        } catch (RuntimeException storageUnavailable) {
            // A best-effort diagnostic must not turn optional refresh into an Activity crash.
        }
    }

    static long generation(Context context) {
        return PhoneStore.preferences(context).getLong(GENERATION, 0);
    }

    static void started(PhoneLocationJob service) {
        active = service;
    }

    static void stopped(PhoneLocationJob service) {
        if (active == service) active = null;
    }

    static boolean accept(Context context, PhoneStore requested, long generation, Location fix) throws Exception {
        synchronized (PhoneStore.LOCK) {
            if (!enabled(context) || generation != generation(context)
                    || !PhoneLocation.usable(fix, SystemClock.elapsedRealtimeNanos())) return false;
            PhoneStore current = PhoneStore.read(context);
            for (String key : CALIBRATION)
                if (!current.value(key).equals(requested.value(key))) return false;
            JSONObject city = current.data.nearest(fix.getLatitude(), fix.getLongitude());
            Map<String, String> changes = new LinkedHashMap<>();
            changes.put("prayer.location", city == null ? "detected" : "detected:" + city.getString("id"));
            changes.put("prayer.latitude", Double.toString(fix.getLatitude()));
            changes.put("prayer.longitude", Double.toString(fix.getLongitude()));
            current.changed(changes).save(context);
            PhoneStore.preferences(context).edit()
                    .putLong("location-refresh", System.currentTimeMillis())
                    .putString("background-location-error", "")
                    .apply();
        }
        PhoneAlarms.rebuild(context);
        return true;
    }
}

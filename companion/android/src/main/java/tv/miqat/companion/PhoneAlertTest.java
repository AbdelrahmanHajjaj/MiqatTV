package tv.miqat.companion;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.JSONObject;
import java.io.IOException;
import java.util.UUID;

/** Explicit one-shot delivery checks; never edits the real schedule or prayer enable flags. */
final class PhoneAlertTest {
    static final String FIRE = "tv.miqat.companion.TEST_PRAYER_ALERT";
    static final long GRACE_MS = 30000, EXPIRY_MS = 120000;
    static final int NOTICE = 42510;
    private static final int REQUEST = 41703;
    private static final String PENDING = "test-event", ACTIVE = "active-test";

    private static AlarmManager manager(Context c) { return c.getSystemService(AlarmManager.class); }
    private static PendingIntent operation(Context c, String id) {
        return PendingIntent.getBroadcast(c, REQUEST, new Intent(c, PrayerAlarm.class)
                .setAction(FIRE).putExtra("event", id), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private static int boot(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }
    private static JSONObject read(Context c, String key) throws Exception {
        String raw = PhoneStore.preferences(c).getString(key, "");
        if (raw.isEmpty()) return null;
        if (raw.length() > 1024) throw new IOException("Invalid test record");
        JSONObject value = new JSONObject(raw);
        String id = value.getString("id"), mode = value.getString("mode");
        long created = value.getLong("created"), due = value.getLong("due");
        int cap = value.getInt("cap");
        if (!id.matches("phone:test:[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                || !(mode.equals("adhan") || mode.equals("notification")) || created < 0
                || due - created != GRACE_MS || due > Long.MAX_VALUE - EXPIRY_MS
                || cap < 0 || cap > 20) throw new IOException("Invalid test record");
        return value;
    }
    private static boolean valid(Context c, JSONObject record, long now) throws Exception {
        return record != null && record.getInt("boot") == boot(c)
                && now >= record.getLong("created") && now < record.getLong("due") + EXPIRY_MS;
    }

    static String schedule(Context c, boolean adhan) { return schedule(c, adhan, 0); }
    static String scheduleFixture(Context c, boolean adhan, int capSeconds) {
        if (capSeconds < 1 || capSeconds > 20) throw new IllegalArgumentException("Invalid fixture cap");
        return schedule(c, adhan, capSeconds);
    }
    private static String schedule(Context c, boolean adhan, int capSeconds) {
        synchronized (PhoneStore.LOCK) {
            try {
                if (PhonePlayback.playing()) return "phone_stop_first";
                if (pending(c)) return "phone_test_pending";
                PhoneAlarms.channels(c);
                PhoneStore settings = PhoneStore.read(c);
                String channel = adhan ? PhoneAlarms.playbackChannel(c, settings.value("reminders.full-screen").equals("true")) : PhoneAlarms.ALERTS;
                if (!PhoneAlarms.channel(c, channel)) return "phone_permission_missing";
                long now = SystemClock.elapsedRealtime();
                JSONObject record = new JSONObject().put("id", "phone:test:" + UUID.randomUUID())
                        .put("mode", adhan ? "adhan" : "notification").put("created", now)
                        .put("due", now + GRACE_MS).put("boot", boot(c)).put("cap", capSeconds);
                if (!PhoneStore.preferences(c).edit().putString(PENDING, record.toString())
                        .remove(ACTIVE).remove("test-result").commit()) throw new IOException("Cannot save test");
                PendingIntent pending = operation(c, record.getString("id"));
                boolean exact = PhoneAlarms.exact(c);
                if (exact) {
                    try { manager(c).setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, now + GRACE_MS, pending); }
                    catch (SecurityException revoked) { exact = false; }
                }
                if (!exact) manager(c).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, now + GRACE_MS, pending);
                return exact ? "phone_test_pending" : "phone_alerts_approximate";
            } catch (Exception failed) {
                cancel(c);
                PhoneStore.preferences(c).edit().putString("test-result", "phone_test_failed").apply();
                return "phone_test_failed";
            }
        }
    }

    static boolean pending(Context c) {
        synchronized (PhoneStore.LOCK) {
            String key = PENDING;
            try {
                JSONObject record = read(c, PENDING);
                if (record == null) {
                    key = ACTIVE;record = read(c, ACTIVE);
                    if (record == null || !record.optString("state").equals("claimed")) return false;
                }
                if (valid(c, record, SystemClock.elapsedRealtime())) return true;
            } catch (Exception invalid) { /* Fail closed for stale or malformed private state. */ }
            PhoneStore.preferences(c).edit().remove(key).putString("test-result", "phone_test_failed").apply();
            return false;
        }
    }
    static long remainingSeconds(Context c) {
        synchronized (PhoneStore.LOCK) {
            try { JSONObject record = read(c, PENDING);return record == null ? 0 : Math.max(0, (record.getLong("due") - SystemClock.elapsedRealtime() + 999) / 1000); }
            catch (Exception invalid) { return 0; }
        }
    }
    // Persist one-shot consumption before any output can begin, including stale callback cleanup.
    @android.annotation.SuppressLint("ApplySharedPref")
    static JSONObject claim(Context c, String id, long now) throws Exception {
        synchronized (PhoneStore.LOCK) {
            JSONObject record = read(c, PENDING);
            if (record == null || !record.getString("id").equals(id)) return null;
            if (!valid(c, record, now)) { PhoneStore.preferences(c).edit().remove(PENDING).putString("test-result", "phone_test_failed").commit();return null; }
            if (now < record.getLong("due")) return null;
            record.put("state", "claimed");
            if (!PhoneStore.preferences(c).edit().remove(PENDING).putString(ACTIVE, record.toString()).commit())
                throw new IOException("Cannot claim test");
            return record;
        }
    }
    static boolean starting(Context c, String id) {
        synchronized (PhoneStore.LOCK) {
            try {
                JSONObject record = read(c, ACTIVE);
                long now = SystemClock.elapsedRealtime();
                if (!valid(c, record, now) || !record.getString("id").equals(id)
                        || !record.getString("mode").equals("adhan") || !record.optString("state").equals("claimed")
                        || now < record.getLong("due")) return false;
                record.put("state", "playing");
                return PhoneStore.preferences(c).edit().putString(ACTIVE, record.toString()).commit();
            } catch (Exception invalid) { return false; }
        }
    }
    static boolean allowed(Context c, String id) {
        synchronized (PhoneStore.LOCK) {
            try {
                JSONObject record = read(c, ACTIVE);
                return record != null && record.getInt("boot") == boot(c) && record.getString("id").equals(id)
                        && record.optString("state").equals("playing") && record.getString("mode").equals("adhan");
            } catch (Exception invalid) { return false; }
        }
    }
    // Complete the durable token before a later callback or service start can inspect it.
    @android.annotation.SuppressLint("ApplySharedPref")
    static void finished(Context c, String id, String result) {
        synchronized (PhoneStore.LOCK) {
            try {
                JSONObject record = read(c, ACTIVE);
                if (record == null || !record.getString("id").equals(id)) return;
                PhoneStore.preferences(c).edit().remove(ACTIVE).putString("test-result", result).commit();
            } catch (Exception invalid) { PhoneStore.preferences(c).edit().remove(ACTIVE).apply(); }
        }
    }
    // Cancellation must be visible to queued receivers before this method returns.
    @android.annotation.SuppressLint("ApplySharedPref")
    static void cancel(Context c) {
        synchronized (PhoneStore.LOCK) {
            manager(c).cancel(operation(c, ""));
            PhoneStore.preferences(c).edit().remove(PENDING).remove(ACTIVE).remove("test-result").commit();
            if (PhonePlayback.currentId().startsWith("phone:test:")) PhonePlayback.stop(c);
            c.getSystemService(NotificationManager.class).cancel(NOTICE);
        }
    }
    static void fire(Context c, String id) {
        try {
            JSONObject record = claim(c, id, SystemClock.elapsedRealtime());
            if (record == null) return;
            if (PhonePlayback.playing()) { finished(c,id,"phone_stop_first");return; }
            if (!PhoneAlarms.notifications(c)) { finished(c,id,"phone_permission_missing");return; }
            if (record.getString("mode").equals("notification")) {
                synchronized (PhoneStore.LOCK) {
                    JSONObject active = read(c, ACTIVE);
                    if (!valid(c, active, SystemClock.elapsedRealtime()) || !active.getString("id").equals(id)
                            || !active.optString("state").equals("claimed")) return;
                    finished(c,id,PhoneAlarms.notifyTest(c,false)?"phone_test_sent":"phone_permission_missing");
                }
                return;
            }
            PhoneStore settings = PhoneStore.read(c);
            if (!PhoneAlarms.channel(c,PhoneAlarms.playbackChannel(c,settings.value("reminders.full-screen").equals("true")))) {
                finished(c,id,"phone_permission_missing");return;
            }
            try {
                c.startForegroundService(PhonePlayback.intent(c,2,settings.value("reminders.voice"),false,id)
                        .putExtra("test-token",id).putExtra("default-audio",true).putExtra("default-voice",true)
                        .putExtra("test-cap-seconds",record.getInt("cap")));
            } catch (RuntimeException unavailable) {
                PhoneAlarms.notifyTest(c,true);finished(c,id,"phone_test_failed");
            }
        } catch (Exception failed) {
            PhoneStore.preferences(c).edit().remove(PENDING).remove(ACTIVE).putString("test-result","phone_test_failed").apply();
        }
    }
}

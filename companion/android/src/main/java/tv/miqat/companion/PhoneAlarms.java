package tv.miqat.companion;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;

import org.json.*;

import tv.miqat.companion.core.PhonePrayerRules;
import tv.miqat.core.ReminderEvent;

import java.util.*;

/** One next prayer event and a bounded daily recovery check; never an idle service. */
final class PhoneAlarms {
    static final String FIRE = "tv.miqat.companion.PRAYER_ALARM",
            REFRESH = "tv.miqat.companion.REFRESH_ALARMS";
    static final String ALERTS = "phone-prayers",
            REMINDERS = "phone-reminders",
            FASTING = "phone-fasting",
            PLAYBACK = "phone-adhan",
            SCREEN = "phone-adhan-screen",
            SILENT = "phone-silent",
            VIBRATION = "phone-vibration",
            ALERTS_VIBRATION = "phone-prayers-vibrate",
            REMINDERS_VIBRATION = "phone-reminders-vibrate";
    private static final int EVENT = 41701, MAINTENANCE = 41702;

    private static AlarmManager manager(Context c) {
        return (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    }

    static boolean exact(Context c) {
        return Build.VERSION.SDK_INT < 31 || manager(c).canScheduleExactAlarms();
    }

    static boolean notifications(Context c) {
        return (Build.VERSION.SDK_INT < 33
                        || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                                == PackageManager.PERMISSION_GRANTED)
                && c.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    static boolean arabic(Context c) {
        boolean unlocked = c.getSystemService(UserManager.class).isUserUnlocked();
        String language =
                unlocked
                        ? c.getSharedPreferences("companion", 0).getString("language", "system")
                        : PhoneStore.preferences(c).getString("language-mode", "system");
        return language.equals("ar")
                || language.equals("system") && Locale.getDefault().getLanguage().equals("ar");
    }

    static String text(Context c, String key) {
        try {
            return PhoneData.get(c).text(key, arabic(c));
        } catch (Exception unavailable) {
            return "Miqat";
        }
    }

    static PendingIntent open(Context c) {
        return PendingIntent.getActivity(
                c,
                41700,
                new Intent(c, MainActivity.class)
                        .putExtra("prayer-home", true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent pending(Context c, String action, int code, String id) {
        return PendingIntent.getBroadcast(
                c,
                code,
                new Intent(c, PrayerAlarm.class).setAction(action).putExtra("event", id),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void channels(Context c) throws Exception {
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        for (String id : new String[] {ALERTS, REMINDERS, ALERTS_VIBRATION, REMINDERS_VIBRATION, FASTING}) {
            boolean vibrate = id.equals(ALERTS_VIBRATION) || id.equals(REMINDERS_VIBRATION);
            String name = text(c, id.equals(FASTING) ? "phone_fasting"
                    : id.equals(ALERTS) || id.equals(ALERTS_VIBRATION) ? "phone_home" : "phone_audio");
            if (vibrate) name += " · " + text(c, "phone_vibrate");
            NotificationChannel tone = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH);
            tone.setSound(android.net.Uri.parse("android.resource://" + c.getPackageName() + "/raw/miqat_short_tone"),
                    new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            tone.enableVibration(vibrate);
            if (vibrate) tone.setVibrationPattern(new long[] {0, 120, 100, 120});
            manager.createNotificationChannel(tone);
        }
        NotificationChannel audio =
                new NotificationChannel(
                        PLAYBACK, text(c, "phone_adhan"), NotificationManager.IMPORTANCE_LOW);
        audio.setSound(null, null);
        audio.enableVibration(false);
        manager.createNotificationChannel(audio);
        for (int mode : new int[] {3, 4}) {
            NotificationChannel quiet =
                    new NotificationChannel(
                            mode == 3 ? VIBRATION : SILENT,
                            text(c, mode == 3 ? "phone_vibration" : "phone_silent"),
                            NotificationManager.IMPORTANCE_HIGH);
            quiet.setSound(null, null);
            quiet.enableVibration(mode == 3);
            if (mode == 3) quiet.setVibrationPattern(new long[] {0, 120, 100, 120});
            manager.createNotificationChannel(quiet);
        }
    }

    static boolean fullScreenAllowed(Context c) {
        return Build.VERSION.SDK_INT < 34
                || c.getSystemService(NotificationManager.class).canUseFullScreenIntent();
    }

    static String playbackChannel(Context c, boolean screen) {
        if (!screen) return PLAYBACK;
        NotificationChannel channel =
                new NotificationChannel(
                        SCREEN, text(c, "phone_full_screen"), NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        channel.enableVibration(false);
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        return SCREEN;
    }

    static boolean channel(Context c, String id) {
        NotificationChannel channel =
                c.getSystemService(NotificationManager.class).getNotificationChannel(id);
        return notifications(c)
                && channel != null
                && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    private static String noticeChannel(int mode, boolean vibrate, boolean reminder) {
        if (mode == 3 || mode == 4 && vibrate) return VIBRATION;
        if (mode == 4) return SILENT;
        return reminder ? vibrate ? REMINDERS_VIBRATION : REMINDERS
                : vibrate ? ALERTS_VIBRATION : ALERTS;
    }

    private static void requiredMode(Set<String> channels, PhoneStore settings, int prayer, int mode, boolean vibrate) {
        if (mode == 0) return;
        channels.add(mode == 2 ? playbackChannelFor(settings) : noticeChannel(mode, vibrate, false));
        if (settings.before[prayer] > 0 || settings.after[prayer] > 0)
            channels.add(noticeChannel(mode, vibrate, true));
    }

    static Set<String> requiredChannels(PhoneStore settings) {
        Set<String> required = new HashSet<>();
        boolean quiet = settings.value("quiet.enabled").equals("true"),
                allDay = quiet && settings.number("quiet.start") == settings.number("quiet.end");
        for (int i = 0; i < 6; i++)
            if (i != 1 && settings.prayerEnabled(i))
                for (int weekday = 0; weekday < 7; weekday++) {
                    if ((settings.number("call." + PhoneStore.PRAYERS[i] + ".weekdays") & (1 << weekday)) == 0) continue;
                    if (!allDay) requiredMode(required, settings, i, settings.effectiveMode(i, weekday), settings.vibrateOnDay(i, weekday));
                    if (quiet) requiredMode(required, settings, i, settings.quietMode(), settings.quietMode() == 3);
                }
        return required;
    }

    private static String playbackChannelFor(PhoneStore settings) {
        return settings.value("reminders.full-screen").equals("true") ? SCREEN : PLAYBACK;
    }

    static String status(Context c, PhoneStore settings) {
        if (!settings.enabled || !settings.anyAlerts()) return "phone_alerts_off";
        if (!settings.locationReady) return "phone_location_needed";
        if (!notifications(c)) return "phone_permission_missing";
        for (String id : requiredChannels(settings))
            if (!channel(c, id)) return "phone_permission_missing";
        String error = PhoneStore.preferences(c).getString("alarm-error", "");
        return error.isEmpty() ? exact(c) ? "phone_alerts_ready" : "phone_alerts_approximate" : error;
    }

    static String rebuild(Context context) {
        Context c = context.getApplicationContext();
        try { PhoneVolume.recover(c); }
        catch (Exception restricted) { android.util.Log.w("Miqat","Volume restoration deferred",restricted); }
        PrayerWidget.updateAll(c);
        synchronized (PhoneStore.LOCK) {
            try {
                cancel(c);
                channels(c);
                if (c.getSystemService(UserManager.class).isUserUnlocked())
                    PhoneStore.preferences(c)
                            .edit()
                            .putString(
                                    "language-mode",
                                    c.getSharedPreferences("companion", 0)
                                            .getString("language", "system"))
                            .apply();
                PhoneStore settings = PhoneStore.read(c);
                playbackChannel(c, settings.value("reminders.full-screen").equals("true"));
                String state = status(c, settings);
                PhonePlayback.applySettings(c);
                if (!notifications(c)) PhonePlayback.stop(c);
                boolean prayers = settings.enabled && settings.anyAlerts();
                boolean fasting = PhoneFasting.preferences(settings).getEnabled() && channel(c, FASTING);
                if ((!prayers && !fasting) || !settings.locationReady || !notifications(c)) return state;
                long now = System.currentTimeMillis();
                double[][] days = settings.days(now);
                String[] handled = handled(c);
                ReminderEvent event = prayers ? settings.nextEvent(days, now, handled) : null;
                if (fasting) {
                    ReminderEvent fast = PhoneFasting.next(settings, now, handled);
                    if (fast != null && (event == null || fast.getTime() < event.getTime())) event = fast;
                }
                if (event != null) arm(c, event, now);
                else {
                    Calendar tomorrow = new GregorianCalendar(settings.zone());
                    tomorrow.setTimeInMillis(now);
                    tomorrow.add(Calendar.DAY_OF_MONTH, 1);
                    tomorrow.set(Calendar.HOUR_OF_DAY, 0);
                    tomorrow.set(Calendar.MINUTE, 5);
                    tomorrow.set(Calendar.SECOND, 0);
                    tomorrow.set(Calendar.MILLISECOND, 0);
                    manager(c)
                            .setAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    tomorrow.getTimeInMillis(),
                                    pending(c, REFRESH, MAINTENANCE, ""));
                }
                if (!PhoneStore.preferences(c)
                        .edit()
                        .putString("alarm-error", days.length == 0 ? "phone_unavailable" : "")
                        .commit()) return "phone_save_failed";
                return status(c, settings);
            } catch (Exception failure) {
                PhoneStore.preferences(c)
                        .edit()
                        .putString("alarm-error", "phone_settings_failed")
                        .apply();
                return "phone_settings_failed";
            }
        }
    }

    static void cancel(Context c) {
        manager(c).cancel(pending(c, FIRE, EVENT, ""));
        manager(c).cancel(pending(c, REFRESH, MAINTENANCE, ""));
        if (!PhoneStore.preferences(c).edit().remove("next-event").commit())
            throw new IllegalStateException("Cannot clear scheduled alarm");
    }

    static void arm(Context c, ReminderEvent event, long now) throws Exception {
        JSONObject value =
                new JSONObject()
                        .put("id", event.getId())
                        .put("kind", event.getKind())
                        .put("prayer", event.getPrayer())
                        .put("time", event.getTime())
                        .put("prayerTime", event.getPrayerTime());
        if (!PhoneStore.preferences(c).edit().putString("next-event", value.toString()).commit())
            throw new java.io.IOException("Cannot save alarm");
        long time = Math.max((long) event.getTime(), now + 500);
        PendingIntent operation = pending(c, FIRE, EVENT, event.getId());
        try {
            if (exact(c)) {
                try {
                    manager(c).setAlarmClock(new AlarmManager.AlarmClockInfo(time, open(c)), operation);
                    return;
                } catch (SecurityException revoked) {
                    // Exact access can be revoked between inspection and registration.
                }
            }
            manager(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, operation);
            value.put("approximate", true);
            if (!PhoneStore.preferences(c).edit().putString("next-event", value.toString()).commit()) {
                manager(c).cancel(operation);
                throw new java.io.IOException("Cannot save approximate alarm");
            }
        } catch (RuntimeException failure) {
            PhoneStore.preferences(c).edit().remove("next-event")
                    .putString("alarm-error", "phone_permission_missing").apply();
            throw failure;
        }
    }

    private static String[] handled(Context c) throws Exception {
        String raw = PhoneStore.preferences(c).getString("handled", "[]");
        if (raw.length() > 16384) throw new java.io.IOException("Claim record too large");
        JSONArray saved = new JSONArray(raw);
        if (saved.length() > 64) throw new java.io.IOException("Too many claims");
        String[] result = new String[saved.length()];
        for (int i = 0; i < result.length; i++) {
            if (!(saved.get(i) instanceof String))
                throw new java.io.IOException("Invalid claim type");
            result[i] = saved.getString(i);
        }
        return result;
    }

    static ReminderEvent claim(Context c, String requested, long now) throws Exception {
        synchronized (PhoneStore.LOCK) {
            String raw = PhoneStore.preferences(c).getString("next-event", "");
            if (raw.isEmpty() || raw.length() > 1024 || requested == null) return null;
            JSONObject value = new JSONObject(raw);
            String id = value.getString("id"), kind = value.getString("kind");
            boolean fasting = PhoneFasting.kind(kind);
            int prayer = value.getInt("prayer");
            if (!id.equals(requested)
                    || !tv.miqat.core.PrayerRules.INSTANCE.isPrayer(prayer)
                    || !fasting && !Arrays.asList("call", "before", "after").contains(kind)
                    || !PhonePrayerRules.INSTANCE.due(value.getDouble("time"), now)) return null;
            PhoneStore settings = PhoneStore.read(c);
            if (!settings.locationReady || !notifications(c)) return null;
            if (fasting ? !channel(c, FASTING) : !settings.enabled || !settings.prayerEnabled(prayer))
                return null;
            ReminderEvent event = new ReminderEvent(id, kind, prayer,
                    value.getDouble("time"), value.getDouble("prayerTime"));
            if (fasting && !PhoneFasting.matches(settings, event)) return null;
            List<String> claims = new ArrayList<>(Arrays.asList(handled(c)));
            if (claims.contains(id)) return null;
            claims.add(id);
            while (claims.size() > 64) claims.remove(0);
            if (!PhoneStore.preferences(c)
                    .edit()
                    .putString("handled", new JSONArray(claims).toString())
                    .commit()) throw new java.io.IOException("Cannot save claim");
            return fasting || settings.eventMode(event) > 0 ? event : null;
        }
    }

    static boolean notifyTest(Context c, boolean failure) {
        try {
            channels(c);
            if (!channel(c, ALERTS)) return false;
            Notification notice = new Notification.Builder(c, ALERTS)
                    .setSmallIcon(R.drawable.companion_icon_mihrab)
                    .setContentTitle(text(c, "phone_test_notification"))
                    .setContentText(failure ? text(c, "phone_test_failed") : "Miqat")
                    .setContentIntent(open(c)).setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_REMINDER).build();
            c.getSystemService(NotificationManager.class).notify(PhoneAlertTest.NOTICE, notice);
            return true;
        } catch (Exception unavailable) { return false; }
    }

    static void notify(Context c, ReminderEvent event, boolean failure) {
        try {
            channels(c);
            PhoneStore settings = PhoneStore.read(c);
            if (PhoneFasting.kind(event.getKind())) {
                notifyFasting(c, settings, event);
                return;
            }
            if (!settings.enabled || !settings.locationReady) return;
            int mode = settings.eventMode(event);
            if (mode == 0) return;
            String channel = noticeChannel(mode, settings.eventVibrate(event), !event.getKind().equals("call"));
            if (!channel(c, channel)) return;
            PhoneData data = PhoneData.get(c);
            String name = data.prayerName(event.getPrayer(), arabic(c));
            String key =
                    event.getKind().equals("before")
                            ? "phone_advance_title"
                            : event.getKind().equals("after")
                                    ? "phone_after_title"
                                    : "phone_adhan_title";
            String title = String.format(Locale.getDefault(), text(c, key), name);
            Notification n =
                    new Notification.Builder(c, channel)
                            .setSmallIcon(R.drawable.companion_icon_mihrab)
                            .setContentTitle(title)
                            .setContentText(
                                    failure ? text(c, "phone_audio_failed") : "Miqat · " + name)
                            .setContentIntent(open(c))
                            .setAutoCancel(true)
                            .setCategory(Notification.CATEGORY_REMINDER)
                            .build();
            int kind =
                    event.getKind().equals("call") ? 0 : event.getKind().equals("before") ? 1 : 2;
            c.getSystemService(NotificationManager.class)
                    .notify(42000 + event.getPrayer() * 3 + kind, n);
        } catch (Exception unavailable) {
            PhoneStore.preferences(c)
                    .edit()
                    .putString("alarm-error", "phone_permission_missing")
                    .apply();
        }
    }

    private static void notifyFasting(Context c, PhoneStore settings, ReminderEvent event) {
        if (!channel(c, FASTING) || !PhoneFasting.matches(settings, event)) return;
        boolean suhoor = tv.miqat.companion.core.FastingRules.SUHOOR.equals(event.getKind());
        String time = PhoneTime.at(settings, event.getPrayerTime(), arabic(c));
        Notification notice = new Notification.Builder(c, FASTING)
                .setSmallIcon(R.drawable.companion_icon_mihrab)
                .setContentTitle(text(c, suhoor ? "phone_suhoor_notice" : "phone_iftar_notice"))
                .setContentText(String.format(Locale.ROOT, text(c, suhoor ? "phone_suhoor_time" : "phone_iftar_time"), time))
                .setContentIntent(open(c))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();
        c.getSystemService(NotificationManager.class).notify(suhoor ? 42030 : 42031, notice);
    }
}

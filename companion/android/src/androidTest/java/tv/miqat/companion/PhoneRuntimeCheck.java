package tv.miqat.companion;

import android.app.*;
import android.content.*;
import android.os.SystemClock;

import tv.miqat.core.ReminderEvent;

import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Explicit owned-emulator suite. Grant notification/exact-alarm access first; audio output is
 * muted.
 */
final class PhoneRuntimeCheck {
    static void denied(Instrumentation test) throws Exception {
        Context c = test.getTargetContext();
        PhoneStore original = PhoneStore.read(c);
        CompanionCheck.check(
                !PhoneAlarms.exact(c) || !PhoneAlarms.notifications(c),
                "Revoke a prayer permission before the denial fixture");
        try {
            Map<String, String> changes = new LinkedHashMap<>();
            changes.put("reminders.enabled", "true");
            changes.put("prayer.location-ready", "true");
            original.changed(changes).save(c);
            CompanionCheck.check(
                    PhoneAlarms.rebuild(c).equals("phone_permission_missing"),
                    "Permission denial must be visible");
            CompanionCheck.check(
                    !PhoneStore.preferences(c).contains("next-event") && !PhonePlayback.playing(),
                    "Denied alerts scheduled or played");
            android.content.pm.ActivityInfo receiver =
                    c.getPackageManager()
                            .getReceiverInfo(new ComponentName(c, PrayerAlarm.class), 0);
            android.content.pm.ServiceInfo service =
                    c.getPackageManager()
                            .getServiceInfo(new ComponentName(c, PhonePlayback.class), 0);
            CompanionCheck.check(
                    !receiver.exported
                            && receiver.directBootAware
                            && !service.exported
                            && service.directBootAware,
                    "Prayer components must stay private and recover before unlock");
        } finally {
            original.save(c);
            PhoneAlarms.rebuild(c);
        }
    }

    static void run(Instrumentation test) throws Exception {
        Context c = test.getTargetContext();
        SharedPreferences preferences = PhoneStore.preferences(c);
        Map<String, ?> original = new HashMap<>(preferences.getAll());
        android.media.AudioManager systemAudio =
                c.getSystemService(android.media.AudioManager.class);
        int alarmVolume = systemAudio.getStreamVolume(android.media.AudioManager.STREAM_ALARM);
        CompanionCheck.check(
                PhoneAlarms.exact(c) && PhoneAlarms.notifications(c),
                "Grant exact-alarm/notification permissions on the owned emulator before this"
                        + " suite");
        MainActivity[] home = {null};
        try {
            PhonePlayback.stop(c);
            PhoneAlarms.cancel(c);
            Map<String, String> changes = new LinkedHashMap<>();
            changes.put("reminders.enabled", "true");
            changes.put("prayer.location-ready", "true");
            changes.put("reminders.volume", "0");
            changes.put("reminders.duration", "30");
            changes.put("reminders.full-screen", "false");
            changes.put("reminders.volume-keys", "false");
            changes.put("call.fajr.mode", "adhan");
            changes.put("call.fajr.weekdays", "127");
            changes.put("quiet.enabled", "false");
            changes.put("prayer.latitude", "30.0444");
            changes.put("prayer.longitude", "31.2357");
            changes.put("prayer.method", "EGYPTIAN");
            changes.put("prayer.zone", "Africa/Cairo");
            changes.put("call.fajr.offset", "0");
            for (String prayer : new String[] {"dhuhr", "asr", "maghrib", "isha"})
                changes.put("call." + prayer + ".mode", "off");
            PhoneStore base = PhoneStore.read(c).changed(changes);
            // Keep real calculated Fajr away from this fixture's three-second alarm.
            base =
                    base.changed(
                            Collections.singletonMap(
                                    "call.fajr.offset",
                                    base.day(System.currentTimeMillis(), 0).times[0]
                                                    >= System.currentTimeMillis()
                                            ? "30"
                                            : "-30"));
            base.save(c);
            PhoneAlarms.channels(c);
            c.sendBroadcast(new Intent(c, PrayerAlarm.class).setAction(PhoneAlarms.REFRESH));
            await(
                    test,
                    () -> preferences.contains("next-event"),
                    10000,
                    "Recovery broadcast did not rebuild the next alarm");
            CompanionCheck.check(
                    !PhonePlayback.playing(), "Recovery must not start audio directly");
            CompanionCheck.check(
                    preferences.edit().putString("handled", "[]").remove("audio-error").commit(),
                    "Cannot initialize alarm fixture");
            preferences.edit().putBoolean("onboarding-seen", true).commit();
            home[0] =
                    (MainActivity)
                            test.startActivitySync(
                                    new Intent(c, MainActivity.class)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Intent otherApp = new Intent(android.provider.Settings.ACTION_SETTINGS);
            String otherPackage = otherApp.resolveActivity(c.getPackageManager()).getPackageName();
            PhoneUiCheck.on(test, () -> home[0].startActivity(otherApp));
            await(
                    test,
                    () -> !home[0].phoneForeground,
                    5000,
                    "Other app did not become active before the alarm");
            awaitPackage(test, otherPackage);
            long now = System.currentTimeMillis(), time = now + 3000;
            ReminderEvent event =
                    new ReminderEvent("phone:call:0:" + time, "call", 0, time, now - 65 * 60000L);
            PhoneAlarms.arm(c, event, now);
            AlarmManager.AlarmClockInfo system =
                    c.getSystemService(AlarmManager.class).getNextAlarmClock();
            CompanionCheck.check(
                    system != null && system.getTriggerTime() == time,
                    "Actual OS alarm clock registration");
            shell(test, "input keyevent KEYCODE_SLEEP");
            await(
                    test,
                    () -> !c.getSystemService(android.os.PowerManager.class).isInteractive(),
                    5000,
                    "Owned emulator did not enter screen-off state");
            await(
                    test,
                    () ->
                            PhonePlayback.outputStarted()
                                    && PhonePlayback.currentId().equals(event.getId()),
                    15000,
                    "Actual alarm did not start foreground audio");
            CompanionCheck.check(
                    Arrays.stream(
                                    c.getSystemService(NotificationManager.class)
                                            .getActiveNotifications())
                            .anyMatch(n ->
                                    (n.getNotification().flags
                                                    & Notification.FLAG_FOREGROUND_SERVICE)
                                            != 0),
                    "Foreground playback notification missing");
            CompanionCheck.check(
                    !c.getSystemService(android.os.PowerManager.class).isInteractive(),
                    "Background-only adhan forced the screen on");
            shell(test, "input keyevent KEYCODE_WAKEUP");
            shell(test, "wm dismiss-keyguard");
            awaitPackage(test, otherPackage);
            CompanionCheck.check(
                    otherPackage.equals(activePackage(test)),
                    "Scheduled adhan forced a window over the active app");
            PhoneUiCheck.on(
                    test,
                    () ->
                            c.startActivity(
                                    new Intent(c, MainActivity.class)
                                            .addFlags(
                                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                                            | Intent
                                                                    .FLAG_ACTIVITY_REORDER_TO_FRONT)));
            await(test, () -> home[0].phoneForeground, 5000, "Could not reopen Miqat during adhan");
            PhoneUiCheck.on(test, () -> home[0].startActivity(otherApp));
            long switchedAt = SystemClock.elapsedRealtime();
            await(
                    test,
                    () ->
                            SystemClock.elapsedRealtime() - switchedAt >= 1500
                                    && !home[0].phoneForeground,
                    5000,
                    "Could not continue using another app during adhan");
            awaitPackage(test, otherPackage);
            CompanionCheck.check(
                    PhonePlayback.outputStarted()
                            && PhonePlayback.currentId().equals(event.getId()),
                    "Leaving Miqat stopped scheduled adhan");
            CompanionCheck.check(
                    PhoneAlarms.claim(c, event.getId(), System.currentTimeMillis()) == null,
                    "Duplicate event was accepted");
            PhoneAdhanCheck.stop(test, c, event);
            await(
                    test,
                    () -> !PhonePlayback.playing(),
                    5000,
                    "Stop did not release foreground playback");
            CompanionCheck.check(
                    systemAudio.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
                            == alarmVolume,
                    "Miqat changed global alarm volume");
            PhoneUiCheck.on(test, home[0]::finish);
            LiveUiCheck.untilUi(test, home[0]::isDestroyed);
            home[0] = null;
            PhoneAdhanCheck.screen(test, c);
            shell(test, "input keyevent KEYCODE_WAKEUP");
            shell(test, "wm dismiss-keyguard");
            await(test, () -> !c.getSystemService(KeyguardManager.class).isKeyguardLocked(),
                    5000, "Unlock the owned emulator before the remaining audition UI");
            // A stale/early fixture event must not be claimed or produce playback.
            now = System.currentTimeMillis();
            ReminderEvent expired =
                    new ReminderEvent(
                            "phone:call:0:expired", "call", 0, now - 120000, now - 120000);
            PhoneAlarms.arm(c, expired, now);
            CompanionCheck.check(
                    PhoneAlarms.claim(c, expired.getId(), now) == null, "Expired prayer accepted");
            PhoneAlarms.cancel(c);
            ReminderEvent future =
                    new ReminderEvent("phone:call:0:future", "call", 0, now + 60000, now + 60000);
            PhoneAlarms.arm(c, future, now);
            CompanionCheck.check(
                    PhoneAlarms.claim(c, future.getId(), now) == null, "Early prayer accepted");
            PhoneAlarms.cancel(c);
            // User preview uses the same foreground player; an external focus owner stops it.
            test.runOnMainSync(() ->
                    c.startForegroundService(
                            PhonePlayback.intent(c, 0, "almalki", true, "preview-fixture")));
            await(test, PhonePlayback::outputStarted, 10000, "Preview did not start");
            test.runOnMainSync(() ->
                    c.startForegroundService(
                            PhonePlayback.intent(c, 0, "almalki", true, "replacement-preview")));
            await(
                    test,
                    () ->
                            PhonePlayback.outputStarted()
                                    && PhonePlayback.currentId().equals("replacement-preview"),
                    10000,
                    "Replacing preview did not release/restart the player");
            android.media.AudioManager audio = c.getSystemService(android.media.AudioManager.class);
            android.media.AudioFocusRequest focus =
                    new android.media.AudioFocusRequest.Builder(
                                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setOnAudioFocusChangeListener(change -> {})
                            .setAudioAttributes(
                                    new android.media.AudioAttributes.Builder()
                                            .setUsage(
                                                    android.media.AudioAttributes
                                                            .USAGE_VOICE_COMMUNICATION)
                                            .build())
                            .build();
            test.runOnMainSync(() -> audio.requestAudioFocus(focus));
            try {
                await(
                        test,
                        () -> !PhonePlayback.playing(),
                        5000,
                        "Audio focus loss did not release playback");
            } finally {
                audio.abandonAudioFocusRequest(focus);
            }
            test.runOnMainSync(() ->
                    c.startForegroundService(
                            PhonePlayback.intent(c, 0, "almalki", true, "bounded-preview")));
            await(test, PhonePlayback::outputStarted, 10000, "Bounded preview did not start");
            long previewStarted = SystemClock.elapsedRealtime();
            await(
                    test,
                    () -> !PhonePlayback.playing(),
                    16000,
                    "Preview exceeded its twelve-second bound");
            CompanionCheck.check(
                    SystemClock.elapsedRealtime() - previewStarted >= 10000
                            && !preferences.contains("audio-error"),
                    "Preview failed instead of completing its bounded playback");
            audition(test, c);
            CompanionCheck.check(
                    PhoneStore.read(c).value("audio.custom.fajr").isEmpty(),
                    "Custom runtime fixture requires an unused Fajr audio slot on the owned"
                            + " emulator");
            PhoneAudio.importFile(c, c.getAssets().open("adhan/almalki.mp3"), "call.fajr.voice");
            Map<String, String> overrides = new LinkedHashMap<>();
            overrides.put("call.fajr.default-audio", "false");
            overrides.put("call.fajr.volume", "15");
            overrides.put("call.fajr.duration", "30");
            PhoneStore.read(c).changed(overrides).save(c);
            test.runOnMainSync(() ->
                    c.startForegroundService(
                            PhonePlayback.intent(c, 0, "custom", false, "custom-bounded-fixture")));
            await(test, PhonePlayback::outputStarted, 10000, "Private custom audio did not start");
            long customStarted = SystemClock.elapsedRealtime();
            long pausedFor = PhoneAdhanCheck.pause(test, c);
            await(
                    test,
                    () -> !PhonePlayback.playing(),
                    34000,
                    "Per-prayer duration did not stop custom audio");
            CompanionCheck.check(
                    SystemClock.elapsedRealtime() - customStarted >= 28000 + pausedFor
                            && !preferences.contains("audio-error"),
                    "Custom playback failed instead of reaching its per-prayer bound");
            PhoneStore beforeDraft = PhoneStore.read(c);
            try {
                Map<String, String> draft = new LinkedHashMap<>();
                draft.put("audio.custom.default", beforeDraft.value("audio.custom.fajr"));
                draft.put("audio.custom.fajr", "");
                draft.put("reminders.voice", "custom");
                draft.put("call.fajr.voice", "almalki");
                beforeDraft.changed(draft).save(c);
                test.runOnMainSync(() ->
                        c.startForegroundService(
                                PhonePlayback.intent(c, 0, "custom", true, "inherited-custom-draft")
                                        .putExtra("default-voice", true)));
                await(
                        test,
                        PhonePlayback::outputStarted,
                        10000,
                        "Inherited custom audition used the saved per-prayer slot");
                PhonePlayback.stop(c);
                await(test, () -> !PhonePlayback.playing(), 5000, "Custom draft Stop failed");
                draft.put("audio.custom.default", "");
                draft.put("audio.custom.fajr", beforeDraft.value("audio.custom.fajr"));
                draft.put("reminders.voice", "almalki");
                draft.put("call.fajr.voice", "inherit");
                beforeDraft.changed(draft).save(c);
                test.runOnMainSync(() ->
                        c.startForegroundService(
                                PhonePlayback.intent(
                                                c, 0, "custom", true, "per-prayer-custom-draft")
                                        .putExtra("default-voice", false)));
                await(
                        test,
                        PhonePlayback::outputStarted,
                        10000,
                        "Per-prayer custom audition used the inherited slot");
                PhonePlayback.stop(c);
                await(test, () -> !PhonePlayback.playing(), 5000, "Custom draft cleanup failed");
            } finally {
                beforeDraft.save(c);
            }
            for (String mode : new String[] {"vibration", "silent"}) {
                PhoneStore.read(c)
                        .changed(Collections.singletonMap("call.fajr.mode", mode))
                        .save(c);
                long stamp = System.currentTimeMillis();
                PhoneAlarms.notify(
                        c,
                        new ReminderEvent("mode-fixture:" + mode, "call", 0, stamp, stamp),
                        false);
                String expectedChannel =
                        mode.equals("vibration") ? PhoneAlarms.VIBRATION : PhoneAlarms.SILENT;
                CompanionCheck.check(
                        Arrays.stream(
                                        c.getSystemService(NotificationManager.class)
                                                .getActiveNotifications())
                                .anyMatch(n ->
                                        n.getNotification().getChannelId().equals(expectedChannel)),
                        "Dedicated silent/vibration output missing");
                CompanionCheck.check(
                        !PhonePlayback.playing(), "Silent/vibration mode started adhan playback");
            }
            PhoneStore.read(c).changed(Collections.singletonMap("call.fajr.mode", "adhan")).save(c);
            test.runOnMainSync(() ->
                    c.startForegroundService(
                            PhonePlayback.intent(c, 0, "almalki", false, "disable-fixture")));
            await(test, PhonePlayback::outputStarted, 10000, "Disable fixture did not start");
            PhoneStore.read(c)
                    .changed(Collections.singletonMap("reminders.enabled", "false"))
                    .save(c);
            PhoneAlarms.rebuild(c);
            await(
                    test,
                    () -> !PhonePlayback.playing(),
                    5000,
                    "Disabling alerts did not stop current playback");
            CompanionCheck.check(
                    !PhonePlayback.playing() && !PhoneStore.preferences(c).contains("next-event"),
                    "Disabled alerts retained active playback/alarm");
        } finally {
            shell(test, "input keyevent KEYCODE_WAKEUP");
            shell(test, "wm dismiss-keyguard");
            if (home[0] != null) PhoneUiCheck.on(test, home[0]::finish);
            PhonePlayback.stop(c);
            PhoneAlarms.cancel(c);
            SharedPreferences.Editor restore = preferences.edit().clear();
            for (Map.Entry<String, ?> entry : original.entrySet()) {
                Object value = entry.getValue();
                String key = entry.getKey();
                if (value instanceof String) restore.putString(key, (String) value);
                else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) restore.putInt(key, (Integer) value);
                else if (value instanceof Long) restore.putLong(key, (Long) value);
                else if (value instanceof Float) restore.putFloat(key, (Float) value);
                else throw new IllegalStateException("Unexpected fixture preference type");
            }
            CompanionCheck.check(restore.commit(), "Cannot restore alarm fixture");
            PhoneAudio.cleanup(c);
            PhoneAlarms.rebuild(c);
        }
    }

    static void shell(Instrumentation test, String command) throws java.io.IOException {
        try (java.io.InputStream input =
                new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                        test.getUiAutomation().executeShellCommand(command))) {
            while (input.read() != -1) {}
        }
    }

    private static void awaitPackage(Instrumentation test, String expected) {
        // Accessibility queries must not block the app main thread serving their requests.
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            if (expected.equals(activePackage(test))) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Active window did not return to " + expected);
    }

    private static String activePackage(Instrumentation test) {
        android.view.accessibility.AccessibilityNodeInfo root =
                test.getUiAutomation().getRootInActiveWindow();
        if (root == null) return "";
        try {
            return String.valueOf(root.getPackageName());
        } finally {
            root.recycle();
        }
    }

    private static void audition(Instrumentation test, Context c) throws Exception {
        String saved = PhoneStore.read(c).value("reminders.voice");
        MainActivity a =
                (MainActivity)
                        test.startActivitySync(
                                new Intent(c, MainActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            PhoneUiCheck.on(
                    test,
                    () -> {
                        a.phoneUi.open("audio");
                        a.body.findViewWithTag("phone.reminders.voice.edit").performClick();
                        android.widget.ListView choices =
                                a.phoneUi
                                        .choiceDialog
                                        .getWindow()
                                        .getDecorView()
                                        .findViewWithTag("phone.voice.list");
                        choices.performItemClick(null, 0, 0);
                        a.phoneUi
                                .choiceDialog
                                .getWindow()
                                .getDecorView()
                                .findViewWithTag("phone.voice.preview")
                                .performClick();
                    });
            await(
                    test,
                    PhonePlayback::outputStarted,
                    10000,
                    "Voice-sheet audition did not start playback");
            CompanionCheck.check(
                    PhoneStore.read(c).value("reminders.voice").equals(saved),
                    "Audition committed a voice");
            PhoneUiCheck.on(test, () -> a.phoneUi.choiceDialog.getButton(-2).performClick());
            await(
                    test,
                    () -> !PhonePlayback.playing(),
                    5000,
                    "Voice-sheet Cancel did not stop audition");
            CompanionCheck.check(
                    PhoneStore.read(c).value("reminders.voice").equals(saved),
                    "Voice-sheet Cancel changed voice");
        } finally {
            PhoneUiCheck.on(test, a::finish);
        }
    }

    static void await(Instrumentation test, BooleanSupplier condition, long timeout, String error) {
        long until = SystemClock.uptimeMillis() + timeout;
        boolean[] ready = {false};
        do {
            test.runOnMainSync(() -> ready[0] = condition.getAsBoolean());
            if (ready[0]) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < until);
        CompanionCheck.check(false, error);
    }
}

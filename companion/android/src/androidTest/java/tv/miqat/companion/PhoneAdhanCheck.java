package tv.miqat.companion;

import static tv.miqat.companion.PhoneRuntimeCheck.await;
import static tv.miqat.companion.PhoneUiCheck.check;

import android.app.*;
import android.content.*;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.*;

/**
 * Notification session controls and deliberate optional-screen dismissal on an owned muted device.
 */
final class PhoneAdhanCheck {
    private static Notification notice(Context c) {
        return Arrays.stream(c.getSystemService(NotificationManager.class).getActiveNotifications())
                .map(n -> n.getNotification())
                .filter(n -> (n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Playback notification missing"));
    }

    static long pause(Instrumentation test, Context c) throws Exception {
        Notification playing = notice(c);
        check(
                playing.actions.length == 2
                        && playing.deleteIntent != null
                        && (playing.flags & Notification.FLAG_ONGOING_EVENT) == 0,
                "Playback must offer pause/stop and dismissal where Android supports it");
        MediaSession.Token token = playing.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
        MediaController controller = new MediaController(c, token);
        check(
                controller.getPlaybackState().getCustomActions().stream()
                        .anyMatch(action -> action.getAction().equals(PhonePlayback.STOP)),
                "Modern Android media controls hide Stop");
        playing.actions[0].actionIntent.send();
        await(test, PhonePlayback::paused, 5000, "Notification pause did not pause output");
        long pausedAt = SystemClock.elapsedRealtime();
        long position = controller.getPlaybackState().getPosition();
        SystemClock.sleep(4000);
        check(
                !PhonePlayback.outputStarted()
                        && PhonePlayback.scheduled()
                        && controller.getPlaybackState().getState() == PlaybackState.STATE_PAUSED
                        && controller.getPlaybackState().getPosition() == position,
                "Pause lost the scheduled session or advanced its position");
        notice(c).actions[0].actionIntent.send();
        await(test, PhonePlayback::outputStarted, 5000, "Notification Play did not resume output");
        check(
                !PhonePlayback.paused() && controller.getPlaybackState().getPosition() >= position,
                "Resume restarted the recording");
        return SystemClock.elapsedRealtime() - pausedAt;
    }

    static void stop(Instrumentation test, Context c, tv.miqat.core.ReminderEvent event)
            throws Exception {
        PhoneStore settings = PhoneStore.read(c);
        boolean ar = PhoneAlarms.arabic(c);
        MediaSession.Token token = notice(c).extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
        MediaMetadata metadata = new MediaController(c, token).getMetadata();
        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        check(
                title.contains(settings.data.prayerName(event.getPrayer(), ar))
                        && title.contains(PhoneTime.at(settings, event.getPrayerTime(), ar)),
                "Notification does not show the actual prayer and scheduled time");
        check(
                metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        .equals(settings.data.voiceName(settings.voice(event.getPrayer()), ar)),
                "Notification does not name the playing voice");
        check(
                metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null
                        && notice(c).getLargeIcon() != null,
                "Selected notification artwork missing");
        PhoneRuntimeCheck.shell(test, "cmd statusbar expand-notifications");
        try {
            PhonePresentationCheck.expandNotification(test,settings.data.prayerName(event.getPrayer(),ar));
            PhoneUiCheck.capture(test,c,"adhan-notification-actions",true);
            LiveUiCheck.until(() -> {
                android.view.accessibility.AccessibilityNodeInfo control = stopControl(test, c);
                if (control == null) return false;
                control.recycle();
                return true;
            });
            PhoneUiCheck.capture(test, c, "adhan-notification-playing", true);
            android.view.accessibility.AccessibilityNodeInfo control = stopControl(test, c);
            check(control != null, "Notification Stop disappeared");
            try {
                check(
                        control.performAction(
                                android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK),
                        "System notification Stop is not clickable");
            } finally {
                control.recycle();
            }
        } finally {
            PhoneRuntimeCheck.shell(test, "cmd statusbar collapse");
        }
    }

    private static android.view.accessibility.AccessibilityNodeInfo stopControl(
            Instrumentation test, Context c) {
        return PhonePresentationCheck.notificationText(test,PhoneAlarms.text(c,"phone_stop"));
    }

    static void screen(Instrumentation test, Context c) throws Exception {
        PhoneStore before = PhoneStore.read(c);
        SharedPreferences language = c.getSharedPreferences("companion", 0);
        String originalLanguage = language.getString("language", "system");
        PhoneAdhanScreen[] shown = {null};
        int volume =
                c.getSystemService(android.media.AudioManager.class)
                        .getStreamVolume(android.media.AudioManager.STREAM_ALARM);
        try {
            Map<String, String> enabled = new HashMap<>();
            enabled.put("reminders.full-screen", "true");
            enabled.put("reminders.volume-keys", "true");
            enabled.put("appearance.theme", "dusk");
            enabled.put("appearance.background", "miqat_signature");
            if (c.getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                enabled.put("display.size", "150");
            before.changed(enabled).save(c);
            language.edit().putString("language", "en").commit();
            shown[0] =
                    PhoneAlarms.fullScreenAllowed(c)
                            ? alarmScreen(test, c)
                            : start(test, c, "screen-dismiss");
            Notification first = notice(c);
            check(
                    PhoneAlarms.fullScreenAllowed(c) == (first.fullScreenIntent != null),
                    "Full-screen notification ignored Android access");
            PhoneUiCheck.on(test, () -> shown[0].content.performClick());
            gesture(test, shown[0], 0, 0, false);
            gesture(test, shown[0], 0, .3f, false);
            gesture(test, shown[0], 60, 1, false);
            gesture(test, shown[0], 0, 1, true);
            check(
                    PhonePlayback.outputStarted(),
                    "Tap, short/horizontal/cancelled pull accidentally stopped adhan");
            LiveUiCheck.key(test, KeyEvent.KEYCODE_BACK);
            await(
                    test,
                    () -> shown[0].closeDialog != null && shown[0].closeDialog.isShowing(),
                    5000,
                    "Back bypassed deliberate-close confirmation");
            PhoneUiCheck.on(
                    test,
                    () -> {
                        check(
                                shown[0].closeDialog.isShowing() && PhonePlayback.outputStarted(),
                                "Touching/Back stopped adhan without confirmation");
                        shown[0].closeDialog.getButton(-2).performClick();
                    });
            check(PhonePlayback.outputStarted(), "Keep playing stopped the session");
            await(test, shown[0]::hasWindowFocus, 5000, "Adhan did not regain focus after Keep");
            captureScreen(test, shown[0], "en-adhan-screen");
            first.deleteIntent.send();
            await(
                    test,
                    () -> !PhonePlayback.playing() && shown[0].isDestroyed(),
                    5000,
                    "Notification dismissal did not stop audio and close its screen");
            PhoneStore.read(c)
                    .changed(
                            java.util.Collections.singletonMap(
                                    "appearance.background", "dusk_peaks"))
                    .save(c);
            language.edit().putString("language", "ar").commit();
            shown[0] = start(test, c, "screen-volume");
            captureScreen(test, shown[0], "ar-adhan-screen");
            first.deleteIntent.send();
            SystemClock.sleep(500);
            check(
                    PhonePlayback.currentId().equals("screen-volume")
                            && PhonePlayback.outputStarted(),
                    "A stale notification stopped a newer adhan");
            LiveUiCheck.key(test, KeyEvent.KEYCODE_VOLUME_DOWN);
            await(
                    test,
                    () -> !PhonePlayback.playing() && shown[0].isDestroyed(),
                    5000,
                    "Opted-in screen volume key did not stop adhan");
            check(
                    volume
                            == c.getSystemService(android.media.AudioManager.class)
                                    .getStreamVolume(android.media.AudioManager.STREAM_ALARM),
                    "Stop shortcut changed global alarm volume");
            shown[0] = start(test, c, "screen-close");
            gesture(test, shown[0], 0, 1, false);
            await(
                    test,
                    () -> !PhonePlayback.playing() && shown[0].isDestroyed(),
                    5000,
                    "Complete upward release did not stop and close adhan");
            shown[0] = start(test, c, "screen-accessible");
            PhoneUiCheck.on(
                    test,
                    () -> {
                        android.view.View stop =
                                shown[0].getWindow()
                                        .getDecorView()
                                        .findViewWithTag("adhan.stop-close");
                        check(
                                stop.performAccessibilityAction(
                                        android.view.accessibility.AccessibilityNodeInfo
                                                .ACTION_CLICK,
                                        null),
                                "Accessible Stop action unavailable");
                    });
            await(
                    test,
                    () -> !PhonePlayback.playing() && shown[0].isDestroyed(),
                    5000,
                    "Accessible Stop did not release adhan");
        } finally {
            if (shown[0] != null) PhoneUiCheck.on(test, shown[0]::finish);
            PhonePlayback.stop(c);
            before.save(c);
            language.edit().putString("language", originalLanguage).commit();
        }
    }

    private static void captureScreen(Instrumentation test, PhoneAdhanScreen screen, String name)
            throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            java.util.concurrent.CountDownLatch frame = new java.util.concurrent.CountDownLatch(1);
            PhoneUiCheck.on(
                    test,
                    () -> {
                        screen.content
                                .getViewTreeObserver()
                                .registerFrameCommitCallback(frame::countDown);
                        screen.content.invalidate();
                    });
            check(
                    frame.await(5, java.util.concurrent.TimeUnit.SECONDS),
                    "Adhan screen did not submit its rendered frame");
        }
        PhoneUiCheck.capture(test, screen, name, true);
    }

    private static PhoneAdhanScreen alarmScreen(Instrumentation test, Context c) throws Exception {
        Instrumentation.ActivityMonitor monitor =
                test.addMonitor(PhoneAdhanScreen.class.getName(), null, false);
        try {
            PhoneRuntimeCheck.shell(test, "input keyevent KEYCODE_SLEEP");
            long now = System.currentTimeMillis(), time = now + 2000;
            PhoneAlarms.arm(
                    c,
                    new tv.miqat.core.ReminderEvent("phone:call:0:" + time, "call", 0, time, time),
                    now);
            Activity opened = test.waitForMonitorWithTimeout(monitor, 15000);
            check(
                    opened instanceof PhoneAdhanScreen,
                    "Allowed full-screen alarm did not open over the lock screen");
            PhoneAdhanScreen screen = (PhoneAdhanScreen) opened;
            await(
                    test,
                    () -> screen.hasWindowFocus() && PhonePlayback.outputStarted(),
                    10000,
                    "Lock-screen adhan did not become ready");
            reachable(test, screen);
            return screen;
        } finally {
            test.removeMonitor(monitor);
        }
    }

    private static PhoneAdhanScreen start(Instrumentation test, Context c, String id)
            throws Exception {
        test.runOnMainSync(
                () -> c.startForegroundService(PhonePlayback.intent(c, 0, "almalki", false, id)));
        await(test, PhonePlayback::outputStarted, 10000, "Screen fixture did not start audio");
        PhoneAdhanScreen screen =
                (PhoneAdhanScreen)
                        test.startActivitySync(
                                PhoneAdhanScreen.intent(c, id, 0)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(test, screen::hasWindowFocus, 5000, "Adhan screen did not become active");
        reachable(test, screen);
        return screen;
    }

    private static void gesture(
            Instrumentation test,
            PhoneAdhanScreen screen,
            float horizontalDp,
            float fraction,
            boolean cancel) {
        PhoneUiCheck.on(
                test,
                () -> {
                    android.view.View stop =
                            screen.getWindow().getDecorView().findViewWithTag("adhan.stop-close");
                    float density = stop.getResources().getDisplayMetrics().density;
                    float x = stop.getWidth() / 2f, y = stop.getHeight() - 32 * density;
                    float endX = x + horizontalDp * density;
                    float endY = y - fraction * (stop.getHeight() - 64 * density);
                    long time = SystemClock.uptimeMillis();
                    for (int action :
                            new int[] {
                                android.view.MotionEvent.ACTION_DOWN,
                                android.view.MotionEvent.ACTION_MOVE,
                                cancel
                                        ? android.view.MotionEvent.ACTION_CANCEL
                                        : android.view.MotionEvent.ACTION_UP
                            }) {
                        android.view.MotionEvent event =
                                android.view.MotionEvent.obtain(
                                        time,
                                        SystemClock.uptimeMillis(),
                                        action,
                                        action == android.view.MotionEvent.ACTION_DOWN ? x : endX,
                                        action == android.view.MotionEvent.ACTION_DOWN ? y : endY,
                                        0);
                        try {
                            stop.dispatchTouchEvent(event);
                        } finally {
                            event.recycle();
                        }
                    }
                });
    }

    private static void reachable(Instrumentation test, PhoneAdhanScreen screen) {
        PhoneUiCheck.on(
                test,
                () -> {
                    android.view.View stop =
                            screen.getWindow().getDecorView().findViewWithTag("adhan.stop-close");
                    android.graphics.Rect bounds = new android.graphics.Rect();
                    check(
                            stop.getGlobalVisibleRect(bounds)
                                    && bounds.height() == stop.getHeight(),
                            "Bottom adhan handle is clipped at this window/text size");
                    android.graphics.Rect scroll = new android.graphics.Rect();
                    ((android.view.View) screen.content.getParent()).getGlobalVisibleRect(scroll);
                    check(
                            bounds.top >= scroll.bottom,
                            "Stop handle must stay below scrolling content");
                });
    }
}

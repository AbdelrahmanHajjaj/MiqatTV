package tv.miqat.companion;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import tv.miqat.core.ReminderEvent;

import static tv.miqat.companion.CompanionCheck.check;
import static tv.miqat.companion.PhoneRuntimeCheck.await;
import static tv.miqat.companion.PhoneRuntimeCheck.shell;
import static tv.miqat.companion.PhoneUiCheck.on;

/** Targeted media check on an owned muted emulator. The fixture serves a packaged recording over loopback. */
final class PhoneRadioCheck {
    static void run(Instrumentation test, String scenario, boolean capture) throws Exception {
        Context c = test.getTargetContext();
        boolean offline = scenario.equals("offline");
        boolean dismiss = scenario.equals("dismiss");
        boolean fixture = offline || dismiss || scenario.equals("fixture") || scenario.equals("alarm-route");
        if (fixture && !offline && !dismiss)
            check(PhoneAlarms.notifications(c), "Grant prayer notification access before scheduling Adhan");
        String id = fixture ? "cairo" : scenario;
        JSONObject station = PhoneData.get(c).station(id);
        check(station != null, "Unknown radio test station");
        String originalUrl = station.getString("url");
        SharedPreferences prefs = c.getSharedPreferences("companion", Context.MODE_PRIVATE);
        String previousStation = prefs.getString(PhoneRadio.STATION_PREFERENCE, null);
        String previousLanguage = prefs.getString("language", null);
        SharedPreferences prayers = PhoneStore.preferences(c);
        boolean seen = prayers.getBoolean("onboarding-seen", false);
        PhoneStore previous = PhoneStore.read(c);
        AtomicBoolean available = new AtomicBoolean(!offline);
        CountDownLatch respond = new CountDownLatch(offline ? 1 : 0);
        MainActivity a = null;
        try (ServerSocket server = fixture ? new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")) : null) {
            if (fixture) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (var input = c.getAssets().open("adhan/almalki.mp3")) {
                    byte[] buffer = new byte[8192];
                    for (int size; (size = input.read(buffer)) != -1;) bytes.write(buffer, 0, size);
                }
                byte[] recording = bytes.toByteArray();
                Thread stream = new Thread(() -> serve(server, recording, available, respond), "radio fixture");
                stream.setDaemon(true);
                stream.start();
                station.put("url", "http://127.0.0.1:" + server.getLocalPort() + "/live.mp3");
            }
            prayers.edit().putBoolean("onboarding-seen", true).commit();
            boolean arabic = scenario.equals("alarm-route");
            prefs.edit().putString(PhoneRadio.STATION_PREFERENCE, id)
                    .putString("language", arabic ? "ar" : "en").commit();
            a = LiveUiCheck.launch(test);
            MainActivity home = a;
            on(test, () -> {
                check(home.findViewById(android.R.id.content).findViewWithTag("phone.radio.open").performClick(),
                        "Radio is missing from the prayer home toolbar");
                check(!PhoneRadio.hasSession(), "Opening Radio started a connection");
                check(home.page.equals("prayers") && home.radio.popup.isShowing(),
                        "Radio replaced the prayer Home instead of opening a popup");
                check(popup(home, "radio.popup").getLayoutDirection() == (arabic ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR),
                        "Radio popup has the wrong language direction");
                click(home, "radio.play");
            });
            if (offline) {
                recoverFromFailure(test, c, home, available, respond);
                return;
            }
            await(test, () -> PhoneRadio.status().equals("phone_radio_live"), 24000,
                    "Station did not become audible: " + id);
            PhoneUiCheck.capture(test, home, arabic ? "radio-ar" : "radio-en", capture);
            MediaController player = mediaPlayer(test, c);
            if (!fixture) return;
            on(test, () -> click(home, "radio.close"));
            if (!arabic && !dismiss) {
                on(test, () -> {
                    View quick = home.findViewById(android.R.id.content).findViewWithTag("phone.radio.quick");
                    check(quick.isShown() && "Pause".contentEquals(quick.getContentDescription())
                            && quick.performClick(), "Home shortcut did not pause Radio");
                });
                await(test, () -> PhoneRadio.status().equals("phone_radio_paused"), 5000, "Home shortcut did not pause playback");
                radioNotice(c).contentIntent.send();
                await(test, () -> home.page.equals("prayers") && home.radio.popup != null
                        && home.radio.popup.isShowing(), 5000, "Media notification did not open the Home popup");
                on(test, () -> click(home, "radio.play"));
                await(test, () -> PhoneRadio.status().equals("phone_radio_live"), 15000, "Popup Play did not reconnect");
                on(test, () -> click(home, "radio.close"));
            }
            on(test, () -> home.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)));
            await(test, () -> !home.phoneForeground, 5000, "Radio fixture did not background Miqat");
            if (dismiss) {
                shell(test, "cmd statusbar expand-settings");
                AccessibilityNodeInfo control = mediaNode(test, "Pause");
                try {
                    PhoneUiCheck.capture(test, c, "radio-system-media", capture);
                    check(control.performAction(AccessibilityNodeInfo.ACTION_CLICK), "System media Pause is unavailable");
                } finally {
                    control.recycle();
                }
                await(test, () -> PhoneRadio.status().equals("phone_radio_paused"), 5000,
                        "The media notification did not pause before dismissing");
                await(test, () -> radioNotice(c) != null
                                && (radioNotice(c).flags & Notification.FLAG_FOREGROUND_SERVICE) == 0,
                        5000, "Paused radio still has a foreground notification");
                dismissMedia(test, PhoneRadio.stationName(station, false));
                await(test, () -> !PhoneRadio.hasSession() && radioNotice(c) == null, 5000,
                        "Dismissing the paused media card left a radio session active");
                return;
            }
            audioAndAdhan(test, c, previous, arabic);
            if (arabic) return;
            player.getTransportControls().play();
            await(test, () -> PhoneRadio.status().equals("phone_radio_live"), 15000,
                    "Media Play did not reconnect");
            clickSystemMedia(test, "Pause");
            await(test, () -> PhoneRadio.status().equals("phone_radio_paused"), 5000,
                    "System media Pause did not release playback");
            clickSystemMedia(test, "Play");
            await(test, () -> PhoneRadio.status().equals("phone_radio_live"), 15000,
                    "System media Play did not resume paused radio");
            if (android.os.Build.VERSION.SDK_INT >= 33) clickSystemMedia(test, "Stop radio");
            else radioNotice(c).actions[1].actionIntent.send();
            await(test, () -> !PhoneRadio.hasSession() && radioNotice(c) == null, 5000,
                    "Stop left the radio session or media notification active");
        } finally {
            respond.countDown();
            on(test, PhoneRadio::stop);
            PhonePlayback.stop(c);
            if (a != null) {
                MainActivity done = a;
                on(test, done::finish);
            }
            station.put("url", originalUrl);
            prefs.edit().putString(PhoneRadio.STATION_PREFERENCE, previousStation)
                    .putString("language", previousLanguage).commit();
            prayers.edit().putBoolean("onboarding-seen", seen).commit();
            previous.save(c);
            PhoneAlarms.rebuild(c);
            shell(test, "cmd statusbar collapse");
            shell(test, "input keyevent KEYCODE_WAKEUP");
            shell(test, "wm dismiss-keyguard");
        }
    }

    private static void recoverFromFailure(Instrumentation test, Context c, MainActivity home,
            AtomicBoolean available, CountDownLatch respond) throws Exception {
        MediaController media = mediaPlayer(test, c);
        AtomicInteger reconnects = new AtomicInteger();
        MediaController.Callback callback = new MediaController.Callback() {
            @Override public void onPlaybackStateChanged(PlaybackState playback) {
                if (playback != null && playback.getState() == PlaybackState.STATE_BUFFERING)
                    reconnects.incrementAndGet();
            }
        };
        media.registerCallback(callback, new Handler(Looper.getMainLooper()));
        try {
            respond.countDown();
            await(test, () -> PhoneRadio.status().equals("phone_radio_retrying"), 16000,
                    "Unavailable connection did not enter bounded retry");
            await(test, () -> PhoneRadio.status().equals("phone_radio_failed"), 85000,
                    "Radio kept retrying an unavailable stream");
            check(reconnects.get() >= 4, "Radio gave up before four automatic reconnects");
            await(test, () -> {
                Notification notice = radioNotice(c);
                PlaybackState playback = media.getPlaybackState();
                return notice != null && (notice.flags & Notification.FLAG_FOREGROUND_SERVICE) == 0
                        && playback != null && playback.getState() == PlaybackState.STATE_ERROR
                        && (playback.getActions() & PlaybackState.ACTION_PLAY) != 0;
            }, 5000, "Failed radio still appeared to be playing in Android media controls");
            on(test, () -> {
                TextView status = (TextView) popup(home, "radio.status");
                View quick = home.findViewById(android.R.id.content).findViewWithTag("phone.radio.quick");
                check("Station unavailable".contentEquals(status.getText())
                                && popup(home, "radio.pause") == null
                                && quick.isEnabled() && "Play".contentEquals(quick.getContentDescription()),
                        "Radio failure did not restore Play and explain why playback stopped");
            });
            available.set(true);
            on(test, () -> click(home, "radio.play"));
            await(test, () -> PhoneRadio.status().equals("phone_radio_live"), 15000,
                    "A single Play did not reconnect the failed stream");
            on(test, () -> click(home, "radio.stop"));
            await(test, () -> !PhoneRadio.hasSession() && radioNotice(c) == null, 5000,
                    "Stop left the recovered radio session active");
        } finally {
            media.unregisterCallback(callback);
        }
    }

    private static void audioAndAdhan(Instrumentation test, Context c, PhoneStore previous, boolean alarm)
            throws Exception {
        shell(test, "input keyevent KEYCODE_SLEEP");
        await(test, () -> !c.getSystemService(PowerManager.class).isInteractive(), 5000, "Screen stayed on");
        check(radioNotice(c) != null
                && (radioNotice(c).flags & Notification.FLAG_FOREGROUND_SERVICE) != 0,
                "Radio lost its foreground notification with the screen off");
        PhoneAlarms.cancel(c);
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("reminders.enabled", "true");
        changes.put("prayer.location-ready", "true");
        changes.put("quiet.enabled", "false");
        changes.put("reminders.full-screen", "false");
        changes.put("reminders.alarm-mode", Boolean.toString(alarm));
        changes.put("reminders.volume", "0");
        changes.put("call.fajr.enabled", "true");
        changes.put("call.fajr.mode", "adhan");
        changes.put("call.fajr.weekdays", "127");
        previous.changed(changes).save(c);
        long now = System.currentTimeMillis(), time = now + 3000;
        ReminderEvent event = new ReminderEvent("phone:call:0:" + time, "call", 0, time, time);
        PhoneAlarms.arm(c, event, now);
        await(test, () -> PhonePlayback.outputStarted() && event.getId().equals(PhonePlayback.currentId()),
                15000, "The scheduled Adhan did not start");
        await(test, () -> PhoneRadio.status().equals("phone_radio_adhan") && !PhoneRadio.engaged(),
                1000, "Radio was still active during Adhan");
        MediaController player = mediaPlayer(test, c);
        check((player.getPlaybackState().getActions() & PlaybackState.ACTION_PLAY) == 0,
                "The radio media session offered Play during Adhan");
        shell(test, "input keyevent KEYCODE_WAKEUP");
        shell(test, "wm dismiss-keyguard");
        if (alarm) {
            stopMedia(player);
            await(test, () -> !PhoneRadio.hasSession() && PhonePlayback.playing(),
                    5000, "Stop radio did not end the paused session independently of Adhan");
        }
        PhonePlayback.stop(c);
        await(test, () -> !PhonePlayback.playing() && !PhoneRadio.engaged(), 5000,
                "Adhan stop did not leave radio paused");
    }

    private static void click(MainActivity a, String tag) {
        View view = popup(a, tag);
        check(view != null && view.isShown() && view.isEnabled() && view.performClick(), "Missing radio action " + tag);
    }

    private static View popup(MainActivity a, String tag) {
        return a.radio.popup.getWindow().getDecorView().findViewWithTag(tag);
    }

    private static Notification radioNotice(Context c) {
        for (var entry : c.getSystemService(NotificationManager.class).getActiveNotifications())
            if (PhoneRadio.CHANNEL.equals(entry.getNotification().getChannelId())) return entry.getNotification();
        return null;
    }

    private static MediaController mediaPlayer(Instrumentation test, Context context) {
        await(test, () -> radioNotice(context) != null, 5000, "Radio has no notification");
        Notification notice = radioNotice(context);
        check(notice.extras.getString(Notification.EXTRA_TEMPLATE, "").endsWith("$MediaStyle")
                && notice.deleteIntent != null, "Radio is missing its dismissible MediaStyle card");
        MediaSession.Token token = notice.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
        check(token != null, "Radio media notification is missing its session");
        return new MediaController(context, token);
    }

    private static void stopMedia(MediaController player) {
        PlaybackState.CustomAction stop = player.getPlaybackState().getCustomActions().stream()
                .filter(action -> "tv.miqat.companion.STOP_RADIO".equals(action.getAction()))
                .findFirst().orElseThrow(() -> new AssertionError("Radio media session has no Stop action"));
        player.getTransportControls().sendCustomAction(stop, null);
    }

    private static void clickSystemMedia(Instrumentation test, String label) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 33) shell(test, "cmd statusbar expand-settings");
        else shell(test, "cmd statusbar expand-notifications");
        AccessibilityNodeInfo control = mediaNode(test, label);
        try {
            check(control.performAction(AccessibilityNodeInfo.ACTION_CLICK),
                    "SystemUI did not offer media action " + label);
        } finally {
            control.recycle();
        }
    }

    private static AccessibilityNodeInfo mediaNode(Instrumentation test, String label) throws Exception {
        return mediaNode(test, label, false);
    }

    private static AccessibilityNodeInfo mediaNode(Instrumentation test, String value, boolean id) throws Exception {
        LiveUiCheck.until(() -> {
            AccessibilityNodeInfo found = PhonePresentationCheck.systemUiNode(test, value, id);
            if (found == null) return false;
            found.recycle();
            return true;
        });
        AccessibilityNodeInfo found = PhonePresentationCheck.systemUiNode(test, value, id);
        check(found != null, "Missing system media control " + value);
        return found;
    }

    private static void dismissMedia(Instrumentation test, String station) throws Exception {
        AccessibilityNodeInfo card = mediaNode(test, station);
        try {
            while (card != null && !card.isLongClickable()) {
                AccessibilityNodeInfo parent = card.getParent();
                card.recycle();
                card = parent;
            }
            check(card != null && card.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK),
                    "SystemUI did not open the paused media card's dismissal options");
        } finally {
            if (card != null) card.recycle();
        }
        AccessibilityNodeInfo dismiss = mediaNode(test, "com.android.systemui:id/dismiss", true);
        try {
            check(dismiss.isEnabled() && dismiss.performAction(AccessibilityNodeInfo.ACTION_CLICK),
                    "Paused media card cannot be dismissed");
        } finally {
            dismiss.recycle();
        }
    }

    private static void serve(ServerSocket server, byte[] recording,
            AtomicBoolean available, CountDownLatch respond) {
        while (!server.isClosed()) {
            try (Socket client = server.accept()) {
                client.setSoTimeout(3000);
                BufferedReader request = new BufferedReader(new InputStreamReader(client.getInputStream()));
                int offset = 0;
                for (String line; (line = request.readLine()) != null && !line.isEmpty();) {
                    if (line.regionMatches(true, 0, "Range: bytes=", 0, 13))
                        offset = Integer.parseInt(line.substring(13, line.indexOf('-', 13)));
                }
                OutputStream response = client.getOutputStream();
                if (!respond.await(12, TimeUnit.SECONDS) || !available.get()) {
                    response.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
                    continue;
                }
                if (offset >= recording.length) {
                    response.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Length: 0\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
                    continue;
                }
                String status = offset == 0 ? "200 OK\r\n" : "206 Partial Content\r\nContent-Range: bytes "
                        + offset + "-" + (recording.length - 1) + "/" + recording.length + "\r\n";
                response.write(("HTTP/1.1 " + status + "Content-Type: audio/mpeg\r\nContent-Length: "
                        + (recording.length - offset) + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                response.write(recording, offset, recording.length - offset);
            } catch (IOException closedOrPaused) {
                // Pausing or stopping may close the reader while serving a response.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}

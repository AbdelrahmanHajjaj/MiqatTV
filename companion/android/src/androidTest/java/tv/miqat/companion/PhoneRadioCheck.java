package tv.miqat.companion;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

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

import tv.miqat.core.ReminderEvent;

import static tv.miqat.companion.CompanionCheck.check;
import static tv.miqat.companion.PhoneRuntimeCheck.await;
import static tv.miqat.companion.PhoneRuntimeCheck.shell;
import static tv.miqat.companion.PhoneUiCheck.on;

/** Targeted media check on an owned muted emulator. The fixture serves a packaged recording over loopback. */
final class PhoneRadioCheck {
    static void run(Instrumentation test, String scenario, boolean capture) throws Exception {
        Context c = test.getTargetContext();
        check(PhoneAlarms.notifications(c), "Grant notification access before the radio suite");
        boolean offline = scenario.equals("offline");
        boolean fixture = offline || scenario.equals("fixture") || scenario.equals("alarm-route");
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
        MainActivity a = null;
        try (ServerSocket server = fixture ? new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")) : null) {
            if (fixture) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (var input = c.getAssets().open("adhan/almalki.mp3")) {
                    byte[] buffer = new byte[8192];
                    for (int size; (size = input.read(buffer)) != -1;) bytes.write(buffer, 0, size);
                }
                byte[] recording = bytes.toByteArray();
                Thread stream = new Thread(() -> serve(server, recording), "radio fixture");
                stream.setDaemon(true);
                stream.start();
                station.put("url", "http://127.0.0.1:" + server.getLocalPort() + "/live.mp3");
                if (offline) server.close();
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
                check(home.body.getLayoutDirection() == (arabic ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR),
                        "Radio screen has the wrong language direction");
                click(home, "radio.play");
            });
            if (offline) {
                await(test, () -> PhoneRadio.status().equals("phone_radio_retrying"), 16000,
                        "Unavailable connection did not enter bounded retry");
                await(test, () -> PhoneRadio.status().equals("phone_radio_failed"), 35000,
                        "Radio kept retrying an unavailable stream");
                on(test, () -> click(home, "radio.stop"));
                await(test, () -> !PhoneRadio.hasSession() && radioNotice(c) == null, 5000,
                        "Stop left the failed notification visible");
                return;
            }
            await(test, () -> PhoneRadio.status().equals("phone_radio_live"), 24000,
                    "Station did not become audible: " + id);
            PhoneUiCheck.capture(test, home, arabic ? "radio-ar" : "radio-en", capture);
            if (!fixture) return;
            on(test, () -> home.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)));
            await(test, () -> !home.phoneForeground, 5000, "Radio fixture did not background Miqat");
            audioAndAdhan(test, c, previous, arabic);
            if (arabic) return;
            actionCount(test, c, 2);
            radioNotice(c).actions[0].actionIntent.send();
            await(test, () -> PhoneRadio.status().equals("phone_radio_live"), 15000,
                    "Notification Play did not reconnect");
            await(test, () -> radioNotice(c) != null
                    && containsLabel(radioNotice(c), "Pause"), 5000, "Missing notification Pause");
            radioNotice(c).actions[0].actionIntent.send();
            await(test, () -> PhoneRadio.status().equals("phone_radio_paused"), 5000,
                    "Notification Pause did not release playback");
            await(test, () -> radioNotice(c) != null
                    && containsLabel(radioNotice(c), "Play"), 5000, "Missing notification Play");
            radioNotice(c).actions[0].actionIntent.send();
            await(test, () -> PhoneRadio.status().equals("phone_radio_live"), 15000,
                    "Notification Play did not resume paused radio");
            if (android.os.Build.VERSION.SDK_INT >= 34) dismiss(test, c, station);
            else radioNotice(c).actions[1].actionIntent.send();
            await(test, () -> !PhoneRadio.hasSession() && radioNotice(c) == null, 5000,
                    "Dismiss or Stop left the radio session or notice active");
        } finally {
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
        actionCount(test, c, 1);
        shell(test, "input keyevent KEYCODE_WAKEUP");
        shell(test, "wm dismiss-keyguard");
        if (alarm) {
            radioNotice(c).actions[0].actionIntent.send();
            await(test, () -> !PhoneRadio.hasSession() && PhonePlayback.playing(),
                    5000, "Stop radio did not end the paused session independently of Adhan");
        }
        PhonePlayback.stop(c);
        await(test, () -> !PhonePlayback.playing() && !PhoneRadio.engaged(), 5000,
                "Adhan stop did not leave radio paused");
    }

    private static void click(MainActivity a, String tag) {
        View view = a.body.findViewWithTag(tag);
        check(view != null && view.isEnabled() && view.performClick(), "Missing radio action " + tag);
    }

    private static Notification radioNotice(Context c) {
        for (var entry : c.getSystemService(NotificationManager.class).getActiveNotifications())
            if (PhoneRadio.CHANNEL.equals(entry.getNotification().getChannelId())) return entry.getNotification();
        return null;
    }

    private static void actionCount(Instrumentation test, Context c, int count) {
        await(test, () -> {
            Notification notice = radioNotice(c);
            return notice != null && notice.actions != null && notice.actions.length == count;
        }, 5000, "Expected " + count + " radio notification actions");
    }

    private static boolean containsLabel(Notification notice, String label) {
        return notice != null && notice.actions != null && notice.actions.length > 0
                && label.contentEquals(notice.actions[0].title);
    }

    private static void dismiss(Instrumentation test, Context c, JSONObject station) throws Exception {
        shell(test, "cmd statusbar expand-notifications");
        String title = PhoneRadio.stationName(station, PhoneAlarms.arabic(c));
        LiveUiCheck.until(() -> {
            AccessibilityNodeInfo found = PhonePresentationCheck.notificationText(test, title);
            if (found == null) return false;
            found.recycle();
            return true;
        });
        AccessibilityNodeInfo row = PhonePresentationCheck.notificationText(test, title);
        try {
            while (row != null && !String.valueOf(row.getViewIdResourceName()).endsWith("/expandableNotificationRow")) {
                AccessibilityNodeInfo parent = row.getParent();
                row.recycle();
                row = parent;
            }
            check(row != null && row.performAction(AccessibilityNodeInfo.ACTION_DISMISS),
                    "SystemUI did not offer radio notification dismissal");
        } finally {
            if (row != null) row.recycle();
        }
    }

    private static void serve(ServerSocket server, byte[] recording) {
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
            }
        }
    }
}

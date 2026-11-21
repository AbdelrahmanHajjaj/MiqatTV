package tv.miqat.companion;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.SystemClock;
import java.util.*;

/** Check the live player and session, rather than only the selected preference. */
final class PhoneAudioRouteCheck {
    static void run(Instrumentation test, boolean capture) throws Exception {
        Context c = test.getTargetContext();
        SharedPreferences prefs = PhoneStore.preferences(c);
        String original = prefs.getString("settings", null);
        SharedPreferences ui = c.getSharedPreferences("companion", 0);
        String language = ui.getString("language", "system");
        try {
            PhonePlayback.stop(c);
            PhoneStore settings = PhoneStore.read(c);
            if (settings.values.containsKey("reminders.alarm-mode"))
                settings = settings.changed(Collections.singletonMap("reminders.alarm-mode", "false"));
            settings.save(c);
            audition(test, c, AudioAttributes.USAGE_MEDIA);
            for (String lang : new String[]{"en", "ar"}) {
                ui.edit().putString("language", lang).commit();
                MainActivity a = LiveUiCheck.launch(test);
                try {
                    PhoneUiCheck.on(test, () -> {
                        a.phoneUi.open("audio");
                        android.widget.Switch toggle = (android.widget.Switch) a.body.findViewWithTag("phone.reminders.alarm-mode.edit");
                        PhoneUiCheck.check(toggle != null && !toggle.isChecked(), "Optional alarm mode is missing or enabled by default");
                        toggle.requestRectangleOnScreen(new android.graphics.Rect(0,0,toggle.getWidth(),toggle.getHeight()),true);
                    });
                    PhoneUiCheck.capture(test,a,lang+"-audio-alarm-mode",capture);
                    PhoneUiCheck.on(test, () -> a.body.findViewWithTag("phone.reminders.alarm-mode.edit").performClick());
                    PhoneUiCheck.check(PhoneStore.read(c).value("reminders.alarm-mode").equals("true")
                            && a.getVolumeControlStream() == AudioManager.STREAM_ALARM, "Alarm toggle did not save and route volume keys immediately");
                    audition(test,c,AudioAttributes.USAGE_ALARM);
                    PhoneUiCheck.on(test, () -> a.body.findViewWithTag("phone.reminders.alarm-mode.edit").performClick());
                    PhoneUiCheck.check(PhoneStore.read(c).value("reminders.alarm-mode").equals("false")
                            && a.getVolumeControlStream() == AudioManager.STREAM_MUSIC, "Disabling alarm mode did not restore media volume");
                    audition(test,c,AudioAttributes.USAGE_MEDIA);
                } finally { PhoneUiCheck.on(test,a::finish); }
            }
        } finally {
            PhonePlayback.stop(c);
            SharedPreferences.Editor restore = prefs.edit();
            if (original == null) restore.remove("settings"); else restore.putString("settings", original);
            PhoneUiCheck.check(restore.commit(),"Cannot restore audio route fixture");
            ui.edit().putString("language",language).commit();
            PhoneAlarms.rebuild(c);
        }
    }

    private static void audition(Instrumentation test, Context c, int usage) throws Exception {
        test.runOnMainSync(() -> c.startForegroundService(PhonePlayback.intent(c,0,"almalki",true,"route-"+UUID.randomUUID())
                .putExtra("preview-cap-seconds",12)));
        long end = SystemClock.elapsedRealtime()+10000;
        while (!PhonePlayback.outputStarted() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(50);
        PhoneUiCheck.check(PhonePlayback.outputStarted(),"Route audition did not start");
        PhoneUiCheck.check(c.getSystemService(AudioManager.class).getActivePlaybackConfigurations().stream()
                        .anyMatch(p -> p.getAudioAttributes().getUsage()==usage),
                "Live Adhan player is not using expected audio usage " + usage);
        Notification notice = Arrays.stream(c.getSystemService(NotificationManager.class).getActiveNotifications())
                .map(n -> n.getNotification()).filter(n -> n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)).findFirst()
                .orElseThrow(() -> new AssertionError("Adhan media session missing"));
        MediaSession.Token token = notice.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
        PhoneUiCheck.check(new MediaController(c,token).getPlaybackInfo().getAudioAttributes().getUsage()==usage,
                "Adhan session volume controls disagree with playback");
        PhonePlayback.stop(c);
        end = SystemClock.elapsedRealtime()+5000;
        while (PhonePlayback.playing() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(50);
        PhoneUiCheck.check(!PhonePlayback.playing(),"Route audition did not stop");
    }
}

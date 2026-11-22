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
        MainActivity visible = LiveUiCheck.launch(test);
        try {
            PhonePlayback.stop(c);
            PhoneStore settings = PhoneStore.read(c);
            if (settings.values.containsKey("reminders.alarm-mode"))
                settings = settings.changed(Collections.singletonMap("reminders.alarm-mode", "false"));
            settings = settings.changed(Collections.singletonMap("reminders.volume","70"));
            settings.save(c);
            audition(test, c, AudioAttributes.USAGE_MEDIA,true);
            recovery(c);
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
                    audition(test,c,AudioAttributes.USAGE_ALARM,false);
                    PhoneUiCheck.on(test, () -> a.body.findViewWithTag("phone.reminders.alarm-mode.edit").performClick());
                    PhoneUiCheck.check(PhoneStore.read(c).value("reminders.alarm-mode").equals("false")
                            && a.getVolumeControlStream() == AudioManager.STREAM_MUSIC, "Disabling alarm mode did not restore media volume");
                    audition(test,c,AudioAttributes.USAGE_MEDIA,false);
                } finally { PhoneUiCheck.on(test,a::finish); }
            }
        } finally {
            PhonePlayback.stop(c);
            SharedPreferences.Editor restore = prefs.edit();
            if (original == null) restore.remove("settings"); else restore.putString("settings", original);
            PhoneUiCheck.check(restore.commit(),"Cannot restore audio route fixture");
            ui.edit().putString("language",language).commit();
            PhoneAlarms.rebuild(c);
            PhoneUiCheck.on(test,visible::finish);
        }
    }

    private static void recovery(Context c) throws Exception {
        AudioManager audio=c.getSystemService(AudioManager.class);
        int stream=AudioManager.STREAM_MUSIC, saved=audio.getStreamVolume(stream);
        SharedPreferences prefs=PhoneStore.preferences(c);
        try {
            for (boolean changed : new boolean[]{false,true}) {
                audio.setStreamVolume(stream,changed?2:8,0);
                prefs.edit().putString("audio-level-restore",new org.json.JSONObject()
                        .put("stream",stream).put("before",1).put("applied",8).toString()).commit();
                PhoneVolume.recover(c);
                PhoneUiCheck.check(audio.getStreamVolume(stream)==(changed?2:1) && !prefs.contains("audio-level-restore"),
                        "Persisted recovery did not restore the owned level or preserve a user change");
            }
            prefs.edit().putString("audio-level-restore","{\"stream\":2,\"before\":1,\"applied\":2}").commit();
            PhoneVolume.recover(c);
            PhoneUiCheck.check(audio.getStreamVolume(stream)==2 && !prefs.contains("audio-level-restore"),
                    "Invalid recovery touched volume or remained stored");
        } finally { prefs.edit().remove("audio-level-restore").commit();audio.setStreamVolume(stream,saved,0); }
    }

    private static void audition(Instrumentation test, Context c, int usage, boolean edit) throws Exception {
        AudioManager audio = c.getSystemService(AudioManager.class);
        int stream = usage == AudioAttributes.USAGE_ALARM ? AudioManager.STREAM_ALARM : AudioManager.STREAM_MUSIC;
        int savedVolume = audio.getStreamVolume(stream);
        audio.setStreamVolume(stream,1,0);
        PhoneUiCheck.check(audio.getStreamVolume(stream)==1,"Cannot prepare low stream volume");
        try {
        test.runOnMainSync(() -> c.startForegroundService(PhonePlayback.intent(c,0,"almalki",true,"route-"+UUID.randomUUID())
                .putExtra("preview-cap-seconds",12).putExtra("default-audio",true)));
        long end = SystemClock.elapsedRealtime()+10000;
        while (!PhonePlayback.outputStarted() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(50);
        PhoneUiCheck.check(PhonePlayback.outputStarted(),"Route audition did not start");
        PhoneUiCheck.check(c.getSystemService(AudioManager.class).getActivePlaybackConfigurations().stream()
                        .anyMatch(p -> p.getAudioAttributes().getUsage()==usage),
                "Live Adhan player is not using expected audio usage " + usage);
        int expected = Math.max(1,(audio.getStreamMaxVolume(stream)*70+50)/100);
        PhoneUiCheck.check(audio.getStreamVolume(stream)==expected,
                "Adhan level must be 70% of maximum, independently of prior stream volume; got " + audio.getStreamVolume(stream) + " expected " + expected);
        Notification notice = Arrays.stream(c.getSystemService(NotificationManager.class).getActiveNotifications())
                .map(n -> n.getNotification()).filter(n -> n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)).findFirst()
                .orElseThrow(() -> new AssertionError("Adhan media session missing"));
        MediaSession.Token token = notice.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
        MediaController controller = new MediaController(c,token);
        PhoneUiCheck.check(controller.getPlaybackInfo().getAudioAttributes().getUsage()==usage,
                "Adhan session volume controls disagree with playback");
        if (edit) {
            controller.getTransportControls().pause();
            end=SystemClock.elapsedRealtime()+5000;
            while (!PhonePlayback.paused() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(50);
            test.waitForIdleSync();
            PhoneUiCheck.check(PhonePlayback.paused() && audio.getStreamVolume(stream)==1,"Pause did not restore prior volume");
            controller.getTransportControls().play();
            end=SystemClock.elapsedRealtime()+5000;
            while (!PhonePlayback.outputStarted() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(50);
            PhoneUiCheck.check(PhonePlayback.outputStarted() && audio.getStreamVolume(stream)==expected,"Resume lost absolute volume");
            PhoneStore.read(c).changed(Collections.singletonMap("reminders.volume","0")).save(c);
            PhonePlayback.applySettings(c);test.waitForIdleSync();
            PhoneUiCheck.check(audio.getStreamVolume(stream)==1 && !PhoneStore.preferences(c).contains("audio-level-restore"),
                    "Zero level should release system volume");
            PhoneStore.read(c).changed(Collections.singletonMap("reminders.volume","50")).save(c);
            PhonePlayback.applySettings(c);test.waitForIdleSync();
            PhoneUiCheck.check(audio.getStreamVolume(stream)==(audio.getStreamMaxVolume(stream)*50+50)/100,
                    "Live Adhan level did not apply against maximum");
            audio.setStreamVolume(stream,2,0);
            PhonePlayback.applySettings(c);test.waitForIdleSync();
            PhoneUiCheck.check(audio.getStreamVolume(stream)==2,"Unrelated settings refresh overrode the user's volume change");
        }
        PhonePlayback.stop(c);
        end = SystemClock.elapsedRealtime()+5000;
        while (PhonePlayback.playing() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(50);
        PhoneUiCheck.check(!PhonePlayback.playing(),"Route audition did not stop");
        PhoneUiCheck.check(audio.getStreamVolume(stream)==(edit?2:1),"Adhan did not restore prior volume or preserve a user change");
        } finally {
            PhonePlayback.stop(c);
            long deadline=SystemClock.elapsedRealtime()+5000;
            while (PhonePlayback.playing() && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(50);
            audio.setStreamVolume(stream,savedVolume,0);
            PhoneStore.read(c).changed(Collections.singletonMap("reminders.volume","70")).save(c);
        }
    }
}

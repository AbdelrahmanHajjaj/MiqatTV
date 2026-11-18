package tv.miqat.companion;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import java.util.*;
import java.util.function.BooleanSupplier;
import tv.miqat.core.ReminderEvent;

/** Explicit owned-emulator suite. Grant notification/exact-alarm access first; audio output is muted. */
final class PhoneRuntimeCheck {
    static void denied(Instrumentation test)throws Exception {
        Context c=test.getTargetContext();PhoneStore original=PhoneStore.read(c);
        CompanionCheck.check(!PhoneAlarms.exact(c)||!PhoneAlarms.notifications(c),"Revoke a prayer permission before the denial fixture");
        try {
            Map<String,String> changes=new LinkedHashMap<>();changes.put("reminders.enabled","true");changes.put("prayer.location-ready","true");
            original.changed(changes).save(c);
            CompanionCheck.check(PhoneAlarms.rebuild(c).equals("phone_permission_missing"),"Permission denial must be visible");
            CompanionCheck.check(!PhoneStore.preferences(c).contains("next-event")&&!PhonePlayback.playing(),"Denied alerts scheduled or played");
            android.content.pm.ActivityInfo receiver=c.getPackageManager().getReceiverInfo(new ComponentName(c,PrayerAlarm.class),0);
            android.content.pm.ServiceInfo service=c.getPackageManager().getServiceInfo(new ComponentName(c,PhonePlayback.class),0);
            CompanionCheck.check(!receiver.exported&&receiver.directBootAware&&!service.exported&&service.directBootAware,"Prayer components must stay private and recover before unlock");
        }finally{original.save(c);PhoneAlarms.rebuild(c);}
    }
    static void run(Instrumentation test)throws Exception {
        Context c=test.getTargetContext();SharedPreferences preferences=PhoneStore.preferences(c);
        Map<String,?> original=new HashMap<>(preferences.getAll());
        android.media.AudioManager systemAudio=c.getSystemService(android.media.AudioManager.class);
        int alarmVolume=systemAudio.getStreamVolume(android.media.AudioManager.STREAM_ALARM);
        CompanionCheck.check(PhoneAlarms.exact(c)&&PhoneAlarms.notifications(c),"Grant exact-alarm/notification permissions on the owned emulator before this suite");
        try {
            PhonePlayback.stop(c);PhoneAlarms.cancel(c);
            Map<String,String> changes=new LinkedHashMap<>();changes.put("reminders.enabled","true");changes.put("prayer.location-ready","true");
            changes.put("reminders.volume","0");changes.put("reminders.duration","30");changes.put("call.fajr.mode","adhan");
            changes.put("prayer.latitude","30.0444");changes.put("prayer.longitude","31.2357");changes.put("prayer.method","EGYPTIAN");
            changes.put("prayer.zone","Africa/Cairo");changes.put("call.fajr.offset","0");
            for(String prayer:new String[]{"dhuhr","asr","maghrib","isha"})changes.put("call."+prayer+".mode","off");
            PhoneStore base=PhoneStore.read(c).changed(changes);
            // Keep real calculated Fajr away from this fixture's three-second alarm.
            base=base.changed(Collections.singletonMap("call.fajr.offset",base.day(System.currentTimeMillis(),0).times[0]>=System.currentTimeMillis()?"30":"-30"));
            base.save(c);PhoneAlarms.channels(c);
            c.sendBroadcast(new Intent(c,PrayerAlarm.class).setAction(PhoneAlarms.REFRESH));
            await(test,()->preferences.contains("next-event"),10000,"Recovery broadcast did not rebuild the next alarm");
            CompanionCheck.check(!PhonePlayback.playing(),"Recovery must not start audio directly");
            CompanionCheck.check(preferences.edit().putString("handled","[]").remove("audio-error").commit(),"Cannot initialize alarm fixture");
            long now=System.currentTimeMillis(),time=now+3000;
            ReminderEvent event=new ReminderEvent("phone:call:0:"+time,"call",0,time,time);
            PhoneAlarms.arm(c,event,now);
            AlarmManager.AlarmClockInfo system=c.getSystemService(AlarmManager.class).getNextAlarmClock();
            CompanionCheck.check(system!=null&&system.getTriggerTime()==time,"Actual OS alarm clock registration");
            await(test,()->PhonePlayback.outputStarted()&&PhonePlayback.currentId().equals(event.getId()),15000,"Actual alarm did not start foreground audio");
            CompanionCheck.check(Arrays.stream(c.getSystemService(NotificationManager.class).getActiveNotifications())
                .anyMatch(n->(n.getNotification().flags&Notification.FLAG_FOREGROUND_SERVICE)!=0),"Foreground playback notification missing");
            CompanionCheck.check(PhoneAlarms.claim(c,event.getId(),System.currentTimeMillis())==null,"Duplicate event was accepted");
            PhonePlayback.stop(c);await(test,()->!PhonePlayback.playing(),5000,"Stop did not release foreground playback");
            CompanionCheck.check(systemAudio.getStreamVolume(android.media.AudioManager.STREAM_ALARM)==alarmVolume,"Miqat changed global alarm volume");
            // A stale/early fixture event must not be claimed or produce playback.
            now=System.currentTimeMillis();ReminderEvent expired=new ReminderEvent("phone:call:0:expired","call",0,now-120000,now-120000);
            PhoneAlarms.arm(c,expired,now);CompanionCheck.check(PhoneAlarms.claim(c,expired.getId(),now)==null,"Expired prayer accepted");PhoneAlarms.cancel(c);
            ReminderEvent future=new ReminderEvent("phone:call:0:future","call",0,now+60000,now+60000);
            PhoneAlarms.arm(c,future,now);CompanionCheck.check(PhoneAlarms.claim(c,future.getId(),now)==null,"Early prayer accepted");PhoneAlarms.cancel(c);
            // User preview uses the same foreground player; an external focus owner stops it.
            test.runOnMainSync(()->c.startForegroundService(PhonePlayback.intent(c,0,"alafasy",true,"preview-fixture")));
            await(test,PhonePlayback::outputStarted,10000,"Preview did not start");
            test.runOnMainSync(()->c.startForegroundService(PhonePlayback.intent(c,0,"alafasy",true,"replacement-preview")));
            await(test,()->PhonePlayback.outputStarted()&&PhonePlayback.currentId().equals("replacement-preview"),10000,"Replacing preview did not release/restart the player");
            android.media.AudioManager audio=c.getSystemService(android.media.AudioManager.class);
            android.media.AudioFocusRequest focus=new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setOnAudioFocusChangeListener(change->{}).setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION).build()).build();
            test.runOnMainSync(()->audio.requestAudioFocus(focus));
            try{await(test,()->!PhonePlayback.playing(),5000,"Audio focus loss did not release playback");}
            finally{audio.abandonAudioFocusRequest(focus);}
            test.runOnMainSync(()->c.startForegroundService(PhonePlayback.intent(c,0,"alafasy",true,"bounded-preview")));
            await(test,PhonePlayback::outputStarted,10000,"Bounded preview did not start");
            long previewStarted=SystemClock.elapsedRealtime();
            await(test,()->!PhonePlayback.playing(),16000,"Preview exceeded its twelve-second bound");
            CompanionCheck.check(SystemClock.elapsedRealtime()-previewStarted>=10000&&!preferences.contains("audio-error"),"Preview failed instead of completing its bounded playback");
            test.runOnMainSync(()->c.startForegroundService(PhonePlayback.intent(c,0,"alafasy",false,"disable-fixture")));
            await(test,PhonePlayback::outputStarted,10000,"Disable fixture did not start");
            PhoneStore.read(c).changed(Collections.singletonMap("reminders.enabled","false")).save(c);PhoneAlarms.rebuild(c);
            await(test,()->!PhonePlayback.playing(),5000,"Disabling alerts did not stop current playback");
            CompanionCheck.check(!PhonePlayback.playing()&&!PhoneStore.preferences(c).contains("next-event"),"Disabled alerts retained active playback/alarm");
        }finally {
            PhonePlayback.stop(c);PhoneAlarms.cancel(c);
            SharedPreferences.Editor restore=preferences.edit().clear();
            for(Map.Entry<String,?> entry:original.entrySet()) {
                Object value=entry.getValue();String key=entry.getKey();
                if(value instanceof String)restore.putString(key,(String)value);
                else if(value instanceof Boolean)restore.putBoolean(key,(Boolean)value);
                else if(value instanceof Integer)restore.putInt(key,(Integer)value);
                else if(value instanceof Long)restore.putLong(key,(Long)value);
                else if(value instanceof Float)restore.putFloat(key,(Float)value);
                else throw new IllegalStateException("Unexpected fixture preference type");
            }
            CompanionCheck.check(restore.commit(),"Cannot restore alarm fixture");PhoneAlarms.rebuild(c);
        }
    }
    private static void await(Instrumentation test,BooleanSupplier condition,long timeout,String error) {
        long until=SystemClock.uptimeMillis()+timeout;boolean[] ready={false};
        do{test.runOnMainSync(()->ready[0]=condition.getAsBoolean());if(ready[0])return;SystemClock.sleep(50);}while(SystemClock.uptimeMillis()<until);
        CompanionCheck.check(false,error);
    }
}

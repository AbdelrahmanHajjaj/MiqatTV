package tv.miqat.companion;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.*;

/** Actual thirty-second AlarmManager checks on an owned muted emulator; no TV or real schedule edits. */
final class PhoneTestCheck {
    private static android.service.notification.StatusBarNotification notice(Context c) {
        return Arrays.stream(c.getSystemService(NotificationManager.class).getActiveNotifications())
                .filter(n->n.getId()==PhoneAlertTest.NOTICE).findFirst().orElse(null);
    }
    static void run(Instrumentation test) throws Exception {
        Context c=test.getTargetContext();SharedPreferences prefs=PhoneStore.preferences(c);
        CompanionCheck.check(android.os.Build.HARDWARE.contains("ranchu")||android.os.Build.HARDWARE.contains("goldfish"),"Owned muted emulator required");
        CompanionCheck.check(PhoneAlarms.notifications(c)&&PhoneAlarms.exact(c),"Grant notification and timing access for actual delivery checks");
        CompanionCheck.check(!PhonePlayback.playing()&&!PhoneAlertTest.pending(c),"Finish playback/pending test before this fixture");
        Map<String,?> original=new LinkedHashMap<>(prefs.getAll());
        try {
            PhoneAlarms.cancel(c);PhoneAlertTest.cancel(c);
            Map<String,String> values=new LinkedHashMap<>();values.put("reminders.enabled","false");
            values.put("prayer.location-ready","false");values.put("reminders.voice","almalki");
            values.put("reminders.volume","0");values.put("reminders.duration","0");values.put("reminders.length-percent","100");
            values.put("reminders.full-screen","false");
            for(String prayer:PhoneStore.PRAYERS)if(!prayer.equals("sunrise")) {
                values.put("call."+prayer+".enabled","false");values.put("call."+prayer+".mode","off");values.put("call."+prayer+".weekdays","0");
            }
            PhoneStore.read(c).changed(values).save(c);
            String savedSettings=prefs.getString("settings","");
            prefs.edit().putString("next-event","{\"fixture\":\"real-schedule\"}").putString("handled","[\"real-claim\"]").commit();
            String schedule=prefs.getString("next-event",""),claims=prefs.getString("handled","");
            CompanionCheck.check(PhoneAlertTest.scheduleFixture(c,true,12).equals("phone_test_pending"),"Disabled prayer flags must not block an explicit test");
            JSONObject cancelled=new JSONObject(prefs.getString("test-event",""));
            CompanionCheck.check(cancelled.getLong("due")-cancelled.getLong("created")==30000
                    && PhoneAlertTest.claim(c,cancelled.getString("id"),cancelled.getLong("due")-1)==null
                    && PhoneAlertTest.pending(c)&&!PhonePlayback.playing(),"Test violated the thirty-second grace period");
            PhoneAlertTest.cancel(c);PhoneAlertTest.fire(c,cancelled.getString("id"));
            CompanionCheck.check(!PhoneAlertTest.pending(c)&&!PhonePlayback.playing()&&notice(c)==null,"Cancelled callback produced output");
            PhoneAlertTest.schedule(c,false);
            JSONObject boot=new JSONObject(prefs.getString("test-event",""));boot.put("boot",Integer.MIN_VALUE);
            prefs.edit().putString("test-event",boot.toString()).commit();
            CompanionCheck.check(PhoneAlertTest.claim(c,boot.getString("id"),boot.getLong("due"))==null,"Test survived a changed boot epoch");
            PhoneAlertTest.cancel(c);PhoneAlertTest.schedule(c,true);
            JSONObject claimed=new JSONObject(prefs.getString("test-event",""));
            CompanionCheck.check(PhoneAlertTest.claim(c,claimed.getString("id"),claimed.getLong("due"))!=null,
                    "Could not model the claimed receiver-to-service handoff");
            CompanionCheck.check(PhoneAlertTest.pending(c)&&PhoneAlertTest.schedule(c,false).equals("phone_test_pending"),
                    "Claimed test lost cancellation or allowed a second test before service startup");
            PhoneAlertTest.cancel(c);
            CompanionCheck.check(!PhoneAlertTest.pending(c)&&!prefs.contains("active-test"),"Cancelled claimed test left a ghost counter");
            PhoneAlertTest.schedule(c,false);
            JSONObject stale=new JSONObject(prefs.getString("test-event",""));
            CompanionCheck.check(PhoneAlertTest.claim(c,stale.getString("id"),stale.getLong("due")+PhoneAlertTest.EXPIRY_MS)==null,
                    "Expired test remained deliverable");
            PhoneAlertTest.cancel(c);
            CompanionCheck.check(PhoneAlertTest.schedule(c,false).equals("phone_test_pending"),"Notification test did not arm normally");
            JSONObject notification=new JSONObject(prefs.getString("test-event",""));
            PhoneAlertTest.fire(c,notification.getString("id"));
            CompanionCheck.check(notice(c)==null&&PhoneAlertTest.pending(c),"Early test callback delivered a notification");
            PhoneRuntimeCheck.await(test,()->notice(c)!=null,45000,"Actual thirty-second notification test did not arrive");
            CompanionCheck.check(SystemClock.elapsedRealtime()-notification.getLong("created")>=30000,"OS notification test fired before its grace period");
            long posted=notice(c).getPostTime();PhoneAlertTest.fire(c,notification.getString("id"));
            CompanionCheck.check(notice(c).getPostTime()==posted,"Duplicate test callback reposted its notification");
            CompanionCheck.check(savedSettings.equals(prefs.getString("settings",""))&&schedule.equals(prefs.getString("next-event",""))
                    && claims.equals(prefs.getString("handled","")),"Explicit tests modified real prayer state");
            PhoneAlertTest.cancel(c);
            CompanionCheck.check(PhoneAlertTest.scheduleFixture(c,true,12).equals("phone_test_pending"),"Adhan test did not arm normally");
            JSONObject adhan=new JSONObject(prefs.getString("test-event",""));
            PhoneAlarms.rebuild(c);
            CompanionCheck.check(PhoneAlertTest.pending(c)&&new JSONObject(prefs.getString("test-event","")).getString("id").equals(adhan.getString("id")),
                    "Normal schedule rebuild cancelled the explicit test");
            String afterRebuild=prefs.getString("next-event",null);
            PhoneRuntimeCheck.await(test,PhonePlayback::outputStarted,45000,"Actual thirty-second test did not start normal foreground audio");
            CompanionCheck.check(SystemClock.elapsedRealtime()-adhan.getLong("created")>=30000&&PhonePlayback.scheduled()
                    && PhonePlayback.currentId().equals(adhan.getString("id")),"Test playback bypassed grace/token validation");
            long started=SystemClock.elapsedRealtime();
            PhoneAlertTest.fire(c,adhan.getString("id"));
            PhoneRuntimeCheck.await(test,()->!PhonePlayback.playing(),16000,"Fixture test audio exceeded its explicit twelve-second cap");
            CompanionCheck.check(SystemClock.elapsedRealtime()-started>=10000&&!prefs.contains("active-test")
                    && !prefs.contains("test-event")&&!prefs.contains("audio-error"),"Test did not finish and release its one-shot token");
            CompanionCheck.check(savedSettings.equals(prefs.getString("settings",""))&&Objects.equals(afterRebuild,prefs.getString("next-event",null))
                    && claims.equals(prefs.getString("handled","")),"Adhan test mutated schedule state or enable flags");
        } finally {
            PhoneAlertTest.cancel(c);PhonePlayback.stop(c);
            PhoneRuntimeCheck.await(test,()->!PhonePlayback.playing(),5000,"Test cleanup retained playback");
            SharedPreferences.Editor restore=prefs.edit().clear();
            for(Map.Entry<String,?> item:original.entrySet()) {
                Object value=item.getValue();String key=item.getKey();
                if(value instanceof String)restore.putString(key,(String)value);
                else if(value instanceof Boolean)restore.putBoolean(key,(Boolean)value);
                else if(value instanceof Integer)restore.putInt(key,(Integer)value);
                else if(value instanceof Long)restore.putLong(key,(Long)value);
                else if(value instanceof Float)restore.putFloat(key,(Float)value);
                else throw new AssertionError("Unknown fixture preference type");
            }
            CompanionCheck.check(restore.commit(),"Cannot restore explicit-test fixture");PhoneAlarms.rebuild(c);
        }
    }
}

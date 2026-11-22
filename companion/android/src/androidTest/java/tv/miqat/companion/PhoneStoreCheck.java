package tv.miqat.companion;

import android.app.Instrumentation;
import android.content.SharedPreferences;
import java.util.*;

/** Real Android storage/metadata boundary; never schedules or plays a prayer. */
final class PhoneStoreCheck {
    static void run(Instrumentation test)throws Exception {
        android.content.Context context=test.getTargetContext();SharedPreferences prefs=PhoneStore.preferences(context);
        String original=prefs.getString("settings",null);
        Map<String,?> tvSettings=new HashMap<>(context.getSharedPreferences("companion",0).getAll());
        try {
            PhoneStore current=PhoneStore.read(context);
            Map<String,String> edits=new LinkedHashMap<>();edits.put("prayer.latitude","30.0444");edits.put("prayer.longitude","31.2357");
            edits.put("prayer.location-ready","true");edits.put("reminders.enabled","false");edits.put("prayer.zone","Africa/Cairo");
            edits.put("call.fajr.mode","notification");edits.put("call.fajr.before","5");edits.put("call.fajr.after","10");
            PhoneStore changed=current.changed(edits);changed.save(context);PhoneStore saved=PhoneStore.read(context);
            CompanionCheck.check(saved.modes[0]==1&&saved.before[0]==5&&saved.after[0]==10,"Phone prayer settings persisted incorrectly");
            CompanionCheck.check(saved.modes[1]==0&&saved.locationReady&&saved.zone().getID().equals("Africa/Cairo"),"Phone location/sunrise contract");
            CompanionCheck.check(tvSettings.equals(context.getSharedPreferences("companion",0).getAll()),"Phone changes modified TV settings/cache");
            for(Map<String,String> bad:Arrays.asList(Collections.singletonMap("unknown","value"),Collections.singletonMap("prayer.latitude","NaN"),
                Collections.singletonMap("prayer.longitude","181"),Collections.singletonMap("reminders.volume","71"),
                Collections.singletonMap("prayer.zone","Missing/Zone"),Collections.singletonMap("call.fajr.mode","execute")))
                LanCheck.rejects(()->saved.changed(bad));
            CompanionCheck.check(saved.day(System.currentTimeMillis(),0).times!=null,"Native offline calculation unavailable");
        }finally{
            SharedPreferences.Editor restore=prefs.edit();if(original==null)restore.remove("settings");else restore.putString("settings",original);
            CompanionCheck.check(restore.commit(),"Cannot restore phone settings fixture");
        }
    }
}

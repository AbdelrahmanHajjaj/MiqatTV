package tv.miqat.companion;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import org.json.*;
import java.util.*;
import tv.miqat.core.ReminderEvent;
import tv.miqat.companion.core.PhonePrayerRules;

/** One next prayer event and a bounded daily recovery check; never an idle service. */
final class PhoneAlarms {
    static final String FIRE="tv.miqat.companion.PRAYER_ALARM",REFRESH="tv.miqat.companion.REFRESH_ALARMS";
    static final String ALERTS="phone-prayers",REMINDERS="phone-reminders",PLAYBACK="phone-adhan";
    private static final int EVENT=41701,MAINTENANCE=41702;
    private static AlarmManager manager(Context c){return (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);}
    static boolean exact(Context c){return Build.VERSION.SDK_INT<31||manager(c).canScheduleExactAlarms();}
    static boolean notifications(Context c){return (Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)&&c.getSystemService(NotificationManager.class).areNotificationsEnabled();}
    static boolean arabic(Context c) {
        boolean unlocked=c.getSystemService(UserManager.class).isUserUnlocked();
        String language=unlocked?c.getSharedPreferences("companion",0).getString("language","system"):
            PhoneStore.preferences(c).getString("language-mode","system");
        return language.equals("ar")||language.equals("system")&&Locale.getDefault().getLanguage().equals("ar");
    }
    static String text(Context c,String key) {
        try{return PhoneData.get(c).text(key,arabic(c));}catch(Exception unavailable){return "Miqat";}
    }
    static PendingIntent open(Context c) {
        return PendingIntent.getActivity(c,41700,new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent pending(Context c,String action,int code,String id) {
        return PendingIntent.getBroadcast(c,code,new Intent(c,PrayerAlarm.class).setAction(action).putExtra("event",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static void channels(Context c)throws Exception {
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(ALERTS,text(c,"phone_home"),NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(new NotificationChannel(REMINDERS,text(c,"phone_audio"),NotificationManager.IMPORTANCE_HIGH));
        NotificationChannel audio=new NotificationChannel(PLAYBACK,text(c,"phone_adhan"),NotificationManager.IMPORTANCE_LOW);
        audio.setSound(null,null);audio.enableVibration(false);manager.createNotificationChannel(audio);
    }
    static boolean channel(Context c,String id){NotificationChannel channel=c.getSystemService(NotificationManager.class).getNotificationChannel(id);return notifications(c)&&channel!=null&&channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;}
    static String status(Context c,PhoneStore settings) {
        if(!settings.enabled)return "phone_alerts_off";
        if(!settings.locationReady)return "phone_location_needed";
        if(!notifications(c)||!exact(c))return "phone_permission_missing";
        if(Arrays.stream(settings.modes).anyMatch(mode->mode==2)&&!channel(c,PLAYBACK)||
           Arrays.stream(settings.modes).anyMatch(mode->mode==1)&&!channel(c,ALERTS))return "phone_permission_missing";
        if(Arrays.stream(settings.modes).noneMatch(mode->mode>0))return "phone_alerts_off";
        String error=PhoneStore.preferences(c).getString("alarm-error","");
        return error.isEmpty()?"phone_alerts_ready":error;
    }
    static String rebuild(Context context) {
        Context c=context.getApplicationContext();
        synchronized(PhoneStore.LOCK) {
            try {
                cancel(c);
                channels(c);
                if(c.getSystemService(UserManager.class).isUserUnlocked())PhoneStore.preferences(c).edit()
                    .putString("language-mode",c.getSharedPreferences("companion",0).getString("language","system")).apply();
                PhoneStore settings=PhoneStore.read(c);String state=status(c,settings);
                PhonePlayback.applySettings(c,settings);
                if(!notifications(c))PhonePlayback.stop(c);
                if(!settings.enabled||!settings.locationReady||!notifications(c)||!exact(c))return state;
                long now=System.currentTimeMillis();double[][] days=settings.days(now);
                ReminderEvent event=PhonePrayerRules.INSTANCE.nextEvent(days,now,settings.modes,settings.before,settings.after,handled(c));
                if(event!=null)arm(c,event,now);
                else if(Arrays.stream(settings.modes).anyMatch(mode->mode>0)) {
                    Calendar tomorrow=new GregorianCalendar(settings.zone());tomorrow.setTimeInMillis(now);tomorrow.add(Calendar.DAY_OF_MONTH,1);
                    tomorrow.set(Calendar.HOUR_OF_DAY,0);tomorrow.set(Calendar.MINUTE,5);tomorrow.set(Calendar.SECOND,0);tomorrow.set(Calendar.MILLISECOND,0);
                    manager(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,tomorrow.getTimeInMillis(),pending(c,REFRESH,MAINTENANCE,""));
                }
                if(!PhoneStore.preferences(c).edit().putString("alarm-error",days.length==0?"phone_unavailable":"").commit())return "phone_save_failed";
                return status(c,settings);
            } catch(Exception failure) {
                PhoneStore.preferences(c).edit().putString("alarm-error","phone_settings_failed").apply();return "phone_settings_failed";
            }
        }
    }
    static void cancel(Context c) {
        manager(c).cancel(pending(c,FIRE,EVENT,""));manager(c).cancel(pending(c,REFRESH,MAINTENANCE,""));
        if(!PhoneStore.preferences(c).edit().remove("next-event").commit())throw new IllegalStateException("Cannot clear scheduled alarm");
    }
    static void arm(Context c,ReminderEvent event,long now)throws Exception {
        JSONObject value=new JSONObject().put("id",event.getId()).put("kind",event.getKind()).put("prayer",event.getPrayer())
            .put("time",event.getTime()).put("prayerTime",event.getPrayerTime());
        if(!PhoneStore.preferences(c).edit().putString("next-event",value.toString()).commit())throw new java.io.IOException("Cannot save alarm");
        long time=Math.max((long)event.getTime(),now+500);
        try{manager(c).setAlarmClock(new AlarmManager.AlarmClockInfo(time,open(c)),pending(c,FIRE,EVENT,event.getId()));}
        catch(SecurityException denied){PhoneStore.preferences(c).edit().remove("next-event").putString("alarm-error","phone_permission_missing").apply();throw denied;}
    }
    private static String[] handled(Context c)throws Exception {
        String raw=PhoneStore.preferences(c).getString("handled","[]");if(raw.length()>16384)throw new java.io.IOException("Claim record too large");
        JSONArray saved=new JSONArray(raw);if(saved.length()>64)throw new java.io.IOException("Too many claims");
        String[] result=new String[saved.length()];for(int i=0;i<result.length;i++){if(!(saved.get(i) instanceof String))throw new java.io.IOException("Invalid claim type");result[i]=saved.getString(i);}return result;
    }
    static ReminderEvent claim(Context c,String requested,long now)throws Exception {
        synchronized(PhoneStore.LOCK) {
            String raw=PhoneStore.preferences(c).getString("next-event","");if(raw.isEmpty()||raw.length()>1024||requested==null)return null;
            JSONObject value=new JSONObject(raw);String id=value.getString("id"),kind=value.getString("kind");int prayer=value.getInt("prayer");
            if(!id.equals(requested)||!tv.miqat.core.PrayerRules.INSTANCE.isPrayer(prayer)||!Arrays.asList("call","before","after").contains(kind)||
                !PhonePrayerRules.INSTANCE.due(value.getDouble("time"),now))return null;
            PhoneStore settings=PhoneStore.read(c);if(!settings.enabled||!settings.locationReady||settings.modes[prayer]==0||!notifications(c))return null;
            List<String> claims=new ArrayList<>(Arrays.asList(handled(c)));if(claims.contains(id))return null;
            claims.add(id);while(claims.size()>64)claims.remove(0);
            if(!PhoneStore.preferences(c).edit().putString("handled",new JSONArray(claims).toString()).commit())throw new java.io.IOException("Cannot save claim");
            return new ReminderEvent(id,kind,prayer,value.getDouble("time"),value.getDouble("prayerTime"));
        }
    }
    static void notify(Context c,ReminderEvent event,boolean failure) {
        try {
            channels(c);String channel=event.getKind().equals("call")?ALERTS:REMINDERS;if(!channel(c,channel))return;
            PhoneData data=PhoneData.get(c);String name=data.prayerName(event.getPrayer(),arabic(c));
            String key=event.getKind().equals("before")?"phone_advance_title":event.getKind().equals("after")?"phone_after_title":"phone_adhan_title";
            String title=String.format(Locale.getDefault(),text(c,key),name);
            Notification n=new Notification.Builder(c,channel).setSmallIcon(R.drawable.companion_icon_mihrab)
                .setContentTitle(title).setContentText(failure?text(c,"phone_audio_failed"):"Miqat · "+name)
                .setContentIntent(open(c)).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER).build();
            int kind=event.getKind().equals("call")?0:event.getKind().equals("before")?1:2;
            c.getSystemService(NotificationManager.class).notify(42000+event.getPrayer()*3+kind,n);
        }catch(Exception unavailable){PhoneStore.preferences(c).edit().putString("alarm-error","phone_permission_missing").apply();}
    }
}

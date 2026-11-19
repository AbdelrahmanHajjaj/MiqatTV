package tv.miqat.companion;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.IOException;
import java.util.*;
import tv.miqat.companion.core.PhonePrayerRules;
import tv.miqat.core.PrayerRules;

/** Independent local phone settings. No TV credentials or cached TV settings enter this store. */
final class PhoneStore {
    static final String[] PRAYERS={"fajr","sunrise","dhuhr","asr","maghrib","isha"};
    static final Object LOCK=new Object();
    final Map<String,String> values;
    final PhoneData data;
    final double latitude,longitude;
    final int[] modes=new int[6],before=new int[6],after=new int[6],offsets=new int[6];
    final boolean enabled,locationReady;
    final int volume,duration;
    private PhoneStore(PhoneData data,Map<String,String> values)throws Exception {
        data.validate(values);this.data=data;this.values=Collections.unmodifiableMap(new LinkedHashMap<>(values));
        latitude=Double.parseDouble(value("prayer.latitude"));longitude=Double.parseDouble(value("prayer.longitude"));
        enabled=value("reminders.enabled").equals("true");locationReady=value("prayer.location-ready").equals("true");
        volume=number("reminders.volume");duration=number("reminders.duration");
        offsets[1]=number("prayer.sunrise-offset");
        for(int i=0;i<6;i++)if(i!=1){String key="call."+PRAYERS[i]+".";
            modes[i]=Arrays.asList("off","notification","adhan","vibration","silent").indexOf(value(key+"mode"));
            before[i]=number(key+"before");after[i]=number(key+"after");offsets[i]=number(key+"offset");
        }
        PhonePrayerRules.INSTANCE.validate(latitude,longitude,modes,before,after,volume,duration);
        PrayerRules.INSTANCE.validateOffsets(offsets);
    }
    static SharedPreferences preferences(Context c){return c.createDeviceProtectedStorageContext().getSharedPreferences("phone-prayer",Context.MODE_PRIVATE);}
    static PhoneStore read(Context context)throws Exception {
        synchronized(LOCK) {
            PhoneData data=PhoneData.get(context);String stored=preferences(context).getString("settings",null);
            if(stored==null)return new PhoneStore(data,data.defaults);
            if(stored.length()>65536)throw new IOException("Phone settings too large");
            JSONObject record=new JSONObject(stored);Object schema=record.get("version");if(!(schema instanceof Integer))throw new IOException("Invalid phone settings version type");int version=(Integer)schema;if(version<1||version>2)throw new IOException("Unsupported phone settings version");
            JSONObject fields=record.getJSONObject("values");Map<String,String> values=new LinkedHashMap<>();
            Iterator<String> keys=fields.keys();while(keys.hasNext()){String key=keys.next();Object value=fields.get(key);if(!(value instanceof String))throw new IOException("Invalid phone setting type");values.put(key,(String)value);}
            if(version==1){if(!values.keySet().equals(data.legacyIds()))throw new IOException("Invalid v1 phone fields");Map<String,String> merged=new LinkedHashMap<>(data.defaults);merged.putAll(values);values=merged;}
            return new PhoneStore(data,values);
        }
    }
    PhoneStore changed(Map<String,String> changes)throws Exception {
        Map<String,String> next=new LinkedHashMap<>(values);next.putAll(changes);return new PhoneStore(data,next);
    }
    void save(Context context)throws Exception {
        String stored=new JSONObject().put("version",2).put("values",new JSONObject(values)).toString();
        synchronized(LOCK){if(!preferences(context).edit().putString("settings",stored).commit())throw new IOException("Phone settings could not be saved");}
    }
    String value(String id){String value=values.get(id);if(value==null)throw new IllegalArgumentException("Unknown phone setting: "+id);return value;}
    int number(String id){return Integer.parseInt(value(id));}
    TimeZone zone(){String id=value("prayer.zone");return id.equals("system")?TimeZone.getDefault():TimeZone.getTimeZone(id);}
    String voice(int prayer){String selected=value("call."+PRAYERS[prayer]+".voice");return selected.equals("inherit")?value("reminders.voice"):selected;}
    boolean vibrate(int prayer){return value("call."+PRAYERS[prayer]+".vibrate").equals("true");}
    boolean anyAlerts(){
        if(value("quiet.enabled").equals("true")&&value("quiet.mode").equals("off")&&number("quiet.start")==number("quiet.end"))return false;
        for(int i=0;i<6;i++)if(i!=1&&modes[i]>0&&number("call."+PRAYERS[i]+".weekdays")>0)return true;return false;
    }
    int audioVolume(int prayer){return prayer<0||value("call."+PRAYERS[prayer]+".default-audio").equals("true")?volume:number("call."+PRAYERS[prayer]+".volume");}
    int audioDuration(int prayer){return prayer<0||value("call."+PRAYERS[prayer]+".default-audio").equals("true")?duration:number("call."+PRAYERS[prayer]+".duration");}
    int quietMode(){String mode=value("quiet.mode");return mode.equals("off")?0:mode.equals("vibration")?3:4;}
    int eventMode(tv.miqat.core.ReminderEvent event){
        Calendar date=new GregorianCalendar(zone());date.setTimeInMillis((long)event.getPrayerTime());int weekday=date.get(Calendar.DAY_OF_WEEK)-1;
        date.setTimeInMillis((long)event.getTime());int minute=date.get(Calendar.HOUR_OF_DAY)*60+date.get(Calendar.MINUTE);
        return tv.miqat.core.ReminderRules.INSTANCE.alertMode(modes[event.getPrayer()],weekday,number("call."+PRAYERS[event.getPrayer()]+".weekdays"),minute,value("quiet.enabled").equals("true"),number("quiet.start"),number("quiet.end"),quietMode());
    }
    tv.miqat.core.ReminderEvent nextEvent(double[][] days,long now,String[] handled){
        List<String> excluded=new ArrayList<>(Arrays.asList(handled));
        for(int i=0;i<days.length*15;i++){
            tv.miqat.core.ReminderEvent event=PhonePrayerRules.INSTANCE.nextEvent(days,now,modes,before,after,excluded.toArray(new String[0]));
            if(event==null||eventMode(event)>0)return event;excluded.add(event.getId());
        }
        return null;
    }
    PhoneSchedule.Day day(long now,int offset) {
        com.batoulapps.adhan.HighLatitudeRule high=value("prayer.high-latitude").equals("middle")?com.batoulapps.adhan.HighLatitudeRule.MIDDLE_OF_THE_NIGHT:
            value("prayer.high-latitude").equals("seventh")?com.batoulapps.adhan.HighLatitudeRule.SEVENTH_OF_THE_NIGHT:com.batoulapps.adhan.HighLatitudeRule.TWILIGHT_ANGLE;
        return PhoneSchedule.day(now,zone(),offset,latitude,longitude,PrayerMethod.valueOf(value("prayer.method")),
            value("prayer.asr").equals("hanafi")?com.batoulapps.adhan.Madhab.HANAFI:com.batoulapps.adhan.Madhab.SHAFI,high,offsets);
    }
    double[][] days(long now) {
        List<double[]> days=new ArrayList<>();
        for(int offset=-1;offset<=7;offset++){PhoneSchedule.Day day=day(now,offset);if(day.times!=null)days.add(day.times);}
        return days.toArray(new double[0][]);
    }
}

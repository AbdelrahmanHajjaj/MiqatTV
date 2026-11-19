package tv.miqat.companion;

import android.app.*;
import android.content.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import tv.miqat.core.ReminderEvent;

/** Private isolated preferences/files; no TV or audible playback. */
final class PhoneCustomCheck {
    static void run(Instrumentation test)throws Exception {
        Context base=test.getTargetContext().createDeviceProtectedStorageContext();
        File directory=Files.createTempDirectory(base.getFilesDir().toPath(),"custom-check-").toFile();String key=directory.getName();
        Context c=new ContextWrapper(base){
            @Override public Context createDeviceProtectedStorageContext(){return this;}
            @Override public File getFilesDir(){return directory;}
            @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return base.getSharedPreferences(key+name,mode);}
        };
        try {
            PhoneStore current=PhoneStore.read(c);Map<String,String> old=new LinkedHashMap<>(current.values);old.keySet().retainAll(current.data.legacyIds());old.put("reminders.volume","35");old.put("call.fajr.voice","inherit");
            PhoneStore.preferences(c).edit().putString("settings",new JSONObject().put("version",1).put("values",new JSONObject(old)).toString()).commit();
            PhoneStore migrated=PhoneStore.read(c);PhoneUiCheck.check(migrated.value("call.fajr.voice").equals("inherit")&&migrated.volume==35&&migrated.number("call.fajr.weekdays")==127&&migrated.value("call.fajr.default-audio").equals("true"),"v1 settings/default migration");
            old.remove("reminders.volume");PhoneStore.preferences(c).edit().putString("settings",new JSONObject().put("version",1).put("values",new JSONObject(old)).toString()).commit();LanCheck.rejects(()->PhoneStore.read(c));migrated.save(c);
            Map<String,String> edits=new LinkedHashMap<>();for(String prayer:PhoneStore.PRAYERS)if(!prayer.equals("sunrise"))edits.put("call."+prayer+".mode","off");
            edits.put("call.fajr.mode","adhan");edits.put("call.fajr.weekdays","1");edits.put("prayer.zone","Africa/Cairo");edits.put("call.fajr.default-audio","false");edits.put("call.fajr.volume","15");edits.put("call.fajr.duration","30");
            PhoneStore sunday=migrated.changed(edits);PhoneUiCheck.check(sunday.audioVolume(0)==15&&sunday.audioDuration(0)==30&&sunday.audioVolume(2)==35,"Per-prayer audio override leaked");
            Calendar day=new GregorianCalendar(sunday.zone());day.clear();day.set(2026,10,19,0,0);long now=day.getTimeInMillis();double[][] days=new double[8][6];
            for(int d=0;d<8;d++){for(int prayer=0;prayer<6;prayer++)days[d][prayer]=day.getTimeInMillis()+(5+prayer*2)*3600000.0;day.add(Calendar.DATE,1);}
            ReminderEvent next=sunday.nextEvent(days,now,new String[0]);day.setTimeInMillis((long)next.getPrayerTime());PhoneUiCheck.check(day.get(Calendar.DAY_OF_WEEK)==Calendar.SUNDAY&&next.getTime()>now+2*86400000L,"Weekday schedule did not plan the next week");
            edits.clear();edits.put("quiet.enabled","true");edits.put("quiet.start","0");edits.put("quiet.end","0");edits.put("quiet.mode","off");PhoneStore quiet=sunday.changed(edits);
            PhoneUiCheck.check(!quiet.anyAlerts()&&quiet.nextEvent(days,now,new String[0])==null,"Suppressed events repeatedly rearmed");
            edits.put("quiet.mode","vibration");PhoneUiCheck.check(sunday.changed(edits).eventMode(next)==3,"Quiet vibration mode not applied");
            PhoneUiCheck.check(PhoneAlarms.requiredChannels(sunday.changed(edits)).equals(Collections.singleton(PhoneAlarms.VIBRATION)),"All-day quiet hours required unused sound channels");
            edits.clear();edits.put("call.fajr.mode","vibration");edits.put("call.fajr.before","5");PhoneUiCheck.check(PhoneAlarms.requiredChannels(sunday.changed(edits)).equals(Collections.singleton(PhoneAlarms.VIBRATION)),"Vibration reminders required an audible channel");
            PhoneAudio.importFile(c,c.getAssets().open("adhan/alafasy.mp3"),"reminders.voice");PhoneStore imported=PhoneStore.read(c);String hash=imported.value("audio.custom.default");
            PhoneUiCheck.check(imported.voice(0).equals("custom")&&PhoneAudio.file(c,imported,0,true).isFile(),"Imported global voice not inherited");
            LanCheck.rejects(()->PhoneAudio.importFile(c,new ByteArrayInputStream(new byte[]{1,2,3}),"reminders.voice"));
            PhoneUiCheck.check(PhoneStore.read(c).value("audio.custom.default").equals(hash),"Invalid audio lost previous selection");
            LanCheck.rejects(()->PhoneAudio.importFile(c,new InputStream(){@Override public int read()throws IOException{throw new IOException("Interrupted fixture copy");}},"reminders.voice"));
            PhoneUiCheck.check(PhoneStore.read(c).value("audio.custom.default").equals(hash),"Failed copy lost previous audio");
            LanCheck.rejects(()->PhoneAudio.importFile(c,new InputStream(){long remaining=PhoneAudio.MAX_BYTES+1;@Override public int read(){return remaining-->0?0:-1;}@Override public int read(byte[] bytes,int offset,int length){if(remaining<=0)return -1;int n=(int)Math.min(length,remaining);Arrays.fill(bytes,offset,offset+n,(byte)0);remaining-=n;return n;}},"reminders.voice"));
            PhoneUiCheck.check(PhoneStore.read(c).value("audio.custom.default").equals(hash),"Oversized import lost previous audio");
            PhoneAlarms.channels(c);NotificationManager manager=c.getSystemService(NotificationManager.class);
            PhoneUiCheck.check(manager.getNotificationChannel(PhoneAlarms.SILENT).getSound()==null&&!manager.getNotificationChannel(PhoneAlarms.SILENT).shouldVibrate(),"Silent channel produces output");
            PhoneUiCheck.check(manager.getNotificationChannel(PhoneAlarms.VIBRATION).getSound()==null&&manager.getNotificationChannel(PhoneAlarms.VIBRATION).shouldVibrate(),"Vibration channel has sound or no vibration");
        } finally {
            PhoneStore.preferences(c).edit().clear().commit();base.deleteSharedPreferences(key+"phone-prayer");
            File audio=new File(directory,"custom-audio");File[] files=audio.listFiles();if(files!=null)for(File file:files)Files.delete(file.toPath());Files.deleteIfExists(audio.toPath());Files.delete(directory.toPath());
        }
    }
}

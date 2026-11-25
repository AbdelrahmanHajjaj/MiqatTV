package tv.miqat.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import tv.miqat.companion.lan.Json;
import tv.miqat.companion.lan.PairingRoutes;
import tv.miqat.core.SettingChanges;
import tv.miqat.core.UiContract;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Semantic LAN IDs bind to the same preference keys and side effects as the native editors. */
final class CompanionSettings implements PairingRoutes.Settings {
    private final CompanionApplication app;
    private final JSONObject catalog;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String,String> strings=new LinkedHashMap<>(),numbers=new LinkedHashMap<>(),toggles=new LinkedHashMap<>();
    private final LinkedHashMap<String,Integer> remote=new LinkedHashMap<>();
    private static final String[] PRAYERS={"fajr","sunrise","dhuhr","asr","maghrib","isha"};
    CompanionSettings(CompanionApplication app)throws Exception {
        this.app=app;
        try(InputStream in=app.getAssets().open("catalog.json")) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
            while((n=in.read(b))!=-1)bytes.write(b,0,n);catalog=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
        }
        map(strings,"prayer.location","city","prayer.method","method","prayer.asr","madhab","reminders.voice","voice","reminders.fajr-voice","voice_fajr",
            "radio.station","station","display.language","locale","display.digits","digits","display.clock","clock","display.bar-mode","dock_mode",
            "display.bar-position","dock_position","appearance.favourites","home_layout","appearance.theme","theme","appearance.background","background",
            "appearance.custom-theme","theme_json","device.redirects","redirects");
        map(numbers,"prayer.hijri","hijri_offset","prayer.facing","facing","reminders.before","remind","reminders.close","call_close",
            "radio.screen-off","radio_dark","display.size","ui_scale","appearance.mihrab-opacity","mihrab_opacity","appearance.lighting","lighting","appearance.cycle","appearance_cycle");
        map(toggles,"reminders.spoken","remind_voice","reminders.adhan","adhan","reminders.announce","announce","radio.resume","radio_resume",
            "display.hide-system-apps","hide_system_apps","display.quick-labels","quick_labels","appearance.focus-lighting","focus_lighting",
            "appearance.reduce-motion","reduce_motion","device.repair","repair_service","device.remote-enabled","remote_enabled");
        for(int i=0;i<PRAYERS.length;i++) {
            numbers.put("offsets."+PRAYERS[i],"prayer_offset_"+i);
            if(i!=1)toggles.put("prayer.adhan-"+PRAYERS[i],"bell_"+i);
        }
        String[] ids={"settings-shortcut","settings","input","tv","red","green","yellow","blue"};
        int[] codes={KeyEvent.KEYCODE_NOTIFICATION,KeyEvent.KEYCODE_SETTINGS,KeyEvent.KEYCODE_TV_INPUT,KeyEvent.KEYCODE_TV,
            KeyEvent.KEYCODE_PROG_RED,KeyEvent.KEYCODE_PROG_GREEN,KeyEvent.KEYCODE_PROG_YELLOW,KeyEvent.KEYCODE_PROG_BLUE};
        for(int i=0;i<ids.length;i++)remote.put("remote."+ids[i],codes[i]);
    }
    private static void map(Map<String,String> out,String... pairs){for(int i=0;i<pairs.length;i+=2)out.put(pairs[i],pairs[i+1]);}
    private <T>T onMain(Callable<T> task)throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper())return task.call();
        FutureTask<T> future=new FutureTask<>(task);main.post(future);
        try{return future.get(6,TimeUnit.SECONDS);}
        catch(java.util.concurrent.ExecutionException failed){Throwable cause=failed.getCause();if(cause instanceof Exception)throw (Exception)cause;throw new IllegalStateException(cause);}
        finally{future.cancel(false);}
    }
    @Override public JSONObject info()throws Exception {
        return new JSONObject().put("protocol",1).put("profile","android").put("name",android.os.Build.MODEL)
            .put("version",app.getPackageManager().getPackageInfo(app.getPackageName(),0).versionName)
            .put("systemLanguage",app.getResources().getConfiguration().getLocales().get(0).getLanguage());
    }
    @Override public JSONObject read()throws Exception{return onMain(this::snapshot);}
    @Override public JSONObject remote()throws Exception{return onMain(()->{
        if(!app.foreground)throw new IllegalStateException("Launcher inactive");
        JSONArray commands=new JSONArray(),inputs=new JSONArray();
        for(String id:app.config.commands)if(!id.equals("device.exit-input"))commands.put(id);
        for(InputSources.Source source:InputSources.list(app))inputs.put(new JSONObject().put("value",source.intent.toUri(0)).put("label",source.label));
        android.app.Activity activity=app.activity();boolean preview=activity instanceof NowActivity&&((NowActivity)activity).companionPreview();
        String radio=Radio.state()==Radio.PLAYING?"playing":Radio.state()==Radio.CONNECTING||Radio.state()==Radio.RETRYING?"loading":Radio.state()==Radio.PAUSED?"paused":"idle";
        return new JSONObject().put("commands",commands).put("inputs",inputs).put("input","")
            .put("view",activity==null?"":activity.getClass().getSimpleName()).put("media",new JSONObject().put("kind",preview?"adhan":Radio.on()?"radio":"")
                .put("state",preview?((NowActivity)activity).companionPlayback():radio));
    });}
    @Override public JSONObject command(String id,String value)throws Exception{return onMain(()->{
        if(!app.foreground)throw new IllegalStateException("Launcher inactive");
        if(!app.config.commands.contains(id)||value.length()>2048||!id.equals("device.inputs")&&!value.isEmpty())throw new IllegalArgumentException("Unsupported command");
        boolean stopping=id.equals("radio.stop")||id.equals("tests.cancel");
        if(!stopping&&app.busy())return Json.object("status","busy");
        android.app.Activity activity=app.activity();
        switch(id){
            case "device.home":app.startActivity(new android.content.Intent(app,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));break;
            case "device.inputs":
                if(value.isEmpty())app.startActivity(new android.content.Intent(app,InputsActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                else{
                    InputSources.Source selected=null;for(InputSources.Source source:InputSources.list(app))if(source.intent.toUri(0).equals(value))selected=source;
                    if(selected==null)return Json.object("status","unavailable");
                    if(!InputSources.launch(app,selected.intent))return Json.object("status","unavailable");
                }break;
            case "radio.play":if(!Radio.on())Radio.start(app);break;
            case "radio.stop":Radio.stop();break;
            case "tests.adhan-short":app.startActivity(new android.content.Intent(app,NowActivity.class).putExtra("companionPreview",true).putExtra("prayer",PrayerDay.DHUHR).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));break;
            case "tests.cancel":if(activity instanceof NowActivity&&((NowActivity)activity).companionPreview())activity.finish();break;
            default:return Json.object("status","unavailable");
        }
        return Json.object("status","accepted");
    });}
    private LinkedHashMap<Integer,String> mappings() {
        LinkedHashMap<Integer,String> result=new LinkedHashMap<>();
        for(String[] row:RemoteKeys.keys(app)){int code=RemoteKeys.code(row[0]);if(RemoteKeys.allowedCode(code))result.put(code,row[1]);}
        for(Integer code:result.keySet())if(!remote.containsValue(code))remote.put("remote.key-"+code,code);
        return result;
    }
    private JSONObject snapshot()throws Exception {
        if(!app.foreground)throw new IllegalStateException("Launcher inactive");
        JSONArray source=catalog.getJSONObject("profiles").getJSONArray("android"),controls=new JSONArray();JSONObject values=new JSONObject();
        LinkedHashMap<Integer,String> mappings=mappings();
        LinkedHashSet<String> apps=new LinkedHashSet<>(Apps.installed(app));apps.addAll(Apps.favourites(app));
        for(String target:mappings.values())if(!target.startsWith("@"))apps.add(target);
        for(String pair:Prefs.of(app).getString("redirects","").split(","))for(String item:pair.split("="))if(!item.isEmpty())apps.add(item);
        JSONArray options=new JSONArray();Text ar=new Text(true,false),en=new Text(false,false);
        for(String pkg:apps)options.put(new JSONObject().put("value",pkg).put("ar",Apps.name(app,ar,pkg)).put("en",Apps.name(app,en,pkg)));
        Set<String> found=new HashSet<>();
        for(int i=0;i<source.length();i++) {
            JSONObject c=new JSONObject(source.getJSONObject(i).toString());String id=c.getString("id");found.add(id);
            if(id.equals("about.changelog"))continue;
            if(id.equals("favourites.order"))c.put("options",options);
            if(remote.containsKey(id))remoteOptions(c,options);
            if(c.optBoolean("available") && !c.getString("kind").equals("action") && !c.getString("section").equals("tests")) {
                String value=value(id,c,mappings);if(value==null)throw new IllegalStateException("Unbound setting: "+id);values.put(id,value);
            }
            controls.put(c);
        }
        for(Map.Entry<String,Integer> entry:remote.entrySet())if(!found.contains(entry.getKey())) {
            JSONObject c=new JSONObject().put("id",entry.getKey()).put("section","remote").put("kind","choice").put("save","immediate")
                .put("available",true).put("default","@default").put("route","")
                .put("title",new JSONObject().put("ar",RemoteButtonsActivity.buttonName(app,ar,entry.getValue())).put("en",RemoteButtonsActivity.buttonName(app,en,entry.getValue())));
            remoteOptions(c,options);controls.put(c);values.put(entry.getKey(),mappings.getOrDefault(entry.getValue(),"@default"));
        }
        return new JSONObject().put("profile","android").put("systemLanguage",info().getString("systemLanguage")).put("controls",controls).put("values",values);
    }
    private void remoteOptions(JSONObject c,JSONArray apps)throws Exception {
        JSONArray choices=new JSONArray();
        JSONObject base=null;
        JSONArray all=catalog.getJSONObject("profiles").getJSONArray("android");
        for(int i=0;i<all.length();i++)if(all.getJSONObject(i).getString("id").equals("remote.settings"))base=all.getJSONObject(i);
        JSONArray actions=Objects.requireNonNull(base).getJSONArray("options");
        for(int i=0;i<actions.length();i++)if(actions.getJSONObject(i).getString("value").startsWith("@"))choices.put(actions.getJSONObject(i));
        for(int i=0;i<apps.length();i++)choices.put(apps.getJSONObject(i));c.put("options",choices);
    }
    private String value(String id,JSONObject c,Map<Integer,String> mappings)throws Exception {
        SharedPreferences p=Prefs.of(app);
        if(id.equals("prayer.location"))return Prefs.city(app).id;
        if(id.equals("prayer.method"))return Prefs.method(app).name();
        if(id.equals("prayer.asr"))return Prefs.madhab(app)==com.batoulapps.adhan.Madhab.HANAFI?"hanafi":"shafi";
        if(id.equals("display.digits"))return p.getString("digits",Screen.loadText(app).eastern?"eastern":"western");
        if(id.equals("display.clock"))return Prefs.clock(app);
        if(id.equals("display.bar-position"))return p.getString("dock_position","bottom");
        if(id.equals("display.size"))return String.valueOf(Prefs.uiScale(app));
        if(id.equals("reminders.spoken"))return String.valueOf(Prefs.remindVoice(app));
        if(id.equals("reminders.close"))return String.valueOf(Prefs.callCloseMinutes(app));
        if(id.equals("radio.playback"))return String.valueOf(Radio.on());
        if(id.equals("favourites.order"))return String.join(",",Apps.favourites(app));
        if(remote.containsKey(id))return mappings.getOrDefault(remote.get(id),"@default");
        String[] positions=positions(id);
        if(positions!=null)return positions[Math.max(0,Math.min(positions.length-1,p.getInt(positionKey(id),0)))];
        if(id.equals("appearance.lighting"))return String.valueOf(Appearance.style(app,p.getString("theme","mihrab"),p.getString("background","theme"),p.getInt("lighting",-1)).lighting);
        if(id.equals("prayer.facing"))return String.valueOf(p.getInt("facing",-1));
        if(strings.containsKey(id))return p.getString(strings.get(id),c.optString("default"));
        if(numbers.containsKey(id))return String.valueOf(p.getInt(numbers.get(id),Integer.parseInt(c.optString("default","0"))));
        if(toggles.containsKey(id))return String.valueOf(p.getBoolean(toggles.get(id),Boolean.parseBoolean(c.optString("default"))));
        return null;
    }
    private static String[] positions(String id) {
        switch(id) {
            case "appearance.countdown":return new String[]{"mihrab","compact","hidden"};
            case "appearance.position":return new String[]{"auto","left","right"};
            case "appearance.compact-position":return new String[]{"top","bottom"};
            case "appearance.prayers":return new String[]{"all","current","next","both","none"};
            default:return null;
        }
    }
    private static String positionKey(String id) {
        switch(id){case "appearance.countdown":return "home_countdown";case "appearance.position":return "home_side";
            case "appearance.compact-position":return "home_bar";case "appearance.prayers":return "home_prayers";default:throw new IllegalArgumentException();}
    }
    @Override public JSONObject apply(JSONObject expected,JSONObject changes)throws Exception{return onMain(()->applyMain(expected,changes));}
    private JSONObject applyMain(JSONObject expected,JSONObject changes)throws Exception {
        JSONObject current=snapshot();
        if(app.busy())return current.put("status","busy").put("conflicts",new JSONArray());
        if(changes.length()==0)throw new IllegalArgumentException("No changes");
        JSONObject values=current.getJSONObject("values");JSONArray catalog=current.getJSONArray("controls");
        LinkedHashMap<String,JSONObject> specs=new LinkedHashMap<>();
        for(int i=0;i<catalog.length();i++){JSONObject c=catalog.getJSONObject(i);if(values.has(c.getString("id")))specs.put(c.getString("id"),c);}
        LinkedHashMap<String,String> patch=new LinkedHashMap<>();
        for(Iterator<String> it=changes.keys();it.hasNext();) {String id=it.next();validate(id,Json.string(changes,id),specs);patch.put(id,Json.string(changes,id));}
        if(patch.containsKey("prayer.location"))for(Prefs.City city:Prefs.CITIES)if(city.id.equals(patch.get("prayer.location"))) {
            patch.putIfAbsent("prayer.method",city.method.name());patch.putIfAbsent("prayer.asr",city.madhab==com.batoulapps.adhan.Madhab.HANAFI?"hanafi":"shafi");
        }
        for(Iterator<String> it=expected.keys();it.hasNext();)if(!specs.containsKey(it.next()))throw new IllegalArgumentException("Unknown baseline");
        String[] ids=specs.keySet().toArray(new String[0]),groups=new String[ids.length],before=new String[ids.length],base=new String[ids.length];
        for(int i=0;i<ids.length;i++){groups[i]=specs.get(ids[i]).optString("section").equals("appearance")?"appearance":"";before[i]=values.getString(ids[i]);base[i]=expected.has(ids[i])?Json.string(expected,ids[i]):null;}
        String[] conflicts=SettingChanges.INSTANCE.conflicts(ids,groups,before,base,patch.keySet().toArray(new String[0]));
        if(conflicts.length>0)return current.put("status","conflict").put("conflicts",new JSONArray(conflicts));
        String theme=patch.getOrDefault("appearance.theme",values.optString("appearance.theme"));
        if(theme.equals("custom"))Appearance.checked(patch.getOrDefault("appearance.custom-theme",values.optString("appearance.custom-theme")));
        SharedPreferences.Editor save=Prefs.of(app).edit();LinkedHashMap<Integer,String> mapping=mappings();boolean keysChanged=false;
        for(Map.Entry<String,String> item:patch.entrySet()) {
            String id=item.getKey(),value=item.getValue();
            if(strings.containsKey(id))save.putString(strings.get(id),value);
            else if(numbers.containsKey(id))save.putInt(numbers.get(id),Integer.parseInt(value));
            else if(toggles.containsKey(id))save.putBoolean(toggles.get(id),Boolean.parseBoolean(value));
            else if(positions(id)!=null)save.putInt(positionKey(id),Arrays.asList(positions(id)).indexOf(value));
            else if(id.equals("favourites.order"))save.putString("favourites",value);
            else if(remote.containsKey(id)){mapping.put(remote.get(id),value);keysChanged=true;}
        }
        if(keysChanged){List<String> table=new ArrayList<>();for(Map.Entry<Integer,String> item:mapping.entrySet())table.add(item.getKey()+"="+item.getValue());String tableValue=String.join(",",table);RemoteKeys.validate(tableValue);save.putString("keys",tableValue);}
        if(patch.containsKey("appearance.cycle")){int interval=Integer.parseInt(patch.get("appearance.cycle"));if(interval==0)save.remove("appearance_cycle_at");else save.putLong("appearance_cycle_at",AppearanceCycle.next(System.currentTimeMillis(),interval));}
        boolean committed=save.commit();
        if(committed) {
            if(keysChanged || patch.containsKey("device.remote-enabled"))RemoteKeys.reload();
            if(patch.containsKey("radio.station"))Radio.switchTo(app,Radio.stationIndex(app));
            if(patch.containsKey("radio.playback")){if(Boolean.parseBoolean(patch.get("radio.playback")))Radio.start(app);else Radio.stop();}
            app.refreshScreen();
        }
        return snapshot().put("status",committed?"saved":"uncertain").put("conflicts",new JSONArray());
    }
    private void validate(String id,String value,Map<String,JSONObject> specs)throws Exception {
        JSONObject c=specs.get(id);if(c==null)throw new IllegalArgumentException("Unknown setting");
        switch(c.getString("kind")) {
            case "toggle":if(!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException();break;
            case "number":
                if(!value.matches("-?(0|[1-9][0-9]*)"))throw new IllegalArgumentException();int number=Integer.parseInt(value);
                if(number<UiContract.INSTANCE.minimum(id)||number>UiContract.INSTANCE.maximum(id)||(id.equals("display.size")||id.equals("appearance.mihrab-opacity"))&&(number-UiContract.INSTANCE.minimum(id))%UiContract.INSTANCE.numberStep(id)!=0)throw new IllegalArgumentException();break;
            case "choice":
                if(!options(c).contains(value) || remote.containsKey(id)&&!value.startsWith("@")&&Apps.launchIntent(app,value)==null)throw new IllegalArgumentException();break;
            case "list":List<String> items=value.isEmpty()?Collections.emptyList():Arrays.asList(value.split(",",-1));
                if(items.size()>Apps.FAVOURITE_SLOTS || new HashSet<>(items).size()!=items.size() || !options(c).containsAll(items))throw new IllegalArgumentException();break;
            case "text":
                if(value.length()>c.getInt("maximum"))throw new IllegalArgumentException();
                if(id.equals("appearance.custom-theme")&&!value.isEmpty())Appearance.checked(Json.read(value).toString());
                if(id.equals("device.redirects"))redirects(value,specs.get("favourites.order"));break;
            default:throw new IllegalArgumentException();
        }
    }
    private static Set<String> options(JSONObject c)throws Exception {Set<String> values=new HashSet<>();JSONArray options=c.getJSONArray("options");for(int i=0;i<options.length();i++)values.add(options.getJSONObject(i).getString("value"));return values;}
    private static void redirects(String value,JSONObject apps)throws Exception {
        Map<String,String> map=new HashMap<>();Set<String> allowed=options(apps);
        if(!value.isEmpty())for(String pair:value.split(",",-1)){String[] parts=pair.split("=",-1);if(parts.length!=2||!allowed.contains(parts[0])||!allowed.contains(parts[1])||map.put(parts[0],parts[1])!=null)throw new IllegalArgumentException();}
        for(String start:map.keySet()){Set<String> seen=new HashSet<>();for(String next=start;map.containsKey(next);next=map.get(next))if(!seen.add(next))throw new IllegalArgumentException("Redirect cycle");}
    }
}

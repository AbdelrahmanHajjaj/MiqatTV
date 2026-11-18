package tv.miqat.companion;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tv.miqat.companion.core.SettingSpec;

/** Phone-only UI metadata comes from shared/data/phone-ui-contract.json. */
final class PhoneData {
    private static volatile PhoneData cached;
    final JSONArray cities,voices;
    final JSONObject source;
    final LinkedHashMap<String,JSONObject> controls=new LinkedHashMap<>();
    final LinkedHashMap<String,String> defaults=new LinkedHashMap<>();
    private final Catalog catalog;
    static PhoneData get(Context context)throws Exception {
        PhoneData value=cached;if(value!=null)return value;
        synchronized(PhoneData.class){if(cached==null)cached=new PhoneData(context);return cached;}
    }
    private PhoneData(Context context)throws Exception {
        catalog=new Catalog(context);
        try(InputStream in=context.getAssets().open("phone.json")) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
            while((n=in.read(b))!=-1){if(out.size()+n>1048576)throw new IOException("Phone metadata too large");out.write(b,0,n);}
            source=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }
        cities=source.getJSONArray("cities");voices=source.getJSONArray("voices");
        JSONObject contract=source.getJSONObject("contract");JSONArray general=contract.getJSONArray("controls");
        for(int i=0;i<general.length();i++)add(general.getJSONObject(i));
        JSONArray perPrayer=contract.getJSONArray("perPrayer");
        for(String prayer:PhoneStore.PRAYERS)if(!prayer.equals("sunrise"))for(int i=0;i<perPrayer.length();i++) {
            JSONObject item=new JSONObject(perPrayer.getJSONObject(i).toString());
            item.put("id","call."+prayer+"."+item.getString("suffix")).put("section",prayer);add(item);
        }
        JSONObject city=city(contract.getString("defaultCity"));
        defaults.put("prayer.location",city.getString("id"));defaults.put("prayer.latitude",Double.toString(city.getDouble("lat")));
        defaults.put("prayer.longitude",Double.toString(city.getDouble("lng")));defaults.put("prayer.location-ready","false");
    }
    private void add(JSONObject c)throws Exception {
        String id=c.getString("id");if(controls.put(id,c)!=null)throw new IOException("Duplicate phone control");
        if(c.getString("kind").equals("number")&&c.optInt("step",1)<1)throw new IOException("Invalid numeric step");
        defaults.put(id,c.getString("default"));
    }
    String text(String key,boolean ar){return catalog.label(catalog.data.optJSONObject("text").optJSONObject(key),ar);}
    String prayerName(int prayer,boolean ar)throws Exception{return catalog.data.getJSONObject("prayerNames").getJSONArray(ar?"ar":"en").getString(prayer);}
    JSONObject city(String id)throws Exception {
        for(int i=0;i<cities.length();i++)if(cities.getJSONObject(i).getString("id").equals(id))return cities.getJSONObject(i);
        throw new IllegalArgumentException("Unknown city");
    }
    List<String> options(JSONObject c)throws Exception {
        Object definition=c.opt("choices");List<String> values=new ArrayList<>();
        if(definition instanceof JSONArray){JSONArray items=(JSONArray)definition;for(int i=0;i<items.length();i++)values.add(items.getString(i));return values;}
        String kind=String.valueOf(definition);
        switch(kind) {
            case "methods":for(PrayerMethod method:PrayerMethod.values())values.add(method.name());break;
            case "voices":case "prayerVoices":
                if(kind.equals("prayerVoices"))values.add("inherit");
                for(int i=0;i<voices.length();i++)values.add(voices.getJSONObject(i).getString("id"));break;
            case "timezones":values.add("system");List<String> zones=new ArrayList<>(Arrays.asList(TimeZone.getAvailableIDs()));Collections.sort(zones);values.addAll(zones);break;
            case "themes":Iterator<String> keys=catalog.data.getJSONObject("themes").keys();while(keys.hasNext()){String id=keys.next();if(!id.equals("custom"))values.add(id);}break;
            case "backgrounds":JSONArray backgrounds=source.getJSONArray("backgrounds");for(int i=0;i<backgrounds.length();i++)values.add(backgrounds.getString(i));break;
            default:throw new IllegalArgumentException("Unknown phone choices");
        }
        return values;
    }
    SettingSpec spec(JSONObject c)throws Exception {
        return new SettingSpec(c.getString("id"),c.getString("kind"),"",c.optString("kind").equals("choice")?options(c):Collections.emptyList(),
            c.optInt("minimum"),c.optInt("maximum"),c.optInt("step",1));
    }
    void validate(Map<String,String> values)throws Exception {
        if(!values.keySet().equals(defaults.keySet()))throw new IllegalArgumentException("Invalid phone setting IDs");
        for(JSONObject c:controls.values()) {
            String value=values.get(c.getString("id"));SettingSpec spec=spec(c);spec.validate(value);
            if(c.getString("kind").equals("number")&&(Integer.parseInt(value)-spec.getMinimum())%spec.getStep()!=0)
                throw new IllegalArgumentException("Invalid phone numeric step");
        }
        if(!Arrays.asList("true","false").contains(values.get("prayer.location-ready")))throw new IllegalArgumentException("Invalid location state");
        if(!values.get("prayer.location").equals("custom"))city(values.get("prayer.location"));
    }
}

package tv.miqat.companion.lan;

import android.util.JsonReader;
import android.util.JsonToken;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Android's JSONObject constructor accepts duplicate keys and non-JSON syntax. Network input must not. */
public final class Json {
    private Json() {}
    public static JSONObject read(String text)throws Exception {
        try(JsonReader reader=new JsonReader(new StringReader(text))) {
            reader.setLenient(false);Object value=read(reader,0);
            if(!(value instanceof JSONObject) || reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("JSON object required");
            return (JSONObject)value;
        }
    }
    private static Object read(JsonReader r,int depth)throws Exception {
        if(depth>16)throw new IOException("JSON too deep");
        switch(r.peek()) {
            case BEGIN_OBJECT:
                r.beginObject();JSONObject object=new JSONObject();Set<String> keys=new HashSet<>();
                while(r.hasNext()) {
                    String key=r.nextName();if(keys.size()>=4096 || !keys.add(key))throw new IOException("Duplicate or excessive JSON keys");
                    object.put(key,read(r,depth+1));
                }
                r.endObject();return object;
            case BEGIN_ARRAY:
                r.beginArray();JSONArray array=new JSONArray();
                while(r.hasNext()) {if(array.length()>=4096)throw new IOException("JSON array too long");array.put(read(r,depth+1));}
                r.endArray();return array;
            case STRING:return r.nextString();
            case NUMBER:
                String number=r.nextString();
                if(!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))throw new IOException("Invalid JSON number");
                return new BigDecimal(number);
            case BOOLEAN:return r.nextBoolean();
            case NULL:r.nextNull();return JSONObject.NULL;
            default:throw new IOException("Invalid JSON value");
        }
    }
    public static void keys(JSONObject object,String... allowed)throws Exception {
        Set<String> keys=new HashSet<>(Arrays.asList(allowed));
        for(Iterator<String> it=object.keys();it.hasNext();)if(!keys.contains(it.next()))throw new IOException("Unknown JSON field");
    }
    public static String string(JSONObject object,String key)throws Exception {
        Object value=object.get(key);if(!(value instanceof String))throw new IOException("Expected string");return (String)value;
    }
    public static JSONObject object(String key,Object value)throws Exception {return new JSONObject().put(key,value);}
}

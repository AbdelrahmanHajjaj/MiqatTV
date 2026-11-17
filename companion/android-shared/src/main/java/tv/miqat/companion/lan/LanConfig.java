package tv.miqat.companion.lan;

import android.content.Context;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class LanConfig {
    public final int version,qrVersion,port,maxPhones,maxBodyBytes,maxResponseBytes,timeoutMillis,maxQrBytes;
    public final long pairWindowMillis;
    public final java.util.Set<String> commands=new java.util.LinkedHashSet<>();
    public LanConfig(Context context)throws Exception {
        JSONObject value;
        try(InputStream in=context.getAssets().open("lan.json")){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int count;
            while((count=in.read(buffer))!=-1){if(bytes.size()+count>4096)throw new IllegalStateException("LAN configuration too large");bytes.write(buffer,0,count);}
            value=Json.read(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
        }
        version=value.getInt("version");qrVersion=value.getInt("qrVersion");port=value.getInt("port");maxPhones=value.getInt("maxPhones");
        org.json.JSONArray offered=value.getJSONArray("commands");
        for(int i=0;i<offered.length();i++){String id=offered.getString(i);if(!id.matches("[a-z]+\\.[a-z-]+")||!commands.add(id)||commands.size()>16)throw new IllegalStateException("Invalid remote commands");}
        maxBodyBytes=value.getInt("maxBodyBytes");maxResponseBytes=value.getInt("maxResponseBytes");
        timeoutMillis=value.getInt("timeoutMillis");maxQrBytes=value.getInt("maxQrBytes");pairWindowMillis=value.getLong("pairWindowMillis");
        if(version!=1 || qrVersion!=3 || port<1024 || port>65535 || maxPhones<1 || maxPhones>32 || maxBodyBytes<1 || maxBodyBytes>65536 ||
            maxResponseBytes<maxBodyBytes || maxResponseBytes>2097152 || timeoutMillis<1000 || timeoutMillis>10000 ||
            maxQrBytes<256 || maxQrBytes>4096 || pairWindowMillis<1000 || pairWindowMillis>300000)throw new IllegalStateException("Invalid LAN configuration");
    }
}

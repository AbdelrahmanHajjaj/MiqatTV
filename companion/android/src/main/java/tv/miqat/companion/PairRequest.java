package tv.miqat.companion;

import android.content.Context;
import org.json.JSONObject;
import tv.miqat.companion.lan.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** One explicitly scanned window. It cannot apply launcher settings. */
final class PairRequest {
    final Endpoint endpoint;
    String certificate;
    final String token,nonce,code;
    LanClient client;
    private final LanConfig config;
    private final Phones store;
    PairRequest(String qr,Context context,Phones store)throws Exception {
        this.store=store;config=new LanConfig(context);
        if(qr.getBytes(StandardCharsets.UTF_8).length>config.maxQrBytes)throw new IllegalArgumentException("QR too large");
        PairingQr.Data compact=qr.startsWith("MIQ3:")?PairingQr.decode(qr):null;
        JSONObject value=compact==null?Json.read(qr):new JSONObject().put("type","miqat-tv-pair").put("version",3).put("url",compact.url).put("pin",compact.pin).put("token",compact.token);
        int version=value.getInt("version");
        if(compact==null&&version!=1&&version!=2)throw new IllegalArgumentException("Compact QR required for schema 3");
        if(!Json.string(value,"type").equals("miqat-tv-pair") || !(value.get("version") instanceof Number) || value.getDouble("version")!=version || version!=1&&version!=2&&version!=config.qrVersion)
            throw new IllegalArgumentException("Unsupported pairing QR");
        if(version==1)Json.keys(value,"type","version","url","pin","certificate","token");
        else Json.keys(value,"type","version","url","pin","token");
        boolean debug=(context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        endpoint=new Endpoint(Json.string(value,"url"),Json.string(value,"pin"),config.port,debug);
        certificate=version==1?Json.string(value,"certificate"):"";token=Json.string(value,"token");if(!Pairing.tokenShape(token))throw new IllegalArgumentException("Invalid pairing window");
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);nonce=Base64.getUrlEncoder().withoutPadding().encodeToString(random);code=Pairing.code(token,nonce);
        if(!certificate.isEmpty())client=new LanClient(endpoint,config,certificate);
    }
    private synchronized void ensureClient()throws Exception {
        if(client==null){String fetched=LanClient.certificate(endpoint,config);LanClient checked=new LanClient(endpoint,config,fetched);certificate=fetched;client=checked;}
    }
    /** Rescanning a known identity may repair its address; existing credentials still have to authenticate. */
    Phones.Phone reconnect()throws Exception {
        for(Phones.Phone known:store.read())if(known.pin.equals(endpoint.pin)) {
            certificate=known.certificate;client=new LanClient(endpoint,config,certificate);
            Phones.Phone moved=new Phones.Phone(endpoint.url,endpoint.pin,certificate,known.id,known.secret,known.name,known.profile,known.pending);
            if(moved.pending)try{return complete(moved,client,store);}catch(java.io.IOException expired){return null;}
            LanClient.Result info=client.call("GET","/v1/info",null,moved.credential());
            if(info.status==200){Phones.Phone learned=confirmed(moved,info.body);return store.update(moved,current->new Phones.Phone(learned.url,learned.pin,learned.certificate,current.id,current.secret,current.name,learned.profile,false));}
        }
        return null;
    }
    void request(String name)throws Exception {
        ensureClient();
        LanClient.Result result=client.call("POST","/v1/pair",new JSONObject().put("token",token).put("nonce",nonce).put("name",name),null);
        if(result.status!=200 || !Json.string(result.body,"state").equals("pending") || !Json.string(result.body,"code").equals(code))throw new java.io.IOException("Pair request not accepted");
    }
    Phones.Phone poll()throws Exception {
        ensureClient();
        LanClient.Result result=client.call("POST","/v1/pair-status",new JSONObject().put("token",token).put("nonce",nonce),null);
        if(result.status!=200){for(Phones.Phone known:store.read())if(known.pin.equals(endpoint.pin))return complete(known,client,store);throw new java.io.IOException("Pairing window closed");}
        String state=Json.string(result.body,"state");if(state.equals("pending"))return null;
        if(!state.equals("approved"))throw new java.io.IOException("Invalid pairing state");
        Phones.Phone granted=new Phones.Phone(endpoint.url,endpoint.pin,certificate,Json.string(result.body,"id"),Json.string(result.body,"secret"),"TV","",true);
        store.put(granted);return complete(granted,client,store);
    }
    /** Persist the grant before acknowledgement; verify it even when the acknowledgement reply is lost. */
    static Phones.Phone complete(Phones.Phone phone,LanClient client,Phones store)throws Exception {
        if(phone.pending) {
            try { client.call("POST","/v1/pair-complete",new JSONObject(),phone.credential()); }
            catch(java.io.IOException uncertain) { /* The authenticated read below resolves a lost acknowledgement. */ }
        }
        LanClient.Result info=client.call("GET","/v1/info",null,phone.credential());
        if(info.status!=200)throw new java.io.IOException("Pair completion not confirmed");
        Phones.Phone learned=confirmed(phone,info.body);
        return store.update(phone,current->current.pending?new Phones.Phone(learned.url,learned.pin,learned.certificate,current.id,current.secret,current.name.equals("TV")?learned.name:current.name,learned.profile,false):current);
    }
    private static Phones.Phone confirmed(Phones.Phone phone,JSONObject info)throws Exception {
        if(!(info.get("protocol") instanceof Number) || info.getInt("protocol")!=1 || info.getDouble("protocol")!=1)throw new java.io.IOException("Unsupported TV protocol");
        String profile=Json.string(info,"profile");if(!profile.equals("android")&&!profile.equals("lg"))throw new java.io.IOException("Unsupported TV platform");
        String name=Json.string(info,"name").trim();if(name.isEmpty())name="TV";if(name.length()>60)name=name.substring(0,60);
        return new Phones.Phone(phone.url,phone.pin,phone.certificate,phone.id,phone.secret,phone.name.equals("TV")?name:phone.name,profile,false);
    }
}

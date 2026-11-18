package tv.miqat.companion.lan;

import android.os.SystemClock;
import org.json.JSONObject;

public final class PairingRoutes implements TlsServer.Handler {
    public interface Settings {
        JSONObject info()throws Exception;
        JSONObject read()throws Exception;
        JSONObject apply(JSONObject expected,JSONObject changes)throws Exception;
        default JSONObject remote()throws Exception{return new JSONObject().put("commands",new org.json.JSONArray()).put("inputs",new org.json.JSONArray());}
        default JSONObject command(String id,String value)throws Exception{return Json.object("status","unavailable");}
    }
    private final Pairing pairing;
    private final Settings settings;
    public PairingRoutes(Pairing pairing,Settings settings){this.pairing=pairing;this.settings=settings;}
    @Override public TlsServer.Response handle(Http.Request request)throws Exception {
        JSONObject body=request.method.equals("POST")?Json.read(request.body):new JSONObject();
        long now=SystemClock.elapsedRealtime();JSONObject result;
        if(request.path.equals("/v1/pair") && request.method.equals("POST")) {
            Json.keys(body,"token","name","nonce");
            Pairing.Pending pending=pairing.request(Json.string(body,"token"),Json.string(body,"name"),Json.string(body,"nonce"),now);
            result=new JSONObject().put("state","pending").put("code",pending.code);
        } else if(request.path.equals("/v1/pair-status") && request.method.equals("POST")) {
            Json.keys(body,"token","nonce");Pairing.Grant grant=pairing.poll(Json.string(body,"token"),Json.string(body,"nonce"),now);
            result=Json.object("state",grant==null?"pending":"approved");
            if(grant!=null)result.put("id",grant.id).put("secret",grant.secret);
        } else {
            String authorization=request.headers.get("authorization");
            if(authorization==null || !authorization.matches("Bearer [A-Za-z0-9_-]{43}\\.[A-Za-z0-9_-]{43}"))throw new SecurityException();
            String id=authorization.substring(7,50),secret=authorization.substring(51);
            if(request.path.equals("/v1/pair-complete") && request.method.equals("POST")) {
                Json.keys(body);pairing.finish(id,secret,now);result=Json.object("state","paired");
            } else {
                if(!pairing.authenticate(id,secret))throw new SecurityException();
                if(request.path.equals("/v1/info") && request.method.equals("GET"))result=settings.info();
                else if(request.path.equals("/v1/remote") && request.method.equals("GET"))result=settings.remote();
                else if(request.path.equals("/v1/command") && request.method.equals("POST")){
                    Json.keys(body,"id","value");String value=Json.string(body,"value");if(value.length()>2048)throw new IllegalArgumentException();
                    result=settings.command(Json.string(body,"id"),value);
                }
                else if(request.path.equals("/v1/settings") && request.method.equals("GET"))result=settings.read();
                else if(request.path.equals("/v1/settings") && request.method.equals("POST")) {
                    Json.keys(body,"expected","changes");result=settings.apply(body.getJSONObject("expected"),body.getJSONObject("changes"));
                } else return new TlsServer.Response(404,"{\"error\":\"unknown\"}");
            }
        }
        return new TlsServer.Response(200,result.toString());
    }
}

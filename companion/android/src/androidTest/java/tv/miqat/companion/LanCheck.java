package tv.miqat.companion;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import tv.miqat.companion.lan.*;
import java.util.ArrayList;
import java.util.List;

/** Real loopback TLS on the owned emulator; fixture settings and direct approval are not TV acceptance. */
final class LanCheck {
    interface Task{void run()throws Exception;}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void rejects(Task task)throws Exception {
        try{task.run();}catch(Exception expected){return;}throw new AssertionError("Accepted invalid input");
    }
    static void run(Context context)throws Exception {
        LanConfig config=new LanConfig(context);
        for(String text:new String[]{"{'a':1}","{a:1}","{\"a\":1,\"a\":2}","{\"a\":NaN}","{\"a\":true,}","{} {}","[]"})rejects(()->Json.read(text));
        check(Json.string(Json.read("{\"name\":\"هاتف\"}"),"name").equals("هاتف"));
        rejects(()->Json.string(Json.read("{\"a\":1}"),"a"));rejects(()->Json.keys(Json.read("{\"x\":1}"),"allowed"));
        String zero="0".repeat(64);
        for(String url:new String[]{"http://192.168.1.2:45761","https://8.8.8.8:45761","https://192.168.01.2:45761","https://tv.local:45761",
            "https://192.168.1.2:80","https://user@192.168.1.2:45761","https://192.168.1.2:45761/","https://192.168.1.2:45761?q=a"})
            rejects(()->new Endpoint(url,zero,config.port,false));
        new Endpoint("https://192.168.1.2:45761",zero,config.port,false);
        rejects(()->new Endpoint("https://127.0.0.1:45761",zero,config.port,false));
        TlsIdentity identity=new TlsIdentity();check(identity.pin.equals(new TlsIdentity().pin));
        List<Pairing.Client> saved=new ArrayList<>();
        Pairing pairing=new Pairing(new Pairing.Store(){
            public List<Pairing.Client> read(){return new ArrayList<>(saved);}
            public void write(List<Pairing.Client> clients){saved.clear();saved.addAll(clients);}
        },config.pairWindowMillis,config.maxPhones);
        int[] reads={0},writes={0},commands={0};String[] setting={"100"};boolean[] uncertain={false},dropAck={false},dropCommand={false};
        org.json.JSONArray controls=new org.json.JSONArray().put(new JSONObject().put("id","display.size").put("kind","number").put("section","display")
            .put("title",new JSONObject().put("ar","الحجم").put("en","Size")).put("options",new org.json.JSONArray()).put("available",true).put("save","immediate").put("default","100"));
        PairingRoutes routes=new PairingRoutes(pairing,new PairingRoutes.Settings(){
            public JSONObject info()throws Exception{return new JSONObject().put("protocol",1).put("profile","android").put("name","Fixture TV").put("systemLanguage","en");}
            public JSONObject read()throws Exception{reads[0]++;return new JSONObject().put("values",Json.object("display.size",setting[0])).put("controls",controls).put("profile","android").put("systemLanguage","en");}
            public JSONObject remote()throws Exception{return new JSONObject().put("commands",new org.json.JSONArray(config.commands)).put("inputs",new org.json.JSONArray());}
            public JSONObject command(String id,String value)throws Exception{if(!config.commands.contains(id)||!value.isEmpty())throw new IllegalArgumentException();commands[0]++;return Json.object("status","accepted");}
            public JSONObject apply(JSONObject expected,JSONObject changes)throws Exception {
                if(!Json.string(expected,"display.size").equals(setting[0]))return Json.object("status","conflict");
                String next=Json.string(changes,"display.size");if(!next.equals("110"))throw new IllegalArgumentException();
                writes[0]++;setting[0]=next;
                if(uncertain[0])throw new java.io.IOException("Fixture lost reply after applying");
                return Json.object("status","saved");
            }
        });
        String url="https://127.0.0.1:"+config.port;
        LanClient client=new LanClient(new Endpoint(url,identity.pin,config.port,true),config,identity.encodedCertificate());
        try(TlsServer server=new TlsServer(identity,config,request->{
            TlsServer.Response response=routes.handle(request);
            if(dropAck[0]&&request.path.equals("/v1/pair-complete")){dropAck[0]=false;return null;} // Deliberately close after persisting the acknowledgement.
            if(dropCommand[0]&&request.path.equals("/v1/command")){dropCommand[0]=false;return null;}
            return response;
        },true);PhoneFixture fixture=new PhoneFixture(context)) {
            check(client.call("GET","/v1/info",null,null).status==403);
            check(client.call("POST","/v1/command",Json.object("id","device.home").put("value",""),null).status==403&&commands[0]==0);
            rejects(()->new LanClient(new Endpoint(url,zero,config.port,true),config,identity.encodedCertificate()));
            String token=pairing.open(SystemClock.elapsedRealtime()),nonce=pairing.token();
            JSONObject request=new JSONObject().put("token",token).put("name","هاتف تجريبي").put("nonce",nonce);
            LanClient.Result result=client.call("POST","/v1/pair",request,null);
            check(result.status==200 && result.body.getString("code").equals(Pairing.code(token,nonce)));
            JSONObject poll=new JSONObject().put("token",token).put("nonce",nonce);
            check(client.call("POST","/v1/pair-status",poll,null).body.getString("state").equals("pending"));
            pairing.approve(nonce,SystemClock.elapsedRealtime());
            result=client.call("POST","/v1/pair-status",poll,null);check(result.status==200);
            String id=result.body.getString("id"),secret=result.body.getString("secret"),credential=id+"."+secret;
            check(client.call("GET","/v1/settings",null,credential).status==403);
            check(client.call("POST","/v1/pair-complete",new JSONObject(),credential).status==200);
            check(client.call("GET","/v1/info",null,credential).body.getString("profile").equals("android"));
            check(reads[0]==0 && writes[0]==0);
            check(client.call("GET","/v1/remote",null,credential).body.getJSONArray("commands").length()==config.commands.size());
            check(client.call("POST","/v1/command",Json.object("id","device.home").put("value",""),credential).body.getString("status").equals("accepted"));
            check(client.call("POST","/v1/command",Json.object("id","pairing.approve").put("value",""),credential).status==400);
            check(commands[0]==1&&reads[0]==0&&writes[0]==0);
            check(client.call("GET","/v1/settings",null,credential).body.getJSONObject("values").getString("display.size").equals("100"));
            JSONObject edit=new JSONObject().put("expected",Json.object("display.size","100")).put("changes",Json.object("display.size","110"));
            check(client.call("POST","/v1/settings",edit,credential).body.getString("status").equals("saved"));
            check(client.call("POST","/v1/settings",edit,credential).body.getString("status").equals("conflict"));
            check(reads[0]==1 && writes[0]==1 && setting[0].equals("110"));
            check(client.call("POST","/v1/approve",new JSONObject(),credential).status==404);
            check(client.call("GET","/v1/settings",null,id+"."+pairing.token()).status==403);
            pairing.revoke(id);check(client.call("GET","/v1/settings",null,credential).status==403);
            check(client.call("POST","/v1/pair-status",poll,null).status==403);
            fixture.verify(identity,url,pairing);
            String window=pairing.open(SystemClock.elapsedRealtime());
            String qr=new JSONObject().put("type","miqat-tv-pair").put("version",1).put("url",url).put("pin",identity.pin)
                .put("certificate",identity.encodedCertificate()).put("token",window).toString();
            checkQrFrames(qr);
            JSONObject oldCompact=new JSONObject(qr).put("version",2);oldCompact.remove("certificate");
            check(ScanActivity.recognizesQr(oldCompact.toString()));new PairRequest(oldCompact.toString(),context,fixture.store);
            String compact=PairingQr.encode(url,identity.pin,window);
            check(compact.length()==112&&compact.length()<qr.length()/2);checkQrFrames(compact);checkStyledQr(context,compact);
            check(ScanActivity.recognizesQr(compact)&&ScanActivity.recognizesQr(qr));check(!ScanActivity.recognizesQr("https://example.invalid")&&!ScanActivity.recognizesQr("{}"));
            int beforeBootstrapReads=reads[0],beforeBootstrapWrites=writes[0];
            PairRequest compactRequest=new PairRequest(compact,context,fixture.store);
            check(compactRequest.client==null&&compactRequest.reconnect()==null);
            check(compactRequest.certificate.equals(identity.encodedCertificate()));
            check(reads[0]==beforeBootstrapReads&&writes[0]==beforeBootstrapWrites);
            rejects(()->LanClient.certificate(new Endpoint(url,pairing.hash("wrong-identity"),config.port,true),config));
            PairRequest phoneRequest=new PairRequest(qr,context,fixture.store);check(phoneRequest.reconnect()==null);
            phoneRequest.request("Phone fixture");check(phoneRequest.poll()==null);check(pairing.clients().isEmpty());
            pairing.approve(phoneRequest.nonce,SystemClock.elapsedRealtime());dropAck[0]=true;
            try{phoneRequest.poll();}catch(Exception lostReply){} // Recover the grant from encrypted storage, never resend settings.
            check(fixture.store.read().size()==1);Phones.Phone pending=fixture.store.read().get(0);
            check(pairing.authenticate(pending.id,pending.secret));
            LanTv adapter=new LanTv(context,fixture.store,pending,new Catalog(context));int writesBefore=writes[0];
            check(adapter.connect()&&!adapter.phone.pending&&writes[0]==writesBefore&&adapter.needsRefresh);
            setting[0]="100";adapter.read();check(!adapter.needsRefresh);
            adapter.readRemote();check(adapter.command("device.home","").getString("status").equals("accepted"));
            dropCommand[0]=true;check(adapter.command("device.home","").getString("status").equals("uncertain"));
            int executed=commands[0];check(adapter.connect());rejects(()->adapter.command("device.home",""));
            adapter.readRemote();check(commands[0]==executed&&!adapter.remoteUncertain);adapter.read();uncertain[0]=true;
            tv.miqat.companion.core.SaveResult outcome=adapter.apply(java.util.Collections.singletonMap("display.size","100"),java.util.Collections.singletonMap("display.size","110"));
            check(outcome.getStatus().equals("uncertain")&&adapter.needsRefresh&&setting[0].equals("110"));int written=writes[0];
            rejects(()->adapter.apply(java.util.Collections.singletonMap("display.size","100"),java.util.Collections.singletonMap("display.size","110")));
            check(adapter.connect()&&adapter.needsRefresh&&writes[0]==written);
            check(adapter.read().getValues().get("display.size").equals("110")&&!adapter.needsRefresh&&writes[0]==written);
            check(phoneRequest.reconnect()!=null&&writes[0]==written);
            pairing.revoke(pending.id);check(!adapter.connect());
        }
    }
    static void checkStyledQr(Context context,String actualQr)throws Exception {
        List<String> samples=new ArrayList<>();samples.add(actualQr);
        for(int i=0;i<8;i++) {
            byte[] pin=java.security.MessageDigest.getInstance("SHA-256").digest(("pin"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] token=java.security.MessageDigest.getInstance("SHA-256").digest(("token"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();for(byte b:pin)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            samples.add(PairingQr.encode("https://192.168.50."+(i+1)+":45761",hex.toString(),java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(token)));
        }
        JSONObject themes=new Catalog(context).data.getJSONObject("themes");
        for(String qr:samples)for(String theme:new String[]{"mihrab","mosaic","brass","rose","cinema"})for(int size:new int[]{220,280,400}) {
            JSONObject colors=themes.getJSONObject(theme).getJSONObject("colors");
            int[] palette=new int[5];String[] keys={"lapis-900","lapis-700","gold-400","plaster-100"};
            for(int i=0;i<4;i++)palette[i]=android.graphics.Color.parseColor(colors.getString(keys[i]));palette[4]=android.graphics.Color.parseColor(themes.getJSONObject("mihrab").getJSONObject("colors").getString("plaster-100"));
            android.graphics.Bitmap image=tv.miqat.companion.qr.QrImage.create(context,qr,size,palette);
            if(qr.equals(actualQr)&&size==400)try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),"qr-"+theme+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
            int[] pixels=new int[size*size];image.getPixels(pixels,0,size,0,0,size,size);image.recycle();
            byte[] frame=new byte[size*size*3/2];java.util.Arrays.fill(frame,(byte)128);
            for(int i=0;i<pixels.length;i++){int c=pixels[i];frame[i]=(byte)((android.graphics.Color.red(c)*299+android.graphics.Color.green(c)*587+android.graphics.Color.blue(c)*114)/1000);}
            com.google.zxing.MultiFormatReader decoder=ScanActivity.qrReader();
            for(int turn=0;turn<4;turn++) {
                if(!qr.equals(ScanActivity.decode(decoder,frame,size,size)))throw new AssertionError("Styled QR "+theme+"/"+size+"/"+turn+" sample "+samples.indexOf(qr));byte[] rotated=frame.clone();
                for(int y=0;y<size;y++)for(int x=0;x<size;x++)rotated[x*size+size-1-y]=frame[y*size+x];frame=rotated;
            }
        }
    }
    static void checkQrFrames(String qr)throws Exception {
        int size=512;com.google.zxing.common.BitMatrix matrix=new com.google.zxing.MultiFormatWriter().encode(qr,com.google.zxing.BarcodeFormat.QR_CODE,size,size);
        byte[] frame=new byte[size*size*3/2];java.util.Arrays.fill(frame,(byte)128);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++)frame[y*size+x]=(byte)(matrix.get(x,y)?16:235);
        com.google.zxing.MultiFormatReader decoder=ScanActivity.qrReader();
        for(int turn=0;turn<4;turn++) {
            check(qr.equals(ScanActivity.decode(decoder,frame,size,size)));byte[] rotated=frame.clone();
            for(int y=0;y<size;y++)for(int x=0;x<size;x++)rotated[x*size+size-1-y]=frame[y*size+x];frame=rotated;
        }
        check(ScanActivity.decode(decoder,new byte[2],size,size)==null);
    }
}

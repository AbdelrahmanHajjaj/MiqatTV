package tv.miqat.home;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.json.JSONObject;
import tv.miqat.companion.lan.*;
import java.util.*;
import java.util.concurrent.Callable;

/** Loopback TLS and real shell preferences on an explicitly selected owned emulator, never a physical TV. */
final class CompanionDeviceCheck {
    static void phase(Instrumentation test,String text){Bundle b=new Bundle();b.putString("stream",text+"\n");test.sendStatus(0,b);}
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void until(Callable<Boolean> condition)throws Exception {
        long end=SystemClock.elapsedRealtime()+12000;while(!condition.call()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(50);
        check(condition.call(),"Timed out waiting for owned emulator state");
    }
    static PairingActivity launch(Instrumentation test)throws Exception {
        PairingActivity activity=(PairingActivity)test.startActivitySync(new Intent(test.getTargetContext(),PairingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CompanionApplication app=(CompanionApplication)activity.getApplication();until(()->activity.hasWindowFocus()&&app.pairing!=null);
        test.runOnMainSync(activity::build);return activity;
    }
    static void click(Instrumentation test,PairingActivity activity,String tag) {
        test.runOnMainSync(()->{activity.onTick();View control=activity.root.findViewWithTag(tag);check(control!=null&&control.isFocusable(),"Missing real TV control: "+tag);control.performClick();});
    }
    static View phone(View view,String id) {
        if(id.equals(view.getTag(R.id.pairing_phone_id)))return view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=phone(group.getChildAt(i),id);if(found!=null)return found;}}
        return null;
    }
    static void revoke(Instrumentation test,PairingActivity activity,String id) {
        test.runOnMainSync(()->{activity.onTick();View control=phone(activity.root,id);check(control!=null,"Fixture phone revoke binding");control.performClick();});
    }
    static Set<String> bindings(Instrumentation test)throws Exception {
        Set<String> bound=new LinkedHashSet<>();PairingActivity activity=launch(test);CompanionApplication app=(CompanionApplication)activity.getApplication();
        SharedPreferences prefs=Prefs.of(activity);boolean hadScale=prefs.contains("ui_scale");int savedScale=Prefs.uiScale(activity);
        String clientId=null;
        try {
            click(test,activity,"pairing.open");bound.add("pairing.open");
            try{until(()->{if(app.failed)throw new AssertionError("TV secure connection initialization",app.failure);return !app.token.isEmpty();});}
            catch(AssertionError error){throw new AssertionError("Pairing state: foreground="+app.foreground+", listening="+app.listening()+", focused="+activity.hasWindowFocus(),error);}
            test.runOnMainSync(activity::build);
            for(int scale:new int[]{80,100,150}) {
                check(Prefs.of(activity).edit().putInt("ui_scale",scale).commit(),"Pairing size fixture");
                test.runOnMainSync(()->{activity.loadSettings(null);activity.build();});test.waitForIdleSync();SystemClock.sleep(100);
                test.runOnMainSync(()->{
                    View image=activity.root.findViewWithTag("pairing.qr");android.graphics.Rect visible=new android.graphics.Rect();
                    check(image!=null&&image.getGlobalVisibleRect(visible),"QR is visible at "+scale);
                    int[] at=new int[2];image.getLocationOnScreen(at);float sx=1,sy=1;
                    for(View view=image;view!=null;view=view.getParent() instanceof View?(View)view.getParent():null){sx*=view.getScaleX();sy*=view.getScaleY();}
                    check(Math.abs(visible.width()-image.getWidth()*sx)<=2&&Math.abs(visible.height()-image.getHeight()*sy)<=2,"Full QR/quiet zone remains visible at "+scale);
                    android.widget.ScrollView scroll=null;for(int i=0;i<activity.root.getChildCount();i++)if(activity.root.getChildAt(i) instanceof android.widget.ScrollView)scroll=(android.widget.ScrollView)activity.root.getChildAt(i);
                    android.graphics.Rect controls=new android.graphics.Rect();check(scroll!=null&&scroll.getGlobalVisibleRect(controls)&&!android.graphics.Rect.intersects(visible,controls),"QR does not overlap pairing controls at "+scale);
                });
            }
            check(Prefs.of(activity).edit().putInt("ui_scale",100).commit(),"Restore pairing size");test.runOnMainSync(()->{activity.loadSettings(null);activity.build();});test.waitForIdleSync();
            Bitmap[] bitmap={null};test.runOnMainSync(()->{ImageView image=activity.root.findViewWithTag("pairing.qr");check(image!=null,"Rendered QR");bitmap[0]=((BitmapDrawable)image.getDrawable()).getBitmap();});
            int[] pixels=new int[bitmap[0].getWidth()*bitmap[0].getHeight()];bitmap[0].getPixels(pixels,0,bitmap[0].getWidth(),0,0,bitmap[0].getWidth(),bitmap[0].getHeight());
            String rawQr=new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(bitmap[0].getWidth(),bitmap[0].getHeight(),pixels)))).getText();
            PairingQr.Data decoded=PairingQr.decode(rawQr);
            JSONObject qr=new JSONObject().put("version",3).put("url",decoded.url).put("pin",decoded.pin).put("token",decoded.token);
            check(qr.getString("pin").equals(app.identity.pin)&&qr.getString("token").equals(app.token),"QR carries this TV's live identity/window");
            new Endpoint(qr.getString("url"),qr.getString("pin"),app.config.port,false);
            check(qr.getInt("version")==3&&!qr.has("certificate"),"Compact QR has only identity fingerprint and window");
            Endpoint bootstrap=new Endpoint("https://127.0.0.1:"+app.config.port,app.identity.pin,app.config.port,true);
            new LanClient(bootstrap,app.config,LanClient.certificate(bootstrap,app.config));
            click(test,activity,"pairing.cancel");bound.add("pairing.cancel");until(()->!app.pairing.isOpen(SystemClock.elapsedRealtime()));
            test.runOnMainSync(()->{app.failed=true;activity.build();});
            click(test,activity,"pairing.open");until(()->!app.token.isEmpty());String token=app.token,nonce=app.pairing.token();
            check(!app.failed,"Failed connection exposes an explicit retry");
            app.pairing.request(token,"Fixture phone",nonce,SystemClock.elapsedRealtime());
            click(test,activity,"pairing.reject");bound.add("pairing.reject");until(()->!app.pairing.isOpen(SystemClock.elapsedRealtime()));
            click(test,activity,"pairing.open");until(()->!app.token.isEmpty());String nextToken=app.token;
            app.pairing.request(nextToken,"Fixture phone",nonce,SystemClock.elapsedRealtime());
            click(test,activity,"pairing.approve");bound.add("pairing.approve");until(()->app.pairing.poll(nextToken,nonce,SystemClock.elapsedRealtime())!=null);
            test.runOnMainSync(()->{activity.onTick();check(activity.root.findViewWithTag("pairing.approve")==null&&activity.root.findViewWithTag("pairing.cancel")!=null,"Approval shows waiting/completion state");});
            Pairing.Grant grant=app.pairing.poll(nextToken,nonce,SystemClock.elapsedRealtime());clientId=grant.id;app.pairing.finish(grant.id,grant.secret,SystemClock.elapsedRealtime());
            revoke(test,activity,clientId);bound.add("pairing.revoke");String revoked=clientId;until(()->app.pairing.clients().stream().noneMatch(c->c.id.equals(revoked)));clientId=null;
            check(!app.pairing.authenticate(grant.id,grant.secret),"TV revocation applied");
            return bound;
        } finally {
            if(clientId!=null){String id=clientId;for(Pairing.Client client:app.pairing.clients())if(client.id.equals(id))app.pairing.revoke(id);}
            SharedPreferences.Editor restore=prefs.edit();if(hadScale)restore.putInt("ui_scale",savedScale);else restore.remove("ui_scale");
            check(restore.commit(),"Restore original pairing size");
            test.runOnMainSync(activity::finish);test.waitForIdleSync();
        }
    }
    static void run(Instrumentation test) {
        Bundle result=new Bundle();int code=0;SharedPreferences prefs=Prefs.of(test.getTargetContext());Map<String,?> saved=prefs.getAll();
        PairingActivity activity=null;String clientId=null;CompanionApplication app=null;
        try {
            check(android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.FINGERPRINT.contains("generic"),"This check is restricted to an owned emulator");
            phase(test,"Pairing bindings");
            for(String language:new String[]{"en","ar"}) {
                check(prefs.edit().putString("locale",language).commit(),"Language prerequisite");
                Set<String> bound=bindings(test);check(bound.size()==5,"All pairing actions have real bindings");
            }
            check(prefs.edit().putString("theme","mihrab").putInt("home_side",0).putInt("ui_scale",100).putBoolean("quick_labels",false).putInt("prayer_offset_2",3).commit(),"Settings prerequisites");
            phase(test,"Actual settings host");
            activity=launch(test);app=(CompanionApplication)activity.getApplication();CompanionApplication host=app;
            check(app.pairing.clients().isEmpty(),"No existing emulator trust may be replaced");app.open();until(()->!host.token.isEmpty());
            LanClient client=new LanClient(new Endpoint("https://127.0.0.1:"+app.config.port,app.identity.pin,app.config.port,true),app.config,app.identity.encodedCertificate());
            java.lang.reflect.Field factory=LanClient.class.getDeclaredField("factory");factory.setAccessible(true);
            try(javax.net.ssl.SSLSocket socket=(javax.net.ssl.SSLSocket)((javax.net.ssl.SSLSocketFactory)factory.get(client)).createSocket("127.0.0.1",app.config.port)) {
                socket.setSoTimeout(app.config.timeoutMillis);socket.setEnabledProtocols(new String[]{"TLSv1.2"});socket.startHandshake();
                check(socket.getSession().getProtocol().equals("TLSv1.2"),"TLS 1.2 works with the pinned standard trust anchor");
            }
            String token=app.token,nonce=app.pairing.token();
            Map<String,?> beforePair=prefs.getAll();
            check(client.call("POST","/v1/pair",new JSONObject().put("token",token).put("nonce",nonce).put("name","Round-trip fixture"),null).status==200,"Pair request");
            click(test,activity,"pairing.approve");until(()->host.pairing.poll(token,nonce,SystemClock.elapsedRealtime())!=null);
            JSONObject grant=client.call("POST","/v1/pair-status",new JSONObject().put("token",token).put("nonce",nonce),null).body;
            clientId=grant.getString("id");String credential=clientId+"."+grant.getString("secret");
            check(client.call("POST","/v1/pair-complete",new JSONObject(),credential).status==200,"Pair completion");
            check(client.call("GET","/v1/info",null,credential).status==200,"Authenticated reconnect");
            check(prefs.getAll().equals(beforePair),"Pair/connect never writes settings");
            check(client.call("GET","/v1/remote",null,null).status==403,"Remote capabilities require authentication");
            check(client.call("POST","/v1/command",Json.object("id","device.home").put("value",""),null).status==403,"Commands require authentication");
            phase(test,"Authenticated Remote capabilities");
            JSONObject remote=client.call("GET","/v1/remote",null,credential).body;
            check(remote.getJSONArray("commands").toString().contains("device.home")&&remote.has("inputs"),"Real shell command/input discovery");
            check(client.call("POST","/v1/command",Json.object("id","pairing.approve").put("value",""),credential).status==400,"Remote cannot approve pairing");
            check(client.call("POST","/v1/command",Json.object("id","device.inputs").put("value","not-a-discovered-input"),credential).body.getString("status").equals("unavailable"),"Only discovered inputs may launch");
            phase(test,"Home command");
            check(client.call("POST","/v1/command",Json.object("id","device.home").put("value",""),credential).body.getString("status").equals("accepted"),"Authenticated Home command");
            until(()->host.activity() instanceof MainActivity);
            check(prefs.getAll().equals(beforePair),"Remote Home never changes settings");
            android.app.Activity home=host.activity();test.runOnMainSync(home::finish);
            until(()->host.activity() instanceof PairingActivity&&host.activity().hasWindowFocus());
            activity=(PairingActivity)host.activity();until(host::listening);
            check(client.call("POST","/v1/command",Json.object("id","device.inputs").put("value",""),credential).body.getString("status").equals("accepted"),"Authenticated input chooser");
            until(()->host.activity() instanceof InputsActivity);
            android.app.Activity chooser=host.activity();test.runOnMainSync(chooser::finish);until(()->host.activity() instanceof PairingActivity);
            check(client.call("POST","/v1/command",Json.object("id","tests.cancel").put("value",""),credential).body.getString("status").equals("accepted"),"Preview Stop is safe while idle");
            phase(test,"Preview command");
            check(client.call("POST","/v1/command",Json.object("id","tests.adhan-short").put("value",""),credential).body.getString("status").equals("accepted"),"Companion preview accepted");
            until(()->host.activity() instanceof NowActivity&&((NowActivity)host.activity()).companionPreview());
            check(client.call("POST","/v1/command",Json.object("id","device.home").put("value",""),credential).body.getString("status").equals("busy"),"Active adhan owns navigation");
            check(client.call("POST","/v1/command",Json.object("id","tests.cancel").put("value",""),credential).body.getString("status").equals("accepted"),"Explicit preview Stop accepted");
            until(()->host.activity() instanceof PairingActivity);


            phase(test,"Settings/lifecycle/revocation");
            JSONObject snapshot=client.call("GET","/v1/settings",null,credential).body,base=snapshot.getJSONObject("values");
            check(base.getString("display.size").equals("100"),"Actual settings read");
            check(base.getString("appearance.mihrab-opacity").equals("85"),"New opacity default is offered");
            check(client.call("POST","/v1/settings",new JSONObject().put("expected",base).put("changes",Json.object("appearance.mihrab-opacity","70")),credential).body.getString("status").equals("saved")&&Prefs.of(app).getInt("mihrab_opacity",0)==70,"Opacity persists through actual companion adapter");
            base=client.call("GET","/v1/settings",null,credential).body.getJSONObject("values");
            check(client.call("POST","/v1/settings",new JSONObject().put("expected",base).put("changes",Json.object("appearance.mihrab-opacity","86")),credential).status==400,"Off-step opacity rejected");

            JSONObject edit=new JSONObject().put("expected",base).put("changes",Json.object("display.size","110"));
            check(client.call("POST","/v1/settings",edit,credential).body.getString("status").equals("saved")&&Prefs.uiScale(app)==110,"Actual settings apply");
            check(client.call("POST","/v1/settings",edit,credential).body.getString("status").equals("conflict")&&Prefs.uiScale(app)==110,"Stale edit rejected");
            base=client.call("GET","/v1/settings",null,credential).body.getJSONObject("values");
            check(prefs.edit().putBoolean("quick_labels",true).commit(),"Concurrent unrelated TV edit");
            check(client.call("POST","/v1/settings",new JSONObject().put("expected",base).put("changes",Json.object("display.size","120")),credential).body.getString("status").equals("saved"),"Independent patch saves");
            check(Prefs.quickLabels(app)&&Prefs.prayerOffset(app,2)==3,"Unrelated TV settings preserved");
            base=client.call("GET","/v1/settings",null,credential).body.getJSONObject("values");
            check(client.call("POST","/v1/settings",new JSONObject().put("expected",base).put("changes",Json.object("prayer.location","makkah")),credential).body.getString("status").equals("saved"),"City apply");
            check(Prefs.city(app).id.equals("makkah")&&Prefs.method(app)==PrayerMethod.UMM_AL_QURA&&Prefs.prayerOffset(app,2)==3,"TV owns dependent defaults");
            base=client.call("GET","/v1/settings",null,credential).body.getJSONObject("values");prefs.edit().putInt("home_side",1).commit();
            check(client.call("POST","/v1/settings",new JSONObject().put("expected",base).put("changes",Json.object("appearance.background","solid")),credential).body.getString("status").equals("conflict"),"Appearance group conflict");
            check(client.call("POST","/v1/settings",new JSONObject().put("expected",base).put("changes",Json.object("display.size","111")),credential).status==400&&Prefs.uiScale(app)==120,"Invalid numeric boundary cannot change storage");
            PairingActivity closing=activity;test.runOnMainSync(closing::finish);activity=null;until(()->!host.foreground&&!host.listening());
            try{client.call("GET","/v1/info",null,credential);throw new AssertionError("Listener survived app backgrounding");}catch(java.io.IOException expected){}
            activity=launch(test);until(host::listening);
            check(client.call("GET","/v1/info",null,credential).status==200,"Approved phone reconnects after foreground return");
            revoke(test,activity,clientId);until(()->host.pairing.clients().isEmpty());clientId=null;
            check(client.call("GET","/v1/settings",null,credential).status==403,"Revocation protects real settings");
            result.putString("stream","PASS: bilingual TV pairing controls and QR decode; real pinned-TLS settings read/apply, defaults, conflicts, preservation, foreground-only listener and revocation (owned phone emulator)\n");code=-1;
        } catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));}
        finally {
            if(app!=null&&clientId!=null)try{app.pairing.revoke(clientId);}catch(Exception ignored){}
            if(activity!=null){PairingActivity close=activity;test.runOnMainSync(close::finish);}
            SharedPreferences.Editor restore=prefs.edit().clear();
            for(Map.Entry<String,?> entry:saved.entrySet()){Object v=entry.getValue();String k=entry.getKey();if(v instanceof String)restore.putString(k,(String)v);else if(v instanceof Integer)restore.putInt(k,(Integer)v);else if(v instanceof Boolean)restore.putBoolean(k,(Boolean)v);else if(v instanceof Long)restore.putLong(k,(Long)v);else if(v instanceof Float)restore.putFloat(k,(Float)v);}
            restore.commit();
        }
        test.finish(code,result);
    }
    /** Bounded second-emulator host; fixture approval is injected through the TV UI, never a LAN route. */
    static void host(Instrumentation test) {
        Bundle result=new Bundle();int code=0;PairingActivity activity=null;CompanionApplication app=null;
        SharedPreferences prefs=Prefs.of(test.getTargetContext());Map<String,?> saved=prefs.getAll();
        java.io.File qr=new java.io.File(test.getTargetContext().getFilesDir(),"companion-host-qr.json"),stop=new java.io.File(test.getTargetContext().getFilesDir(),"companion-host-stop");
        try {
            check(android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.FINGERPRINT.contains("generic"),"Emulator host only");
            check(!qr.exists()&&!stop.exists(),"No stale host fixture may be replaced");
            check(prefs.edit().putString("locale","en").putInt("ui_scale",100).putInt("home_side",0).putString("theme","mihrab").commit(),"Host prerequisites");
            activity=launch(test);app=(CompanionApplication)activity.getApplication();CompanionApplication ready=app;
            check(app.pairing.clients().isEmpty(),"Host requires fresh emulator trust");app.open();until(()->!ready.token.isEmpty());
            JSONObject payload=new JSONObject().put("type","miqat-tv-pair").put("version",app.config.qrVersion).put("url",app.endpoint()).put("pin",app.identity.pin)
                .put("token",app.token);
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(qr)){out.write(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}
            Bundle status=new Bundle();status.putString("stream","READY: owned TV emulator host; fixture-only approval; 180-second maximum\n");test.sendStatus(0,status);
            long deadline=SystemClock.elapsedRealtime()+180000;
            while(SystemClock.elapsedRealtime()<deadline&&!stop.exists()) {
                Pairing.Pending pending=app.pairing.pending(SystemClock.elapsedRealtime());
                if(pending!=null&&pending.name.equals("Companion integration fixture")&&!app.pairing.approved(SystemClock.elapsedRealtime()))click(test,activity,"pairing.approve");
                SystemClock.sleep(150);
            }
            check(stop.exists(),"Phone bridge check did not finish within the bounded host window");
            result.putString("stream","PASS: owned-emulator TV host completed; fixture approval was injected, not physical remote acceptance\n");code=-1;
        }catch(Throwable failure){result.putString("stream",android.util.Log.getStackTraceString(failure));}
        finally {
            if(app!=null&&app.pairing!=null)try{for(Pairing.Client client:app.pairing.clients())if(client.name.equals("Companion integration fixture"))app.pairing.revoke(client.id);app.cancel();}catch(Exception cleanup){code=0;result.putString("stream","Host credential cleanup failed\n");}
            if(activity!=null){PairingActivity close=activity;test.runOnMainSync(close::finish);}
            qr.delete();stop.delete();SharedPreferences.Editor restore=prefs.edit().clear();
            for(Map.Entry<String,?> entry:saved.entrySet()){Object v=entry.getValue();String k=entry.getKey();if(v instanceof String)restore.putString(k,(String)v);else if(v instanceof Integer)restore.putInt(k,(Integer)v);else if(v instanceof Boolean)restore.putBoolean(k,(Boolean)v);else if(v instanceof Long)restore.putLong(k,(Long)v);else if(v instanceof Float)restore.putFloat(k,(Float)v);}
            if(!restore.commit()){code=0;result.putString("stream","Host preferences could not be restored\n");}
        }
        test.finish(code,result);
    }
}

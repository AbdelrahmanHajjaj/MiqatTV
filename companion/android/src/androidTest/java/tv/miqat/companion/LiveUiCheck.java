package tv.miqat.companion;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import org.json.JSONObject;
import tv.miqat.companion.lan.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.Callable;

/** Owned phone emulator against an Android app or native LG fixture. No physical camera/remote/TV evidence. */
final class LiveUiCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void until(Callable<Boolean> condition)throws Exception {
        long deadline=SystemClock.elapsedRealtime()+25000;while(!condition.call()&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(50);
        check(condition.call(),"Phone integration timed out");
    }
    static void untilUi(Instrumentation test,Callable<Boolean> condition)throws Exception {
        until(()->{boolean[] value={false};Throwable[] failure={null};test.runOnMainSync(()->{try{value[0]=condition.call();}catch(Throwable error){failure[0]=error;}});if(failure[0]!=null)throw new AssertionError(failure[0]);return value[0];});
    }
    static void key(Instrumentation test, int code) {
        long down = SystemClock.uptimeMillis();
        android.app.UiAutomation input = test.getUiAutomation();
        // UiAutomation waits for window surface operations before dispatching input.
        check(input.injectInputEvent(new android.view.KeyEvent(down, down,
                android.view.KeyEvent.ACTION_DOWN, code, 0), true), "Key down was not injected");
        check(input.injectInputEvent(new android.view.KeyEvent(down, SystemClock.uptimeMillis(),
                android.view.KeyEvent.ACTION_UP, code, 0), true), "Key up was not injected");
    }
    static MainActivity launch(Instrumentation test){return (MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    static void click(Instrumentation test,MainActivity a,String tag){test.runOnMainSync(()->{View control=a.getWindow().getDecorView().findViewWithTag(tag);check(control!=null&&control.isEnabled(),"Missing enabled phone control: "+tag);control.performClick();});}
    static void run(Instrumentation test,String expectedProfile)throws Exception {
        check(android.os.Build.HARDWARE.contains("ranchu")||android.os.Build.FINGERPRINT.contains("generic"),"Owned emulator only");
        android.content.Context context=test.getTargetContext();Phones store=new Phones(context);check(store.read().isEmpty(),"Fresh companion emulator trust required");
        File qrFile=new File(context.getFilesDir(),"companion-live-qr.json"),ready=new File(context.getFilesDir(),"companion-ui-offline-ready"),go=new File(context.getFilesDir(),"companion-ui-offline-go");
        check(!ready.exists()&&!go.exists(),"No stale bridge phase may be reused");
        String qr=new String(Files.readAllBytes(qrFile.toPath()),StandardCharsets.UTF_8);String pin=qr.startsWith("MIQ3:")?PairingQr.decode(qr).pin:Json.string(Json.read(qr),"pin");
        MainActivity[] current={null};String previous=context.getSharedPreferences("companion",0).getString("language","system");
        String previousName=context.getSharedPreferences("companion",0).getString("phone-name",null);
        try {
            context.getSharedPreferences("companion",0).edit().putString("language","en").putString("phone-name","Companion integration fixture").commit();
            MainActivity a=launch(test);current[0]=a;test.runOnMainSync(()->a.pairingUi.beginPairing(qr));
            untilUi(test,()->a.real()!=null&&!a.networkBusy&&a.page.equals("overview"));
            check(a.settingsUi.snapshot==null&&a.real().needsRefresh,"Pairing must not read/upload settings");
            Phones.Phone phone=store.read().get(0);check(!phone.pending&&phone.pin.equals(pin),"Encrypted confirmed grant");
            check(phone.profile.equals(expectedProfile),"Actual paired platform matches the integration target");
            LanConfig config=new LanConfig(context);LanClient verifier=new LanClient(new Endpoint(phone.url,phone.pin,config.port,true),config,phone.certificate);
            JSONObject initial=verifier.call("GET","/v1/settings",null,phone.credential()).body.getJSONObject("values");
            check(initial.getString("display.size").equals("100"),"Actual TV host prerequisites");
            test.runOnMainSync(()->{a.settingsUi.readSettings();a.page="settings";a.render();});untilUi(test,()->!a.networkBusy&&a.settingsUi.editable());
            check(a.backend().getSpecs().size()==a.settingsUi.snapshot.getValues().size(),"Full actual settings inventory");
            if(expectedProfile.equals("android"))check(!a.settingsUi.spec("favourites.order").getOptions().contains("quran"),"Actual app IDs replace demo apps");
            else {
                check(!a.catalog.control(a.profile(),"device.remote").getBoolean("available")&&!a.settingsUi.snapshot.getValues().containsKey("favourites.order"),"LG actual capabilities exclude unsupported Android editors");
                check(a.settingsUi.snapshot.getValues().get("reminders.volume").equals("25"),"LG reads the native fixture value instead of demo defaults");
                test.runOnMainSync(()->a.settingsUi.openSection("reminders"));click(test,a,"reminders.volume.plus");click(test,a,"reminders.volume.save");
                untilUi(test,()->!a.networkBusy&&a.settingsUi.snapshot.getValues().get("reminders.volume").equals("30"));
                check(verifier.call("GET","/v1/settings",null,phone.credential()).body.getJSONObject("values").getString("reminders.volume").equals("30"),"LG volume uses the shared 5% editor and real adapter");
            }
            test.runOnMainSync(()->a.settingsUi.openSection("display"));click(test,a,"display.size.plus");
            check(verifier.call("GET","/v1/settings",null,phone.credential()).body.getJSONObject("values").getString("display.size").equals("100"),"Inline number remains staged");
            click(test,a,"display.size.save");untilUi(test,()->!a.networkBusy&&a.settingsUi.snapshot.getValues().get("display.size").equals("110"));
            check(verifier.call("GET","/v1/settings",null,phone.credential()).body.getJSONObject("values").getString("display.size").equals("110"),"Phone saved to real TV preference adapter");
            test.runOnMainSync(()->a.settingsUi.openSection("appearance"));click(test,a,"appearance.theme.next");
            check(verifier.call("GET","/v1/settings",null,phone.credential()).body.getJSONObject("values").getString("appearance.theme").equals("mihrab"),"Phone preview does not write");
            click(test,a,"appearance.cancel");test.runOnMainSync(()->a.settingsUi.openSection("appearance"));click(test,a,"appearance.theme.next");click(test,a,"appearance.apply");
            untilUi(test,()->!a.networkBusy&&a.settingsUi.appearance!=null&&a.settingsUi.appearance.getChanges().isEmpty()&&a.settingsUi.snapshot.getValues().get("appearance.theme").equals("aurora"));check(verifier.call("GET","/v1/settings",null,phone.credential()).body.getJSONObject("values").getString("appearance.theme").equals("aurora"),"Appearance Apply confirmed by actual TV");
            test.runOnMainSync(()->a.settingsUi.openSection("appearance"));click(test,a,"appearance.theme.next");String delivered=a.settingsUi.appearance.getValues().get("appearance.theme");
            JSONObject expected=verifier.call("GET","/v1/settings",null,phone.credential()).body.getJSONObject("values");
            check(verifier.call("POST","/v1/settings",new JSONObject().put("expected",expected).put("changes",Json.object("appearance.theme",delivered)),phone.credential()).body.getString("status").equals("saved"),"Fixture models a delivered save whose UI confirmation was lost");
            test.runOnMainSync(a.settingsUi::readSettings);untilUi(test,()->!a.networkBusy&&a.settingsUi.appearance.getChanges().isEmpty());
            check(a.settingsUi.snapshot.getValues().get("appearance.theme").equals(delivered),"Refresh clears a draft already confirmed in actual TV values");
            click(test,a,"appearance.theme.next");String draft=a.settingsUi.appearance.getValues().get("appearance.theme");
            Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);
            try {
                test.runOnMainSync(a::recreate);until(()->monitor.getLastActivity()!=null);MainActivity recreated=(MainActivity)monitor.getLastActivity();current[0]=recreated;
                untilUi(test,()->recreated.body!=null&&recreated.settingsUi.appearance!=null);
                check(recreated.settingsUi.appearance.getValues().get("appearance.theme").equals(draft)&&!recreated.settingsUi.editable(),"Recreation preserves draft and requires refreshed settings");
                click(test,recreated,"appearance.cancel");
            }finally{test.removeMonitor(monitor);}
            Map<String,String> before=MainActivity.decode(verifier.call("GET","/v1/settings",null,phone.credential()).body.getJSONObject("values").toString());
            check(current[0].real().connect(),"Known phone reconnects");
            check(before.equals(MainActivity.decode(verifier.call("GET","/v1/settings",null,phone.credential()).body.getJSONObject("values").toString())),"Reconnect never uploads cached settings/draft");
            check(ready.createNewFile(),"Offline phase marker");until(go::exists);MainActivity live=current[0];
            test.runOnMainSync(()->{live.section="display";live.settingsUi.readSettings();});untilUi(test,()->!live.networkBusy);
            check(!live.settingsUi.editable()&&live.settingsUi.snapshot.getValues().get("display.size").equals("110"),"Actual transport loss retains stale settings");
            test.runOnMainSync(()->check(!live.body.findViewWithTag("display.size.plus").isEnabled(),"Stale controls are read-only"));
            test.runOnMainSync(live::finish);context.getSharedPreferences("companion",0).edit().putString("language","ar").commit();
            MainActivity arabic=launch(test);current[0]=arabic;test.runOnMainSync(()->{arabic.selected=2;arabic.page="settings";arabic.section="display";arabic.settingsUi.readSettings();});untilUi(test,()->!arabic.networkBusy);
            check(arabic.ar&&!arabic.settingsUi.editable()&&arabic.settingsUi.snapshot.getValues().get("display.size").equals("110"),"Arabic restart retains actual TV cache without upload");
            check(arabic.body.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL,"Real TV phone UI keeps RTL");
        }finally {
            if(current[0]!=null){MainActivity close=current[0];test.runOnMainSync(close::finish);test.waitForIdleSync();check(close.io.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS),"Phone fixture IO stopped before cleanup");}
            java.util.List<Phones.Phone> records=store.read();if(records.size()==1&&records.get(0).pin.equals(pin))context.deleteFile("paired-tvs.bin");
            android.content.SharedPreferences.Editor restore=context.getSharedPreferences("companion",0).edit().putString("language",previous).remove("cache-tv-"+pin);
            if(previousName==null)restore.remove("phone-name");else restore.putString("phone-name",previousName);restore.apply();
            qrFile.delete();ready.delete();go.delete();
        }
    }
}

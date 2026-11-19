package tv.miqat.companion;

import android.app.*;
import android.content.*;
import android.location.Location;
import android.os.SystemClock;
import java.util.*;
import tv.miqat.companion.core.PhonePrayerRules;

/** Inject a fresh London GPS fix into the explicitly owned emulator before the granted suite. */
final class PhoneLocationCheck {
    static void denied(Instrumentation test){
        Context c=test.getTargetContext();PhoneUiCheck.check(!PhoneLocation.allowed(c),"Deny foreground location permissions before this fixture");
        String[] error={""};PhoneLocation locator=new PhoneLocation(c);
        PhoneUiCheck.on(test,()->locator.request(value->{throw new AssertionError("Denied request returned a location");},message->error[0]=message));
        PhoneUiCheck.check(error[0].equals("phone_location_denied")&&!locator.running,"Denied request must stop without reading location");
    }
    static void run(Instrumentation test,boolean capture,boolean approximate)throws Exception {
        Context c=test.getTargetContext();long refresh=PhoneStore.preferences(c).getLong("location-refresh",0);PhoneStore original=PhoneStore.read(c);String language=c.getSharedPreferences("companion",0).getString("language","system");MainActivity[] current={null};
        PhoneUiCheck.check(PhoneLocation.allowed(c),"Grant foreground location and inject London GPS on the owned emulator first");
        Location stale=new Location("fixture");stale.setLatitude(51.5074);stale.setLongitude(-0.1278);stale.setAccuracy(3000);stale.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-121000000000L);
        PhoneUiCheck.check(!PhoneLocation.usable(stale,SystemClock.elapsedRealtimeNanos()),"Stale fix accepted");
        stale.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());PhoneUiCheck.check(PhoneLocation.usable(stale,SystemClock.elapsedRealtimeNanos()),"Approximate fix rejected");
        android.location.LocationManager manager=c.getSystemService(android.location.LocationManager.class);boolean mock=false;
        try {
            if(approximate){
                PhoneUiCheck.check(c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED,"Approximate fixture must not have precise access");
                manager.addTestProvider("network",false,false,false,false,true,true,true,android.location.Criteria.POWER_LOW,android.location.Criteria.ACCURACY_COARSE);
                mock=true;manager.setTestProviderEnabled("network",true);
                Location network=new Location("network");network.setLatitude(51.5074);network.setLongitude(-0.1278);network.setAccuracy(3000);network.setTime(System.currentTimeMillis());network.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());manager.setTestProviderLocation("network",network);
            }
            for(String lang:new String[]{"en","ar"}) {
                original.changed(Collections.singletonMap("prayer.travel-refresh","false")).save(c);c.getSharedPreferences("companion",0).edit().putString("language",lang).commit();
                MainActivity a=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));current[0]=a;
                PhoneUiCheck.on(test,()->{
                    a.phoneUi.open("calculation");
                    PhoneUiCheck.check(a.body.findViewWithTag("phone_latitude")==null,"Coordinates must stay in Advanced");
                    a.body.findViewWithTag("phone.location.detect").performClick();
                });
                LiveUiCheck.untilUi(test,()->a.phoneUi.locationUi.locationDialog!=null);
                PhoneUiCheck.check(!a.phoneUi.locationUi.locator.running,"One-shot detection did not release providers");
                PhoneUiCheck.on(test,()->a.phoneUi.locationUi.locationDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
                LiveUiCheck.untilUi(test,()->a.phoneUi.settings.locationReady&&a.phoneUi.settings.value("prayer.location").startsWith("detected"));
                PhoneUiCheck.on(test,()->{
                    PhoneUiCheck.check(a.phoneUi.settings.locationReady&&a.phoneUi.settings.value("prayer.location").startsWith("detected"),"Detected location was not confirmed/persisted");
                    double distance=tv.miqat.core.LocationRules.INSTANCE.distanceKm(51.5074,-0.1278,a.phoneUi.settings.latitude,a.phoneUi.settings.longitude);
                    PhoneUiCheck.check(distance<20,"Detector did not use injected GPS");
                    a.body.findViewWithTag("phone.location.advanced").performClick();
                    PhoneUiCheck.check(a.body.findViewWithTag("phone_latitude")!=null,"Advanced coordinate fallback missing");
                    a.phoneUi.locationUi.advancedLocation=false;a.render();
                });
                PhoneUiCheck.capture(test,a,lang+"-phone-detected-location",capture);
                PhoneUiCheck.on(test,a::finish);LiveUiCheck.untilUi(test,a::isDestroyed);current[0]=null;
            }
            if(!approximate){
                Map<String,String> travel=new LinkedHashMap<>();travel.put("prayer.travel-refresh","true");travel.put("prayer.location-ready","true");travel.put("prayer.location","custom");travel.put("prayer.latitude","0");travel.put("prayer.longitude","0");travel.put("prayer.zone","Asia/Tokyo");travel.put("prayer.method","KARACHI");travel.put("call.fajr.offset","7");original.changed(travel).save(c);
                PhoneStore.preferences(c).edit().putLong("location-refresh",0).commit();
                MainActivity a=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));current[0]=a;
                LiveUiCheck.untilUi(test,()->a.phoneUi.settings.value("prayer.location").startsWith("detected"));
                PhoneUiCheck.check(a.phoneUi.locationUi.locationDialog==null&&!a.phoneUi.locationUi.locator.running,"Opt-in refresh did not release provider");
                PhoneStore updated=PhoneStore.read(c);PhoneUiCheck.check(updated.value("prayer.method").equals("KARACHI")&&updated.offsets[0]==7&&updated.zone().getID().equals("Asia/Tokyo")&&updated.locationReady,"Travel refresh lost calibration/fixed timezone");
                PhoneUiCheck.check(tv.miqat.core.LocationRules.INSTANCE.distanceKm(51.5074,-0.1278,updated.latitude,updated.longitude)<20,"Travel refresh ignored fixture location");
                PhoneUiCheck.on(test,a::finish);LiveUiCheck.untilUi(test,a::isDestroyed);current[0]=null;
            }
        }finally{if(mock)manager.removeTestProvider("network");if(current[0]!=null)PhoneUiCheck.on(test,current[0]::finish);original.save(c);PhoneStore.preferences(c).edit().putLong("location-refresh",refresh).commit();c.getSharedPreferences("companion",0).edit().putString("language",language).commit();PhoneAlarms.rebuild(c);}
    }
}

package tv.miqat.companion;

import android.app.*;
import android.content.*;
import android.location.Location;
import android.os.SystemClock;
import java.util.*;
import tv.miqat.companion.core.PhonePrayerRules;

/** Native framework-mock network/GPS fixtures on an explicitly owned emulator; no physical GPS claim. */
final class PhoneLocationCheck {
    static void denied(Instrumentation test){
        Context c=test.getTargetContext();PhoneUiCheck.check(!PhoneLocation.allowed(c),"Deny foreground location permissions before this fixture");
        String[] error={""};PhoneLocation locator=new PhoneLocation(c);
        PhoneUiCheck.on(test,()->locator.request(value->{throw new AssertionError("Denied request returned a location");},message->error[0]=message));
        PhoneUiCheck.check(error[0].equals("phone_location_denied")&&!locator.running,"Denied request must stop without reading location");
    }
    static void run(Instrumentation test,boolean capture,boolean approximate)throws Exception {
        Context c=test.getTargetContext();long refresh=PhoneStore.preferences(c).getLong("location-refresh",0);PhoneStore original=PhoneStore.read(c);String language=c.getSharedPreferences("companion",0).getString("language","system");MainActivity[] current={null};
        SharedPreferences preferences=PhoneStore.preferences(c);boolean hadSeen=preferences.contains("onboarding-seen"),seen=preferences.getBoolean("onboarding-seen",false);
        PhoneUiCheck.check(PhoneLocation.allowed(c),"Grant foreground location and the test-only mock-location app-op on the owned emulator");
        Location stale=new Location("fixture");stale.setLatitude(51.5074);stale.setLongitude(-0.1278);stale.setAccuracy(3000);stale.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-121000000000L);
        PhoneUiCheck.check(!PhoneLocation.usable(stale,SystemClock.elapsedRealtimeNanos()),"Stale fix accepted");
        stale.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());PhoneUiCheck.check(PhoneLocation.usable(stale,SystemClock.elapsedRealtimeNanos()),"Approximate fix rejected");
        android.location.LocationManager manager=c.getSystemService(android.location.LocationManager.class);String mockProvider="";
        try {
            if(approximate) {
                PhoneUiCheck.check(c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED,"Approximate fixture must not have precise access");
            } else {
                PhoneUiCheck.check(c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED,"Precise framework-GPS fixture requires precise access");
            }
            String provider=approximate?"network":"gps";
            manager.addTestProvider(provider,false,false,false,false,true,true,true,android.location.Criteria.POWER_LOW,
                    approximate?android.location.Criteria.ACCURACY_COARSE:android.location.Criteria.ACCURACY_FINE);
            mockProvider=provider;manager.setTestProviderEnabled(provider,true);
            Location fixture=new Location(provider);fixture.setLatitude(51.5074);fixture.setLongitude(-0.1278);
            fixture.setAccuracy(approximate?3000:1);fixture.setTime(System.currentTimeMillis());
            fixture.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());manager.setTestProviderLocation(provider,fixture);
            for(String lang:new String[]{"en","ar"}) {
                original.changed(Collections.singletonMap("prayer.travel-refresh","false")).save(c);c.getSharedPreferences("companion",0).edit().putString("language",lang).commit();
                MainActivity a=LiveUiCheck.launch(test);current[0]=a;
                PhoneUiCheck.on(test,()->{
                    a.phoneUi.open("calculation");
                    PhoneUiCheck.check(a.body.findViewWithTag("phone_latitude")==null,"Coordinates must stay in Advanced");
                    a.body.findViewWithTag("phone.location.detect").performClick();
                });
                LiveUiCheck.untilUi(test,()->!a.phoneUi.locationUi.locator.running
                        && a.phoneUi.locationUi.locationMessage.equals("phone_location_found")
                        && !a.phoneUi.settings.locationReady
                        && a.phoneUi.settings.value("prayer.location").startsWith("detected"));
                PhoneUiCheck.check(a.phoneUi.locationUi.locationDialog==null,
                        "Detection must fill the page without another confirmation dialog");
                PhoneUiCheck.on(test,()->{
                    PhoneUiCheck.check(a.getWindow().getDecorView().findViewWithTag("phone.location.confirm")!=null,
                            "Detected coordinates must remain reviewable before confirmation");
                    double fixtureDistance=tv.miqat.core.LocationRules.INSTANCE.distanceKm(51.5074,-0.1278,a.phoneUi.settings.latitude,a.phoneUi.settings.longitude);
                    PhoneUiCheck.check(fixtureDistance<20,"Expected injected London fixture; provider returned "
                            +a.phoneUi.settings.latitude+","+a.phoneUi.settings.longitude+" ("+fixtureDistance+" km away)");
                    PhoneUiCheck.check(!a.phoneUi.locationUi.selectedCountry.isEmpty(),"Detected London location did not fill the country field");
                    a.getWindow().getDecorView().findViewWithTag("phone.location.confirm").performClick();
                });
                LiveUiCheck.untilUi(test,()->a.phoneUi.settings.locationReady);
                PhoneUiCheck.on(test,()->{
                    PhoneUiCheck.check(a.phoneUi.settings.locationReady&&a.phoneUi.settings.value("prayer.location").startsWith("detected"),"Detected location was not confirmed/persisted");
                    PhoneUiCheck.check(a.getWindow().getDecorView().findViewWithTag("phone.location.confirm")==null,
                            "A confirmed detection must not ask for confirmation again");
                    a.body.findViewWithTag("phone.location.advanced").performClick();
                    PhoneUiCheck.check(a.phoneUi.locationUi.locationDialog!=null
                            && a.phoneUi.locationUi.locationDialog.getWindow().getDecorView().findViewWithTag("phone_latitude")!=null
                            && a.body.findViewWithTag("phone_latitude")==null,"Advanced coordinates must open in a dialog");
                    double latitudeBefore=a.phoneUi.settings.latitude,longitudeBefore=a.phoneUi.settings.longitude;
                    ((android.widget.EditText)a.phoneUi.locationUi.locationDialog.getWindow().getDecorView().findViewWithTag("phone_latitude")).setText("0");
                    ((android.widget.EditText)a.phoneUi.locationUi.locationDialog.getWindow().getDecorView().findViewWithTag("phone_longitude")).setText("0");
                    a.phoneUi.locationUi.locationDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                    PhoneUiCheck.check(a.phoneUi.settings.locationReady&&a.phoneUi.settings.latitude==latitudeBefore
                            && a.phoneUi.settings.longitude==longitudeBefore,"Cancelling Advanced changed the confirmed location");
                });
                PhoneUiCheck.on(test,()->{
                    a.tab("prayers");
                    Location homeFix=new Location("home-fixture");
                    homeFix.setLatitude(51.5074);homeFix.setLongitude(-0.1278);homeFix.setAccuracy(3000);
                    homeFix.setTime(System.currentTimeMillis());homeFix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                    a.phoneUi.locationUi.offerLocation(homeFix);
                    PhoneUiCheck.check(a.page.equals("phone-settings")&&a.section.equals("calculation")
                            && !a.phoneUi.settings.locationReady&&a.phoneUi.locationUi.locationDialog==null,
                            "Home detection must open the unconfirmed location editor");
                    android.view.View confirm=a.getWindow().getDecorView().findViewWithTag("phone.location.confirm");
                    PhoneUiCheck.check(confirm!=null,"Home detection has no confirmation action");
                    confirm.performClick();
                    PhoneUiCheck.check(a.phoneUi.settings.locationReady
                            && a.getWindow().getDecorView().findViewWithTag("phone.location.confirm")==null,
                            "Home detection did not finish with one explicit confirmation");
                });
                PhoneUiCheck.capture(test,a,lang+"-phone-detected-location",capture);
                PhoneUiCheck.on(test,()->{
                    Location unknown=new Location("unmapped-fixture");unknown.setLatitude(0);unknown.setLongitude(0);
                    unknown.setAccuracy(1);unknown.setTime(System.currentTimeMillis());
                    unknown.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                    a.phoneUi.locationUi.offerLocation(unknown);
                    PhoneUiCheck.check(a.phoneUi.settings.value("prayer.location").equals("detected")
                            && a.phoneUi.settings.latitude==0&&a.phoneUi.settings.longitude==0
                            && a.phoneUi.locationUi.selectedCountry.isEmpty()&&!a.phoneUi.settings.locationReady,
                            "Unmapped coordinates must remain authoritative without a fabricated city/country");
                    PhoneUiCheck.check(((android.widget.TextView)a.body.findViewWithTag("phone.country")).getText().toString()
                            .startsWith(a.t("phone_choose_country"))
                            && ((android.widget.TextView)a.body.findViewWithTag("phone.city")).getText().toString()
                            .contains(a.t("phone_current_location")),"Unmapped location fields misrepresent the detected position");
                    PhoneUiCheck.check(a.phoneUi.locationUi.locationDialog==null,"Unmapped fix opened a redundant confirmation dialog");
                    android.view.View confirm=a.getWindow().getDecorView().findViewWithTag("phone.location.confirm");
                    PhoneUiCheck.check(confirm!=null,"Unmapped coordinates cannot be confirmed");confirm.performClick();
                    PhoneUiCheck.check(a.phoneUi.settings.locationReady
                            && a.getWindow().getDecorView().findViewWithTag("phone.location.confirm")==null,
                            "Unmapped location did not finish with one confirmation");
                });
                PhoneUiCheck.on(test,a::finish);LiveUiCheck.untilUi(test,a::isDestroyed);current[0]=null;
            }
            if(!approximate){
                Map<String,String> travel=new LinkedHashMap<>();travel.put("prayer.travel-refresh","true");travel.put("prayer.location-ready","true");travel.put("prayer.location","custom");travel.put("prayer.latitude","0");travel.put("prayer.longitude","0");travel.put("prayer.zone","Asia/Tokyo");travel.put("prayer.method","KARACHI");travel.put("call.fajr.offset","7");original.changed(travel).save(c);
                // Cold launch an opted-in user who has already left the first-use guide.
                preferences.edit().putLong("location-refresh",0).putBoolean("onboarding-seen",true).commit();
                MainActivity a=LiveUiCheck.launch(test);current[0]=a;
                LiveUiCheck.untilUi(test,()->a.phoneUi.settings.value("prayer.location").startsWith("detected"));
                PhoneUiCheck.check(a.phoneUi.locationUi.locationDialog==null&&!a.phoneUi.locationUi.locator.running,"Opt-in refresh did not release provider");
                PhoneStore updated=PhoneStore.read(c);PhoneUiCheck.check(updated.value("prayer.method").equals("KARACHI")&&updated.offsets[0]==7&&updated.zone().getID().equals("Asia/Tokyo")&&updated.locationReady,"Travel refresh lost calibration/fixed timezone");
                PhoneUiCheck.check(tv.miqat.core.LocationRules.INSTANCE.distanceKm(51.5074,-0.1278,updated.latitude,updated.longitude)<20,"Travel refresh ignored fixture location");
                PhoneUiCheck.on(test,a::finish);LiveUiCheck.untilUi(test,a::isDestroyed);current[0]=null;
            }
        }finally{if(!mockProvider.isEmpty())manager.removeTestProvider(mockProvider);if(current[0]!=null)PhoneUiCheck.on(test,current[0]::finish);original.save(c);SharedPreferences.Editor restore=preferences.edit().putLong("location-refresh",refresh);if(hadSeen)restore.putBoolean("onboarding-seen",seen);else restore.remove("onboarding-seen");PhoneUiCheck.check(restore.commit(),"Cannot restore travel/onboarding fixture");c.getSharedPreferences("companion",0).edit().putString("language",language).commit();PhoneAlarms.rebuild(c);}
    }
}

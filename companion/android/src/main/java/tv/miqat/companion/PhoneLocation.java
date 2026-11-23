package tv.miqat.companion;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import java.util.*;
import java.util.function.Consumer;
import tv.miqat.companion.core.PhonePrayerRules;

/** A bounded OS fix; callers gate foreground/background access. Miqat sends no coordinates. */
final class PhoneLocation {
    private final LocationManager manager;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<CancellationSignal> cancellations=new ArrayList<>();
    private final List<LocationListener> listeners=new ArrayList<>();
    private int generation;
    boolean running;
    PhoneLocation(Context context){this.context=context;manager=context.getSystemService(LocationManager.class);}
    static boolean allowed(Context c){return c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED||c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    static boolean preciseAllowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    static boolean usable(Location value,long elapsedNanos){
        return value!=null&&value.hasAccuracy()&&tv.miqat.core.LocationRules.INSTANCE.usableLocation(value.getLatitude(),value.getLongitude(),value.getAccuracy(),(elapsedNanos-value.getElapsedRealtimeNanos())/1000000.0);
    }
    void request(Consumer<Location> found,Consumer<String> failed){
        request(false, found, failed);
    }
    void requestPrecise(Consumer<Location> found, Consumer<String> failed) {
        request(true, found, failed);
    }
    static boolean usablePrecise(Location value, long elapsedNanos) {
        return usable(value, elapsedNanos) && value.getAccuracy() <= 100;
    }
    private void request(boolean precise, Consumer<Location> found, Consumer<String> failed) {
        close();if(!(precise ? preciseAllowed(context) : allowed(context))){failed.accept("phone_location_denied");return;}
        final int token=++generation;List<String> providers=new ArrayList<>();
        java.util.function.Predicate<Location> usableFix = value -> precise
                ? usablePrecise(value, SystemClock.elapsedRealtimeNanos())
                : usable(value, SystemClock.elapsedRealtimeNanos());
        for(String name:precise ? Arrays.asList(LocationManager.GPS_PROVIDER, "fused")
                : Arrays.asList(LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER,"fused"))try{if(manager.getAllProviders().contains(name)&&manager.isProviderEnabled(name))providers.add(name);}catch(RuntimeException ignored){/* Other providers may remain usable. */}
        if(providers.isEmpty()){failed.accept("phone_location_disabled");return;}
        running=true;
        Consumer<Location> accept=value->{if(token!=generation||!running||!usableFix.test(value))return;Location copy=new Location(value);close();found.accept(copy);};
        Location best=null;
        for(String provider:providers)try{Location value=manager.getLastKnownLocation(provider);if(usableFix.test(value)&&(best==null||value.getAccuracy()<best.getAccuracy()))best=value;}catch(SecurityException ignored){/* Approximate access can exclude a provider on older Android. */}
        if(best!=null){accept.accept(best);return;}
        main.postDelayed(()->{if(token==generation){close();failed.accept("phone_location_timeout");}},20000);
        for(String provider:providers)try {
            if(!precise && Build.VERSION.SDK_INT>=30){CancellationSignal cancel=new CancellationSignal();cancellations.add(cancel);manager.getCurrentLocation(provider,cancel,context.getMainExecutor(),accept);}
            else {
                LocationListener listener=new LocationListener(){
                    public void onLocationChanged(Location value){accept.accept(value);}
                    public void onStatusChanged(String name,int status,Bundle extras){}
                    public void onProviderEnabled(String name){}
                    public void onProviderDisabled(String name){}
                };
                listeners.add(listener);
                if (precise) manager.requestLocationUpdates(provider, 0, 0, listener, Looper.getMainLooper());
                else manager.requestSingleUpdate(provider,listener,Looper.getMainLooper());
            }
        } catch (SecurityException denied) {
            if (!(precise ? preciseAllowed(context) : allowed(context))) {
                close();
                failed.accept("phone_location_denied");
                return;
            }
        } catch (RuntimeException unavailable) {
            /* Another enabled provider may return a fix before the timeout. */
        }
    }
    void close(){
        generation++;running=false;main.removeCallbacksAndMessages(null);
        for(CancellationSignal signal:cancellations)signal.cancel();cancellations.clear();
        for(LocationListener listener:listeners)try{manager.removeUpdates(listener);}catch(RuntimeException ignored){/* Revoked permission also ends the request. */}
        listeners.clear();
    }
}

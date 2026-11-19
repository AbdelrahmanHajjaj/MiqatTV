package tv.miqat.companion;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import java.util.*;
import java.util.function.Consumer;
import tv.miqat.companion.core.PhonePrayerRules;

/** A bounded foreground fix. No background permission, tracking service or coordinates sent by Miqat. */
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
    static boolean usable(Location value,long elapsedNanos){
        return value!=null&&value.hasAccuracy()&&PhonePrayerRules.INSTANCE.usableLocation(value.getLatitude(),value.getLongitude(),value.getAccuracy(),(elapsedNanos-value.getElapsedRealtimeNanos())/1000000.0);
    }
    void request(Consumer<Location> found,Consumer<String> failed){
        close();if(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){failed.accept("phone_location_denied");return;}
        final int token=++generation;List<String> providers=new ArrayList<>();
        for(String name:Arrays.asList(LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER,"fused"))try{if(manager.getAllProviders().contains(name)&&manager.isProviderEnabled(name))providers.add(name);}catch(RuntimeException ignored){/* Other providers may remain usable. */}
        if(providers.isEmpty()){failed.accept("phone_location_disabled");return;}
        running=true;
        Consumer<Location> accept=value->{if(token!=generation||!running||!usable(value,SystemClock.elapsedRealtimeNanos()))return;Location copy=new Location(value);close();found.accept(copy);};
        Location best=null;
        for(String provider:providers)try{Location value=manager.getLastKnownLocation(provider);if(usable(value,SystemClock.elapsedRealtimeNanos())&&(best==null||value.getAccuracy()<best.getAccuracy()))best=value;}catch(SecurityException ignored){/* Approximate access can exclude a provider on older Android. */}
        if(best!=null){accept.accept(best);return;}
        main.postDelayed(()->{if(token==generation){close();failed.accept("phone_location_timeout");}},20000);
        for(String provider:providers)try {
            if(Build.VERSION.SDK_INT>=30){CancellationSignal cancel=new CancellationSignal();cancellations.add(cancel);manager.getCurrentLocation(provider,cancel,context.getMainExecutor(),accept);}
            else {
                LocationListener listener=new LocationListener(){
                    public void onLocationChanged(Location value){accept.accept(value);}
                    public void onStatusChanged(String name,int status,Bundle extras){}
                    public void onProviderEnabled(String name){}
                    public void onProviderDisabled(String name){}
                };
                listeners.add(listener);manager.requestSingleUpdate(provider,listener,Looper.getMainLooper());
            }
        }catch(RuntimeException unavailable){/* Another enabled provider may return a fix before the timeout. */}
    }
    void close(){
        generation++;running=false;main.removeCallbacksAndMessages(null);
        for(CancellationSignal signal:cancellations)signal.cancel();cancellations.clear();
        for(LocationListener listener:listeners)try{manager.removeUpdates(listener);}catch(RuntimeException ignored){/* Revoked permission also ends the request. */}
        listeners.clear();
    }
}

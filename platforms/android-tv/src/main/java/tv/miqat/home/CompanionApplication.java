package tv.miqat.home;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import tv.miqat.companion.lan.*;
import java.net.Inet4Address;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground activities own LAN availability; no service, wake lock or scheduler is added. */
public final class CompanionApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Object serverLock=new Object();
    volatile boolean foreground,failed;
    volatile Exception failure;
    volatile Pairing pairing;
    volatile TlsIdentity identity;
    volatile LanConfig config;
    volatile String token="";
    private volatile boolean pairingPage;
    private final java.util.Set<Activity> pairingPages=new java.util.HashSet<>();
    private volatile long generation;
    private volatile TlsServer server;
    private CompanionSettings settings;
    private Activity resumed;
    private int started;

    @Override public void onCreate() {
        super.onCreate();registerActivityLifecycleCallbacks(this);
        worker.execute(()->{try{initialize();refresh();}catch(Exception error){unavailable(error);}});
    }
    private void initialize()throws Exception {
        if(config==null)config=new LanConfig(this);
        if(pairing==null)pairing=new Pairing(new TrustStore(this),config.pairWindowMillis,config.maxPhones);
    }
    private void refresh()throws Exception {
        if(!foreground || pairing==null || !pairingPage && pairing.clients().isEmpty()){closeServer();return;}
        if(server!=null)return;
        if(identity==null)identity=new TlsIdentity();
        if(settings==null)settings=new CompanionSettings(this);
        if(!foreground)return;
        PairingRoutes routes=new PairingRoutes(pairing,settings);
        synchronized(serverLock) {
            if(foreground && server==null)server=new TlsServer(identity,config,request->{
                if(!foreground)throw new SecurityException();return routes.handle(request);
            },(getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0);
        }
    }
    private void closeServer(){synchronized(serverLock){if(server!=null){server.close();server=null;}}}
    boolean listening(){return server!=null;}
    private void unavailable(Exception error){failure=error;failed=true;}
    void open() {
        long request=++generation;failed=false;
        worker.execute(()->{
            try{initialize();refresh();if(request==generation && foreground && pairingPage && server!=null)token=pairing.open(SystemClock.elapsedRealtime());}
            catch(Exception error){unavailable(error);}
        });
    }
    void cancel(){generation++;token="";Pairing ready=pairing;if(ready!=null)ready.close();worker.execute(()->{try{refresh();}catch(Exception error){unavailable(error);}});}
    void approve(String nonce){failed=false;worker.execute(()->{try{if(foreground && pairingPage)pairing.approve(nonce,SystemClock.elapsedRealtime());}catch(Exception error){unavailable(error);}});}
    void reject(String nonce){failed=false;worker.execute(()->{try{if(foreground && pairingPage){pairing.reject(nonce,SystemClock.elapsedRealtime());token="";}}catch(Exception error){unavailable(error);}});}
    void revoke(String id){failed=false;worker.execute(()->{try{if(foreground && pairingPage)pairing.revoke(id);}catch(Exception error){unavailable(error);}});}
    boolean busy(){return !foreground || !(resumed instanceof Screen) || !resumed.hasWindowFocus() || resumed instanceof NowActivity || ((Screen)resumed).companionEditing();}
    Activity activity(){return resumed;}
    void refreshScreen(){if(resumed instanceof Screen){Screen screen=(Screen)resumed;screen.loadSettings(null);screen.build();}}
    String endpoint() {
        try {
            ConnectivityManager manager=getSystemService(ConnectivityManager.class);
            LinkProperties properties=manager==null?null:manager.getLinkProperties(manager.getActiveNetwork());
            if(properties!=null && config!=null)for(LinkAddress address:properties.getLinkAddresses())
                if(address.getAddress() instanceof Inet4Address && address.getAddress().isSiteLocalAddress())return "https://"+address.getAddress().getHostAddress()+":"+config.port;
        }catch(SecurityException ignored){}
        return "";
    }
    @Override public void onActivityStarted(Activity activity){
        started++;foreground=true;if(activity instanceof PairingActivity)pairingPages.add(activity);pairingPage=!pairingPages.isEmpty();
        worker.execute(()->{try{initialize();refresh();}catch(Exception error){unavailable(error);}});
    }
    @Override public void onActivityStopped(Activity activity){
        if(pairingPages.remove(activity)&&pairingPages.isEmpty()){pairingPage=false;cancel();}
        if(--started==0){foreground=false;generation++;token="";if(pairing!=null)pairing.close();closeServer();}
    }
    @Override public void onActivityResumed(Activity activity){resumed=activity;}
    @Override public void onActivityPaused(Activity activity){if(resumed==activity)resumed=null;}
    @Override public void onActivityCreated(Activity activity,Bundle state){}
    @Override public void onActivitySaveInstanceState(Activity activity,Bundle state){}
    @Override public void onActivityDestroyed(Activity activity){}
}

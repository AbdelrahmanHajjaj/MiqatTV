package tv.miqat.companion;

import android.app.Instrumentation;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Paired only with check-lg-install.cjs on the host, never a physical TV. */
final class LgInstallCheck {
    static void run(Instrumentation test)throws Exception {
        for(String bad:new String[]{"127.0.0.1","example.com","192.168.1.2;reboot","192.168.01.2","256.1.2.3","8.8.8.8"})
            LanCheck.rejects(()->LgInstall.host(bad));
        CompanionCheck.check(LgInstall.frames("{\"returnValue\":true,\"text\":\"}\\\"{\",\"a\":[{}]}\n{\"returnValue\":false}").size()==2,"LG JSON frames");
        LanCheck.rejects(()->LgInstall.frames("[]"));
        LanCheck.rejects(()->LgInstall.frames("{\"a\":"+new String(new char[40]).replace("\0","[")+"0"));
        String host="10.0.2.2",pin="";
        try(LgInstall probe=new LgInstall()) {
            try{probe.connect(host,"",new byte[0]);throw new AssertionError("Unknown host accepted");}
            catch(LgInstall.Trust trust){pin=trust.key;}
        }
        final String expected=pin;
        try(LgInstall wrong=new LgInstall()) {
            LanCheck.rejects(()->wrong.connect(host,Base64.getEncoder().encodeToString(new byte[64]),password()));
        }
        try(LgInstall wrongPhrase=new LgInstall()) {LanCheck.rejects(()->wrongPhrase.connect(host,expected,"wrong".getBytes(StandardCharsets.UTF_8)));}
        File fixture=new File(test.getTargetContext().getCacheDir(),"lg-wire-fixture.ipk");
        HttpURLConnection download=(HttpURLConnection)new URL("http://10.0.2.2:9991/fixture.ipk").openConnection();
        download.setConnectTimeout(10000);download.setReadTimeout(10000);download.setInstanceFollowRedirects(false);
        try(InputStream in=download.getInputStream();OutputStream out=new FileOutputStream(fixture)) {
            byte[] b=new byte[65536];int n;long count=0;
            while((n=in.read(b))!=-1){count+=n;if(count>LgPackage.MAX_BYTES)throw new IOException("Fixture too big");out.write(b,0,n);}
        }finally{download.disconnect();}
        try(LgInstall tv=new LgInstall()) {
            tv.connect(host,expected,password());
            CompanionCheck.check(tv.installedVersion().equals("0.2.0"),"Initial installed version");
            LgPackage pkg=LgPackage.read(fixture);
            LanCheck.rejects(()->tv.install(pkg));
            CompanionCheck.check(tv.installedVersion().equals("0.2.0"),"Failed install changed version");
            tv.install(pkg);
            CompanionCheck.check(tv.installedVersion().equals(pkg.version),"Installed version not verified");
            tv.launch();
        }finally{if(fixture.exists()&&!fixture.delete())throw new IOException("Cannot clean fixture");}
    }
    private static byte[] password(){return "miqat-fixture-only".getBytes(StandardCharsets.UTF_8);}
}

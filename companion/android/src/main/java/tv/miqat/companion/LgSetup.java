package tv.miqat.companion;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Uses the companion's native surfaces/controls; all privileged work is explicit. */
final class LgSetup {
    private final MainActivity a;
    private volatile LgInstall client;
    private boolean connected;
    private String version="",message="lg_intro";
    private LgPackage selected;
    private volatile boolean destroyed;
    LgSetup(MainActivity activity){a=activity;File cached=new File(a.getCacheDir(),"lg-selected.ipk");if(cached.isFile())try{selected=LgPackage.read(cached);}catch(IOException ignored){/* Ask for a valid package again. */}}

    void render(LinearLayout parent) {
        LinearLayout guide=a.card(parent);
        guide.addView(a.text(a.t("lg_intro"),15,a.ink));
        guide.addView(a.text(a.t("lg_steps"),14,a.muted));
        guide.addView(a.text(a.t("lg_expiry"),13,a.muted));
        a.button(guide,a.t("lg_official"),()->a.startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://webostv.developer.lge.com/develop/getting-started/developer-mode-app"))));
        LinearLayout packageCard=a.card(parent);
        packageCard.addView(a.text(selected==null?a.t("lg_no_package"):"Miqat "+selected.version+" · IPK",15,a.ink));
        a.button(packageCard,a.t("lg_choose"),()->a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),73)).setTag("lg.choose");
        LinearLayout connect=a.card(parent);
        String saved=a.prefs().getString("lg-host","");
        EditText address=field(connect,a.t("lg_address"),saved,false);address.setTag("lg.host");
        EditText pass=field(connect,a.t("lg_passphrase"),"",true);pass.setTag("lg.passphrase");
        connect.addView(a.text(a.t("lg_session_keys"),12,a.muted));
        a.button(connect,a.t(connected?"reconnect":"lg_connect"),()->connect(address.getText().toString().trim(),pass.getText().toString()))
            .setTag("lg.connect");
        connect.addView(a.text(a.t(message),14,a.accent));
        if(connected) {
            connect.addView(a.text(version.isEmpty()?a.t("lg_not_installed"):"Miqat "+version,16,a.ink));
            if(selected!=null)a.button(connect,a.t("lg_install"),()->new AlertDialog.Builder(a).setTitle(a.t("lg_install"))
                .setMessage(a.t("lg_install_confirm")+"\nMiqat "+selected.version)
                .setNegativeButton(a.t("cancel"),null).setPositiveButton(a.t("lg_install"),(dialog,which)->install()).show()).setTag("lg.install");
            if(!version.isEmpty())a.button(connect,a.t("lg_launch"),()->run(()->{client.launch();return true;},ignored->{message="lg_launched";a.render();})).setTag("lg.launch");
            a.button(connect,a.t("pair"),()->{close();a.navigate("pair");});
        }
        a.button(parent,a.t("lg_forget_key"),()->new AlertDialog.Builder(a).setMessage(a.t("lg_forget_key_info"))
            .setNegativeButton(a.t("cancel"),null).setPositiveButton(a.t("lg_forget_key"),(d,w)->{
                close();a.prefs().edit().remove("lg-key-"+saved).apply();message="lg_intro";a.render();
            }).show());
    }
    private EditText field(LinearLayout parent,String label,String value,boolean secret) {
        parent.addView(a.text(label,13,a.muted));EditText field=new EditText(a);field.setSingleLine(true);field.setText(value);
        field.setTextColor(a.ink);field.setTextSize(16);field.setTypeface(a.regular);
        field.setTextDirection(View.TEXT_DIRECTION_LTR);field.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_URI));
        field.setSaveEnabled(false);field.setContentDescription(label);parent.addView(field,new LinearLayout.LayoutParams(-1,a.dp(56)));return field;
    }
    private void connect(String address,String phrase) {
        final String host;
        try{host=LgInstall.host(address);}catch(IOException invalid){message="lg_invalid_host";a.render();return;}
        a.prefs().edit().putString("lg-host",host).apply();
        String pinned=a.prefs().getString("lg-key-"+host,"");
        close();destroyed=false;final byte[] pass=phrase.getBytes(StandardCharsets.UTF_8);
        run(()->{
            LgInstall transport=new LgInstall();client=transport;
            try{
                if(destroyed)throw new IOException("Setup closed");
                transport.connect(host,pinned,pass);return new String[]{"",transport.installedVersion()};
            }catch(LgInstall.Trust trust){transport.close();return new String[]{trust.key,""};}
            finally{Arrays.fill(pass,(byte)0);}
        },result->{
            if(!result[0].isEmpty()) {
                String fingerprint;
                try{fingerprint=LgInstall.fingerprint(result[0]);}catch(Exception invalid){message="lg_failed";a.render();return;}
                new AlertDialog.Builder(a).setTitle(a.t("lg_trust"))
                    .setMessage(a.t("lg_trust_info")+"\n\n"+host+"\n"+fingerprint)
                    .setNegativeButton(a.t("cancel"),null).setPositiveButton(a.t("lg_trust"),(d,w)->{
                        a.prefs().edit().putString("lg-key-"+host,result[0]).apply();message="lg_enter_passphrase";a.render();
                    }).show();
            } else {connected=true;version=result[1];message="lg_connected";}
            a.render();
        });
    }
    void select(Uri uri) {
        close();run(()->{
            File part=new File(a.getCacheDir(),"lg-selected.part"),file=new File(a.getCacheDir(),"lg-selected.ipk");
            try {
                try(InputStream in=a.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(part)) {
                    if(in==null)throw new IOException("No package");byte[] b=new byte[65536];int n;long count=0;
                    while((n=in.read(b))!=-1){count+=n;if(count>LgPackage.MAX_BYTES)throw new IOException("Package too large");out.write(b,0,n);}
                }
                LgPackage.read(part);Files.move(part.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);return LgPackage.read(file);
            } finally {if(part.exists()&&!part.delete())part.deleteOnExit();}
        },pkg->{selected=pkg;message="lg_package_ready";a.render();});
    }
    private void install() {
        LgPackage pkg=selected;if(!connected||pkg==null)return;
        try{if(!version.isEmpty()&&LgPackage.compareVersions(pkg.version,version)<0){message="lg_downgrade";a.render();return;}}
        catch(IOException invalid){message="lg_failed";a.render();return;}
        message="lg_installing";
        LgInstall transport=client;
        run(()->{transport.install(pkg);return transport.installedVersion();},installed->{version=installed;message="lg_installed";a.render();});
    }
    private <T> void run(Callable<T> work,Consumer<T> done) {
        a.network(work,done,()->{close();message="lg_failed";a.render();});
    }
    void close(){destroyed=true;if(connected)message=a.networkBusy?"lg_failed":"lg_reconnect";connected=false;LgInstall transport=client;if(transport!=null)transport.close();client=null;}
}

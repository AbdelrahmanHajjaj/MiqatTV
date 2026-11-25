package tv.miqat.companion;

import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
        SharedPreferences prefs = test.getTargetContext().getSharedPreferences("lg-credential-fixture", 0);
        LgCredentials credentials = new LgCredentials(prefs);
        byte[] remembered = password();
        byte[] privateKey = LgInstall.downloadKey(host);
        try {
            credentials.save(host, expected, remembered, privateKey);
            CompanionCheck.check(new LgCredentials(prefs).has(host), "Remembered LG passphrase missing");
            CompanionCheck.check(!credentials.has("10.0.2.3"), "Passphrase offered for a different TV");
            CompanionCheck.check(!prefs.getAll().toString().contains(new String(remembered, StandardCharsets.UTF_8)),
                    "LG passphrase persisted in plaintext");
            CompanionCheck.check(!prefs.getAll().toString().contains("PRIVATE KEY"), "LG key persisted in plaintext");
            LanCheck.rejects(() -> credentials.read(host, "changed host key"));
            Arrays.fill(remembered, (byte) 0);
            remembered = new LgCredentials(prefs).read(host, expected);
            CompanionCheck.check(Arrays.equals(remembered, password()), "Remembered passphrase did not reopen");
        } finally {
            Arrays.fill(privateKey, (byte) 0);
            credentials.forget(host);
        }
        CompanionCheck.check(!credentials.has(host) && !prefs.contains("lg-private-" + host),
                "Forget left saved LG credentials");
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
            tv.connect(host,expected,remembered);
            CompanionCheck.check(tv.installedVersion().equals("0.2.0"),"Initial installed version");
            LgPackage pkg=LgPackage.read(fixture);
            List<LgInstall.Stage> failedStages = Collections.synchronizedList(new ArrayList<>());
            LanCheck.rejects(()->tv.install(pkg, (stage, percent) -> failedStages.add(stage)));
            CompanionCheck.check(!failedStages.contains(LgInstall.Stage.CHECK_VERSION),
                    "Refused install reached version confirmation");
            CompanionCheck.check(tv.installedVersion().equals("0.2.0"),"Failed install changed version");
            List<Integer> sent = Collections.synchronizedList(new ArrayList<>());
            List<LgInstall.Stage> stages = Collections.synchronizedList(new ArrayList<>());
            tv.install(pkg, (stage, percent) -> {
                if (stage == LgInstall.Stage.SEND) sent.add(percent);
                if (stages.isEmpty() || stages.get(stages.size() - 1) != stage) stages.add(stage);
            });
            CompanionCheck.check(sent.size() > 2 && sent.get(0) == 0 && sent.get(sent.size() - 1) == 100,
                    "LG transfer must report intermediate percentages and completion");
            for (int i = 1; i < sent.size(); i++)
                CompanionCheck.check(sent.get(i) > sent.get(i - 1), "LG transfer percent went backward");
            CompanionCheck.check(stages.equals(Arrays.asList(LgInstall.Stage.SEND, LgInstall.Stage.CHECK_TRANSFER,
                    LgInstall.Stage.INSTALL, LgInstall.Stage.CHECK_VERSION)), "LG install stages out of order");
            CompanionCheck.check(tv.installedVersion().equals(pkg.version),"Installed version not verified");
            tv.launch();
            verifyRemembered(test, host, fixture);
        }finally{if(fixture.exists()&&!fixture.delete())throw new IOException("Cannot clean fixture");}
    }

    private static void verifyRemembered(Instrumentation test, String host, File fixture) throws Exception {
        MainActivity phone = LiveUiCheck.launch(test);
        SharedPreferences prefs = phone.prefs();
        String oldHost = prefs.getString("lg-host", null);
        LgCredentials credentials = new LgCredentials(prefs);
        File selected = new File(phone.getCacheDir(), "lg-selected.ipk");
        CompanionCheck.check(!credentials.has(host) && !prefs.contains("lg-key-" + host),
                "LG UI fixture needs a fresh installation identity");
        CompanionCheck.check(!selected.exists(), "LG UI fixture needs an empty package cache");
        try {
            openLocalSetup(test, phone, fixture);
            test.runOnMainSync(() -> {
                ((EditText) phone.body.findViewWithTag("lg.host")).setText(host);
                ((EditText) phone.body.findViewWithTag("lg.passphrase")).setText("miqat-fixture-only");
                phone.body.findViewWithTag("lg.remember").performClick();
                phone.getWindow().getDecorView().findViewWithTag("lg.connect").performClick();
                TextView stage = phone.getWindow().getDecorView().findViewWithTag("lg.progress");
                CompanionCheck.check(stage != null && stage.getText().toString().equals(phone.t("lg_connecting")),
                        "Connecting stage must be visible on the setup screen");
            });
            boolean[] accepted = {false};
            LiveUiCheck.until(() -> accepted[0] || (accepted[0] = acceptTrust(test, phone.t("lg_trust"))));
            LiveUiCheck.untilUi(test, () -> !phone.networkBusy && phone.body.findViewWithTag("lg.install") != null);
            CompanionCheck.check(credentials.has(host), "First-time trust did not save the opted-in passphrase");

            openLocalSetup(test, phone, fixture);
            test.runOnMainSync(() -> {
                EditText address = phone.body.findViewWithTag("lg.host");
                EditText phrase = phone.body.findViewWithTag("lg.passphrase");
                Switch remember = phone.body.findViewWithTag("lg.remember");
                CompanionCheck.check(host.contentEquals(address.getText()) && remember.isChecked()
                        && phrase.length() == 0 && phone.t("lg_saved_passphrase").contentEquals(phrase.getHint()),
                        "LG setup must reopen with the saved address and a blank passphrase field");
            });
            LiveUiCheck.click(test, phone, "lg.connect");
            LiveUiCheck.untilUi(test, () -> !phone.networkBusy && phone.body.findViewWithTag("lg.install") != null);
        } finally {
            test.runOnMainSync(phone::finish);
            test.waitForIdleSync();
            credentials.forget(host);
            prefs.edit().putString("lg-host", oldHost).commit();
            if (selected.exists() && !selected.delete()) throw new IOException("Cannot clean selected fixture");
        }
    }

    private static void openLocalSetup(Instrumentation test, MainActivity phone, File fixture) throws Exception {
        test.runOnMainSync(phone.tvSetup::open);
        LiveUiCheck.click(test, phone, "setup.platform.lg");
        LiveUiCheck.click(test, phone, "setup.advanced");
        test.runOnMainSync(() -> phone.lgSetup.select(Uri.fromFile(fixture)));
        LiveUiCheck.untilUi(test, () -> {
            android.view.View connect = phone.getWindow().getDecorView().findViewWithTag("lg.connect");
            return connect != null && connect.isEnabled();
        });
    }

    private static boolean acceptTrust(Instrumentation test, String label) {
        AccessibilityNodeInfo root = test.getUiAutomation().getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> buttons = root.findAccessibilityNodeInfosByViewId("android:id/button1");
        try {
            for (AccessibilityNodeInfo button : buttons)
                if (label.equalsIgnoreCase(String.valueOf(button.getText()))
                        && button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    return true;
            return false;
        } finally {
            for (AccessibilityNodeInfo button : buttons) button.recycle();
            root.recycle();
        }
    }
    private static byte[] password(){return "miqat-fixture-only".getBytes(StandardCharsets.UTF_8);}
}

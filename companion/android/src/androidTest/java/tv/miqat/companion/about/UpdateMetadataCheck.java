package tv.miqat.companion.about;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import org.json.JSONException;

public final class UpdateMetadataCheck {
    private UpdateMetadataCheck() {}

    public static void run() throws Exception {
        String stable = "{\"tag\":\"v0.7.6\",\"android\":{"
                + "\"tv.miqat.companion\":{\"versionCode\":38,\"versionName\":\"0.5.22\"},"
                + "\"tv.miqat.home\":{\"versionCode\":15,\"versionName\":\"0.7.4\"}}}";
        AboutUpdates.Result phone = AboutUpdates.read(stable, "tv.miqat.companion");
        AboutUpdates.Result tv = AboutUpdates.read(stable, "tv.miqat.home");
        if (phone.code() != 38 || tv.code() != 15 || !phone.version().equals("0.5.22")
                || !phone.url().equals("https://github.com/AbdelrahmanHajjaj/MiqatTV/releases/download/v0.7.6/miqat-phone.apk")
                || !tv.url().equals("https://github.com/AbdelrahmanHajjaj/MiqatTV/releases/download/v0.7.6/miqat.apk")) {
            throw new AssertionError("One stable release must supply independent Android versions");
        }
        reject(stable.replace("v0.7.6", "v0.7.6-preview.1"), "tv.miqat.companion");
        reject(stable.replace(":38", ":38.5"), "tv.miqat.companion");
        reject(stable, "tv.miqat.webos");
        if (!UpdatePackage.trustedHost(new URL("https://release-assets.githubusercontent.com/example"))
                || UpdatePackage.trustedHost(new URL("http://github.com/example"))
                || UpdatePackage.trustedHost(new URL("https://github.com:444/example"))
                || UpdatePackage.trustedHost(new URL("https://github.com.example.org/example"))) {
            throw new AssertionError("APK redirects must stay on trusted HTTPS hosts");
        }
    }

    public static void automaticOffer(Activity activity, AlertDialog.Builder builder, boolean rtl) {
        AboutUpdates.Labels labels = new AboutUpdates.Labels("Updates", "Checking", "Current", "Available",
                "Failed", "Install", "Downloading", "Install failed", "Close");
        AboutUpdates.Result candidate = new AboutUpdates.Result(20, "1.0.0", "");
        if (AboutUpdates.showAvailable(activity, builder, rtl, labels, null, 19, () -> true) != null
                || AboutUpdates.showAvailable(activity, builder, rtl, labels, candidate, 20, () -> true) != null
                || AboutUpdates.showAvailable(activity, builder, rtl, labels, candidate, 19, () -> false) != null)
            throw new AssertionError("Automatic checks must stay silent when unavailable, current or backgrounded");
        AlertDialog dialog = AboutUpdates.showAvailable(activity, builder, rtl, labels, candidate, 19, () -> true);
        if (dialog == null || !dialog.isShowing() || !dialog.getButton(AlertDialog.BUTTON_POSITIVE).isShown())
            throw new AssertionError("An available foreground update must show its installation action");
        dialog.dismiss();
    }

    public static void install(Instrumentation test) throws Exception {
        run();
        Context context = test.getTargetContext();
        Path fixture = context.getCacheDir().toPath().resolve("update-fixture.apk");
        PackageInfo current = AboutMiqat.packageInfo(context);
        PackageInfo candidate = context.getPackageManager().getPackageArchiveInfo(fixture.toString(), 0);
        if (candidate == null || AboutUpdates.code(candidate) <= AboutUpdates.code(current)) {
            throw new AssertionError("Supply a newer same-signed Miqat APK as update-fixture.apk");
        }
        try {
            int wrong = UpdatePackage.stage(context, fixture, new AboutUpdates.Result(AboutUpdates.code(current), current.versionName, ""));
            context.getPackageManager().getPackageInstaller().abandonSession(wrong);
            throw new AssertionError("Accepted APK with the wrong release version");
        } catch (IOException expected) {}
        Activity activity = test.startActivitySync(new Intent(Intent.ACTION_MAIN).setClassName(context, "tv.miqat.companion.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        int session = UpdatePackage.stage(context, fixture, new AboutUpdates.Result(AboutUpdates.code(candidate), candidate.versionName, ""));
        test.runOnMainSync(() -> {
            try {
                UpdatePackage.confirm(activity, session, "Update failed");
            } catch (IOException failure) {
                throw new AssertionError(failure);
            }
        });
        long deadline = SystemClock.elapsedRealtime() + 15_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            AccessibilityNodeInfo root = test.getUiAutomation().getRootInActiveWindow();
            if (root != null && root.getPackageName() != null && root.getPackageName().toString().contains("packageinstaller")) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Android's confirmation screen did not open");
    }

    private static void reject(String data, String appId) throws Exception {
        try {
            AboutUpdates.read(data, appId);
        } catch (IOException | JSONException expected) {
            return;
        }
        throw new AssertionError("Accepted invalid stable update metadata");
    }
}

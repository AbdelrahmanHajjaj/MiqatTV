package tv.miqat.companion.about;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

/** Fetches public stable release metadata only when a user opens the check. */
public final class AboutUpdates {
    private static final String METADATA = "https://abdelrahmanhajjaj.github.io/MiqatTV/updates.json";
    private static final String RELEASE = "https://github.com/AbdelrahmanHajjaj/MiqatTV/releases/tag/";
    private static final int MAX_BYTES = 4096;
    private static final int TIMEOUT_MS = 5000;

    public record Labels(String title, String checking, String current, String available,
                         String failed, String viewRelease, String close) {}
    record Result(long code, String version, String url) {}

    private AboutUpdates() {}

    @SuppressWarnings("deprecation")
    public static boolean available(Context context) {
        PackageManager manager = context.getPackageManager();
        String name = context.getPackageName();
        try {
            String installer;
            if (Build.VERSION.SDK_INT >= 30) {
                InstallSourceInfo source = manager.getInstallSourceInfo(name);
                if (Build.VERSION.SDK_INT >= 33 && source.getPackageSource() == PackageInstaller.PACKAGE_SOURCE_STORE) return false;
                installer = source.getInstallingPackageName();
            } else {
                installer = manager.getInstallerPackageName(name);
            }
            return !"com.android.vending".equals(installer)
                    && !"com.amazon.venezia".equals(installer)
                    && !"com.sec.android.app.samsungapps".equals(installer)
                    && !"com.huawei.appmarket".equals(installer)
                    && !"org.fdroid.fdroid".equals(installer);
        } catch (PackageManager.NameNotFoundException | IllegalArgumentException unavailable) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    public static void show(Activity activity, AlertDialog.Builder builder, boolean rtl, Labels labels,
                            Consumer<String> openRelease) {
        if (!available(activity)) return;
        PackageInfo installed = AboutMiqat.packageInfo(activity);
        long installedCode = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
        AlertDialog dialog = builder
                .setTitle(labels.title()).setMessage(labels.checking())
                .setNegativeButton(labels.close(), null)
                .setPositiveButton(labels.viewRelease(), null).show();
        dialog.getWindow().getDecorView().setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);
        new Thread(() -> {
            Result result;
            try {
                result = read(stableMetadata(), installed.packageName);
            } catch (IOException | JSONException invalid) {
                result = null;
            }
            Result found = result;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed() || !dialog.isShowing()) return;
                if (found == null) dialog.setMessage(labels.failed());
                else if (found.code() <= installedCode) dialog.setMessage(labels.current());
                else {
                    dialog.setMessage(labels.available() + " · \u2066" + found.version() + "\u2069");
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                        dialog.dismiss();
                        openRelease.accept(found.url());
                    });
                }
            });
        }, "miqat-update-check").start();
    }

    static Result read(String json, String appId) throws IOException, JSONException {
        JSONObject root = new JSONObject(json);
        String tag = root.getString("tag");
        JSONObject app = root.getJSONObject("android").getJSONObject(appId);
        String version = app.getString("versionName");
        Object value = app.get("versionCode");
        if (!tag.matches("v[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")
                || !version.matches("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")
                || !(value instanceof Integer) || ((Integer) value) < 1) {
            throw new IOException("Invalid stable release metadata");
        }
        return new Result((Integer) value, version, RELEASE + tag);
    }

    private static String stableMetadata() throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(METADATA).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Update metadata unavailable");
            long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                for (int length; (length = input.read(buffer)) != -1; ) {
                    if (output.size() + length > MAX_BYTES || SystemClock.elapsedRealtime() >= deadline) {
                        throw new IOException("Update metadata exceeded request bounds");
                    }
                    output.write(buffer, 0, length);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}

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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.ObjLongConsumer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

/** Checks public stable release metadata and offers verified Android updates. */
public final class AboutUpdates {
    private static final String METADATA = "https://abdelrahmanhajjaj.github.io/MiqatTV/updates.json";
    private static final String DOWNLOAD = "https://github.com/AbdelrahmanHajjaj/MiqatTV/releases/download/";
    private static final int MAX_BYTES = 4096;
    private static final int TIMEOUT_MS = 5000;

    public record Labels(String title, String checking, String current, String available,
                         String failed, String install, String downloading, String installFailed, String close) {}
    record Result(long code, String version, String url) {}

    private AboutUpdates() {}

    public static long androidTvCode(String tag) throws IOException, JSONException {
        Result result = read(stableMetadata(), "tv.miqat.home");
        if (!result.url().equals(DOWNLOAD + tag + "/miqat.apk"))
            throw new IOException("Stable package and version metadata disagree");
        return result.code();
    }

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
    static long code(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    public static AlertDialog show(Activity activity, AlertDialog.Builder builder, boolean rtl, Labels labels) {
        if (!available(activity)) return null;
        AlertDialog dialog = dialog(builder, rtl, labels, labels.checking());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);
        check(activity, (found, installed) -> {
            if (!dialog.isShowing()) return;
            if (found == null) dialog.setMessage(labels.failed());
            else if (found.code() <= installed) dialog.setMessage(labels.current());
            else offer(activity, dialog, found, labels);
        });
        return dialog;
    }

    public static void checkInBackground(Activity activity, AlertDialog.Builder builder, boolean rtl,
                                         Labels labels, BooleanSupplier foreground, Consumer<AlertDialog> presented) {
        if (!available(activity)) return;
        check(activity, (found, installed) -> {
            AlertDialog available = showAvailable(activity, builder, rtl, labels, found, installed, foreground);
            if (available != null) presented.accept(available);
        });
    }

    static AlertDialog showAvailable(Activity activity, AlertDialog.Builder builder, boolean rtl, Labels labels,
                                     Result found, long installed, BooleanSupplier foreground) {
        if (found == null || found.code() <= installed || !foreground.getAsBoolean()) return null;
        AlertDialog available = dialog(builder, rtl, labels, labels.available());
        offer(activity, available, found, labels);
        return available;
    }

    private static AlertDialog dialog(AlertDialog.Builder builder, boolean rtl, Labels labels, String message) {
        AlertDialog dialog = builder.setTitle(labels.title()).setMessage(message)
                .setNegativeButton(labels.close(), null).setPositiveButton(labels.install(), null).show();
        dialog.getWindow().getDecorView().setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        return dialog;
    }

    private static void offer(Activity activity, AlertDialog dialog, Result update, Labels labels) {
        dialog.setMessage(labels.available() + " · \u2066" + update.version() + "\u2069");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> download(activity, dialog, update, labels));
    }

    private static void check(Activity activity, ObjLongConsumer<Result> finished) {
        PackageInfo installed = AboutMiqat.packageInfo(activity);
        new Thread(() -> {
            Result result;
            try {
                result = read(stableMetadata(), installed.packageName);
            } catch (IOException | JSONException invalid) {
                result = null;
            }
            Result found = result;
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) finished.accept(found, code(installed));
            });
        }, "miqat-update-check").start();
    }

    private static void download(Activity activity, AlertDialog dialog, Result update, Labels labels) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.setMessage(labels.downloading());
        Thread job = new Thread(() -> {
            int session;
            try {
                session = UpdatePackage.stage(activity.getApplicationContext(), update);
            } catch (IOException | PackageManager.NameNotFoundException | RuntimeException failure) {
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) installFailed(dialog, labels);
                });
                return;
            }
            activity.runOnUiThread(() -> {
                PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
                if (activity.isFinishing() || activity.isDestroyed() || !dialog.isShowing()
                        || !dialog.getWindow().getDecorView().hasWindowFocus()) {
                    installer.abandonSession(session);
                    dialog.dismiss();
                    return;
                }
                try {
                    UpdatePackage.confirm(activity, session, labels.installFailed());
                    dialog.dismiss();
                } catch (IOException | RuntimeException failure) {
                    installer.abandonSession(session);
                    installFailed(dialog, labels);
                }
            });
        }, "miqat-update-download");
        dialog.setOnDismissListener(ignored -> job.interrupt());
        job.start();
    }

    private static void installFailed(AlertDialog dialog, Labels labels) {
        if (!dialog.isShowing()) return;
        dialog.setMessage(labels.installFailed());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
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
        String asset = switch (appId) {
            case "tv.miqat.companion" -> "miqat-phone.apk";
            case "tv.miqat.home" -> "miqat.apk";
            default -> throw new IOException("Unknown Android update package");
        };
        return new Result((Integer) value, version, DOWNLOAD + tag + "/" + asset);
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

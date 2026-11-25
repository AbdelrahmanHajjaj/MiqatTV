package tv.miqat.companion.about;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntConsumer;
import javax.net.ssl.HttpsURLConnection;

final class UpdatePackage {
    static final int INDETERMINATE = -1;
    static final int VERIFYING = -2;
    private static final int MAX_SIZE = 100 * 1024 * 1024;
    private static final int TIMEOUT_MS = 15_000;

    private UpdatePackage() {}

    static int stage(Context context, AboutUpdates.Result update, IntConsumer progress)
            throws IOException, PackageManager.NameNotFoundException {
        Path apk = Files.createTempFile(context.getCacheDir().toPath(), "miqat-update-", ".apk");
        try {
            download(new URL(update.url()), apk, progress);
            progress.accept(VERIFYING);
            return stage(context, apk, update);
        } finally {
            try {
                Files.deleteIfExists(apk);
            } catch (IOException failure) {
                Log.w("MiqatUpdate", "Could not delete temporary APK", failure);
            }
        }
    }

    static int stage(Context context, Path apk, AboutUpdates.Result update) throws IOException, PackageManager.NameNotFoundException {
        verify(context, apk, update);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        long size = Files.size(apk);
        params.setSize(size);
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id);
             OutputStream output = session.openWrite("base.apk", 0, size)) {
            Files.copy(apk, output);
            session.fsync(output);
            return id;
        } catch (IOException | RuntimeException failure) {
            installer.abandonSession(id);
            throw failure;
        }
    }

    static void confirm(Activity activity, int id, String failureText) throws IOException {
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        Intent result = new Intent(activity, UpdateConfirmation.class).putExtra(UpdateConfirmation.FAILURE_TEXT, failureText);
        int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent status = PendingIntent.getActivity(activity, id, result, flags);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            session.commit(status.getIntentSender());
        } catch (IOException | RuntimeException failure) {
            status.cancel();
            throw failure;
        }
    }

    @SuppressWarnings("deprecation")
    private static void verify(Context context, Path apk, AboutUpdates.Result update)
            throws IOException, PackageManager.NameNotFoundException {
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
        PackageInfo candidate = manager.getPackageArchiveInfo(apk.toString(), flags);
        if (candidate == null || !installed.packageName.equals(candidate.packageName)
                || AboutUpdates.code(candidate) != update.code() || AboutUpdates.code(candidate) <= AboutUpdates.code(installed)
                || !update.version().equals(candidate.versionName) || !sameSigner(installed, candidate)) {
            throw new IOException("Update APK does not match this installed app and release");
        }
    }

    private static boolean sameSigner(PackageInfo installed, PackageInfo candidate) {
        Signature[] current = signers(installed), next = signers(candidate);
        return current != null && next != null && current.length == 1 && next.length == 1
                && current[0].equals(next[0]);
    }

    @SuppressWarnings("deprecation")
    private static Signature[] signers(PackageInfo info) {
        if (Build.VERSION.SDK_INT < 28) return info.signatures;
        return info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
    }

    static boolean trustedHost(URL url) {
        return url.getProtocol().equals("https") && url.getPort() == -1 && url.getUserInfo() == null
                && (url.getHost().equals("github.com") || url.getHost().equals("release-assets.githubusercontent.com")
                    || url.getHost().equals("objects.githubusercontent.com"));
    }

    private static void download(URL url, Path apk, IntConsumer progress) throws IOException {
        long deadline = SystemClock.elapsedRealtime() + 180_000;
        for (int hop = 0; hop <= 5; hop++) {
            checkTime(deadline);
            if (!trustedHost(url)) throw new IOException("Untrusted update download host");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            try {
                int status = connection.getResponseCode();
                if (status == 200) {
                    long expected = connection.getContentLengthLong();
                    if (expected > MAX_SIZE) throw new IOException("Update APK is too large");
                    try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(apk)) {
                        copyDownload(input, output, expected, deadline, progress);
                    }
                    return;
                }
                String location = connection.getHeaderField("Location");
                if (status < 300 || status >= 400 || location == null) throw new IOException("Update download unavailable");
                url = new URL(url, location);
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many update redirects");
    }

    static void copyDownload(InputStream input, OutputStream output, long expected, long deadline,
                             IntConsumer progress) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long bytes = 0;
        int previous = expected > 0 ? 0 : INDETERMINATE;
        progress.accept(previous);
        for (int length; (length = input.read(buffer)) != -1; ) {
            checkTime(deadline);
            bytes += length;
            if (bytes > MAX_SIZE) throw new IOException("Update APK is too large");
            if (expected >= 0 && bytes > expected) throw new IOException("Update APK exceeds response length");
            output.write(buffer, 0, length);
            if (expected <= 0) continue;
            int percent = (int) (bytes * 100 / expected);
            if (percent != previous) {
                progress.accept(percent);
                previous = percent;
            }
        }
        if (bytes == 0 || expected >= 0 && bytes != expected) throw new IOException("Incomplete update APK");
    }

    private static void checkTime(long deadline) throws IOException {
        if (Thread.currentThread().isInterrupted() || SystemClock.elapsedRealtime() >= deadline) {
            throw new InterruptedIOException("Update download stopped");
        }
    }
}

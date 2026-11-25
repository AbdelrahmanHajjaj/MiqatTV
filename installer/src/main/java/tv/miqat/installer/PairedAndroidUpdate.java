package tv.miqat.installer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import io.github.muntashirakon.adb.AdbPairingRequiredException;

/** Updates only Miqat on a TV previously authorized by the phone installer. */
public final class PairedAndroidUpdate implements AutoCloseable {
    public record Version(String name, long code) {}
    private final Adb adb;
    private final Tv tv;

    public static boolean remembered(Context context, String host, String previousHost) {
        return (settings(context).contains("port-" + host) || host.equals(previousHost))
                && new File(context.getFilesDir(), "adb.key").isFile()
                && new File(context.getFilesDir(), "adb.crt").isFile();
    }

    static void remember(Context context, String host, int port, Adb adb) throws IOException, InterruptedException {
        if (host == null || port <= 0) return;
        String serial = serial(adb);
        settings(context).edit().putInt("port-" + host, port).putString("serial-" + host, serial).apply();
    }

    public PairedAndroidUpdate(Context context, String host, String previousHost) throws Exception {
        if (!remembered(context, host, previousHost)) throw new IOException("No remembered Android TV connection");
        int port = settings(context).getInt("port-" + host, 0);
        adb = new Adb(context);
        tv = new Tv(adb);
        adb.setTimeout(7, TimeUnit.SECONDS);
        try {
            if (!connect(host, port)) {
                int discovered = wirelessPort(context, host);
                if (discovered != port && connect(host, discovered)) port = discovered;
                else if (port != Finder.CLASSIC_PORT && connect(host, Finder.CLASSIC_PORT)) port = Finder.CLASSIC_PORT;
                else throw new IOException("TV debugging is unavailable");
            }
            String expected = settings(context).getString("serial-" + host, "");
            if (!expected.isEmpty() && !expected.equals(serial(adb))) throw new IOException("TV debugging identity changed");
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            remember(context, host, port, adb);
        } catch (Exception failure) {
            try { close(); } catch (IOException closing) { failure.addSuppressed(closing); }
            throw failure;
        }
    }

    private boolean connect(String host, int port) throws IOException, InterruptedException {
        if (port <= 0) return false;
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        adb.disconnect();
        try {
            return adb.connect(host, port);
        } catch (IOException | AdbPairingRequiredException unavailable) {
            return false;
        }
    }

    private static int wirelessPort(Context context, String host) throws InterruptedException {
        CountDownLatch found = new CountDownLatch(1);
        AtomicInteger port = new AtomicInteger();
        Finder finder = new Finder(context, candidates -> {
            for (Finder.Tv candidate : candidates)
                if (host.equals(candidate.host) && candidate.tls > 0) {
                    port.set(candidate.tls);
                    found.countDown();
                    return;
                }
        });
        try {
            finder.startWireless();
            found.await(5, TimeUnit.SECONDS);
            return port.get();
        } finally {
            finder.stop();
        }
    }

    public Version version() throws IOException, InterruptedException {
        String name = tv.miqatVersion();
        long code = tv.miqatVersionCode();
        if (name == null || code < 1) throw new IOException("Miqat is missing from the paired Android TV");
        return new Version(name, code);
    }

    @SuppressWarnings("deprecation")
    public void install(Context context, File file, Version expected, IntConsumer progress) throws Exception {
        PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(file.getPath(), 0);
        long code = archive == null ? -1 : android.os.Build.VERSION.SDK_INT >= 28
                ? archive.getLongVersionCode() : archive.versionCode;
        if (archive == null || !Setup.PACKAGE.equals(archive.packageName)
                || !expected.name().equals(archive.versionName) || code != expected.code())
            throw new IOException("Miqat Android TV package disagrees with the selected release");
        if (code <= version().code()) throw new IOException("A newer version is required");
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        String result = adb.install(file, fraction -> progress.accept(Math.round(100 * fraction)));
        if (!result.startsWith("Success") || !version().equals(expected))
            throw new IOException("The TV did not confirm the Miqat update");
    }

    private static String serial(Adb adb) throws IOException, InterruptedException {
        String value = adb.shell("getprop ro.serialno").out;
        return value.matches("[A-Za-z0-9_-]{4,80}") && !value.equalsIgnoreCase("unknown") ? value : "";
    }

    private static SharedPreferences settings(Context context) {
        return context.getSharedPreferences("android-tv-installation", Context.MODE_PRIVATE);
    }

    @Override public void close() throws IOException { adb.disconnect(); }
}

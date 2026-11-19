package tv.miqat.companion;

import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.widget.*;

import tv.miqat.installer.PackageImport;
import tv.miqat.installer.PublicRelease;

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
    private String version = "", message = "lg_intro";
    private LgPackage selected;
    private boolean preview, local, downloading;
    private final String channel;
    private volatile boolean destroyed;

    LgSetup(MainActivity activity) {
        this(activity, "latest");
    }

    LgSetup(MainActivity activity, String channel) {
        this(activity, channel, false);
    }

    LgSetup(MainActivity activity, String channel, boolean local) {
        a = activity;
        this.channel = channel;
        this.local = local;
    }

    void save(android.os.Bundle state) {
        state.putString("lg-selected-version", selected == null ? "" : selected.version);
        state.putBoolean("lg-preview", preview);
        state.putBoolean("lg-local", local);
    }

    void restore(android.os.Bundle state) {
        local = state.getBoolean("lg-local", local);
        String version = state.getString("lg-selected-version", "");
        if (!version.isEmpty())
            try {
                LgPackage pkg = LgPackage.read(new File(a.getCacheDir(), "lg-selected.ipk"));
                if (pkg.version.equals(version)) {
                    selected = pkg;
                    preview = state.getBoolean("lg-preview");
                }
            } catch (IOException invalid) {
                message = "lg_no_package";
            }
    }

    void render(LinearLayout parent) {
        LinearLayout source = a.views.card(parent);
        source.addView(
                a.views.text(a.t(local ? "tv_local" : "tv_channel_" + channel), 14, a.accent));
        source.addView(
                a.views.text(
                        selected == null
                                ? a.t("lg_no_package")
                                : "Miqat " + selected.version + " · IPK",
                        16,
                        a.ink));
        if (preview) source.addView(a.views.text(a.t("tv_public_preview"), 13, a.muted));
        if (!local && selected == null)
            source.addView(a.views.text(a.t("lg_download_after_connect"), 13, a.muted));
        if (message.startsWith("tv_public_")) {
            source.addView(a.views.text(a.t(message), 14, a.accent));
            a.views.button(source, a.t("tv_public_download"), this::download).setTag("lg.download");
        }
        a.views
                .button(source, a.t("tv_change_build"), () -> a.navigate("tv-setup"))
                .setTag("setup.source.change");
        LinearLayout connect = a.views.card(parent);
        String saved = a.prefs().getString("lg-host", "");
        EditText address = field(connect, a.t("lg_address"), saved, false);
        address.setTag("lg.host");
        EditText pass = field(connect, a.t("lg_passphrase"), "", true);
        pass.setTag("lg.passphrase");
        connect.addView(a.views.text(a.t("lg_session_keys"), 12, a.muted));
        a.views
                .button(
                        connect,
                        a.t(connected ? "reconnect" : "lg_connect"),
                        () ->
                                connect(
                                        address.getText().toString().trim(),
                                        pass.getText().toString()))
                .setTag("lg.connect");
        if (local && selected == null) connect.findViewWithTag("lg.connect").setEnabled(false);
        connect.addView(a.views.text(a.t(message), 14, a.accent));
        if (connected) {
            connect.addView(
                    a.views.text(
                            version.isEmpty() ? a.t("lg_not_installed") : "Miqat " + version,
                            16,
                            a.ink));
            if (selected != null)
                a.views
                        .button(
                                connect,
                                a.t("lg_install"),
                                () ->
                                        a.views
                                                .dialog()
                                                .setTitle(a.t("lg_install"))
                                                .setMessage(
                                                        a.t("lg_install_confirm")
                                                                + "\nMiqat "
                                                                + selected.version)
                                                .setNegativeButton(a.t("cancel"), null)
                                                .setPositiveButton(
                                                        a.t("lg_install"),
                                                        (dialog, which) -> install())
                                                .show())
                        .setTag("lg.install");
            if (!version.isEmpty())
                a.views
                        .button(
                                connect,
                                a.t("lg_launch"),
                                () ->
                                        run(
                                                () -> {
                                                    client.launch();
                                                    return true;
                                                },
                                                ignored -> {
                                                    message = "lg_launched";
                                                    a.render();
                                                }))
                        .setTag("lg.launch");
            if (!version.isEmpty())
                a.views.button(
                        connect,
                        a.t("pair"),
                        () -> {
                            close();
                            a.navigate("pair");
                        });
        }
        a.views.button(
                parent,
                a.t("lg_forget_key"),
                () ->
                        a.views
                                .dialog()
                                .setMessage(a.t("lg_forget_key_info"))
                                .setNegativeButton(a.t("cancel"), null)
                                .setPositiveButton(
                                        a.t("lg_forget_key"),
                                        (d, w) -> {
                                            close();
                                            a.prefs().edit().remove("lg-key-" + saved).apply();
                                            message = "lg_intro";
                                            a.render();
                                        })
                                .show());
    }

    private EditText field(LinearLayout parent, String label, String value, boolean secret) {
        parent.addView(a.views.text(label, 13, a.muted));
        EditText field = new EditText(a);
        field.setSingleLine(true);
        field.setText(value);
        field.setTextColor(a.ink);
        field.setTextSize(16);
        field.setTypeface(a.regular);
        field.setTextDirection(View.TEXT_DIRECTION_LTR);
        field.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | (secret
                                ? InputType.TYPE_TEXT_VARIATION_PASSWORD
                                : InputType.TYPE_TEXT_VARIATION_URI));
        field.setSaveEnabled(false);
        field.setContentDescription(label);
        parent.addView(field, new LinearLayout.LayoutParams(-1, a.views.dp(56)));
        return field;
    }

    private void connect(String address, String phrase) {
        final String host;
        try {
            host = LgInstall.host(address);
        } catch (IOException invalid) {
            message = "lg_invalid_host";
            a.render();
            return;
        }
        a.prefs().edit().putString("lg-host", host).apply();
        String pinned = a.prefs().getString("lg-key-" + host, "");
        close();
        destroyed = false;
        final byte[] pass = phrase.getBytes(StandardCharsets.UTF_8);
        run(
                () -> {
                    LgInstall transport = new LgInstall();
                    client = transport;
                    try {
                        if (destroyed) throw new IOException("Setup closed");
                        transport.connect(host, pinned, pass);
                        return new String[] {"", transport.installedVersion()};
                    } catch (LgInstall.Trust trust) {
                        transport.close();
                        return new String[] {trust.key, ""};
                    } finally {
                        Arrays.fill(pass, (byte) 0);
                    }
                },
                result -> {
                    if (!result[0].isEmpty()) {
                        String fingerprint;
                        try {
                            fingerprint = LgInstall.fingerprint(result[0]);
                        } catch (Exception invalid) {
                            message = "lg_failed";
                            a.render();
                            return;
                        }
                        a.views
                                .dialog()
                                .setTitle(a.t("lg_trust"))
                                .setMessage(
                                        a.t("lg_trust_info") + "\n\n" + host + "\n" + fingerprint)
                                .setNegativeButton(a.t("cancel"), null)
                                .setPositiveButton(
                                        a.t("lg_trust"),
                                        (d, w) -> {
                                            a.prefs()
                                                    .edit()
                                                    .putString("lg-key-" + host, result[0])
                                                    .apply();
                                            message = "lg_enter_passphrase";
                                            a.render();
                                        })
                                .show();
                    } else {
                        connected = true;
                        version = result[1];
                        message = "lg_connected";
                    }
                    a.render();
                    if (connected && selected == null && !local) download();
                });
    }

    void cancellation(LinearLayout shell) {
        if (!downloading) return;
        LinearLayout actions = a.views.horizontal(shell);
        actions.setPadding(a.views.dp(20), 0, a.views.dp(20), 0);
        a.views.button(actions, a.t("cancel"), this::cancelDownload).setTag("lg.download.cancel");
    }

    private void cancelDownload() {
        if (!downloading) return;
        downloading = false;
        selected = null;
        preview = false;
        a.cancelNetwork();
        message = "tv_public_cancelled";
        a.render();
    }

    private void download() {
        if (a.networkBusy) return;
        downloading = true;
        a.networkMessage = "tv_public_download";
        message = "tv_public_downloading";
        a.network(
                () -> {
                    try {
                        PublicRelease.Asset asset = PublicRelease.latest(true, channel);
                        File file = asset.download(a.getCacheDir(), (done, total) -> {});
                        LgPackage.read(file);
                        File cached = new File(a.getCacheDir(), "lg-selected.ipk");
                        Files.move(
                                file.toPath(),
                                cached.toPath(),
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                        return new Object[] {LgPackage.read(cached), asset.preview};
                    } catch (PublicRelease.Unavailable unavailable) {
                        return null;
                    }
                },
                result -> {
                    downloading = false;
                    if (result == null) message = "tv_public_unavailable";
                    else {
                        selected = (LgPackage) result[0];
                        preview = (Boolean) result[1];
                        message = "lg_package_ready";
                    }
                    a.render();
                },
                () -> {
                    downloading = false;
                    message = "tv_public_failed";
                    a.render();
                });
    }

    void select(Uri uri) {
        close();
        run(
                () -> {
                    File file =
                            PackageImport.copy(
                                    a.getContentResolver().openInputStream(uri),
                                    new File(a.getCacheDir(), "lg-selected.ipk"),
                                    LgPackage.MAX_BYTES,
                                    LgPackage::read);
                    return LgPackage.read(file);
                },
                pkg -> {
                    selected = pkg;
                    local = true;
                    preview = false;
                    message = "lg_package_ready";
                    a.render();
                });
    }

    private void install() {
        LgPackage pkg = selected;
        if (!connected || pkg == null) return;
        try {
            if (!version.isEmpty() && LgPackage.compareVersions(pkg.version, version) < 0) {
                message = "lg_downgrade";
                a.render();
                return;
            }
        } catch (IOException invalid) {
            message = "lg_failed";
            a.render();
            return;
        }
        message = "lg_installing";
        LgInstall transport = client;
        run(
                () -> {
                    transport.install(pkg);
                    return transport.installedVersion();
                },
                installed -> {
                    version = installed;
                    message = "lg_installed";
                    a.render();
                });
    }

    private <T> void run(Callable<T> work, Consumer<T> done) {
        a.network(
                work,
                done,
                () -> {
                    close();
                    message = "lg_failed";
                    a.render();
                });
    }

    void close() {
        destroyed = true;
        if (connected) message = a.networkBusy ? "lg_failed" : "lg_reconnect";
        connected = false;
        LgInstall transport = client;
        if (transport != null) transport.close();
        client = null;
    }
}

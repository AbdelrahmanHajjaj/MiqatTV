package tv.miqat.companion;

import android.net.Uri;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.View;
import android.widget.*;

import tv.miqat.installer.PackageImport;
import tv.miqat.installer.PublicRelease;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Uses the companion's native surfaces/controls; all privileged work is explicit. */
final class LgSetup {
    private final MainActivity a;
    private final LgCredentials credentials;
    private volatile LgInstall client;
    private boolean connected;
    private String address = "";
    private String version = "", message = "lg_intro";
    private LgPackage selected;
    private boolean preview, local, downloading;
    private String channel;
    private String passphrase = "";
    private boolean remember, credentialSaveFailed;
    private String stage;
    private int percent;
    private TextView stageLabel;
    private ProgressBar stageBar;

    LgSetup(MainActivity activity) {
        this(activity, "latest");
    }

    LgSetup(MainActivity activity, String channel) {
        this(activity, channel, false);
    }

    LgSetup(MainActivity activity, String channel, boolean local) {
        a = activity;
        credentials = new LgCredentials(a.prefs());
        address = a.prefs().getString("lg-host", "");
        remember = credentials.has(address);
        this.channel = channel;
        this.local = local;
        a.tvSetup.channel = channel;
    }

    void source(String channel, boolean local) {
        if (this.local != local || !this.channel.equals(channel)) selected = null;
        this.channel = channel;
        this.local = local;
    }

    void save(android.os.Bundle state) {
        state.putString("lg-selected-version", selected == null ? "" : selected.version);
        state.putBoolean("lg-preview", preview);
        state.putBoolean("lg-local", local);
        state.putString("lg-address", address);
    }

    void restore(android.os.Bundle state) {
        local = state.getBoolean("lg-local", local);
        address = state.getString("lg-address", address);
        remember = credentials.has(address.trim());
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
        if (!connected) {
            a.tvSetup.lgGuide();
            EditText host = field(parent, a.t("lg_address"), address, false);
            host.setTag("lg.host");
            EditText phrase = field(parent, a.t("lg_passphrase"), passphrase, true);
            phrase.setTag("lg.passphrase");
            phrase.addTextChangedListener(MainActivity.watcher(value -> passphrase = value));
            Switch keep = a.views.toggle(parent, a.t("lg_remember"), remember, value -> remember = value);
            keep.setTag("lg.remember");
            if (credentials.has(address.trim())) phrase.setHint(a.t("lg_saved_passphrase"));
            host.addTextChangedListener(MainActivity.watcher(value -> {
                boolean changed = !value.trim().equals(address.trim());
                address = value;
                if (changed) {
                    boolean saved = credentials.has(value.trim());
                    keep.setChecked(saved);
                    phrase.setHint(saved ? a.t("lg_saved_passphrase") : "");
                }
            }));
            if (!message.equals("lg_intro"))
                parent.addView(a.views.text(a.t(message), 14, a.accent));
            String hostToForget = a.prefs().getString("lg-host", "");
            if (!a.prefs().getString("lg-key-" + hostToForget, "").isEmpty())
                forgetButton(parent, hostToForget);
            return;
        }
        LinearLayout connect = parent;
        connect.addView(a.views.text(a.t(message), 14, a.accent));
        if (credentialSaveFailed)
            connect.addView(a.views.text(a.t("lg_save_failed"), 14, a.accent));
        if (selected != null)
            connect.addView(a.views.text("Miqat " + selected.version + " · IPK", 16, a.ink));
        if (preview) connect.addView(a.views.text(a.t("tv_public_preview"), 13, a.muted));
        if (message.startsWith("tv_public_"))
            a.views
                    .button(connect, a.t("tv_public_download"), this::download)
                    .setTag("lg.download");
        if (connected) {
            connect.addView(a.views.text(a.t("lg_expiry"), 13, a.muted));
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
                                () -> {
                                    setStage("lg_launch", -1);
                                    LgInstall transport = client;
                                    run(
                                                () -> {
                                                    transport.launch();
                                                    return true;
                                                },
                                                ignored -> {
                                                    message = "lg_launched";
                                                    a.render();
                                                });
                                })
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
        forgetButton(parent, address);
    }

    private void forgetButton(LinearLayout parent, String host) {
        a.views.button(
                parent,
                a.t("lg_forget_key"),
                () ->
                        a.views
                                .dialog()
                                .setMessage(a.t("lg_forget_key_info") + "\n\n" + host)
                                .setNegativeButton(a.t("cancel"), null)
                                .setPositiveButton(
                                        a.t("lg_forget_key"),
                                        (d, w) -> {
                                            close();
                                            credentials.forget(host);
                                            remember = credentials.has(address.trim());
                                            message = "lg_intro";
                                            a.render();
                                        })
                                .show());
    }

    void footer(LinearLayout shell) {
        if (connected || a.networkBusy) return;
        Button next =
                a.views.primaryAction(
                        a.views.actionBar(shell),
                        a.t("lg_connect"),
                        this::connect);
        next.setTag("lg.connect");
        next.setEnabled(!local || selected != null);
        next.setAlpha(next.isEnabled() ? 1f : .45f);
    }

    void back() {
        close();
        a.tvSetup.back();
    }

    private EditText field(LinearLayout parent, String label, String value, boolean secret) {
        parent.addView(a.views.text(label, 13, a.muted));
        EditText field = new EditText(a);
        field.setImeOptions(
                field.getImeOptions() | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);

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

    private void connect() {
        if (a.networkBusy) return;
        final String host;
        try {
            host = LgInstall.host(address.trim());
        } catch (IOException invalid) {
            message = "lg_invalid_host";
            a.render();
            return;
        }
        address = host;
        a.prefs().edit().putString("lg-host", host).apply();
        String pinned = a.prefs().getString("lg-key-" + host, "");
        String phrase = passphrase;
        boolean keep = remember;
        if (client != null) client.close();
        LgInstall transport = new LgInstall();
        client = transport;
        credentialSaveFailed = false;
        message = "lg_intro";
        setStage("lg_connecting", -1);
        a.network(
                () -> {
                    byte[] pass = pinned.isEmpty() ? new byte[0] : phrase.isEmpty()
                            ? credentials.read(host, pinned) : phrase.getBytes(StandardCharsets.UTF_8);
                    byte[] attempt = pass.clone();
                    try {
                        if (client != transport) throw new IOException("Setup closed");
                        try {
                            transport.connect(host, pinned, attempt);
                        } catch (LgInstall.Trust trust) {
                            transport.close();
                            return new String[] {trust.key, ""};
                        }
                        String installed = transport.installedVersion();
                        if (client != transport || Thread.currentThread().isInterrupted())
                            throw new IOException("Setup closed");
                        boolean saved = true;
                        try {
                            if (keep && !phrase.isEmpty()) credentials.save(host, pinned, pass);
                            else if (!keep) credentials.clearPassphrase(host);
                        } catch (Exception unavailable) {
                            saved = false;
                        }
                        return new String[] {"", installed, Boolean.toString(saved)};
                    } finally {
                        Arrays.fill(attempt, (byte) 0);
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
                                            if (client != transport) return;
                                            a.prefs()
                                                    .edit()
                                                    .putString("lg-key-" + host, result[0])
                                                    .apply();
                                            connect();
                                        })
                                .show();
                    } else {
                        connected = true;
                        version = result[1];
                        credentialSaveFailed = !Boolean.parseBoolean(result[2]);
                        passphrase = "";
                        message = "lg_connected";
                    }
                    a.render();
                    if (connected && selected == null && !local) download();
                },
                () -> {
                    if (client != null) client.close();
                    client = null;
                    message = "lg_failed";
                    a.render();
                });
    }

    void progress(LinearLayout shell) {
        stageLabel = a.views.text("", 14, a.accent);
        stageLabel.setTag("lg.progress");
        stageLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        shell.addView(stageLabel);
        stageBar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        stageBar.setIndeterminateTintList(ColorStateList.valueOf(a.accent));
        stageBar.setProgressTintList(ColorStateList.valueOf(a.accent));
        shell.addView(stageBar, new LinearLayout.LayoutParams(-1, a.views.dp(4)));
        setStage(stage, percent);
        if (!downloading) return;
        LinearLayout actions = a.views.horizontal(shell);
        actions.setPadding(a.views.dp(20), 0, a.views.dp(20), 0);
        a.views
                .secondaryAction(actions, a.t("cancel"), this::cancelDownload)
                .setTag("lg.download.cancel");
    }

    private void setStage(String stage, int percent) {
        this.stage = stage;
        this.percent = percent;
        if (!a.networkBusy || stageLabel == null) return;
        String label = a.t(stage == null ? "working" : stage);
        String fraction = NumberFormat.getPercentInstance(a.ar ? Locale.forLanguageTag("ar") : Locale.ENGLISH)
                .format(percent / 100d);
        stageLabel.setText(percent < 0 ? label : label + " · " + fraction);
        stageBar.setVisibility(percent >= 0 || a.views.motionEnabled() ? View.VISIBLE : View.GONE);
        stageBar.setIndeterminate(percent < 0 && a.views.motionEnabled());
        stageBar.setProgress(Math.max(0, percent));
    }

    private void postStage(int generation, String stage, int percent) {
        a.runOnUiThread(() -> {
            if (a.networkBusy && generation == a.networkGeneration && !a.isDestroyed())
                setStage(stage, percent);
        });
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
        setStage("tv_public_lookup", -1);
        int generation = a.networkGeneration + 1;
        String selectedChannel = channel;
        a.network(
                () -> {
                    try {
                        PublicRelease.Asset asset = PublicRelease.latest(true, selectedChannel);
                        postStage(generation, "tv_public_downloading", 0);
                        File file = asset.download(a.getCacheDir(), new PublicRelease.Progress() {
                            private int last;

                            @Override public void on(long done, long total) {
                                int next = (int) (100 * done / total);
                                if (next != last) postStage(generation, "tv_public_downloading", next);
                                last = next;
                                if (done == total) postStage(generation, "tv_public_verifying", -1);
                            }
                        });
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
                        message = "lg_release_ready";
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
        setStage("tv_public_verifying", -1);
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
        setStage("tv_public_verifying", -1);
        int generation = a.networkGeneration + 1;
        LgInstall transport = client;
        run(
                () -> {
                    transport.install(pkg, (stage, percent) -> postStage(generation, stage.label, percent));
                    return pkg.version;
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
        passphrase = "";
        if (connected) message = a.networkBusy ? "lg_failed" : "lg_reconnect";
        connected = false;
        LgInstall transport = client;
        client = null;
        if (transport != null) transport.close();
    }

    void stop() {
        if (a.networkBusy && a.page.equals("lg-setup")) {
            a.cancelNetwork();
            message = downloading ? "tv_public_cancelled" : "lg_failed";
            downloading = false;
            connected = false;
        }
        close();
    }
}

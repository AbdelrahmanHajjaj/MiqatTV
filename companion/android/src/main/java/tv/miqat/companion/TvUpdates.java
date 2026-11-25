package tv.miqat.companion;

import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import tv.miqat.companion.about.AboutUpdates;
import tv.miqat.installer.PairedAndroidUpdate;
import tv.miqat.installer.PublicRelease;

/** Foreground update flow for the currently paired TV. No setup steps run during an update. */
final class TvUpdates {
    private record Check(String installed, PublicRelease.Asset available, long code) {}

    private final MainActivity a;
    private final LgCredentials lgCredentials;
    private volatile LgInstall lgTransport;
    private String channel = "production";
    private String status = "";
    private Check result;
    private TextView progress;

    TvUpdates(MainActivity activity) {
        a = activity;
        lgCredentials = new LgCredentials(a.prefs());
    }

    void open() {
        result = null;
        status = "";
        a.navigate("updates");
        check();
    }

    void render() {
        a.views.segmentedChoices(a.body, "updates.channel", List.of(
                new Choices.Option("production", a.t("tv_channel_production")),
                new Choices.Option("staging", a.t("tv_channel_staging"))), channel, value -> {
            if (channel.equals(value)) return;
            channel = value;
            check();
        });
        if (result != null) a.body.addView(a.views.text("Miqat " + result.installed(), 16, a.ink));
        if (!status.isEmpty()) a.body.addView(a.views.text(a.t(status), 15, a.accent));
        progress = a.views.text(a.networkBusy ? a.t(a.networkMessage) : "", 14, a.muted);
        progress.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        if (a.networkBusy) a.body.addView(progress);
        a.views.button(a.body, a.t("aboutCheckUpdates"), this::check).setTag("tv.update.check");
        Check checked = result;
        if (checked != null && checked.available() != null) {
            a.views.button(a.body, a.t("aboutInstallUpdate"), () -> confirm(checked))
                    .setTag("tv.update.install");
        }
    }

    private void check() {
        LanTv target = a.real();
        if (target == null || a.networkBusy) return;
        boolean lg = target.phone.profile.equals("lg");
        String host = URI.create(target.phone.url).getHost();
        result = null;
        status = "";
        String previousHost = previousAndroidHost();
        String lgPin = savedLgPin(host);
        if (lg ? lgPin.isEmpty() || !lgCredentials.has(host)
                : !PairedAndroidUpdate.remembered(a, host, previousHost)) {
            status = lg ? "tv_update_lg_setup" : "tv_update_android_setup";
            a.render();
            return;
        }
        String chosenChannel = channel;
        a.networkMessage = "aboutCheckingUpdates";
        a.network(() -> {
            verify(target, host);
            PublicRelease.Asset asset;
            try {
                asset = PublicRelease.latest(lg, chosenChannel);
            } catch (PublicRelease.Unavailable unavailable) {
                asset = null;
            }
            if (lg) {
                try (LgInstall transport = new LgInstall()) {
                    openLg(transport, host, lgPin);
                    String installed = transport.installedVersion();
                    if (installed.isEmpty()) throw new IOException("Miqat is missing from the paired LG TV");
                    return new Check(installed, asset != null
                            && LgPackage.compareVersions(asset.version(), installed) > 0 ? asset : null, 0);
                } finally { lgTransport = null; }
            }
            try (PairedAndroidUpdate transport = new PairedAndroidUpdate(a, host, previousHost)) {
                PairedAndroidUpdate.Version installed = transport.version();
                long code = asset == null ? 0 : asset.staging ? asset.stagingAndroidCode() : AboutUpdates.androidTvCode(asset.tag);
                return new Check(installed.name(), code > installed.code() ? asset : null, code);
            }
        }, checked -> {
            result = checked;
            status = checked.available() == null ? "aboutNoUpdate" : "aboutNewUpdate";
            a.render();
        }, () -> failed(lg));
    }

    private void confirm(Check checked) {
        LanTv target = a.real();
        if (target == null || checked != result) return;
        try {
            a.views.dialog().setTitle(a.t("aboutInstallUpdate"))
                    .setMessage(target.phone.name + "\nMiqat " + checked.available().version())
                    .setNegativeButton(a.t("cancel"), null)
                    .setPositiveButton(a.t("aboutInstallUpdate"), (dialog, which) -> install(target, checked))
                    .show();
        } catch (IOException invalid) {
            failed(target.phone.profile.equals("lg"));
        }
    }

    private void install(LanTv target, Check checked) {
        if (a.real() != target || checked != result || a.networkBusy) return;
        boolean lg = target.phone.profile.equals("lg");
        String host = URI.create(target.phone.url).getHost();
        String previousHost = previousAndroidHost();
        String lgPin = savedLgPin(host);
        PublicRelease.Asset asset = checked.available();
        a.networkMessage = "tv_public_downloading";
        int generation = a.networkGeneration + 1;
        a.network(() -> {
            int[] last = {-1};
            File file = asset.download(a.getCacheDir(), (done, total) -> {
                int percent = (int) (100 * done / total);
                if (last[0] == percent) return;
                last[0] = percent;
                progress(generation, "tv_public_downloading", percent);
            });
            verify(target, host);
            if (lg) {
                LgPackage pkg = LgPackage.read(file);
                if (!pkg.version.equals(asset.version())) throw new IOException("IPK release version mismatch");
                try (LgInstall transport = new LgInstall()) {
                    openLg(transport, host, lgPin);
                    String installed = transport.installedVersion();
                    if (installed.isEmpty() || LgPackage.compareVersions(pkg.version, installed) <= 0)
                        throw new IOException("A newer LG version is required");
                    requireActive();
                    transport.install(pkg, (stage, percent) -> progress(generation, stage.label, percent));
                } finally { lgTransport = null; }
            } else {
                try (PairedAndroidUpdate transport = new PairedAndroidUpdate(a, host, previousHost)) {
                    transport.install(a, file, new PairedAndroidUpdate.Version(asset.version(), checked.code()),
                            percent -> progress(generation, "lg_sending", percent));
                }
            }
            return asset.version();
        }, version -> {
            result = new Check(version, null, 0);
            status = "tv_update_installed";
            a.render();
        }, () -> failed(lg));
    }

    private void openLg(LgInstall transport, String host, String pin) throws Exception {
        lgTransport = transport;
        requireActive();
        lgCredentials.connectSaved(transport, host, pin);
        requireActive();
    }

    private static void verify(LanTv target, String host) throws Exception {
        requireActive();
        if (!target.connect() || !host.equals(URI.create(target.phone.url).getHost()))
            throw new IOException("Could not authenticate the paired TV");
        requireActive();
    }

    private static void requireActive() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }

    private String savedLgPin(String host) { return a.prefs().getString("lg-key-" + host, ""); }

    private String previousAndroidHost() {
        return a.getSharedPreferences("SetupActivity", MainActivity.MODE_PRIVATE).getString("last_tv", "");
    }

    private void failed(boolean lg) {
        result = null;
        status = lg ? "tv_update_lg_failed" : "tv_update_android_failed";
        a.render();
    }

    private void progress(int generation, String label, int percent) {
        a.runOnUiThread(() -> {
            if (a.networkGeneration != generation || !a.networkBusy || a.isDestroyed() || progress == null) return;
            progress.setText(a.t(label) + (percent < 0 ? "" : " · " + NumberFormat
                    .getPercentInstance(a.ar ? Locale.forLanguageTag("ar") : Locale.ENGLISH)
                    .format(percent / 100d)));
        });
    }

    void stop() {
        if (!a.page.equals("updates") || !a.networkBusy) return;
        a.cancelNetwork();
        LgInstall transport = lgTransport;
        if (transport != null) transport.close();
        status = "tv_update_interrupted";
        result = null;
    }
}

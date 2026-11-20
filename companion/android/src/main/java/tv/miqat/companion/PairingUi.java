package tv.miqat.companion;

import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

/** Foreground approval journey and polling; transport/credential storage remain separate. */
final class PairingUi {
    private final MainActivity a;
    PairRequest pairing;
    String pairState = "", pairCode = "";
    private final android.os.Handler polling =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private int pairGeneration;

    PairingUi(MainActivity activity) {
        a = activity;
    }

    private static final class Session {
        final PairRequest request;
        final String state;

        Session(PairRequest request, String state) {
            this.request = request;
            this.state = state;
        }
    }

    Object retain() {
        return pairing == null ? null : new Session(pairing, pairState);
    }

    void resume(Object retained) {
        if (!(retained instanceof Session) || !a.page.equals("pair")) return;
        Session session = (Session) retained;
        pairing = session.request;
        pairState = session.state;
        pairCode = pairing.code;
        if (pairState.equals("requesting"))
            a.getWindow().getDecorView().post(() -> requestPairing(pairing));
        else a.render();
    }

    void pause() {
        polling.removeCallbacksAndMessages(null);
    }

    void render() {
        if (pairState.equals("requesting") || pairState.equals("waiting")) {
            boolean waiting = pairState.equals("waiting");
            LinearLayout progress = a.views.card(a.body);
            progress.setTag("pair.progress");
            pairStep(progress, a.t("pair_found"), true, false);
            pairStep(progress, a.t("pair_verify"), waiting, !waiting);
            pairStep(progress, a.t("pair_approve"), false, waiting);
            if (waiting) {
                a.body.addView(a.views.text(a.t("pair_match"), 16, a.ink));
                TextView code = a.views.text(pairCode, 44, a.accent);
                code.setTag("pair.code");
                code.setLetterSpacing(.16f);
                code.setTextDirection(View.TEXT_DIRECTION_LTR);
                code.setGravity(Gravity.CENTER);
                code.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                a.body.addView(code);
            }
            TextView status =
                    a.views.text(a.t(waiting ? "pair_waiting" : "pair_connecting"), 15, a.muted);
            status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            a.body.addView(status);
            Button cancel =
                    a.views.secondaryAction(
                            a.views.secondaryActions(a.views.actionBar(a.actionHost)),
                            a.t("cancel"),
                            () -> {
                                cancelPairing();
                                a.render();
                            });
            a.views.leadingIcon(cancel, "close");
            cancel.setTag("pair.cancel");
            return;
        }
        a.views.numberedSteps(a.body, a.t("tv_pair_steps"), "pair.instruction");
        a.body.addView(a.views.text(a.t("phone_name"), 13, a.muted));
        EditText name = new EditText(a);
        name.setImeOptions(
                name.getImeOptions() | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);

        name.setSingleLine(true);
        name.setText(a.prefs().getString("phone-name", android.os.Build.MODEL));
        name.setHint(a.t("phone_name"));
        name.setTextColor(a.ink);
        name.setHintTextColor(a.muted);
        name.setPadding(a.views.dp(16), a.views.dp(12), a.views.dp(16), a.views.dp(12));
        name.setBackground(a.views.background(a.surface, 14));
        a.body.addView(name);
        Button scan =
                a.views.bindAction(
                        a.views.primaryAction(
                                a.views.actionBar(a.actionHost),
                                a.t("scan_qr"),
                                () -> {
                                    String value = name.getText().toString().trim();
                                    if (value.isEmpty() || value.length() > 64) {
                                        name.setError(a.t("invalid"));
                                        return;
                                    }
                                    a.prefs().edit().putString("phone-name", value).apply();
                                    cancelPairing();
                                    a.startActivityForResult(
                                            new android.content.Intent(a, ScanActivity.class), 71);
                                }),
                        "scan-pairing-code");
        scan.setEnabled(!a.vaultFailed);
        scan.setTag("pair.scan");
        a.views.leadingIcon(scan, "scan");
        if (a.prefs().getBoolean("show-demo", false))
            a.views.bindAction(
                    a.views.button(
                            a.body,
                            a.t("simulate_pair"),
                            () -> a.info(a.t("pair"), a.t("pair_done"))),
                    "simulate-pairing");
    }

    void pairStep(LinearLayout parent, String label, boolean complete, boolean active) {
        LinearLayout row = a.views.horizontal(parent);
        row.setMinimumHeight(a.views.dp(56));
        if (active && a.views.motionEnabled()) {
            ProgressBar spinner = new ProgressBar(a);
            spinner.setIndeterminateTintList(ColorStateList.valueOf(a.accent));
            spinner.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(spinner, new LinearLayout.LayoutParams(a.views.dp(24), a.views.dp(24)));
        } else {
            ImageView glyph = new ImageView(a);
            glyph.setImageDrawable(
                    Icons.get(a, complete ? "check" : "tv", complete ? a.accent : a.muted));
            glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(glyph, new LinearLayout.LayoutParams(a.views.dp(24), a.views.dp(24)));
        }
        TextView title = a.views.text(label, 15, complete || active ? a.ink : a.muted);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMarginStart(a.views.dp(16));
        row.addView(title, lp);
    }

    void beginPairing(String qr) {
        cancelPairing();
        a.page = "pair";
        try {
            pairing = new PairRequest(qr, a, a.phones);
            requestPairing(pairing);
        } catch (Exception invalid) {
            a.render();
            a.notice("invalid_qr");
        }
    }

    void requestPairing(PairRequest request) {
        if (request == null) return;
        final int generation = pairGeneration;
        pairState = "requesting";
        a.networkMessage = "pair_connecting";
        String name = a.prefs().getString("phone-name", android.os.Build.MODEL);
        if (name.length() > 64) name = name.substring(0, 64);
        final String phoneName = name;
        a.network(
                () -> {
                    Phones.Phone known = request.reconnect();
                    if (known == null) request.request(phoneName);
                    return known;
                },
                known -> {
                    if (generation != pairGeneration) {
                        a.render();
                        return;
                    }
                    if (known != null) {
                        paired(known);
                        return;
                    }
                    pairState = "waiting";
                    pairCode = request.code;
                    a.render();
                    schedulePoll();
                },
                () -> {
                    if (generation == pairGeneration) {
                        cancelPairing();
                        a.render();
                        a.notice("pair_failed");
                    } else a.render();
                });
    }

    void schedulePoll() {
        if (!a.phoneForeground
                || !a.page.equals("pair")
                || !pairState.equals("waiting")
                || pairing == null) return;
        final int generation = pairGeneration;
        PairRequest request = pairing;
        polling.removeCallbacksAndMessages(null);
        polling.postDelayed(
                () ->
                        a.io.execute(() -> {
                            try {
                                Phones.Phone phone = request.poll();
                                a.runOnUiThread(() -> {
                                    if (a.isDestroyed() || generation != pairGeneration) return;
                                    if (phone != null) paired(phone);
                                    else schedulePoll();
                                });
                            } catch (Exception closed) {
                                a.runOnUiThread(() -> {
                                    if (a.isDestroyed() || generation != pairGeneration) return;
                                    cancelPairing();
                                    a.render();
                                    a.notice("pair_failed");
                                });
                            }
                        }),
                1500);
    }

    void paired(Phones.Phone phone) {
        try {
            a.addPhone(phone);
            a.selected = 2;
            for (int i = 0; i < a.lanTvs.size(); i++)
                if (a.lanTvs.get(i).phone.pin.equals(phone.pin)) a.selected = i + 2;
            a.real().online = true;
            a.settingsUi.snapshot = null;
            a.settingsUi.appearance = null;
            a.settingsUi.clearPending();
            cancelPairing();
            a.page = "overview";
            a.remoteUi.readRemote();
            a.notice("paired");
        } catch (Exception failed) {
            cancelPairing();
            a.render();
            a.notice("vault_failed");
        }
    }

    void cancelPairing() {
        if (pairState.equals("requesting") && a.networkBusy) a.cancelNetwork();
        pairGeneration++;
        polling.removeCallbacksAndMessages(null);
        pairing = null;
        pairState = "";
        pairCode = "";
    }
}

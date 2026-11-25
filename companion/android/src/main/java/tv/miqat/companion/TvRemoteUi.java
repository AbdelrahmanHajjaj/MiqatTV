package tv.miqat.companion;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;

import org.json.*;

/** Foreground remote commands and readback; no cached command is replayed automatically. */
final class TvRemoteUi {
    private final MainActivity a;
    boolean remoteLoading;
    private int remoteGeneration;
    private LanTv remoteTarget;
    private final JSONObject[] demoRemotes = {new JSONObject(), new JSONObject()};

    TvRemoteUi(MainActivity activity) {
        a = activity;
    }

    void invalidate() {
        remoteGeneration++;
        remoteLoading = false;
        remoteTarget = null;
    }

    void renderOverview() {
        renderRemote();
        if (a.real() != null)
            a.views.navigationRow(a.body, "gear", a.t("aboutCheckUpdates"), "", a.tvUpdates::open)
                    .setTag("tv.updates");
        LinearLayout options = a.views.horizontal(a.body);
        options.setGravity(Gravity.END);
        a.views.iconButton(
                options,
                "more",
                a.t("more_actions"),
                () -> {
                    PopupMenu menu = new PopupMenu(a, options);
                    menu.getMenu()
                            .add(a.t("rename"))
                            .setOnMenuItemClickListener(item -> {
                                renameTv();
                                return true;
                            });
                    if (a.real() == null) menu.getMenu()
                            .add(a.t("updates"))
                            .setOnMenuItemClickListener(item -> {
                                a.navigate("updates");
                                return true;
                            });
                    if (a.real() != null) {
                        LanTv target = a.real();
                        menu.getMenu()
                                .add(a.t("forget_tv"))
                                .setOnMenuItemClickListener(item -> {
                                    a.tvListUi.confirmForget(target);
                                    return true;
                                });
                    }
                    menu.show();
                });
        if (a.real() != null || !a.prefs().getBoolean("show-demo", false)) return;
        LinearLayout tools = a.views.card(a.body);
        tools.addView(a.views.text(a.t("demo_tools"), 16, a.ink));
        a.views.button(
                tools,
                a.t(a.tv().getOnline() ? "disconnect" : "reconnect"),
                () -> {
                    a.tv().setOnline(!a.tv().getOnline());
                    a.prefs().edit().putBoolean("online-" + a.selected, a.tv().getOnline()).apply();
                    a.render();
                });
        a.views.button(
                tools,
                a.t("fail_next"),
                () -> {
                    a.tv().setFailNextSave(true);
                    a.notice("fail_armed");
                });
    }

    void readRemote() {
        if (a.real() == null) {
            if (!a.tv().getOnline()) a.tv().setOnline(true);
            a.render();
            return;
        }
        LanTv target = a.real();
        if (remoteLoading && remoteTarget == target) return;
        remoteTarget = target;
        final int generation = ++remoteGeneration;
        remoteLoading = true;
        a.render();
        a.io.execute(() -> {
            try {
                if (!target.online && !target.connect()) throw new java.io.IOException();
                target.readRemote();
                a.runOnUiThread(() -> {
                    if (a.isDestroyed() || generation != remoteGeneration) return;
                    remoteLoading = false;
                    if (a.real() == target && a.page.equals("overview")) a.render();
                });
            } catch (Exception unavailable) {
                a.runOnUiThread(() -> {
                    if (a.isDestroyed() || generation != remoteGeneration) return;
                    remoteLoading = false;
                    if (a.real() == target && a.page.equals("overview")) {
                        a.render();
                        a.notice("remote_failed");
                    }
                });
            }
        });
    }

    JSONObject remoteData() {
        if (a.real() != null) return a.real().remote;
        JSONObject data = demoRemotes[a.selected];
        try {
            if (!data.has("commands"))
                data.put("commands", a.catalog.data.getJSONArray("commands"))
                        .put(
                                "inputs",
                                new JSONArray()
                                        .put(
                                                new JSONObject()
                                                        .put("value", "HDMI_1")
                                                        .put("label", "HDMI 1"))
                                        .put(
                                                new JSONObject()
                                                        .put("value", "HDMI_2")
                                                        .put("label", "HDMI 2")))
                        .put("media", new JSONObject().put("kind", "").put("state", "idle"));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
        return data;
    }

    boolean commandOffered(String id) {
        JSONArray commands = remoteData().optJSONArray("commands");
        if (commands != null)
            for (int i = 0; i < commands.length(); i++)
                if (id.equals(commands.optString(i))) return true;
        return false;
    }

    void sendCommand(String id, String value) {
        if (a.real() == null) {
            try {
                JSONObject data = remoteData();
                if (id.equals("radio.play") || id.equals("tests.adhan-short"))
                    data.put(
                            "media",
                            new JSONObject()
                                    .put("kind", id.equals("radio.play") ? "radio" : "adhan")
                                    .put("state", "playing"));
                if (id.equals("radio.stop") || id.equals("tests.cancel"))
                    data.put("media", new JSONObject().put("kind", "").put("state", "idle"));
                if (id.equals("device.inputs")) data.put("input", value);
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
            a.render();
            a.notice("demo");
            return;
        }
        LanTv target = a.real();
        a.networkMessage = "sending_command";
        a.network(
                () -> {
                    boolean stopping = id.equals("radio.stop") || id.equals("tests.cancel");
                    if (stopping && !target.online && !target.connect())
                        throw new java.io.IOException();
                    JSONObject result = target.command(id, value);
                    if (result.optString("status").equals("accepted"))
                        try {
                            target.readRemote();
                        } catch (Exception disconnected) {
                        }
                    return result;
                },
                reply -> {
                    String status = reply.optString("status");
                    a.render();
                    a.notice(
                            status.equals("accepted")
                                    ? "remote_accepted"
                                    : status.equals("busy")
                                            ? "tv_busy"
                                            : status.equals("uncertain")
                                                    ? "remote_uncertain"
                                                    : "unavailable");
                },
                () -> {
                    a.render();
                    a.notice("remote_uncertain");
                });
    }

    Button commandButton(LinearLayout parent, String id, String icon, String label) {
        Button button = a.views.iconButton(parent, icon, label, () -> sendCommand(id, ""));
        button.setTag(id);
        boolean stopping = id.equals("radio.stop") || id.equals("tests.cancel");
        button.setEnabled(
                !a.networkBusy
                        && commandOffered(id)
                        && (stopping
                                || a.online() && (a.real() == null || !a.real().remoteUncertain)));
        return button;
    }

    void renderRemote() {
        JSONObject state = remoteData();
        JSONArray offered = state.optJSONArray("commands");
        if (a.real() != null && a.real().remoteUncertain)
            a.body.addView(a.views.text(a.t("remote_uncertain"), 14, a.accent));
        if (!a.online()) a.body.addView(a.views.text(a.t("disconnected"), 14, a.accent));
        if (offered == null || offered.length() == 0) {
            a.body.addView(
                    a.views.text(
                            a.t(
                                    remoteLoading
                                            ? "loading_controls"
                                            : offered == null
                                                    ? "remote_failed"
                                                    : "remote_unavailable"),
                            15,
                            a.muted));
            return;
        }
        LinearLayout navigation = a.views.horizontal(a.body);
        ((LinearLayout.LayoutParams) navigation.getLayoutParams()).bottomMargin = a.views.dp(12);
        Button home = commandButton(navigation, "device.home", "mihrab", a.t("tv_home"));
        a.views.flex(home);
        home.setForeground(null);
        home.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf((a.accent & 0x00ffffff) | 0x22000000),
                        a.views.background(a.surface, 24),
                        null));
        home.setText(a.t("tv_home"));
        home.setMinHeight(a.views.dp(88));
        home.setCompoundDrawablesRelative(null, Icons.get(a, "mihrab", a.accent), null, null);
        home.setCompoundDrawablePadding(a.views.dp(12));
        home.setPadding(a.views.dp(12), a.views.dp(8), a.views.dp(12), a.views.dp(8));
        Button inputs = commandButton(navigation, "device.inputs", "input", a.t("tv_inputs"));
        a.views.flex(inputs);
        inputs.setForeground(null);
        inputs.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf((a.accent & 0x00ffffff) | 0x22000000),
                        a.views.background(a.surface, 24),
                        null));
        inputs.setText(a.t("tv_inputs"));
        inputs.setMinHeight(a.views.dp(88));
        inputs.setCompoundDrawablesRelative(null, Icons.get(a, "input", a.accent), null, null);
        inputs.setCompoundDrawablePadding(a.views.dp(12));
        inputs.setPadding(a.views.dp(12), a.views.dp(8), a.views.dp(12), a.views.dp(8));
        inputs.setOnClickListener(v -> {
            JSONArray items = state.optJSONArray("inputs");
            if (items == null || items.length() == 0) {
                sendCommand("device.inputs", "");
                return;
            }
            String[] labels = new String[items.length() + 1];
            labels[0] = a.t("tv_inputs");
            for (int i = 0; i < items.length(); i++)
                labels[i + 1] = items.optJSONObject(i).optString("label");
            ListPopupWindow menu = new ListPopupWindow(a);
            menu.setAnchorView(inputs);
            menu.setModal(true);
            menu.setWidth(a.views.dp(240));
            menu.setHeight(a.views.dp(Math.min(6, labels.length) * 48));
            menu.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_list_item_1, labels));
            menu.setOnItemClickListener((p, view, index, position) -> {
                menu.dismiss();
                sendCommand(
                        "device.inputs",
                        index == 0 ? "" : items.optJSONObject(index - 1).optString("value"));
            });
            menu.show();
        });
        if (commandOffered("device.exit-input"))
            a.views
                    .navigationRow(
                            a.body,
                            "tv",
                            a.t("tv_input"),
                            state.optString("input"),
                            () -> sendCommand("device.exit-input", ""))
                    .setEnabled(a.online() && (a.real() == null || !a.real().remoteUncertain));
        for (String section : new String[] {"radio", "adhan"}) {
            LinearLayout card = a.views.card(a.body);
            card.setPadding(a.views.dp(18), a.views.dp(10), a.views.dp(18), a.views.dp(10));
            LinearLayout row = a.views.horizontal(card);
            ImageView glyph = new ImageView(a);
            glyph.setImageDrawable(
                    Icons.get(a, section.equals("radio") ? "radio" : "bell", a.accent));
            row.addView(glyph, new LinearLayout.LayoutParams(a.views.dp(28), a.views.dp(28)));
            LinearLayout labels = a.views.column();
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMarginStart(a.views.dp(12));
            row.addView(labels, lp);
            labels.addView(
                    a.views.text(
                            a.t(section.equals("radio") ? "radio_title" : "adhan_preview"),
                            16,
                            a.ink));
            LinearLayout controls = a.views.horizontal(row);
            controls.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
            Button play = commandButton(
                    controls,
                    section.equals("radio") ? "radio.play" : "tests.adhan-short",
                    "play",
                    a.t("play")
                            + " · "
                            + a.t(section.equals("radio") ? "radio_title" : "adhan_preview"));
            Button stop = commandButton(
                    controls,
                    section.equals("radio") ? "radio.stop" : "tests.cancel",
                    "stop",
                    a.t("stop")
                            + " · "
                            + a.t(section.equals("radio") ? "radio_title" : "adhan_preview"));
            for (Button button : new Button[] {play, stop}) {
                LinearLayout.LayoutParams spacing = (LinearLayout.LayoutParams) button.getLayoutParams();
                spacing.setMarginStart(a.views.dp(5));
                spacing.setMarginEnd(a.views.dp(5));
                button.setBackground(new RippleDrawable(
                        ColorStateList.valueOf((a.accent & 0xffffff) | 0x33000000),
                        a.views.background(a.ground, 24), null));
            }
        }
    }

    void renameTv() {
        EditText input = new EditText(a);
        input.setImeOptions(
                input.getImeOptions() | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);

        input.setText(a.name(a.selected));
        input.setSingleLine(true);
        input.setHint(a.t("rename"));
        AlertDialog d =
                a.views
                        .dialog()
                        .setTitle(a.t("rename"))
                        .setView(input)
                        .setNegativeButton(a.t("cancel"), null)
                        .setPositiveButton(a.t("done"), null)
                        .create();
        d.setOnShowListener(v ->
                d.getButton(-1)
                        .setOnClickListener(w -> {
                            String n = input.getText().toString().trim();
                            if (n.isEmpty() || n.length() > 60) {
                                input.setError(a.t("invalid"));
                                return;
                            }
                            d.dismiss();
                            if (a.real() == null) {
                                a.prefs().edit().putString("name-" + a.selected, n).apply();
                                a.render();
                            } else {
                                LanTv tv = a.real();
                                a.network(
                                        () -> tv.rename(n),
                                        renamed -> a.render(),
                                        () -> {
                                            a.notice("vault_failed");
                                            a.render();
                                        });
                            }
                        }));
        d.show();
        a.views.styleDialog(d);
    }
}

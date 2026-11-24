package tv.miqat.companion;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

import org.json.*;

/** One entry journey for installation and pairing; transports keep their own guards. */
final class TvSetup {
    private final MainActivity a;
    private String platform = "";
    String channel = "production";
    private int step;
    private boolean local;

    TvSetup(MainActivity activity) {
        a = activity;
    }

    void restore(Bundle state) {
        platform = state.getString("setup-platform", "");
        channel = "staging".equals(state.getString("setup-channel")) ? "staging" : "production";
        step = Math.max(0, Math.min(1, state.getInt("setup-step")));
        if (!(platform.equals("android") || platform.equals("lg"))) {
            platform = "";
            step = 0;
        }
        local = state.getBoolean("setup-local");
    }

    void save(Bundle state) {
        state.putString("setup-platform", platform);
        state.putString("setup-channel", channel);
        state.putInt("setup-step", step);
        state.putBoolean("setup-local", local);
    }

    void open() {
        step = 0;
        platform = "";
        channel = "production";
        local = false;
        a.navigate("tv-setup");
    }

    void back() {
        if (a.lgSetup != null) a.lgSetup.close();
        if (step == 0) a.navigate("tvs");
        else {
            step = 0;
            a.navigate("tv-setup");
        }
    }

    String locationKey() {
        return "tv-setup:" + platform + ":" + step + ":" + local;
    }

    void render() {
        if (step == 0) {
            platforms();
            return;
        }
        guide();
        source();
    }

    private void platforms() {
        JSONArray platforms = a.catalog.data.optJSONArray("setupPlatforms");
        for (int i = 0; i < platforms.length(); i++) {
            JSONObject item = platforms.optJSONObject(i);
            String id = item.optString("id");
            if (!(id.equals("android") || id.equals("lg")) || item.isNull("setup")) continue;
            LinearLayout row =
                    a.views.navigationRow(
                            a.body,
                            "tv",
                            a.catalog.label(item, a.ar),
                            "",
                            () -> {
                                if (!platform.equals(id)) {
                                    channel = "production";
                                    local = false;
                                }
                                platform = id;
                                step = 1;
                                if (id.equals("lg")) {
                                    a.lgSetup = new LgSetup(a, channel, local);
                                    a.navigate("lg-setup");
                                } else a.render();
                            });
            row.setTag("setup.platform." + id);
            row.setContentDescription(a.catalog.label(item, a.ar));
        }
    }

    void lgGuide() {
        platform = "lg";
        step = 1;
        guide();
        source();
    }

    private void guide() {
        a.views.button(a.body, a.t("tv_already"), () -> a.navigate("pair")).setTag("setup.pair");
        a.views.numberedSteps(
                a.body,
                a.t(platform.equals("android") ? "tv_android_steps" : "tv_lg_steps"),
                "setup.instruction");
    }

    void footer(LinearLayout shell) {
        if (step == 0 || local) return;
        a.views
                .primaryAction(a.views.actionBar(shell), a.t("tv_continue"), this::proceed)
                .setTag("setup.continue");
    }

    private void updateSource() {
        if (platform.equals("lg") && a.lgSetup != null) a.lgSetup.source(channel, local);
        a.render();
    }

    private void source() {
        LinearLayout heading = a.views.horizontal(a.body);
        TextView title = a.views.text(a.t("tv_source"), 14, a.muted);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        a.views
                .iconButton(
                        heading,
                        "gear",
                        a.t("phone_advanced"),
                        () -> {
                            local = !local;
                            updateSource();
                        })
                .setTag("setup.advanced");
        if (local) {
            a.body.addView(
                    a.views.text(
                            a.t("tv_local") + " · " + (platform.equals("android") ? "APK" : "IPK"),
                            16,
                            a.ink));
            a.views.button(a.body, a.t("tv_local"), this::chooseFile).setTag("setup.local");
            a.views.button(
                    a.body,
                    a.t("tv_public"),
                    () -> {
                        local = false;
                        updateSource();
                    });
            return;
        }
        java.util.List<Choices.Option> channels = new java.util.ArrayList<>();
        for (String id : new String[] {"production", "staging"})
            channels.add(new Choices.Option(id, a.t("tv_channel_" + id)));
        a.views.segmentedChoices(
                a.body,
                "setup.channel",
                channels,
                channel,
                selected -> {
                    channel = selected;
                    updateSource();
                });
    }

    private void proceed() {
        if (platform.equals("android"))
            a.startActivityForResult(
                    new Intent(a, SetupActivity.class)
                            .putExtra("release-channel", channel)
                            .putExtra("prepared", true),
                    72);
        else {
            a.lgSetup = new LgSetup(a, channel, local);
            a.navigate("lg-setup");
        }
    }

    private void chooseFile() {
        if (platform.equals("android"))
            a.startActivityForResult(
                    new Intent(a, SetupActivity.class)
                            .putExtra("choose-local", true)
                            .putExtra("prepared", true),
                    72);
        else {
            if (a.lgSetup == null) a.lgSetup = new LgSetup(a, channel, local);
            a.lgSetup.source(channel, local);
            a.startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .setType("*/*")
                            .addCategory(Intent.CATEGORY_OPENABLE),
                    73);
        }
    }
}

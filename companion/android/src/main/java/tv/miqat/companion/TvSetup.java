package tv.miqat.companion;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

import org.json.*;

/** One entry journey for installation and pairing; transports keep their own guards. */
final class TvSetup {
    private final MainActivity a;
    private String platform = "";
    String channel = "latest";
    private int step;
    private boolean local;

    TvSetup(MainActivity activity) {
        a = activity;
    }

    void restore(Bundle state) {
        platform = state.getString("setup-platform", "");
        channel = state.getString("setup-channel", "latest");
        step = state.getInt("setup-step");
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
        channel = "latest";
        local = false;
        a.navigate("tv-setup");
    }

    void back() {
        if (step == 0) a.navigate("tvs");
        else {
            step--;
            a.render();
        }
    }

    String locationKey() {
        return "tv-setup:" + platform + ":" + step + ":" + local;
    }

    void render() {
        a.body.addView(
                a.views.text(
                        a.t(
                                step == 0
                                        ? "tv_platform_hint"
                                        : step == 1 ? "tv_guide" : "tv_channel_help"),
                        15,
                        a.muted));
        if (step == 0) {
            platforms();
            return;
        }
        a.body.addView(
                a.views.text(
                        (platform.equals("android") ? "Android / Google TV" : "LG webOS")
                                + " · "
                                + (step + 1)
                                + " / 3",
                        13,
                        a.accent));
        if (step == 1) {
            guide();
            return;
        }
        source();
    }

    private void platforms() {
        JSONArray platforms = a.catalog.data.optJSONArray("setupPlatforms");
        for (int i = 0; i < platforms.length(); i++) {
            JSONObject item = platforms.optJSONObject(i);
            String id = item.optString("id");
            boolean supported = !item.isNull("setup");
            LinearLayout row =
                    a.views.navigationRow(
                            a.body,
                            supported ? "tv" : "lock",
                            a.catalog.label(item, a.ar),
                            supported ? "" : a.t("coming_soon"),
                            () -> {
                                if (!supported) {
                                    a.info(a.t("coming_soon"), a.t("locked_hint"));
                                    return;
                                }
                                platform = id;
                                step = 1;
                                a.render();
                            });
            row.setTag("setup.platform." + id);
            row.setContentDescription(
                    a.catalog.label(item, a.ar) + (supported ? "" : ", " + a.t("coming_soon")));
        }
    }

    private void guide() {
        LinearLayout guide = a.views.card(a.body);
        guide.addView(a.views.heading(a.t("tv_guide")));
        guide.addView(
                a.views.text(
                        a.t(platform.equals("android") ? "tv_android_steps" : "tv_lg_steps"),
                        15,
                        a.ink));
        if (platform.equals("lg")) guide.addView(a.views.text(a.t("lg_expiry"), 13, a.muted));
        a.views
                .button(
                        a.body,
                        a.t("tv_ready"),
                        () -> {
                            step = 2;
                            a.render();
                        })
                .setTag("setup.ready");
        a.views.button(a.body, a.t("tv_already"), () -> a.navigate("pair")).setTag("setup.pair");
    }

    private void source() {
        LinearLayout heading = a.views.horizontal(a.body);
        TextView title = a.views.heading(a.t("tv_source"));
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        a.views
                .iconButton(
                        heading,
                        "gear",
                        a.t("phone_advanced"),
                        () -> {
                            local = !local;
                            a.render();
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
                        a.render();
                    });
            return;
        }
        for (String id : new String[] {"latest", "stable", "preview"}) {
            Button choice =
                    a.views.button(
                            a.body,
                            a.t("tv_channel_" + id),
                            () -> {
                                channel = id;
                                a.render();
                            });
            choice.setSelected(channel.equals(id));
            choice.setTag("setup.channel." + id);
            if (choice.isSelected()) {
                choice.setTextColor(a.ground);
                choice.setBackground(a.views.background(a.accent, 24));
            }
        }
        a.views.button(a.body, a.t("tv_continue"), this::proceed).setTag("setup.continue");
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
            a.lgSetup = new LgSetup(a, channel, local);
            a.navigate("lg-setup");
            a.startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .setType("*/*")
                            .addCategory(Intent.CATEGORY_OPENABLE),
                    73);
        }
    }
}

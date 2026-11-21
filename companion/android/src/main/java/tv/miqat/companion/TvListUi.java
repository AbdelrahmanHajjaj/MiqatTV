package tv.miqat.companion;

import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;

import org.json.*;

/** Paired-TV inventory and explicit forget action; credentials stay in Phones. */
final class TvListUi {
    private final MainActivity a;

    TvListUi(MainActivity activity) {
        a = activity;
    }

    void renderTvs() {
        if (a.vaultFailed) a.body.addView(a.views.text(a.t("vault_failed"), 15, a.accent));
        if (a.lanTvs.isEmpty()) {
            LinearLayout empty = a.views.card(a.body);
            ImageView glyph = new ImageView(a);
            glyph.setImageDrawable(Icons.get(a, "mihrab", a.accent));
            empty.addView(glyph, new LinearLayout.LayoutParams(-1, a.views.dp(72)));
            empty.addView(a.views.heading(a.t("no_tvs")));
        }
        for (int i = 0; i < a.lanTvs.size(); i++) {
            final int index = i + 2;
            LanTv tv = a.lanTvs.get(i);
            LinearLayout row =
                    a.views.navigationRow(
                            a.body,
                            "tv",
                            tv.phone.name,
                            (tv.phone.profile.equals("android") ? "Android TV" : "LG webOS")
                                    + " · "
                                    + a.t(tv.online ? "connected" : "disconnected"),
                            () -> {
                                a.selected = index;
                                a.settingsUi.snapshot = null;
                                a.section = "";
                                a.settingsUi.clearPending();
                                a.page = "overview";
                                a.remoteUi.readRemote();
                                a.render();
                            });
            row.setTag("tv." + tv.key);
            row.removeViewAt(row.getChildCount() - 1);
            Button more =
                    a.views.iconButton(
                            row,
                            "more",
                            a.t("more_actions"),
                            () -> {
                                PopupMenu menu = new PopupMenu(a, row);
                                menu.getMenu()
                                        .add(a.t("forget_tv"))
                                        .setOnMenuItemClickListener(item -> {
                                            confirmForget(tv);
                                            return true;
                                        });
                                menu.show();
                            });
            more.setTag(tv.key + ".forget");
        }
        Button pair = a.views.button(a.body, a.t("pair"), () -> a.tvSetup.open());
        a.views.leadingIcon(pair, "add");
        pair.setTag("pair.open");
        pair.setEnabled(!a.vaultFailed);
        pair.setTextColor(a.ground);
        pair.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf(0x22000000),
                        a.views.background(a.accent, 28),
                        null));
        pair.setCompoundDrawableTintList(ColorStateList.valueOf(a.ground));
        pair.setMinHeight(a.views.dp(56));
        a.views
                .navigationRow(a.body, "tv", a.t("tv_setup"), "", () -> a.tvSetup.open())
                .setTag("install.open");
        if (!a.prefs().getBoolean("show-demo", false)) return;
        a.body.addView(a.views.text(a.t("demo"), 13, a.muted));
        for (int i = 0; i < a.tvs.length; i++) {
            final int index = i;
            a.views.navigationRow(
                    a.body,
                    "tv",
                    a.name(i),
                    (i == 0 ? "Android TV" : "LG webOS")
                            + " · "
                            + a.t(a.tvs[i].getOnline() ? "connected" : "disconnected"),
                    () -> {
                        a.selected = index;
                        a.settingsUi.snapshot = null;
                        a.section = "";
                        a.navigate("overview");
                    });
        }
    }

    void confirmForget(LanTv target) {
        a.views
                .dialog()
                .setTitle(a.t("forget_tv"))
                .setMessage(a.t("forget_tv_info"))
                .setNegativeButton(a.t("cancel"), null)
                .setPositiveButton(a.t("forget_tv"), (dialog, which) -> forgetTv(target))
                .show();
    }

    void forgetTv(LanTv target) {
        LanTv selectedTv = a.real();
        a.network(
                () -> {
                    a.phones.remove(target.phone);
                    return target;
                },
                removed -> {
                    a.remoteUi.invalidate();
                    a.lanTvs.remove(removed);
                    a.prefs().edit().remove("cache-" + removed.key).apply();
                    if (selectedTv == removed) {
                        a.pairingUi.cancelPairing();
                        a.settingsUi.snapshot = null;
                        a.settingsUi.clearPending();
                        a.section = "";
                        a.selected = 0;
                        a.page = "tvs";
                    } else if (selectedTv != null) a.selected = a.lanTvs.indexOf(selectedTv) + 2;
                    a.render();
                },
                () -> {
                    a.render();
                    a.notice("vault_failed");
                });
    }
}

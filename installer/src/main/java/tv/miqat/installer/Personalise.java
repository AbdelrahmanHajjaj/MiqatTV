package tv.miqat.installer;

import android.app.AlertDialog;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Make it yours", after the install: the city and prayer settings, the favourites, the remote's app buttons
 * and the cleanup. Every choice is read from and written to Miqat on the TV (SetupProvider), so its cities and
 * settings stay the one source of truth; on an Miqat without the provider (0.5) only the cleanup runs.
 */
final class Personalise {
    private final MainActivity a;
    private final Tv tv;
    private final boolean ar;

    /** Miqat’s own data, from the TV. */
    private JSONObject info;
    private JSONArray cities, apps;
    /** The choices so far. */
    private String city, locale;
    private boolean adhan, remind;
    private final List<String> favourites = new ArrayList<>();
    /** The remote's app buttons (key without KEYCODE_ to package), read so the cleanup never offers their apps. */
    private final Map<String, String> keys = new LinkedHashMap<>();
    private final Map<String, byte[]> icons = new HashMap<>();
    private int cleanedCount = -1;

    Personalise(MainActivity a, Tv tv) {
        this.a = a;
        this.tv = tv;
        this.ar = Ui.arabic(a);
    }

    void start() {
        a.frame(MainActivity.PERSONAL, Hero.Mode.CITY, a.getString(R.string.personal_title),
                a.getString(R.string.personal_loading));
        a.back = null;
        int g = a.gen;
        a.work.execute(() -> {
            try {
                info = tv.info();
                if (info != null) {
                    cities = tv.list("cities");
                    apps = tv.list("apps");
                    JSONArray k = tv.list("keys");
                    for (int i = 0; i < k.length(); i++) {
                        keys.put(strip(k.getJSONObject(i).getString("key")), k.getJSONObject(i).getString("p"));
                    }
                    city = info.optString("city", "cairo");
                    locale = info.optString("locale", "system");
                    adhan = info.optBoolean("adhan", true);
                    remind = info.optBoolean("remind_voice", true);
                    List<String[]> fav = new ArrayList<>();
                    for (int i = 0; i < apps.length(); i++) {
                        JSONObject app = apps.getJSONObject(i);
                        if (app.optInt("fav", -1) >= 0) fav.add(new String[] {String.valueOf(app.getInt("fav")), app.getString("p")});
                    }
                    fav.sort((x, y) -> Integer.compare(Integer.parseInt(x[0]), Integer.parseInt(y[0])));
                    for (String[] f : fav) favourites.add(f[1]);
                }
                a.post(g, () -> {
                    if (info == null) clean();
                    else prayer();
                });
            } catch (Exception e) {
                a.post(g, this::clean);
            }
        });
    }

    // Prayer times: city, language, adhan

    private void prayer() {
        a.frame(MainActivity.PERSONAL, Hero.Mode.CITY, a.getString(R.string.prayer_title),
                a.getString(R.string.prayer_lead));
        LinearLayout where = Ui.row(a);
        GradientDrawable bg = Ui.round(Ui.palette(a).surface, 18, a);
        bg.setStroke(Ui.dp(a, 1), Ui.alpha(Ui.palette(a).accent, 0x66));
        Ui.pressable(where, bg, Ui.alpha(Ui.palette(a).accent, 0x33));
        int p = Ui.dp(a, 16);
        where.setPadding(p, p, p, p);
        LinearLayout words = Ui.column(a);
        words.addView(Ui.small(a, a.getString(R.string.city_label)));
        JSONObject c = cityById(city);
        words.addView(Ui.text(a, c == null ? city : name(c) + "  ·  " + country(c), Ui.palette(a).ink, 18, Ui.semibold()),
                Ui.wide(a, 4));
        where.addView(words, Ui.fill(a));
        where.addView(Ui.text(a, "›", Ui.palette(a).accent, 26, Ui.medium()));
        where.setOnClickListener(v -> a.go(this::cityPicker));
        a.add(where, 20);

        LinearLayout lang = Ui.card(a);
        lang.addView(Ui.label(a, a.getString(R.string.lang_label)));
        lang.addView(segmented(new String[] {"ar", "en", "system"},
                new int[] {R.string.lang_ar, R.string.lang_en, R.string.lang_tv}, locale, v -> locale = v), Ui.wide(a, 12));
        a.add(lang, 10);

        LinearLayout toggles = Ui.card(a);
        toggles.addView(toggle(R.string.adhan_label, adhan, on -> adhan = on));
        toggles.addView(toggle(R.string.remind_label, remind, on -> remind = on), Ui.wide(a, 12));
        a.add(toggles, 10);

        a.foot(Ui.primary(a, a.getString(R.string.continue_), v -> save(v, () -> tv.set("city", city, "locale", locale,
                "adhan", adhan ? "on" : "off", "remind_voice", remind ? "on" : "off"), this::favourites)));
        a.back = null;
    }

    private void cityPicker() {
        a.frame(MainActivity.PERSONAL, Hero.Mode.CITY, a.getString(R.string.city_title), a.getString(R.string.city_lead));
        EditText search = new EditText(a);
        search.setHint(R.string.city_search);
        search.setHintTextColor(Ui.alpha(Ui.palette(a).muted, 0x80));
        search.setTextColor(Ui.palette(a).ink);
        search.setTextSize(16);
        search.setTypeface(Ui.medium());
        search.setSingleLine();
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setBackground(Ui.round(Ui.palette(a).surface, 14, a));
        search.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
        a.add(search, 20);
        LinearLayout list = Ui.column(a);
        a.add(list, 8);
        Runnable fill = () -> fillCities(list, search.getText().toString());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void onTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void afterTextChanged(Editable e) { fill.run(); }
        });
        fill.run();
        a.foot(Ui.secondary(a, a.getString(R.string.back), v -> a.go(this::prayer)));
        a.back = () -> a.go(this::prayer);
    }

    private void fillCities(LinearLayout list, String query) {
        list.removeAllViews();
        String q = query.trim().toLowerCase(Locale.ROOT);
        String lastCountry = null;
        for (int i = 0; i < cities.length(); i++) {
            JSONObject c = cities.optJSONObject(i);
            if (c == null) continue;
            String hay = (c.optString("ar") + " " + c.optString("en") + " " + c.optString("countryAr") + " "
                    + c.optString("countryEn")).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !hay.contains(q)) continue;
            String country = country(c);
            if (!country.equals(lastCountry)) {
                TextView h = Ui.text(a, country, Ui.palette(a).muted, 13, Ui.medium());
                h.setPadding(Ui.dp(a, 4), Ui.dp(a, lastCountry == null ? 4 : 18), 0, Ui.dp(a, 6));
                list.addView(h);
                lastCountry = country;
            }
            boolean chosen = c.optString("id").equals(city);
            LinearLayout row = Ui.row(a);
            GradientDrawable bg = Ui.round(chosen ? Ui.palette(a).accentSoft : Ui.palette(a).surface, 12, a);
            if (chosen) bg.setStroke(Ui.dp(a, 1), Ui.palette(a).accent);
            Ui.pressable(row, bg, Ui.alpha(Ui.palette(a).accent, 0x33));
            row.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
            row.addView(Ui.text(a, name(c), chosen ? Ui.palette(a).accent : Ui.palette(a).ink, 16, Ui.medium()), Ui.fill(a));
            if (chosen) row.addView(Ui.text(a, "✓", Ui.palette(a).accent, 16, Ui.semibold()));
            String id = c.optString("id");
            row.setOnClickListener(v -> {
                city = id;
                a.go(this::prayer);
            });
            LinearLayout.LayoutParams lp = Ui.wide(a, 6);
            list.addView(row, lp);
        }
        if (list.getChildCount() == 0) list.addView(Ui.small(a, a.getString(R.string.city_none)));
    }

    // Favourites

    private void favourites() {
        a.frame(MainActivity.PERSONAL, Hero.Mode.APPS, a.getString(R.string.fav_title), a.getString(R.string.fav_lead));
        TextView count = Ui.latin(Ui.chip(a, "", true));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        cp.topMargin = Ui.dp(a, 14);
        a.content.addView(count, cp);
        LinearLayout grid = Ui.column(a);
        a.add(grid, 14);
        List<View> tiles = new ArrayList<>();
        Runnable paint = () -> {
            count.setText(a.getString(R.string.fav_count, favourites.size(), Math.max(4, Math.min(9, info.optInt("favourite_slots", 4)))));
            for (View t : tiles) paintTile(t);
        };
        LinearLayout row = null;
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app == null) continue;
            if (tiles.size() % 3 == 0) {
                row = Ui.row(a);
                row.setGravity(Gravity.TOP);
                grid.addView(row, Ui.wide(a, tiles.isEmpty() ? 0 : 10));
            }
            View tile = appTile(app, paint);
            LinearLayout.LayoutParams lp = Ui.fill(a);
            if (tiles.size() % 3 != 0) lp.setMarginStart(Ui.dp(a, 10));
            row.addView(tile, lp);
            tiles.add(tile);
        }
        while (row != null && tiles.size() % 3 != 0) {
            View spacer = new View(a);
            LinearLayout.LayoutParams lp = Ui.fill(a);
            lp.setMarginStart(Ui.dp(a, 10));
            row.addView(spacer, lp);
            tiles.add(spacer);
        }
        paint.run();
        loadIcons(tiles);
        a.foot(Ui.primary(a, a.getString(R.string.continue_), v -> save(v, () -> {
            if (!favourites.isEmpty()) tv.set("favourites", String.join(",", favourites));
        }, this::clean)));
        a.foot(Ui.secondary(a, a.getString(R.string.back), v -> a.go(this::prayer)));
        a.back = () -> a.go(this::prayer);
    }

    private View appTile(JSONObject app, Runnable paint) {
        String pkg = app.optString("p");
        LinearLayout t = Ui.column(a);
        t.setTag(pkg);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 10));
        android.widget.FrameLayout iconBox = new android.widget.FrameLayout(a);
        ImageView icon = new ImageView(a);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setBackground(Ui.round(Ui.palette(a).raised, 14, a));
        int s = Ui.dp(a, 52);
        iconBox.addView(icon, new android.widget.FrameLayout.LayoutParams(s, s));
        TextView badge = Ui.badge(a, "");
        android.widget.FrameLayout.LayoutParams bp = new android.widget.FrameLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22));
        bp.gravity = Gravity.TOP | Gravity.END;
        badge.setTextSize(11);
        iconBox.addView(badge, bp);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(a, 60), Ui.dp(a, 58));
        t.addView(iconBox, ip);
        TextView name = Ui.text(a, ar ? app.optString("ar") : app.optString("en"), Ui.palette(a).ink, 12, Ui.medium());
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(name, Ui.wide(a, 8));
        t.setOnClickListener(v -> {
            if (favourites.contains(pkg)) favourites.remove(pkg);
            else if (favourites.size() < Math.max(4, Math.min(9, info.optInt("favourite_slots", 4)))) favourites.add(pkg);
            paint.run();
        });
        return t;
    }

    private void paintTile(View t) {
        if (!(t.getTag() instanceof String)) return;
        int slot = favourites.indexOf((String) t.getTag());
        GradientDrawable bg = Ui.round(slot >= 0 ? Ui.palette(a).accentSoft : Ui.palette(a).surface, 16, a);
        bg.setStroke(Ui.dp(a, slot >= 0 ? 2 : 1), slot >= 0 ? Ui.palette(a).accent : Ui.palette(a).lineSoft);
        Ui.pressable(t, bg, Ui.alpha(Ui.palette(a).accent, 0x33));
        TextView badge = (TextView) ((ViewGroup) ((ViewGroup) t).getChildAt(0)).getChildAt(1);
        badge.setVisibility(slot >= 0 ? View.VISIBLE : View.INVISIBLE);
        badge.setText(String.format(Locale.US, "%d", slot + 1));
    }

    private void loadIcons(List<View> tiles) {
        int g = a.gen;
        new Thread(() -> {
            for (View t : tiles) {
                if (g != a.gen) return;
                if (!(t.getTag() instanceof String)) continue;
                String pkg = (String) t.getTag();
                byte[] png = icons.get(pkg);
                try {
                    if (png == null) png = tv.icon(pkg);
                } catch (Exception e) {
                    png = null;
                }
                if (png == null) continue;
                icons.put(pkg, png);
                byte[] b = png;
                a.post(g, () -> {
                    ImageView icon = (ImageView) ((ViewGroup) ((ViewGroup) t).getChildAt(0)).getChildAt(0);
                    icon.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length));
                    icon.setBackground(null);
                });
            }
        }).start();
    }

    // Cleanup: tick, turn off (done), confirm off (checked)

    private void clean() {
        a.frame(MainActivity.PERSONAL, Hero.Mode.CLEAN, a.getString(R.string.clean_title), a.getString(R.string.clean_lead));
        View wait = a.waiting(a.getString(R.string.clean_loading));
        a.add(wait, 20);
        a.back = null;
        int g = a.gen;
        a.work.execute(() -> {
            try {
                List<Setup.Bloat> catalogue = Setup.catalogue();
                Set<String> enabled = tv.enabledPackages();
                Tv.Cleaned earlier = tv.cleaned();
                List<Setup.Bloat> offered = new ArrayList<>();
                for (Setup.Bloat b : catalogue) {
                    // Never the apps just chosen as favourites or for a button.
                    if (enabled.contains(b.pkg) && !favourites.contains(b.pkg) && !keys.containsValue(b.pkg)) offered.add(b);
                }
                // Turned off before and on again since (a firmware update, say): offered again, ticked.
                for (String p : earlier.backOn) {
                    boolean listed = false;
                    for (Setup.Bloat b : offered) listed |= b.pkg.equals(p);
                    if (!listed) offered.add(new Setup.Bloat(p, 0, 0, "background", ""));
                }
                a.post(g, () -> showClean(offered, earlier, catalogue.isEmpty()));
            } catch (Exception e) {
                a.post(g, () -> showClean(new ArrayList<>(), new Tv.Cleaned(), true));
            }
        });
    }

    private static final String[] KINDS = {"tracking", "ads", "demo", "launcher", "apps", "background"};
    private static final int[] KIND_NAMES = {R.string.kind_tracking, R.string.kind_ads, R.string.kind_demo,
            R.string.kind_launcher, R.string.kind_apps, R.string.kind_background};

    private void showClean(List<Setup.Bloat> offered, Tv.Cleaned earlier, boolean offline) {
        a.frame(MainActivity.PERSONAL, Hero.Mode.CLEAN, a.getString(R.string.clean_title), a.getString(R.string.clean_lead));
        List<String> chosen = new ArrayList<>();
        for (Setup.Bloat b : offered) if (b.suggested() || earlier.backOn.contains(b.pkg)) chosen.add(b.pkg);
        TextView go = Ui.primary(a, "", null);
        Runnable count = () -> go.setText(chosen.isEmpty() ? a.getString(R.string.continue_)
                : a.getString(R.string.clean_button, String.format(Locale.US, "%d", chosen.size())));

        if (!earlier.off.isEmpty()) {
            // Still off from an earlier cleanup: checked, with the way back.
            LinearLayout card = Ui.card(a);
            LinearLayout head = Ui.row(a);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18));
            mp.setMarginEnd(Ui.dp(a, 10));
            head.addView(Timeline.mark(a, Timeline.State.CHECKED), mp);
            head.addView(Ui.label(a, a.getString(R.string.clean_earlier, String.format(Locale.US, "%d", earlier.off.size()))),
                    Ui.fill(a));
            card.addView(head);
            card.addView(Ui.small(a, a.getString(R.string.clean_still_off)), Ui.wide(a, 6));
            card.addView(Ui.secondary(a, a.getString(R.string.clean_undo), v -> a.go(() -> restore(earlier.off))),
                    Ui.wide(a, 12));
            a.add(card, 20);
        }
        if (offered.isEmpty()) {
            LinearLayout card = Ui.card(a);
            card.addView(Ui.small(a, a.getString(offline ? R.string.clean_offline : R.string.clean_none)));
            a.add(card, 20);
        }
        for (int k = 0; k < KINDS.length; k++) {
            List<Setup.Bloat> group = new ArrayList<>();
            for (Setup.Bloat b : offered) if (b.kind.equals(KINDS[k])) group.add(b);
            if (group.isEmpty()) continue;
            a.add(cleanGroup(KIND_NAMES[k], group, chosen, earlier.backOn, count), 14);
        }
        TextView credit = Ui.small(a, a.getString(R.string.clean_credit));
        credit.setGravity(Gravity.CENTER);
        a.add(credit, 16);

        count.run();
        go.setOnClickListener(v -> {
            if (chosen.isEmpty()) a.go(this::done);
            else a.go(() -> cleaning(new ArrayList<>(chosen)));
        });
        a.foot(go);
        a.foot(Ui.secondary(a, a.getString(R.string.skip), v -> a.go(this::done)));
        a.back = null;
    }

    private View cleanGroup(int title, List<Setup.Bloat> group, List<String> chosen, List<String> backOn, Runnable count) {
        LinearLayout card = Ui.card(a);
        LinearLayout head = Ui.row(a);
        head.addView(Ui.label(a, a.getString(title)), Ui.fill(a));
        head.addView(Ui.latin(Ui.chip(a, String.format(Locale.US, "%d", group.size()), false)));
        card.addView(head);
        LinearLayout rows = Ui.column(a);
        card.addView(rows, Ui.wide(a, 6));
        int shown = Math.min(group.size(), 6);
        for (int i = 0; i < group.size(); i++) {
            View r = bloatRow(group.get(i), chosen, backOn.contains(group.get(i).pkg), count);
            if (i >= shown) r.setVisibility(View.GONE);
            rows.addView(r, Ui.wide(a, 4));
        }
        if (group.size() > shown) {
            TextView more = Ui.text(a, a.getString(R.string.clean_more, String.format(Locale.US, "%d", group.size() - shown)),
                    Ui.palette(a).accent, 14, Ui.medium());
            more.setPadding(0, Ui.dp(a, 10), 0, 0);
            more.setOnClickListener(v -> {
                for (int i = 0; i < rows.getChildCount(); i++) rows.getChildAt(i).setVisibility(View.VISIBLE);
                more.setVisibility(View.GONE);
            });
            card.addView(more);
        }
        return card;
    }

    private View bloatRow(Setup.Bloat b, List<String> chosen, boolean backOn, Runnable count) {
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 4));
        TextView box = Ui.text(a, "", Ui.palette(a).ground, 13, Ui.semibold());
        box.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22));
        bp.setMarginEnd(Ui.dp(a, 12));
        bp.topMargin = Ui.dp(a, 1);
        row.addView(box, bp);
        LinearLayout words = Ui.column(a);
        String label = appName(b.pkg);
        words.addView(Ui.text(a, label, Ui.palette(a).ink, 14, Ui.medium()));
        StringBuilder sub = new StringBuilder();
        if (!label.equals(b.pkg)) sub.append(b.pkg);
        if (!b.note.isEmpty()) sub.append(sub.length() > 0 ? " · " : "").append(b.note);
        if (b.kept > 0) sub.append(sub.length() > 0 ? " · " : "").append(a.getString(R.string.clean_kept));
        if (sub.length() > 0) {
            TextView s = Ui.small(a, sub.toString());
            s.setTextSize(12);
            words.addView(s, Ui.wide(a, 2));
        }
        if (backOn) {
            TextView s = Ui.small(a, a.getString(R.string.clean_back_on));
            s.setTextSize(12);
            s.setTextColor(Ui.palette(a).accentLight);
            words.addView(s, Ui.wide(a, 2));
        }
        row.addView(words, Ui.fill(a));
        Runnable paint = () -> {
            boolean on = chosen.contains(b.pkg);
            GradientDrawable g = Ui.round(on ? Ui.palette(a).accent : 0, 6, a);
            g.setStroke(Ui.dp(a, 2), on ? Ui.palette(a).accent : Ui.alpha(Ui.palette(a).muted, 0x99));
            box.setBackground(g);
            box.setText(on ? "✓" : "");
        };
        paint.run();
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (chosen.contains(b.pkg)) chosen.remove(b.pkg);
            else chosen.add(b.pkg);
            paint.run();
            count.run();
        });
        return row;
    }

    /** Each chosen package: turned off (done), then confirmed off on the TV (checked). */
    private void cleaning(List<String> pkgs) {
        a.frame(MainActivity.PERSONAL, Hero.Mode.CLEAN, a.getString(R.string.cleaning_title),
                a.getString(R.string.cleaning, "0", String.format(Locale.US, "%d", pkgs.size())));
        Timeline list = packageTimeline(pkgs);
        a.add(list, 20);
        a.add(Timeline.legend(a), 12);
        a.back = null;
        int g = a.gen;
        a.work.execute(() -> {
            for (int i = 0; i < pkgs.size(); i++) {
                int row = i;
                a.post(g, () -> list.set(row, Timeline.State.WORKING));
                boolean ran;
                try {
                    ran = tv.disable(pkgs.get(i));
                } catch (Exception e) {
                    ran = false;
                }
                boolean r = ran;
                int d = i + 1;
                a.post(g, () -> {
                    list.set(row, r ? Timeline.State.DONE : Timeline.State.FAILED);
                    a.hero.progress((float) d / pkgs.size());
                    a.leadView.setText(a.getString(R.string.cleaning, String.format(Locale.US, "%d", d),
                            String.format(Locale.US, "%d", pkgs.size())));
                });
            }
            // Checked: what the TV itself lists as off now.
            Set<String> off;
            try {
                off = tv.disabledPackages();
            } catch (Exception e) {
                off = new java.util.HashSet<>();
            }
            int ok = 0;
            for (int i = 0; i < pkgs.size(); i++) {
                boolean isOff = off.contains(pkgs.get(i));
                if (isOff) ok++;
                int row = i;
                a.post(g, () -> list.set(row, isOff ? Timeline.State.CHECKED : Timeline.State.FAILED));
            }
            cleanedCount = ok;
            a.post(g, () -> {
                a.leadView.setText(a.getString(R.string.cleaned_checked, String.format(Locale.US, "%d", cleanedCount),
                        String.format(Locale.US, "%d", pkgs.size())));
                a.foot(Ui.primary(a, a.getString(R.string.continue_), v -> a.go(this::done)));
            });
        });
    }

    private Timeline packageTimeline(List<String> pkgs) {
        String[] labels = new String[pkgs.size()];
        for (int i = 0; i < pkgs.size(); i++) labels[i] = appName(pkgs.get(i));
        return new Timeline(a, labels);
    }

    /** Turns earlier-cleaned apps back on, each confirmed on (checked). */
    private void restore(List<String> pkgs) {
        a.frame(MainActivity.PERSONAL, Hero.Mode.CLEAN, a.getString(R.string.restoring_title), "");
        Timeline list = packageTimeline(pkgs);
        a.add(list, 20);
        a.back = null;
        int g = a.gen;
        a.work.execute(() -> {
            for (int i = 0; i < pkgs.size(); i++) {
                int row = i;
                a.post(g, () -> list.set(row, Timeline.State.WORKING));
                try {
                    tv.enable(pkgs.get(i));
                } catch (Exception ignored) {
                    // Checked below.
                }
                a.post(g, () -> list.set(row, Timeline.State.DONE));
            }
            Set<String> on;
            try {
                on = tv.enabledPackages();
            } catch (Exception e) {
                on = new java.util.HashSet<>();
            }
            boolean all = true;
            for (int i = 0; i < pkgs.size(); i++) {
                boolean isOn = on.contains(pkgs.get(i));
                all &= isOn;
                int row = i;
                a.post(g, () -> list.set(row, isOn ? Timeline.State.CHECKED : Timeline.State.FAILED));
            }
            if (all) {
                try {
                    tv.forgetCleaned();
                } catch (Exception ignored) {
                    // The list stays; restoring again is harmless.
                }
            }
            a.post(g, () -> a.foot(Ui.primary(a, a.getString(R.string.continue_), v -> a.go(this::clean))));
        });
    }

    // Done

    private void done() {
        List<String> summary = new ArrayList<>();
        if (info != null) {
            JSONObject c = cityById(city);
            if (c != null) summary.add(a.getString(R.string.sum_city, name(c)));
            if (!favourites.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (String p : favourites) names.add(appName(p));
                summary.add(a.getString(R.string.sum_favourites, String.join(ar ? "، " : ", ", names)));
            }
        }
        if (cleanedCount > 0) summary.add(a.getString(R.string.sum_cleaned, String.format(Locale.US, "%d", cleanedCount)));
        a.showDone(summary, info == null);
        // Back to Home, so the TV shows the result.
        a.work.execute(() -> {
            try {
                a.adbOrNull().shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
            } catch (Exception ignored) {
                // The user can press Home.
            }
        });
    }

    // Helpers

    /** Saves on the worker thread, then moves on. */
    /** Runs the job on the worker, then next; the button says it is saving meanwhile, and takes no second tap. */
    private void save(View button, Job job, Runnable next) {
        button.setEnabled(false);
        ((TextView) button).setText(R.string.saving);
        int g = ++a.gen;
        a.work.execute(() -> {
            try {
                job.run();
            } catch (Exception ignored) {
                // A setting that didn't take is not worth stopping for; Miqat’s Settings can set it.
            }
            a.post(g, next);
        });
    }

    private interface Job { void run() throws Exception; }

    private JSONObject cityById(String id) {
        if (cities == null) return null;
        for (int i = 0; i < cities.length(); i++) {
            JSONObject c = cities.optJSONObject(i);
            if (c != null && c.optString("id").equals(id)) return c;
        }
        return null;
    }

    private String name(JSONObject c) { return ar ? c.optString("ar") : c.optString("en"); }
    private String country(JSONObject c) { return ar ? c.optString("countryAr") : c.optString("countryEn"); }

    private String appName(String pkg) {
        if (apps != null) {
            for (int i = 0; i < apps.length(); i++) {
                JSONObject app = apps.optJSONObject(i);
                if (app != null && app.optString("p").equals(pkg)) return ar ? app.optString("ar") : app.optString("en");
            }
        }
        return pkg;
    }

    private static String strip(String key) {
        return key.startsWith("KEYCODE_") ? key.substring(8) : key;
    }

    private interface Choice { void on(String value); }
    private interface Flip { void on(boolean value); }

    private View segmented(String[] values, int[] labels, String current, Choice choice) {
        LinearLayout row = Ui.row(a);
        row.setBackground(Ui.round(Ui.palette(a).ground, 14, a));
        row.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        List<TextView> parts = new ArrayList<>();
        String[] now = {current};
        Runnable paint = () -> {
            for (int i = 0; i < parts.size(); i++) {
                boolean on = values[i].equals(now[0]);
                parts.get(i).setBackground(on ? Ui.round(Ui.palette(a).accent, 10, a) : null);
                parts.get(i).setTextColor(on ? Ui.palette(a).ground : Ui.palette(a).ink);
            }
        };
        for (int i = 0; i < values.length; i++) {
            TextView t = Ui.text(a, a.getString(labels[i]), Ui.palette(a).ink, 14, Ui.medium());
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 10));
            String v = values[i];
            t.setOnClickListener(x -> {
                now[0] = v;
                choice.on(v);
                paint.run();
            });
            parts.add(t);
            row.addView(t, Ui.fill(a));
        }
        paint.run();
        return row;
    }

    private View toggle(int label, boolean on, Flip flip) {
        LinearLayout row = Ui.row(a);
        row.addView(Ui.text(a, a.getString(label), Ui.palette(a).ink, 15, Ui.medium()), Ui.fill(a));
        Toggle t = new Toggle(a, on);
        row.addView(t, new LinearLayout.LayoutParams(Ui.dp(a, 48), Ui.dp(a, 28)));
        row.setClickable(true);
        row.setOnClickListener(v -> flip.on(t.flip()));
        return row;
    }

    /** An on/off pill in Miqat’s colours; on sits at the end of the reading direction. */
    private static final class Toggle extends View {
        private boolean on;
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();

        Toggle(android.content.Context c, boolean on) {
            super(c);
            this.on = on;
        }

        boolean flip() {
            on = !on;
            invalidate();
            return on;
        }

        @Override protected void onDraw(android.graphics.Canvas c) {
            float w = getWidth(), h = getHeight();
            r.set(0, h * 0.14f, w, h * 0.86f);
            p.setColor(on ? Ui.alpha(Ui.palette(getContext()).accent, 0x80) : Ui.palette(getContext()).raised);
            c.drawRoundRect(r, h, h, p);
            boolean end = on != (getLayoutDirection() == LAYOUT_DIRECTION_RTL);
            float rad = h * 0.42f;
            p.setColor(on ? Ui.palette(getContext()).accent : Ui.palette(getContext()).muted);
            c.drawCircle(end ? w - rad - h * 0.04f : rad + h * 0.04f, h / 2f, rad, p);
        }
    }
}

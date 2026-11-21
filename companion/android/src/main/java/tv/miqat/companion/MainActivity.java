package tv.miqat.companion;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONObject;

import tv.miqat.companion.core.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Native phone settings and explicit foreground Remote, with optional isolated demo TVs. */
public final class MainActivity extends Activity {
    final CompanionViews views = new CompanionViews(this);
    Catalog catalog;
    TvListUi tvListUi;
    TvRemoteUi remoteUi;
    LgSetup lgSetup;
    PhoneUi phoneUi;
    TvSetup tvSetup;
    PhoneOnboarding onboarding;
    QiblaUi qibla;
    WidgetUi widgetUi;
    final DemoTv[] tvs = new DemoTv[2];
    final List<LanTv> lanTvs = new ArrayList<>();
    final ExecutorService io = Executors.newFixedThreadPool(2);
    private Future<?> networkTask;
    Phones phones;
    boolean networkBusy, vaultFailed;
    String networkMessage = "working";
    int networkGeneration;
    PairingUi pairingUi;
    boolean phoneForeground;
    int selected;
    boolean ar;
    String page = "prayers", section = "", query = "";
    TvSettingsUi settingsUi;
    LinearLayout body, actionHost;
    ScrollView scroll;
    FrameLayout contentRoot;
    String renderedLocation = "";
    Toast toast;
    Typeface regular, heading;
    int ground, surface, accent, ink, muted;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(localized(base));
    }

    static android.content.Context localized(android.content.Context base) {
        String language =
                base.getSystemService(android.os.UserManager.class).isUserUnlocked()
                        ? base.getSharedPreferences("companion", MODE_PRIVATE)
                                .getString("language", "system")
                        : PhoneStore.preferences(base).getString("language-mode", "system");
        android.content.res.Configuration config =
                new android.content.res.Configuration(base.getResources().getConfiguration());
        if (!language.equals("system")) {
            Locale locale = new Locale(language);
            config.setLocale(locale);
            config.setLayoutDirection(locale);
        }
        try {
            config.fontScale *= PhoneStore.read(base).number("display.size") / 100f;
        } catch (Exception unavailable) {
            /* TV access survives invalid prayer preferences. */
        }
        base = base.createConfigurationContext(config);
        return base;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            catalog = new Catalog(this);
            tvListUi = new TvListUi(this);
            remoteUi = new TvRemoteUi(this);
            settingsUi = new TvSettingsUi(this);
            String language = prefs().getString("language", "system");
            ar =
                    language.equals("ar")
                            || language.equals("system")
                                    && getResources()
                                            .getConfiguration()
                                            .getLocales()
                                            .get(0)
                                            .getLanguage()
                                            .equals("ar");
            regular = getResources().getFont(R.font.alexandria_regular);
            heading = getResources().getFont(ar ? R.font.amiri_bold : R.font.marcellus_regular);
            ground = color("lapis-900");
            surface = color("lapis-800");
            accent = color("gold-400");
            ink = color("plaster-100");
            muted = color("mist-400");
            getWindow().setStatusBarColor(ground);
            getWindow().setNavigationBarColor(ground);
            for (int i = 0; i < tvs.length; i++) {
                tvs[i] =
                        catalog.demo(
                                i == 0 ? "android" : "lg",
                                decode(prefs().getString("demo-" + i, "{}")));
                tvs[i].setOnline(prefs().getBoolean("online-" + i, true));
            }
            phoneUi = new PhoneUi(this);
            if (state != null) phoneUi.restore(state);
            pairingUi = new PairingUi(this);
            tvSetup = new TvSetup(this);
            onboarding = new PhoneOnboarding(this);
            qibla = new QiblaUi(this);
            widgetUi = new WidgetUi(this);
            if (state != null) tvSetup.restore(state);
            phones = new Phones(this);
            try {
                for (Phones.Phone phone : phones.read()) addPhone(phone);
            } catch (Exception unavailable) {
                vaultFailed = true;
            }
            if (state != null) {
                selected = state.getInt("selected");
                page = state.getString("page", "prayers");
                section = state.getString("section", "");
                query = state.getString("query", "");
                if (selected >= 2) {
                    String pin = state.getString("selected-pin", "");
                    selected = -1;
                    for (int i = 0; i < lanTvs.size(); i++)
                        if (lanTvs.get(i).phone.pin.equals(pin)) selected = i + 2;
                    if (selected < 0) {
                        selected = 0;
                        page = "tvs";
                        phoneUi.resetNavigation();
                        state = null;
                    }
                }
            }
            if (state != null) {
                settingsUi.restore(state);
                if (page.equals("lg-setup")) {
                    lgSetup = new LgSetup(this, tvSetup.channel);
                    lgSetup.restore(state);
                }
            }
            if (getIntent().getBooleanExtra("prayer-home", false)) {
                page = "prayers";
                section = "";
            }
            boolean widgetConfiguration = widgetUi.configure(getIntent());
            if (isFinishing()) return;
            if (state != null) widgetUi.restore(state);
            if (!widgetConfiguration && state == null && onboarding.shouldStart())
                page = "onboarding";
            render();
            Object retained = getLastNonConfigurationInstance();
            pairingUi.resume(retained);
            settingsUi.resumeEdit();
            for (LanTv tv : lanTvs)
                io.execute(() -> {
                    tv.connect();
                    runOnUiThread(() -> {
                        if (!isDestroyed() && !networkBusy) {
                            if (page.equals("overview")
                                    && real() == tv
                                    && tv.online
                                    && tv.remote.length() == 0) remoteUi.readRemote();
                            else if (page.equals("tvs") || page.equals("overview")) render();
                        }
                    });
                });
        } catch (Exception e) {
            throw new IllegalStateException("Companion catalog could not be loaded", e);
        }
    }

    android.content.SharedPreferences prefs() {
        return getSharedPreferences("companion", MODE_PRIVATE);
    }

    static Map<String, String> decode(String json) {
        try {
            JSONObject value = new JSONObject(json);
            Map<String, String> out = new LinkedHashMap<>();
            Iterator<String> keys = value.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                out.put(key, value.getString(key));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid saved demo state", e);
        }
    }

    static String encode(Map<String, String> values) {
        return new JSONObject(values).toString();
    }

    LanTv real() {
        return selected >= 2 && selected - 2 < lanTvs.size() ? lanTvs.get(selected - 2) : null;
    }

    TvSettings backend() {
        return real() == null ? tv() : real();
    }

    boolean online() {
        return real() == null ? tv().getOnline() : real().online;
    }

    String profile() {
        return real() == null ? (selected == 0 ? "android" : "lg") : real().key;
    }

    DemoTv tv() {
        return tvs[selected];
    }

    String name(int i) {
        return i >= 2
                ? lanTvs.get(i - 2).phone.name
                : prefs().getString("name-" + i, t(i == 0 ? "living" : "bedroom"));
    }

    String t(String key) {
        return catalog.label(catalog.data.optJSONObject("text").optJSONObject(key), ar);
    }

    int color(String key) {
        JSONObject colors = catalog.data.optJSONObject("colors");
        if (phoneUi != null && phoneUi.settings != null)
            colors =
                    catalog.data
                            .optJSONObject("themes")
                            .optJSONObject(phoneUi.settings.value("appearance.theme"))
                            .optJSONObject("colors");
        return Color.parseColor(colors.optString(key));
    }

    String title(JSONObject c) {
        return catalog.label(c.optJSONObject("title"), ar);
    }

    String sectionTitle(String id) {
        return catalog.label(catalog.data.optJSONObject("sections").optJSONObject(id), ar);
    }

    void addPhone(Phones.Phone phone) throws Exception {
        LanTv next = new LanTv(this, phones, phone, catalog);
        String cache = prefs().getString("cache-" + next.key, null);
        if (cache != null) next.restore(cache);
        catalog.install(next);
        for (int i = 0; i < lanTvs.size(); i++)
            if (lanTvs.get(i).key.equals(next.key)) {
                lanTvs.set(i, next);
                return;
            }
        lanTvs.add(next);
    }

    void cacheReal(LanTv tv) {
        try {
            catalog.install(tv);
            if (!tv.cached().getValues().isEmpty())
                prefs().edit().putString("cache-" + tv.key, tv.cache()).apply();
        } catch (Exception invalid) {
            tv.online = false;
            tv.needsRefresh = true;
        }
    }

    <T> void network(Callable<T> task, Consumer<T> done, Runnable failed) {
        if (networkBusy) return;
        networkBusy = true;
        final int generation = ++networkGeneration;
        render();
        networkTask =
                io.submit(() -> {
                    try {
                        T result = task.call();
                        runOnUiThread(() -> {
                            if (isDestroyed() || generation != networkGeneration) return;
                            networkTask = null;
                            networkBusy = false;
                            networkMessage = "working";
                            done.accept(result);
                        });
                    } catch (Exception error) {
                        runOnUiThread(() -> {
                            if (isDestroyed() || generation != networkGeneration) return;
                            networkTask = null;
                            networkBusy = false;
                            networkMessage = "working";
                            failed.run();
                        });
                    }
                });
    }

    void cancelNetwork() {
        networkGeneration++;
        networkBusy = false;
        networkMessage = "working";
        Future<?> pending = networkTask;
        networkTask = null;
        if (pending != null) pending.cancel(true);
    }

    void tab(String target) {
        if (networkBusy) return;
        if (page.equals(target)
                && (!Arrays.asList("settings", "phone-settings").contains(target)
                        || section.isEmpty())) return;
        Runnable change =
                () -> {
                    if (phoneUi != null) {
                        phoneUi.resetNavigation();
                    }
                    if (qibla != null) qibla.stop();
                    stopAudio();
                    if (lgSetup != null) lgSetup.close();
                    pairingUi.cancelPairing();
                    settingsUi.clearPending();
                    settingsUi.appearance = null;
                    section = "";
                    query = "";
                    page = target;
                    if (target.equals("settings")) settingsUi.readSettings();
                    else if (target.equals("overview")) remoteUi.readRemote();
                    render();
                };
        if (settingsUi.appearance != null && !settingsUi.appearance.getChanges().isEmpty())
            confirmDiscard(change);
        else change.run();
    }

    void bottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(views.dp(6), views.dp(6), views.dp(6), views.dp(6));
        nav.setTag("navigation");
        nav.setElevation(views.dp(8));
        GradientDrawable material = views.background(surface, 40);
        material.setStroke(views.dp(1), (muted & 0x00ffffff) | 0x33000000);
        nav.setBackground(material);
        FrameLayout.LayoutParams bar =
                new FrameLayout.LayoutParams(
                        Math.min(
                                getResources().getDisplayMetrics().widthPixels - views.dp(32),
                                views.dp(480)),
                        -2,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bar.bottomMargin = views.dp(12);
        contentRoot.addView(nav, bar);
        scroll.setClipToPadding(false);
        nav.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        scroll.setPadding(0, 0, 0, bottom - top + views.dp(28)));
        String active =
                Arrays.asList("prayers", "qibla", "widgets").contains(page)
                        ? "prayers"
                        : Arrays.asList("phone-settings", "preferences").contains(page)
                                ? "phone-settings"
                                : "tvs";
        String[] targets = {"prayers", "tvs", "phone-settings"};
        String[] icons = {"mihrab", "tv", "gear"};
        String[] labels = {t("phone_home"), t("tvs"), t("phone_settings")};
        for (int i = 0; i < targets.length; i++) {
            final String target = targets[i];
            boolean selected = target.equals(active);
            int tint = selected ? accent : muted;
            Button item = views.button(nav, labels[i], () -> tab(target));
            views.flex(item);
            item.setMinHeight(views.dp(56));
            item.setTextSize(11);
            item.setPadding(views.dp(4), views.dp(4), views.dp(4), views.dp(4));
            item.setCompoundDrawablesRelative(null, Icons.get(this, icons[i], tint), null, null);
            item.setCompoundDrawablePadding(views.dp(4));
            item.setTag("nav." + target);
            item.setContentDescription(labels[i]);
            item.setSelected(selected);
            item.setTypeface(regular, selected ? Typeface.BOLD : Typeface.NORMAL);
            item.setBackground(
                    new RippleDrawable(
                            ColorStateList.valueOf((accent & 0x00ffffff) | 0x33000000),
                            views.background(selected ? color("lapis-700") : Color.TRANSPARENT, 32),
                            null));
            item.setTextColor(tint);
            if (target.equals("settings")) views.bindAction(item, "open-settings");
            item.setEnabled(!networkBusy);
            item.setAlpha(item.isEnabled() ? 1f : 0.45f);
        }
    }

    void notice(String key) {
        if (toast != null) toast.cancel();
        toast = Toast.makeText(this, t(key), Toast.LENGTH_LONG);
        toast.show();
    }

    void info(String title, String message) {
        views.dialog()
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(t("done"), null)
                .show();
    }

    void navigate(String target) {
        if (phoneUi != null) phoneUi.locationUi.cancelLocation();
        if (qibla != null) qibla.stop();
        stopAudio();
        if (lgSetup != null && !target.equals("lg-setup")) lgSetup.close();
        page = target;
        query = "";
        render();
    }

    void render() {
        if (phoneUi != null) phoneUi.palette();
        String location =
                page.equals("onboarding")
                        ? onboarding.locationKey()
                        : page.equals("tv-setup") ? tvSetup.locationKey() : page + ":" + section;
        String previousLocation = renderedLocation;
        int previousY =
                scroll != null && location.equals(renderedLocation) ? scroll.getScrollY() : 0;
        renderedLocation = location;
        LinearLayout shell = views.column();
        shell.setBackgroundColor(ground);
        boolean stylePage = page.equals("phone-settings") && section.equals("appearance");
        boolean homeBackground = page.equals("prayers")
                && phoneUi.settings.value("appearance.home-background").equals("true");
        if (page.equals("onboarding")) onboarding.background(shell);
        else if (stylePage) phoneUi.appearance.background(shell, phoneUi.settings);
        else if (homeBackground) phoneUi.appearance.homeBackground(shell, phoneUi.settings);
        shell.setLayoutDirection(ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        LinearLayout heading = views.column();
        shell.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        toolbar(heading);
        body = views.column();
        body.setPadding(views.dp(20), views.dp(4), views.dp(20), views.dp(12));
        body.setBackgroundColor(
                page.equals("onboarding") || stylePage || homeBackground ? Color.TRANSPARENT : ground);
        String screen =
                page.equals("settings") && section.equals("appearance") ? "appearance" : page;
        if (!CompanionContract.INSTANCE.getScreens().contains(screen))
            throw new IllegalStateException("Unknown companion screen: " + screen);
        body.setTag(screen);
        body.setLayoutDirection(ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.addView(body);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        boolean attachRoot = contentRoot == null;
        if (attachRoot) contentRoot = new FrameLayout(this);
        else contentRoot.removeAllViews();
        contentRoot.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        shell.addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    boolean shortLandscape =
                            getResources().getConfiguration().orientation
                                            == android.content.res.Configuration
                                                    .ORIENTATION_LANDSCAPE
                                    && bottom - top < views.dp(260);
                    int visibility = shortLandscape ? View.GONE : View.VISIBLE;
                    if (heading.getVisibility() != visibility)
                        heading.post(() -> heading.setVisibility(visibility));
                });
        if (attachRoot) setContentView(contentRoot);
        ScrollView current = scroll;
        current.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                current.getViewTreeObserver().removeOnPreDrawListener(this);
                // Restore after measurement, before any replacement frame can appear at the top.
                current.scrollTo(0, previousY);
                return true;
            }
        });
        current.addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    View focused = getCurrentFocus();
                    if (current == scroll
                            && bottom - top != oldBottom - oldTop
                            && focused instanceof EditText
                            && focused.isAttachedToWindow())
                        focused.requestRectangleOnScreen(
                                new android.graphics.Rect(
                                        0, 0, focused.getWidth(), focused.getHeight()),
                                true);
                });
        actionHost = views.column();
        shell.addView(actionHost, new LinearLayout.LayoutParams(-1, -2));
        renderPage();
        renderActions(actionHost);
        if (views.motionEnabled() && !location.equals(previousLocation)) {
            body.setAlpha(0f);
            body.animate().alpha(1f).setDuration(160).start();
        }
    }

    private void toolbar(LinearLayout shell) {
        LinearLayout toolbar = views.horizontal(shell);
        toolbar.setPadding(views.dp(12), views.dp(6), views.dp(12), views.dp(6));
        toolbar.setMinimumHeight(views.dp(page.equals("prayers") ? 56 : 64));
        if (page.equals("onboarding")
                || page.equals("tv-setup")
                || page.equals("qibla")
                || page.equals("widgets")
                || page.equals("pair")
                || page.equals("lg-setup")
                || page.equals("preferences")
                || page.equals("updates")
                || page.equals("overview")
                || page.equals("settings")
                || page.equals("phone-settings") && !section.isEmpty())
            views.iconButton(toolbar, "back", t("back"), this::back).setTag("navigation.back");
        View backButton = toolbar.findViewWithTag("navigation.back");
        if (backButton != null) backButton.setEnabled(!networkBusy || page.equals("pair"));
        if (page.equals("tvs") || page.equals("prayers")) {
            ImageView brand = new ImageView(this);
            brand.setImageResource(R.drawable.miqat_mark);
            brand.setContentDescription(ar ? "ميقات" : "Miqat");
            LinearLayout.LayoutParams mark =
                    new LinearLayout.LayoutParams(views.dp(40), views.dp(40));
            mark.setMarginEnd(views.dp(12));
            toolbar.addView(brand, mark);
        }
        LinearLayout titles = views.column();
        toolbar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        String pageTitle =
                page.equals("tv-setup")
                        ? t("tv_setup")
                        : page.equals("onboarding")
                                ? (ar ? "ميقات" : "Miqat")
                                : page.equals("qibla")
                                        ? t("phone_qibla")
                                        : page.equals("widgets")
                                                ? t("phone_widgets")
                                                : page.equals("prayers")
                                                        ? t("phone_home")
                                                        : page.equals("phone-settings")
                                                                ? phoneUi.sectionTitle()
                                                                : page.equals("tvs")
                                                                        ? (ar ? "ميقات" : "Miqat")
                                                                        : page.equals("overview")
                                                                                ? t("remote_title")
                                                                                : page.equals(
                                                                                                "settings")
                                                                                        ? (section
                                                                                                        .isEmpty()
                                                                                                ? t(
                                                                                                        "settings")
                                                                                                : section
                                                                                                                .equals(
                                                                                                                        "appearance")
                                                                                                        ? t(
                                                                                                                "appearance_title")
                                                                                                        : sectionTitle(
                                                                                                                section))
                                                                                        : t(page);
        TextView title =
                views.text(pageTitle, page.equals("settings") && !section.isEmpty() ? 22 : 26, ink);
        title.setPadding(0, 0, 0, 0);
        title.setTypeface(heading);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (!page.equals("prayers")
                && !page.equals("tvs")
                && !(page.equals("onboarding") && onboarding.step() == 0)) titles.addView(title);
        if (page.equals("prayers")) {
            TextView greeting = views.text(t("phone_greeting"), 20, ink);
            greeting.setTypeface(heading);
            greeting.setTag("phone.greeting");
            greeting.setPadding(0, 0, 0, 0);
            titles.addView(greeting);
        }
        if (page.equals("phone-settings") && section.equals("calculation")) {
            title.setText(t("phone_location_title"));
            title.setTextSize(20);
            phoneUi.locationUi.addDetect(toolbar);
        }
        if (page.equals("prayers"))
            views.iconButton(toolbar, "bell", t("phone_audio"), () -> phoneUi.open("audio"));
        String subtitle =
                page.equals("tvs")
                        ? t("tvs")
                        : Arrays.asList("overview", "settings", "updates").contains(page)
                                ? name(selected)
                                        + " · "
                                        + t(online() ? "connected" : "disconnected")
                                : "";
        if (!subtitle.isEmpty()) {
            TextView secondary = views.text(subtitle, 11, muted);
            secondary.setPadding(0, views.dp(2), 0, 0);
            titles.addView(secondary);
        }
        if (page.equals("tvs"))
            views.iconButton(toolbar, "gear", t("preferences"), () -> navigate("preferences"));
        if (Arrays.asList("overview", "settings").contains(page))
            views.iconButton(
                            toolbar,
                            "refresh",
                            t("refresh"),
                            () -> {
                                if (page.equals("settings")) settingsUi.readSettings();
                                else remoteUi.readRemote();
                            })
                    .setEnabled(!networkBusy);
        if (Arrays.asList("overview", "settings", "updates").contains(page)) {
            LinearLayout tabs = views.horizontal(shell);
            tabs.setPadding(views.dp(20), 0, views.dp(20), 0);
            for (String target : new String[] {"overview", "settings"}) {
                Button item =
                        views.button(
                                tabs,
                                t(target.equals("overview") ? "remote_title" : "settings"),
                                () -> tab(target));
                views.flex(item);
                item.setTag("tv-nav." + target);
                item.setSelected(page.equals(target));
                item.setTextColor(page.equals(target) ? accent : muted);
                item.setEnabled(!networkBusy);
                if (target.equals("settings")) views.bindAction(item, "open-settings");
            }
        }
    }

    private void renderPage() {
        switch (page) {
            case "prayers":
            case "phone-settings":
                phoneUi.render();
                break;
            case "onboarding":
                onboarding.render();
                break;
            case "tv-setup":
                tvSetup.render();
                break;
            case "qibla":
                qibla.render();
                break;
            case "widgets":
                widgetUi.render();
                break;
            case "lg-setup":
                if (lgSetup == null) lgSetup = new LgSetup(this);
                lgSetup.render(body);
                break;
            case "tvs":
                tvListUi.renderTvs();
                break;
            case "overview":
                remoteUi.renderOverview();
                break;
            case "settings":
                settingsUi.renderSettings();
                break;
            case "preferences":
                renderPreferences();
                break;
            case "pair":
                pairingUi.render();
                break;
            case "updates":
                renderUpdates();
                break;
            default:
                throw new IllegalStateException("Unknown screen");
        }
    }

    private void renderActions(LinearLayout shell) {
        boolean editorActions = shell.getChildCount() > 0;
        if (!editorActions
                && page.equals("settings")
                && section.equals("appearance")
                && settingsUi.appearance != null) {
            LinearLayout actions = views.actionBar(shell);
            Button cancel =
                    views.bindAction(
                            views.secondaryAction(
                                    views.secondaryActions(actions),
                                    t("cancel"),
                                    () -> {
                                        settingsUi.appearance = null;
                                        section = "";
                                        render();
                                    }),
                            "cancel-appearance");
            cancel.setTag("appearance.cancel");
            cancel.setEnabled(!networkBusy);
            Button apply =
                    views.bindAction(
                            views.primaryAction(actions, t("apply"), settingsUi::applyAppearance),
                            "apply-appearance");
            apply.setTag("appearance.apply");
            apply.setEnabled(
                    settingsUi.editable() && !settingsUi.appearance.getChanges().isEmpty());
            apply.setAlpha(apply.isEnabled() ? 1f : 0.4f);
        }
        if (networkBusy && !page.equals("pair")) {
            views.disableContent(body);
            if (editorActions) views.disableContent(shell);
            ProgressBar progress =
                    new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setIndeterminate(views.motionEnabled());
            if (!views.motionEnabled()) progress.setProgress(50);
            progress.setIndeterminateTintList(ColorStateList.valueOf(accent));
            progress.setContentDescription(t(networkMessage));
            progress.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            shell.addView(progress, new LinearLayout.LayoutParams(-1, views.dp(3)));
        }
        if (networkBusy && lgSetup != null) lgSetup.cancellation(shell);
        if (editorActions) return;
        if (page.equals("onboarding")) onboarding.footer(shell);
        else if (page.equals("tv-setup")) tvSetup.footer(shell);
        else if (page.equals("lg-setup") && lgSetup != null) lgSetup.footer(shell);
        else if (page.equals("widgets")) widgetUi.footer(shell);
        else if (page.equals("prayers")
                || page.equals("tvs")
                || page.equals("phone-settings") && section.isEmpty()) bottomNavigation();
    }

    static TextWatcher watcher(Consumer<String> changed) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

            public void onTextChanged(CharSequence s, int st, int b, int c) {
                changed.accept(s.toString());
            }

            public void afterTextChanged(Editable e) {}
        };
    }

    void renderPreferences() {
        languageChoices(body);
        views.button(
                body,
                t("try_demo"),
                () -> {
                    prefs().edit().putBoolean("show-demo", true).apply();
                    navigate("tvs");
                });
    }

    void languageChoices(LinearLayout parent) {
        java.util.List<Choices.Option> choices =
                java.util.Arrays.asList(
                        new Choices.Option("system", t("system")),
                        new Choices.Option("ar", "العربية"),
                        new Choices.Option("en", "English"));
        String selected = prefs().getString("language", "system");
        String label = selected.equals("en") ? "English" : selected.equals("ar") ? "العربية" : t("system");
        LinearLayout row = views.navigationRow(parent, "gear", t("preferences"), label,
                () -> phoneUi.picker(t("preferences"), choices, selected, value -> {
                    if (value.equals(selected)) return;
                    prefs().edit().putString("language", value).apply();
                    recreate();
                }));
        row.setTag("phone.language");
        views.bindAction(row, "choose-language");
    }

    @Override
    protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 79 && phoneUi != null)
            phoneUi.sound.audioResult(result == RESULT_OK && data != null ? data.getData() : null);
        if (request == 71 && result == RESULT_OK && data != null) {
            String qr = data.getStringExtra("qr");
            if (qr != null) pairingUi.beginPairing(qr);
        }
        if (request == 72 && result == RESULT_OK) navigate("pair");
        if (request == 73 && result == RESULT_OK && data != null && data.getData() != null) {
            if (lgSetup == null) lgSetup = new LgSetup(this);
            lgSetup.select(data.getData());
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return pairingUi == null ? null : pairingUi.retain();
    }

    void renderUpdates() {
        if (real() != null) {
            body.addView(views.text(t("real_updates"), 16, ink));
            return;
        }
        body.addView(views.text(t("update_info"), 16, ink));
        views.bindAction(
                        views.button(
                                body,
                                t("simulate_update"),
                                () ->
                                        views.dialog()
                                                .setTitle(t("update_confirm"))
                                                .setMessage(t("demo"))
                                                .setNegativeButton(t("cancel"), null)
                                                .setPositiveButton(
                                                        t("done"),
                                                        (d, w) ->
                                                                info(
                                                                        t("updates"),
                                                                        t("update_done")))
                                                .show()),
                        "simulate-update")
                .setEnabled(tv().getOnline());
    }

    void playAudio(String voice) {
        if (PhonePlayback.scheduled()) {
            notice("phone_stop_first");
            return;
        }
        if (!PhoneAlarms.notifications(this)) {
            phoneUi.open("permissions");
            return;
        }
        startForegroundService(
                PhonePlayback.intent(this, 0, voice, true, "preview:" + System.nanoTime()));
    }

    void stopAudio() {
        PhonePlayback.stopPreview(this);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("prayer-home", false)) tab("prayers");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (phoneUi != null) {
            phoneUi.locationUi.background.resume();
            PhoneAlarms.rebuild(this);
            if (page.equals("prayers")
                    || page.equals("phone-settings")
                    || page.equals("onboarding")
                    || page.equals("qibla")) render();
            phoneUi.home.start();
            phoneUi.locationUi.refreshTravel();
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 75) {
            onboarding.notificationResult();
            PhoneAlarms.rebuild(this);
            render();
        }
        if (request == 78 && phoneUi != null) phoneUi.locationUi.locationPermissionResult();
        if ((request == 80 || request == 81) && phoneUi != null)
            phoneUi.locationUi.background.result(request);
    }

    @Override
    protected void onStart() {
        super.onStart();
        phoneForeground = true;
        pairingUi.schedulePoll();
        if (lgSetup != null && page.equals("lg-setup")) render();
    }

    @Override
    protected void onPause() {
        if (phoneUi != null) phoneUi.locationUi.background.pause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        phoneForeground = false;
        if (phoneUi != null) phoneUi.stop();
        if (qibla != null) qibla.stop();
        pairingUi.pause();
        stopAudio();
        if (lgSetup != null) lgSetup.close();
        if (toast != null) toast.cancel();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        pairingUi.cancelPairing();
        if (lgSetup != null) lgSetup.close();
        io.shutdownNow();
        super.onDestroy();
    }

    void confirmDiscard(Runnable next) {
        views.dialog()
                .setMessage(t("discard"))
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("discard_yes"), (d, w) -> next.run())
                .show();
    }

    void back() {
        if (phoneUi != null) phoneUi.locationUi.cancelLocation();
        if (networkBusy && !page.equals("pair")) return;
        stopAudio();
        if (page.equals("widgets") && widgetUi.cancel()) return;
        if (page.equals("onboarding")) {
            onboarding.back();
            return;
        }
        if (page.equals("tv-setup")) {
            tvSetup.back();
            return;
        }
        if (page.equals("lg-setup") && lgSetup != null) {
            lgSetup.back();
            return;
        }
        if (page.equals("qibla") || page.equals("widgets")) {
            qibla.stop();
            tab("prayers");
            return;
        }
        if (page.equals("phone-settings") && phoneUi.back()) return;
        if (page.equals("phone-settings") && !section.isEmpty()) {
            phoneUi.pending = "";
            section = "";
            render();
            return;
        }
        if (page.equals("phone-settings") || page.equals("tvs")) {
            tab("prayers");
            return;
        }
        if (page.equals("prayers")) {
            if (phoneUi.home.dayOffset != 0 || phoneUi.home.selectedDay != 0) phoneUi.home.today();
            else finish();
            return;
        }
        if (page.equals("preferences")) {
            navigate("phone-settings");
            return;
        }
        if (page.equals("pair")) {
            pairingUi.cancelPairing();
            navigate("tvs");
            return;
        }
        if (settingsUi.hasPending() && settingsUi.editor == null) {
            settingsUi.clearPending();
            render();
            return;
        }
        if (page.equals("settings")
                && settingsUi.appearance != null
                && !settingsUi.appearance.getChanges().isEmpty()) {
            confirmDiscard(() -> {
                settingsUi.appearance = null;
                section = "";
                render();
            });
            return;
        }
        if (page.equals("settings") && !section.isEmpty()) {
            settingsUi.appearance = null;
            section = "";
            query = "";
            render();
        } else if (page.equals("settings") || page.equals("updates")) navigate("overview");
        else if (!page.equals("tvs")) navigate("tvs");
        else finish();
    }

    @Override
    public void onBackPressed() {
        back();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (tvSetup != null) tvSetup.save(state);
        if (phoneUi != null) phoneUi.save(state);
        if (widgetUi != null) widgetUi.save(state);
        state.putInt("selected", selected);
        state.putString("page", page);
        state.putString("section", section);
        state.putString("query", query);
        if (real() != null) state.putString("selected-pin", real().phone.pin);
        settingsUi.saveState(state);
        if (lgSetup != null) lgSetup.save(state);
    }
}

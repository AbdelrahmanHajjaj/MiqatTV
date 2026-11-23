package tv.miqat.installer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.AdbPairingRequiredException;

/**
 * The installer, one screen at a time in three stages: Prepare (turning on debugging on the TV), Connect (finding
 * the TV, then "Allow debugging?" or a pairing code) and Install (download, install, the setup from
 * tools/install.sh). Every screen is the same frame: stage bar, arch, title, lead, content, and the actions pinned
 * to the bottom.
 */
public abstract class MainActivity extends Activity {
    InstallerPalette palette = InstallerPalette.DEFAULT;

    protected InstallerPalette createPalette() { return InstallerPalette.DEFAULT; }

    static final int PREPARE = 0, CONNECT = 1, INSTALL = 2, PERSONAL = 3;

    final ExecutorService work = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Finder finder, pairFinder;
    private Adb adb;

    private LinearLayout stages, footer;
    LinearLayout content;
    private ScrollView scroll;
    Hero hero;
    private int stage;
    /** What Back does on this screen; null while it must not be interrupted. */
    Runnable back;
    /**
     * Bumped whenever the user moves to another screen, so a worker that finishes afterwards (a connect that
     * succeeds after the user went Back) leaves the screen alone.
     */
    int gen;
    /** The TV list on the find screen, refreshed as the finder hears from TVs. */
    private LinearLayout tvList;
    private View help;

    /** Embedded hosts may also honor their own reduced-motion preference. */
    public boolean motionEnabled(){return android.animation.ValueAnimator.areAnimatorsEnabled();}

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        palette = createPalette();
        Ui.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Ui.palette(this).ground);
        getWindow().setNavigationBarColor(Ui.palette(this).ground);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.palette(this).ground);
        stages = Ui.row(this);
        stages.setPadding(dp(20), dp(18), dp(20), dp(6));
        root.addView(stages, Ui.wide(this, 0));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = Ui.column(this);
        content.setPadding(dp(20), dp(4), dp(20), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        footer = Ui.column(this);
        footer.setPadding(dp(20), dp(8), dp(20), dp(20));
        root.addView(footer, Ui.wide(this, 0));
        setContentView(root);

        hero = new Hero(this);
        finder = new Finder(this, tvs -> runOnUiThread(() -> showTvs(tvs)));

        if(getIntent().getBooleanExtra("choose-local",false)){showGuide();chooseLocal();}
        else if(getIntent().getBooleanExtra("prepared",false))showFind();
        else showGuide();
    }

    @Override
    public void onBackPressed() {
        if (back != null) back.run();
    }

    @Override
    protected void onDestroy() {
        stopFinders();
        work.shutdownNow();
        if (adb != null) {
            Adb a = adb;
            new Thread(() -> { try { a.disconnect(); } catch (IOException ignored) { } }).start();
        }
        super.onDestroy();
    }

    // Prepare

    private void showGuide() {
        stopFinders();
        frame(PREPARE, Hero.Mode.TV, getString(R.string.guide_title), getString(R.string.guide_lead));
        TextView both = Ui.chip(this, getString(R.string.guide_both), false);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.gravity = Gravity.CENTER_HORIZONTAL;
        bp.topMargin = dp(12);
        content.addView(both, bp);
        add(step(1, R.string.g1_title, 0, Ui.tvText(this, R.string.tv_about_en, R.string.tv_about_ar)), 18);
        add(step(2, R.string.g2_title, R.string.g2_body, Ui.tvText(this, R.string.tv_build_en, R.string.tv_build_ar)), 10);
        add(step(3, R.string.g3_title, R.string.g3_body, Ui.tvText(this, R.string.tv_dev_en, R.string.tv_dev_ar),
                Ui.tvText(this, R.string.tv_usb_en, R.string.tv_usb_ar),
                Ui.small(this, getString(R.string.g3_wireless)),
                Ui.tvText(this, R.string.tv_wireless_en, R.string.tv_wireless_ar)), 10);
        add(step(4, R.string.g4_title, R.string.g4_body), 10);
        add(Ui.small(this,getString(localApk==null?R.string.public_download_help:R.string.local_package_selected)),12);
        foot(Ui.secondary(this, getString(R.string.advanced_setup), v -> new AlertDialog.Builder(this)
            .setTitle(R.string.advanced_setup).setItems(new String[]{getString(R.string.choose_local_apk),getString(R.string.use_public_download)}, (d,which) -> {
                if(which==1){localApk=null;showGuide();}
                else chooseLocal();
            }).show()));
        foot(Ui.primary(this, getString(R.string.guide_ready), v -> go(this::showFind)));
        recoveryActions();
        back = this::finish;
    }

    private void chooseLocal(){
        startActivityForResult(new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
            .setType("application/vnd.android.package-archive").addCategory(android.content.Intent.CATEGORY_OPENABLE),81);
    }

    private void leavePreparation(){
        if(getIntent().getBooleanExtra("prepared",false))finish();else go(this::showGuide);
    }

    /** A numbered card: its title, then what to find on the TV (in both languages), with the detail after the first. */
    private View step(int n, int title, int body, View... tv) {
        LinearLayout card = Ui.card(this);
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        row.addView(Ui.badge(this, number(n)));
        LinearLayout words = Ui.column(this);
        words.setPaddingRelative(dp(14), dp(3), 0, 0);
        words.addView(Ui.label(this, getString(title)));
        for (int i = 0; i < tv.length; i++) {
            words.addView(tv[i], Ui.wide(this, 8));
            if (i == 0 && body != 0) words.addView(Ui.small(this, getString(body)), Ui.wide(this, 8));
        }
        if (tv.length == 0 && body != 0) words.addView(Ui.small(this, getString(body)), Ui.wide(this, 8));
        row.addView(words, Ui.fill(this));
        card.addView(row);
        return card;
    }

    // Connect: finding the TV

    private void showFind() {
        if(getIntent().getBooleanExtra("choose-local",false)&&localApk==null){chooseLocal();return;}
        stopFinders();
        back = this::leavePreparation;
        if (Finder.phoneAddress(this) == null) {
            frame(CONNECT, Hero.Mode.PROBLEM, getString(R.string.not_wifi_title), getString(R.string.not_wifi));
            foot(Ui.primary(this, getString(R.string.try_again), v -> go(this::showFind)));
            foot(Ui.secondary(this, getString(R.string.back), v -> back.run()));
            return;
        }
        frame(CONNECT, Hero.Mode.SEARCH, getString(R.string.find_title), getString(R.string.find_lead));
        LinearLayout heading = Ui.row(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(14), dp(14));
        sp.setMarginEnd(dp(8));
        heading.addView(Timeline.spinner(this), sp);
        heading.addView(Ui.text(this, getString(R.string.found_heading), Ui.palette(this).muted, 13, Ui.medium()));
        add(heading, 22);
        tvList = Ui.column(this);
        add(tvList, 0);
        LinearLayout h = Ui.card(this);
        h.addView(Ui.label(this, getString(R.string.help_title)));
        h.addView(Ui.small(this, getString(R.string.help_body)), Ui.wide(this, 8));
        h.setVisibility(View.GONE);
        help = h;
        add(h, 8);
        add(addressCard(), 20);
        int g = gen;
        // Help only once a search has had time to turn something up.
        main.postDelayed(() -> {
            if (g == gen && help != null && tvList != null && tvList.getChildCount() == 0) {
                help.setAlpha(motionEnabled()?0f:1f);
                help.setVisibility(View.VISIBLE);
                if(motionEnabled())help.animate().alpha(1f).setDuration(300).start();
            }
        }, 8000);
        finder.start();
    }

    private void showTvs(List<Finder.Tv> tvs) {
        if (tvList == null || tvList.getParent() == null) return;
        tvList.removeAllViews();
        for (Finder.Tv t : tvs) {
            if (t.classic == 0 && t.tls == 0 && t.pairing == 0) continue;
            tvList.addView(tvCard(t), Ui.wide(this, tvList.getChildCount() == 0 ? 8 : 10));
        }
        if (help != null && tvList.getChildCount() > 0) help.setVisibility(View.GONE);
    }

    private View tvCard(Finder.Tv t) {
        LinearLayout card = Ui.row(this);
        GradientDrawable bg = Ui.round(Ui.palette(this).surface, 18, this);
        bg.setStroke(dp(1), Ui.alpha(Ui.palette(this).accent, 0x66));
        Ui.pressable(card, bg, Ui.alpha(Ui.palette(this).accent, 0x33));
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.addView(new TvGlyph(this), new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout words = Ui.column(this);
        words.setPaddingRelative(dp(14), 0, dp(8), 0);
        words.addView(Ui.small(this, getString(R.string.tv_found)));
        TextView host = Ui.latin(Ui.text(this, t.host, Ui.palette(this).ink, 18, Ui.semibold()));
        host.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        words.addView(host, Ui.wide(this, 2));
        if (t.tls > 0) words.addView(door(R.string.badge_wireless, t.tls, true), Ui.wide(this, 6));
        if (t.classic > 0) words.addView(door(R.string.badge_network, t.classic, false), Ui.wide(this, 4));
        if (t.pairing > 0) words.addView(door(R.string.badge_pair, t.pairing, true), Ui.wide(this, 4));
        card.addView(words, Ui.fill(this));

        TextView chevron = Ui.text(this, "›", Ui.palette(this).accent, 26, Ui.medium());
        card.addView(chevron);
        card.setOnClickListener(v -> go(() -> connect(t.host, t.tls, t.classic)));
        return card;
    }

    /** "● Wireless debugging · 41234": a way into the TV and its port. */
    private View door(int name, int port, boolean accent) {
        LinearLayout row = Ui.row(this);
        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(accent ? Ui.palette(this).accent : Ui.palette(this).muted);
        dot.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(6), dp(6));
        lp.setMarginEnd(dp(8));
        row.addView(dot, lp);
        row.addView(Ui.text(this, getString(name), accent ? Ui.palette(this).accentLight : Ui.palette(this).muted, 13, Ui.medium()));
        TextView p = Ui.latin(Ui.text(this, "·  " + number(port), Ui.palette(this).muted, 13, Ui.medium()));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.setMarginStart(dp(8));
        row.addView(p, pp);
        return row;
    }

    /**
     * The TV's address, typed: "192.168.1.50" checks every port, "192.168.1.50:41234" goes straight there. The
     * last address that worked is filled in, so a second run is one tap.
     */
    private View addressCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.label(this, getString(R.string.manual_title)));
        card.addView(Ui.small(this, getString(R.string.manual_body)), Ui.wide(this, 6));
        LinearLayout row = Ui.row(this);
        EditText field = new EditText(this);
        field.setHint(R.string.manual_hint);
        field.setHintTextColor(Ui.alpha(Ui.palette(this).muted, 0x80));
        field.setTextColor(Ui.palette(this).ink);
        field.setTextSize(16);
        field.setTypeface(Ui.medium());
        field.setSingleLine();
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        field.setBackground(Ui.round(Ui.palette(this).ground, 12, this));
        field.setPadding(dp(14), dp(12), dp(14), dp(12));
        Ui.latin(field);
        field.setText(getPreferences(MODE_PRIVATE).getString("last_tv", ""));
        row.addView(field, Ui.fill(this));
        TextView go = Ui.primary(this, getString(R.string.connect), v -> typed(field.getText().toString()));
        go.setMinHeight(dp(46));
        go.setPadding(dp(18), dp(10), dp(18), dp(10));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.setMarginStart(dp(10));
        row.addView(go, gp);
        field.setOnEditorActionListener((v, action, e) -> {
            typed(field.getText().toString());
            return true;
        });
        card.addView(row, Ui.wide(this, 12));
        return card;
    }

    private void typed(String text) {
        String s = text.trim().replace(" ", "");
        if (s.isEmpty()) return;
        int colon = s.lastIndexOf(':');
        if (colon > 0) {
            int port;
            try {
                port = Integer.parseInt(s.substring(colon + 1));
            } catch (NumberFormatException e) {
                return;
            }
            String host = s.substring(0, colon);
            if (port == Finder.CLASSIC_PORT) go(() -> connect(host, 0, port));
            else go(() -> connect(host, port, 0));
        } else {
            go(() -> scan(s));
        }
    }

    /** No port given: try 5555 and every port Wireless debugging may use. */
    private void scan(String host) {
        this.host = host;
        stopFinders();
        frame(CONNECT, Hero.Mode.SEARCH, getString(R.string.scanning_title), getString(R.string.scanning, host));
        foot(Ui.secondary(this, getString(R.string.back), v -> go(this::showFind)));
        back = () -> go(this::showFind);
        int g = gen;
        work.execute(() -> {
            try {
                List<Integer> open = Finder.scanPorts(host);
                if (g != gen) return;
                Adb a = adb();
                for (int port : open) {
                    if (port == Finder.CLASSIC_PORT) continue;
                    // Wireless debugging answers a stranger with "pairing required"; anything else isn't ADB.
                    a.setTimeout(5, TimeUnit.SECONDS);
                    try {
                        a.disconnect();
                        if (a.connect(host, port)) {
                            post(g, this::install);
                            return;
                        }
                    } catch (AdbPairingRequiredException e) {
                        post(g, () -> showPair(host, port));
                        return;
                    } catch (Exception notAdb) {
                        // Next port.
                    } finally {
                        a.setTimeout(90, TimeUnit.SECONDS);
                    }
                }
                if (open.contains(Finder.CLASSIC_PORT)) {
                    post(g, () -> connect(host, 0, Finder.CLASSIC_PORT));
                    return;
                }
                fail(g, getString(R.string.scan_none, host));
            } catch (Exception e) {
                fail(g, getString(R.string.error_connect, message(e)));
            }
        });
    }

    /** Wireless debugging first (no prompt on the TV once paired), then classic with "Allow debugging?". */
    private void connect(String host, int tls, int classic) {
        this.host = host;
        stopFinders();
        frame(CONNECT, Hero.Mode.SEARCH, getString(R.string.connecting_title), getString(R.string.connecting, host));
        foot(Ui.secondary(this, getString(R.string.back), v -> go(this::showFind)));
        back = () -> go(this::showFind);
        int g = gen;
        work.execute(() -> {
            Adb a;
            try {
                a = adb();
                a.disconnect();
            } catch (Exception e) {
                fail(g, getString(R.string.error_connect, message(e)));
                return;
            }
            if (tls > 0) {
                try {
                    if (a.connect(host, tls)) {
                        post(g, this::install);
                        return;
                    }
                } catch (AdbPairingRequiredException e) {
                    post(g, () -> showPair(host, tls));
                    return;
                } catch (Exception e) {
                    if (classic == 0) {
                        fail(g, getString(R.string.error_connect, message(e)));
                        return;
                    }
                }
            }
            if (classic > 0) {
                post(g, this::showAllow);
                try {
                    if (a.connect(host, classic)) {
                        post(g, this::install);
                        return;
                    }
                    fail(g, getString(R.string.not_accepted));
                } catch (Exception e) {
                    fail(g, getString(R.string.error_connect, message(e)));
                }
                return;
            }
            fail(g, getString(R.string.error_connect, host));
        });
    }

    /** Classic network debugging: the TV asks to allow this phone. */
    private void showAllow() {
        frame(CONNECT, Hero.Mode.ALLOW, getString(R.string.allow_title), getString(R.string.allow_lead));
        add(Ui.tvText(this, R.string.tv_allow_q_en, R.string.tv_allow_q_ar), 14);
        LinearLayout card = Ui.card(this);
        card.addView(numbered(1, getString(R.string.allow_1), Ui.tvText(this, R.string.tv_always_en, R.string.tv_always_ar)));
        card.addView(numbered(2, getString(R.string.allow_2), Ui.tvText(this, R.string.tv_allow_en, R.string.tv_allow_ar)),
                Ui.wide(this, 14));
        add(card, 14);
        add(waiting(getString(R.string.waiting)), 18);
        foot(Ui.secondary(this, getString(R.string.back), v -> go(this::showFind)));
    }

    // Connect: pairing

    /** Pairing for Wireless debugging: the TV shows a six-digit code and its own pairing port. */
    private void showPair(String host, int tlsPort) {
        stopFinders();
        frame(CONNECT, Hero.Mode.PAIR, getString(R.string.pair_title), getString(R.string.pair_lead));
        LinearLayout card = Ui.card(this);
        card.addView(numbered(1, getString(R.string.pair_1),
                Ui.tvText(this, R.string.tv_wireless_path_en, R.string.tv_wireless_path_ar)));
        card.addView(numbered(2, getString(R.string.pair_2), Ui.tvText(this, R.string.tv_pair_en, R.string.tv_pair_ar)),
                Ui.wide(this, 14));
        card.addView(numbered(3, getString(R.string.pair_3), null), Ui.wide(this, 14));
        add(card, 20);

        TextView pairButton = Ui.primary(this, getString(R.string.pair_button), null);
        EditText port = new EditText(this);
        CodeInput code = new CodeInput(this, c -> {
            Ui.enabled(pairButton, c.length() == 6 && port.length() > 0);
            if (c.length() == 6 && port.length() > 0) pairButton.performClick();
        });
        add(code, 20);

        // The pairing port: found by mDNS while the TV's dialog is open, or typed.
        TextView found = Ui.latin(Ui.chip(this, "", true));
        found.setVisibility(View.GONE);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.gravity = Gravity.CENTER_HORIZONTAL;
        fp.topMargin = dp(16);
        content.addView(found, fp);
        View wait = waiting(getString(R.string.pair_port_waiting));
        add(wait, 14);
        port.setHint(R.string.pair_port_hint);
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setTextColor(Ui.palette(this).ink);
        port.setHintTextColor(Ui.palette(this).muted);
        port.setTextSize(15);
        port.setTypeface(Ui.medium());
        port.setGravity(Gravity.CENTER);
        port.setBackground(Ui.round(Ui.palette(this).surface, 12, this));
        port.setPadding(dp(12), dp(12), dp(12), dp(12));
        Ui.latin(port);
        add(port, 10);
        port.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable e) {
                Ui.enabled(pairButton, code.code().length() == 6 && e.length() > 0);
            }
        });
        pairFinder = new Finder(this, tvs -> runOnUiThread(() -> {
            for (Finder.Tv t : tvs) {
                if (t.host.equals(host) && t.pairing > 0) {
                    port.setText(number(t.pairing));
                    port.setVisibility(View.GONE);
                    wait.setVisibility(View.GONE);
                    found.setText("✓  " + getString(R.string.pair_port_found, number(t.pairing)));
                    found.setVisibility(View.VISIBLE);
                }
            }
        }));
        pairFinder.start();

        Ui.enabled(pairButton, false);
        pairButton.setOnClickListener(v -> {
            String c = code.code();
            String p = port.getText().toString().trim();
            if (c.length() != 6 || p.isEmpty()) return;
            int pp = Integer.parseInt(p);
            go(() -> pair(host, pp, c, tlsPort));
        });
        foot(pairButton);
        foot(Ui.secondary(this, getString(R.string.back), v -> go(this::showFind)));
        back = () -> go(this::showFind);
        code.post(code::focus);
    }

    private void pair(String host, int pairPort, String code, int tlsPort) {
        stopFinders();
        frame(CONNECT, Hero.Mode.PAIR, getString(R.string.pair_title), getString(R.string.pairing));
        back = null;
        int g = gen;
        work.execute(() -> {
            try {
                Adb a = adb();
                a.pair(host, pairPort, code);
                a.disconnect();
                if (a.connect(host, tlsPort)) {
                    post(g, this::install);
                } else {
                    fail(g, getString(R.string.error_connect, host));
                }
            } catch (Exception e) {
                fail(g, getString(R.string.pair_failed, message(e)));
            }
        });
    }

    // Install

    private Timeline timeline;
    private TextView installStatus;
    TextView titleView, leadView;
    /** Bundled setup steps, mirrored in tools/setup-steps.txt. */
    private List<Setup.Step> steps;
    /** The release's version ("0.5"), and whether every required step went through. */
    String version = "";
    boolean installedOk;
    /** The address of the TV this run is working on, remembered for next time. */
    private String host;
    private Tv tv;
    private File localApk;

    private void install() {
        stopFinders();
        if (host != null) getPreferences(MODE_PRIVATE).edit().putString("last_tv", host).apply();
        frame(INSTALL, Hero.Mode.PROGRESS, getString(R.string.install_title), getString(R.string.install_lead));
        installStatus = Ui.small(this, getString(R.string.install_check_tv));
        installStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(installStatus, 12);
        back = null;
        int g = gen;
        work.execute(() -> {
            try {
                Adb a = adb();
                tv = new Tv(a);
                Adb.Result sdk = a.shell("getprop ro.build.version.sdk");
                int api = 0;
                try { api = Integer.parseInt(sdk.out.trim()); } catch (NumberFormatException ignored) { }
                if (api < 26) {
                    fail(g, getString(R.string.tv_too_old));
                    return;
                }
                tv.recovery = new Recovery(a, getFilesDir(), host, tv);
                steps = Setup.steps(null);
                post(g, () -> {
                    timeline = timeline();
                    add(timeline, 20);
                    add(Timeline.legend(this), 12);
                    timeline.set(0, Timeline.State.WORKING);
                    installPhase(localApk == null ? R.string.install_find_release : R.string.install_check_package, -1);
                });
                PublicRelease.Asset release=localApk==null?PublicRelease.latest(false,getIntent().getStringExtra("release-channel")==null?"latest":getIntent().getStringExtra("release-channel")):null;
                if (release != null) post(g, () -> installPhase(R.string.t_download, 0));
                File apk=localApk!=null?localApk:release.download(getCacheDir(),(done,total)->post(g,()->{
                    installPhase(R.string.t_download, (float) done / total);
                    timeline.note(0, getString(R.string.mb_of, mb(done), mb(total)));
                    if (done == total) installPhase(R.string.install_check_package, -1);
                }));
                android.content.pm.PackageInfo archive=getPackageManager().getPackageArchiveInfo(apk.getPath(),0);
                if(archive==null||!Setup.PACKAGE.equals(archive.packageName)||archive.versionName==null)throw new IOException("Choose a Miqat APK");
                version=archive.versionName;
                long code=android.os.Build.VERSION.SDK_INT>=28?archive.getLongVersionCode():archive.versionCode;
                String onTv=tv.miqatVersion();long installedCode=onTv==null?-1:tv.miqatVersionCode();
                if(onTv!=null&&(installedCode<0||code<installedCode))throw new IOException(getString(R.string.downgrade_kept));
                boolean current=onTv!=null&&onTv.equals(version)&&code==installedCode;
                post(g, () -> {
                    leadView.setText(release!=null&&release.preview?getString(R.string.install_preview_version,version):getString(R.string.install_lead_version,version));
                    if (current) {
                        timeline.set(0, release == null ? Timeline.State.SKIPPED : Timeline.State.CHECKED);
                        timeline.set(1, Timeline.State.CHECKED);
                        timeline.note(1, getString(R.string.already_on_tv, version));
                    } else {
                        timeline.set(0, Timeline.State.WORKING);
                    }
                });
                if (current) {
                    runSteps(g, a);
                    return;
                }
                send(g, a, apk, release != null);
            } catch (Exception e) {
                fail(g, e instanceof PublicRelease.Unavailable?getString(R.string.public_unavailable):getString(R.string.error_install,message(e)));
            }
        });
    }

    private void installPhase(int label, float progress) {
        String text = getString(label);
        if (!text.contentEquals(installStatus.getText())) installStatus.setText(text);
        hero.progress(progress);
    }

    private Timeline timeline() {
        if (steps == null) steps = Setup.steps(null);
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.t_download));
        labels.add(getString(R.string.t_send));
        boolean ar = Ui.arabic(this);
        for (Setup.Step s : steps) labels.add(s.label(ar));
        return new Timeline(this, labels.toArray(new String[0]));
    }

    /** On the worker thread: the APK into the TV, then the setup steps. */
    private void send(int g, Adb a, File apk, boolean downloaded) throws IOException, InterruptedException {
        post(g, () -> {
            timeline.set(0, downloaded ? Timeline.State.CHECKED : Timeline.State.SKIPPED);
            timeline.set(1, Timeline.State.WORKING);
            installPhase(R.string.t_send, 0);
        });
        long size = apk.length();
        String result = a.install(apk, f -> post(g, () -> {
            installPhase(R.string.t_send, f);
            timeline.note(1, getString(R.string.mb_of, mb((long) (f * size)), mb(size)));
            if (f >= 1) installPhase(R.string.install_wait_tv, -1);
        }));
        if (result.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE")) {
            // Keep the installed Home and its data when signatures differ.
            fail(g, getString(R.string.signature_kept));
            return;
        }
        if (!result.startsWith("Success")) {
            fail(g, getString(R.string.install_failed, result));
            return;
        }
        // Checked: the TV now reports the version just sent.
        post(g, () -> installPhase(R.string.install_check_version, -1));
        String now = tv.miqatVersion();
        post(g, () -> timeline.set(1, version.equals(now) ? Timeline.State.CHECKED : Timeline.State.DONE));
        runSteps(g, a);
    }

    /**
     * On the worker thread: each setup step from init to checked. A step whose check already passes is checked
     * as it stands; otherwise its command runs (done) and the check runs again (checked, or failed / not on this TV).
     */
    private void runSteps(int g, Adb a) throws IOException, InterruptedException {
        post(g, () -> installPhase(R.string.install_apply_setup, 0));
        tv.startHome("tv.miqat.home/.MainActivity");
        for (String p : tv.enabledPackages()) if (p.startsWith("com.cvte."))
            throw new IOException("CVTE: select Home and verify a reboot with PC setup before enabling remote helpers.");
        try { tv.recovery.prepareSetup(); } catch (Exception e) { throw new IOException("Could not journal setup changes", e); }
        for (int i = 0; i < steps.size(); i++) {
            Setup.Step s = steps.get(i);
            int row = i + 2;
            float at = (float) (i + 1) / (steps.size() + 1);
            post(g, () -> timeline.set(row, Timeline.State.WORKING));
            Timeline.State end;
            if (s.check != null && a.shell(s.check).ok()) {
                end = Timeline.State.CHECKED;
                post(g, () -> timeline.note(row, getString(R.string.state_was_set)));
            } else {
                boolean ran = a.shell(s.command).ok();
                if (s.check == null) {
                    end = ran ? Timeline.State.DONE : s.required ? Timeline.State.FAILED : Timeline.State.SKIPPED;
                } else {
                    post(g, () -> timeline.set(row, Timeline.State.DONE));
                    end = a.shell(s.check).ok() ? Timeline.State.CHECKED
                            : s.required ? Timeline.State.FAILED : Timeline.State.SKIPPED;
                }
            }
            if (end == Timeline.State.FAILED) {
                installedOk = false;
                post(g, () -> {
                    timeline.set(row, Timeline.State.FAILED);
                    timeline.note(row, getString(R.string.state_failed));
                    hero.set(Hero.Mode.PROBLEM);
                    installStatus.setVisibility(View.GONE);
                    titleView.setText(R.string.done_partly_title);
                    leadView.setText(R.string.steps_failed_lead);
                    foot(Ui.primary(this, getString(R.string.try_again), v -> go(this::install)));
                });
                return;
            }
            Timeline.State shown = end;
            post(g, () -> {
                boolean keepNote = shown == Timeline.State.CHECKED && timeline.hasNote(row);
                timeline.set(row, shown);
                if (keepNote) timeline.note(row, getString(R.string.state_was_set));
                if (shown == Timeline.State.SKIPPED) timeline.note(row, getString(R.string.state_not_here));
                if (shown == Timeline.State.FAILED) timeline.note(row, getString(R.string.state_failed));
                hero.progress(at);
            });
        }
        tv.requireHome();
        installedOk = true;
        post(g, () -> installPhase(R.string.install_setup_checked, 1));
        // A moment on the finished list, then the next stage.
        Thread.sleep(900);
        post(g, () -> new Personalise(this, tv).start());
    }

    /** The end: what was set, and the tip about debugging. */
    void showDone(List<String> summary, boolean oldMiqat) {
        Timeline steps = timeline;
        if (steps != null && steps.getParent() != null) ((ViewGroup) steps.getParent()).removeView(steps);
        boolean ok = installedOk;
        String lead = !ok ? getString(R.string.done_partly_lead)
                : version.isEmpty() ? getString(R.string.done_lead_plain) : getString(R.string.done_lead, version);
        frame(PERSONAL, ok ? Hero.Mode.DONE : Hero.Mode.PROBLEM,
                getString(ok ? R.string.done_title : R.string.done_partly_title), lead);
        if (!ok && steps != null) add(steps, 20);
        if (!summary.isEmpty()) {
            LinearLayout card = Ui.card(this);
            for (int i = 0; i < summary.size(); i++) {
                LinearLayout row = Ui.row(this);
                row.setGravity(Gravity.TOP);
                TextView tick = Ui.text(this, "✓", Ui.palette(this).accent, 15, Ui.semibold());
                tick.setPadding(0, 0, dp(10), 0);
                row.addView(tick);
                row.addView(Ui.text(this, summary.get(i), Ui.palette(this).ink, 15, Ui.medium()), Ui.fill(this));
                card.addView(row, Ui.wide(this, i == 0 ? 0 : 10));
            }
            add(card, 20);
        }
        if (oldMiqat) {
            LinearLayout note = Ui.card(this);
            note.addView(Ui.small(this, getString(R.string.personal_old)));
            add(note, 12);
        }
        LinearLayout tip = Ui.card(this);
        tip.addView(Ui.label(this, getString(R.string.tip_title)));
        tip.addView(Ui.small(this, getString(R.string.tip_body)), Ui.wide(this, 8));
        add(tip, 12);
        boolean embedded = getCallingActivity() != null && getPackageName().equals(getCallingActivity().getPackageName());
        foot(Ui.primary(this, getString(ok && embedded ? R.string.pair_tv : R.string.finish), v -> {
            if (ok && embedded) setResult(RESULT_OK);
            finish();
        }));
        foot(Ui.secondary(this, getString(R.string.another), v -> go(this::showGuide)));
        recoveryActions();
        back = this::finish;
    }

    private void recoveryActions() {
        if (tv == null || tv.recovery == null) return;
        foot(Ui.secondary(this, getString(R.string.export_backup), v -> {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT)
                .setType("application/json").addCategory(android.content.Intent.CATEGORY_OPENABLE)
                .putExtra(android.content.Intent.EXTRA_TITLE, "recovery.json");
            startActivityForResult(i, 80);
        }));
        foot(Ui.secondary(this, getString(R.string.restore_backup), v -> new AlertDialog.Builder(this)
            .setMessage(R.string.restore_backup_help)
            .setPositiveButton(R.string.restore_backup, (d, w) -> work.execute(() -> {
                int g = gen;
                try {
                    tv.recovery.restore(tv);
                    post(g, () -> { installedOk = false; showDone(java.util.Collections.singletonList(getString(R.string.backup_restored)), false); });
                } catch (Exception e) { fail(g, message(e)); }
            })).setNegativeButton(R.string.cancel, null).show()));
    }

    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if(request==81&&(result!=RESULT_OK||data==null||data.getData()==null)&&getIntent().getBooleanExtra("choose-local",false)){finish();return;}
        if (request == 81 && result == RESULT_OK && data != null && data.getData() != null) {
            android.net.Uri uri = data.getData();
            final int generation=gen;
            work.execute(() -> {
                try {
                    File selected=PackageImport.copy(getContentResolver().openInputStream(uri),new File(getCacheDir(),"local-miqat.apk"),200L*1024*1024,file->{
                        android.content.pm.PackageInfo archive=getPackageManager().getPackageArchiveInfo(file.getPath(),0);
                        if(archive==null||!Setup.PACKAGE.equals(archive.packageName))throw new IOException("Choose a Miqat APK");
                    });
                    post(generation,()->{localApk=selected;go(this::showFind);});
                } catch (Exception e) { fail(generation, message(e)); }
            });
            return;
        }
        if (request != 80 || result != RESULT_OK || data == null || data.getData() == null || tv == null || tv.recovery == null) return;
        File backup = tv.recovery.file;
        android.net.Uri uri = data.getData();
        work.execute(() -> {
            try (java.io.OutputStream out = getContentResolver().openOutputStream(uri);
                 java.io.InputStream in = new java.io.FileInputStream(backup)) {
                if (out == null) throw new IOException("Cannot open backup destination");
                byte[] bytes = new byte[8192];
                int n;
                while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            } catch (Exception e) { fail(gen, message(e)); }
        });
    }

    /** The connection, for a last command after the work is done; null if there is none. */
    Adb adbOrNull() {
        return adb;
    }

    // Problems

    private void fail(int g, String message) {
        post(g, () -> {
            stopFinders();
            frame(stage, Hero.Mode.PROBLEM, getString(R.string.problem_title), message);
            LinearLayout card = Ui.card(this);
            card.addView(Ui.label(this, getString(R.string.check_title)));
            card.addView(Ui.small(this, getString(R.string.check_body)), Ui.wide(this, 8));
            add(card, 20);
            foot(Ui.primary(this, getString(R.string.try_again), v -> go(this::showFind)));
            foot(Ui.secondary(this, getString(R.string.back), v -> leavePreparation()));
            recoveryActions();
            back = this::leavePreparation;
        });
    }

    private static String message(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    // The frame

    /** Moves to another screen on the user's word: workers still running for the old one are ignored. */
    void go(Runnable screen) {
        gen++;
        screen.run();
    }

    /** Runs on the main thread if the user is still on the screen that started the work. */
    void post(int g, Runnable r) {
        runOnUiThread(() -> {
            if (g == gen) r.run();
        });
    }

    void frame(int stageNow, Hero.Mode mode, String title, String lead) {
        stage = stageNow;
        tvList = null;
        help = null;
        content.removeAllViews();
        footer.removeAllViews();
        footer.setVisibility(View.GONE);
        stages(stageNow);
        hero.set(mode);
        content.addView(hero, Ui.wide(this, 8));
        titleView = Ui.title(this, title);
        content.addView(titleView, Ui.wide(this, 18));
        leadView = Ui.lead(this, lead);
        content.addView(leadView, Ui.wide(this, 10));
        scroll.scrollTo(0, 0);
        content.animate().cancel();content.setAlpha(1f);content.setTranslationY(0);
        if(motionEnabled()){content.setAlpha(0f);content.setTranslationY(dp(8));content.animate().alpha(1f).translationY(0).setDuration(220).start();}
    }

    void add(View v, int topDp) {
        content.addView(v, Ui.wide(this, topDp));
    }

    void foot(View v) {
        footer.setVisibility(View.VISIBLE);
        footer.addView(v, Ui.wide(this, footer.getChildCount() == 0 ? 0 : 10));
    }

    /** Prepare · Connect · Install: done in plaster, now in gold, to come in lapis. */
    private void stages(int now) {
        stages.removeAllViews();
        int[] names = {R.string.stage_prepare, R.string.stage_connect, R.string.stage_install, R.string.stage_personal};
        for (int i = 0; i < names.length; i++) {
            LinearLayout s = Ui.column(this);
            View bar = new View(this);
            bar.setBackground(Ui.round(i < now ? Ui.palette(this).ink : i == now ? Ui.palette(this).accent : Ui.palette(this).raised, 2, this));
            s.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));
            TextView t = Ui.text(this, number(i + 1) + "  " + getString(names[i]),
                    i < now ? Ui.palette(this).ink : i == now ? Ui.palette(this).accent : Ui.palette(this).muted, 12, i == now ? Ui.semibold() : Ui.medium());
            s.addView(t, Ui.wide(this, 8));
            LinearLayout.LayoutParams lp = Ui.fill(this);
            if (i > 0) lp.setMarginStart(dp(8));
            stages.addView(s, lp);
        }
    }

    /** A numbered line, with what to find on the TV under it (in both languages) if there is one. */
    private View numbered(int n, String text, View tv) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        TextView b = Ui.badge(this, number(n));
        row.addView(b);
        LinearLayout words = Ui.column(this);
        words.setPaddingRelative(dp(12), dp(4), 0, 0);
        words.addView(Ui.text(this, text, Ui.palette(this).ink, 15, Ui.medium()));
        if (tv != null) words.addView(tv, Ui.wide(this, 8));
        row.addView(words, Ui.fill(this));
        return row;
    }

    /** A small spinning mark and a line of status, centred. */
    View waiting(String text) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(18), dp(18));
        lp.setMarginEnd(dp(10));
        row.addView(Timeline.spinner(this), lp);
        row.addView(Ui.small(this, text));
        return row;
    }

    private void stopFinders() {
        finder.stop();
        if (pairFinder != null) pairFinder.stop();
        pairFinder = null;
    }

    private synchronized Adb adb() throws Exception {
        if (adb == null) adb = new Adb(this);
        return adb;
    }

    /** Latin digits whatever the language, to match what the TV shows. */
    private static String number(long n) {
        return String.format(Locale.US, "%d", n);
    }

    private static String mb(long bytes) {
        return String.format(Locale.US, "%.1f", bytes / 1_048_576f);
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    /** A TV outline with Miqat’s small arch on it, for the found-TV cards. */
    private static final class TvGlyph extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        TvGlyph(Context c) {
            super(c);
            setBackground(Ui.round(Ui.palette(getContext()).raised, 12, c));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setStrokeWidth(w * 0.05f);
            p.setColor(Ui.palette(getContext()).ink);
            r.set(w * 0.2f, h * 0.24f, w * 0.8f, h * 0.66f);
            c.drawRoundRect(r, w * 0.05f, w * 0.05f, p);
            c.drawLine(w * 0.38f, h * 0.78f, w * 0.62f, h * 0.78f, p);
            p.setColor(Ui.palette(getContext()).accent);
            android.graphics.Path a = new android.graphics.Path();
            MiqatShape.set(a, w * .18f, h * .3f, false);
            a.offset(w * .41f, h * .29f);
            c.drawPath(a, p);
        }
    }
}

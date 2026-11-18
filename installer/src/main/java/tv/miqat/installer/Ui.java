package tv.miqat.installer;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Miqat’s Mihrab look (shared/design/mihrab/tokens.json) for a phone: lapis ground, gold accent, plaster text, Alexandria
 * for both interface and headings. Small factories, so the screens in
 * MainActivity read as layout.
 */
final class Ui {
    private Ui() {}

    static final int LAPIS_900 = InstallerColors.LAPIS_900, LAPIS_800 = InstallerColors.LAPIS_800, LAPIS_700 = InstallerColors.LAPIS_700,
            GOLD_400 = InstallerColors.GOLD_400, GOLD_200 = InstallerColors.GOLD_200, PLASTER = InstallerColors.PLASTER_100, MIST = InstallerColors.MIST_400,
            LINE_SOFT = (PLASTER & 0xffffff) | 0x40000000, GOLD_SOFT = (GOLD_400 & 0xffffff) | 0x26000000, CORAL = 0xffe39a86, CORAL_SOFT = 0x26e39a86;

    private static Typeface light, regular, medium, semibold, display;

    static void init(Context c) {
        if (light == null) {
            light = c.getResources().getFont(R.font.alexandria_light);
            regular = c.getResources().getFont(R.font.alexandria_regular);
            medium = c.getResources().getFont(R.font.alexandria_medium);
            semibold = c.getResources().getFont(R.font.alexandria_semibold);
        }
        display = Typeface.create(regular, Typeface.BOLD);
    }

    /** Whether the Arabic strings are in use (values-ar), which is also what decides the title face. */
    static boolean arabic(Context c) {
        return "ar".equals(c.getString(R.string.lang));
    }

    static Typeface medium() { return medium; }
    static Typeface semibold() { return semibold; }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, CharSequence s, int color, float sp, Typeface face) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(face);
        t.setLineSpacing(0, 1.3f);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView title(Context c, CharSequence s) {
        TextView t = text(c, s, PLASTER, 24, display);
        t.setGravity(Gravity.START);
        t.setLineSpacing(0, 1.1f);
        return t;
    }

    static TextView lead(Context c, CharSequence s) {
        TextView t = text(c, s, MIST, 15, regular);
        t.setGravity(Gravity.START);
        return t;
    }

    static TextView label(Context c, CharSequence s) { return text(c, s, PLASTER, 16, medium); }

    static TextView small(Context c, CharSequence s) { return text(c, s, MIST, 13, light); }

    /** Numbers (ports, codes, addresses) stay in Latin digits and left-to-right, as the TV shows them. */
    static TextView latin(TextView t) {
        t.setTextDirection(View.TEXT_DIRECTION_LTR);
        t.setTextLocale(java.util.Locale.US);
        return t;
    }

    static GradientDrawable round(int fill, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** A lapis-800 card with a soft line, radius 18. */
    static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        GradientDrawable bg = round(LAPIS_800, 24, c);
        l.setBackground(bg);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** Pressable: a ripple over the given background. */
    static void pressable(View v, GradientDrawable bg, int ripple) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), bg, bg));
        v.setClickable(true);
        v.setFocusable(true);
    }

    static TextView primary(Context c, CharSequence s, View.OnClickListener click) {
        TextView b = text(c, s, LAPIS_900, 16, semibold);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 54));
        b.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
        pressable(b, round(GOLD_400, 27, c), 0x40033B44);
        b.setOnClickListener(click);
        return b;
    }

    static TextView secondary(Context c, CharSequence s, View.OnClickListener click) {
        TextView b = text(c, s, GOLD_400, 15, medium);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 50));
        b.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12));
        GradientDrawable bg = round(LAPIS_800, 24, c);
        pressable(b, bg, 0x33F5EEDC);
        b.setOnClickListener(click);
        return b;
    }

    static void enabled(TextView primary, boolean on) {
        primary.setEnabled(on);
        primary.setAlpha(on ? 1f : 0.4f);
    }

    /** A pill: gold on a gold tint (accent), or mist on lapis-700 (neutral). */
    static TextView chip(Context c, CharSequence s, boolean accent) {
        TextView t = text(c, s, accent ? GOLD_400 : PLASTER, 12, medium);
        t.setBackground(round(accent ? GOLD_SOFT : LAPIS_700, 99, c));
        t.setPadding(dp(c, 10), dp(c, 5), dp(c, 10), dp(c, 5));
        t.setSingleLine();
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** A TV menu path, as a chip the user can match against the screen. */
    static TextView path(Context c, CharSequence s) {
        TextView t = text(c, s, GOLD_200, 13, regular);
        t.setBackground(round(LAPIS_700, 8, c));
        t.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
        return t;
    }

    /**
     * Something written on the TV, in both languages, since the TV's language isn't known yet: the phone's
     * language first, the other under it. Each line keeps its own direction.
     */
    static LinearLayout tvText(Context c, int en, int ar) {
        boolean phoneAr = arabic(c);
        LinearLayout box = column(c);
        box.setBackground(round(LAPIS_700, 10, c));
        box.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        int[] order = phoneAr ? new int[] {ar, en} : new int[] {en, ar};
        for (int i = 0; i < 2; i++) {
            boolean lineAr = order[i] == ar;
            TextView t = text(c, c.getString(order[i]), i == 0 ? GOLD_200 : MIST, i == 0 ? 14 : 12.5f,
                    i == 0 ? medium : regular);
            t.setTextDirection(lineAr ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
            t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            box.addView(t, wide(c, i == 0 ? 0 : 4));
        }
        return box;
    }

    /** A gold disc with a number, for numbered steps. */
    static TextView badge(Context c, String number) {
        TextView t = text(c, number, LAPIS_900, 14, semibold);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(GOLD_400);
        t.setBackground(g);
        int s = dp(c, 28);
        t.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return t;
    }

    static LinearLayout.LayoutParams wide(Context c, float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    static LinearLayout.LayoutParams fill(Context c) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }
}

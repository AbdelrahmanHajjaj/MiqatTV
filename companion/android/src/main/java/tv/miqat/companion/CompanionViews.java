package tv.miqat.companion;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

import tv.miqat.companion.core.CompanionContract;

/** Native Miqat surfaces and accessible controls, shared by the phone journeys. */
final class CompanionViews {
    private final MainActivity a;

    CompanionViews(MainActivity activity) {
        a = activity;
    }

    AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(a) {
            @Override
            public AlertDialog create() {
                AlertDialog dialog = super.create();
                dialog.getWindow().setBackgroundDrawable(background(a.surface, 24));
                dialog.setOnShowListener(ignored -> styleDialog(dialog));
                return dialog;
            }
        };
    }

    void styleDialog(AlertDialog dialog) {
        dialog.getWindow().setBackgroundDrawable(background(a.surface, 24));
        for (int which :
                new int[] {
                    AlertDialog.BUTTON_POSITIVE,
                    AlertDialog.BUTTON_NEGATIVE,
                    AlertDialog.BUTTON_NEUTRAL
                }) {
            Button button = dialog.getButton(which);
            if (button != null) button.setTextColor(a.accent);
        }
    }

    int dp(float value) {
        return Math.round(a.getResources().getDisplayMetrics().density * value);
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.START);
        return l;
    }

    TextView text(String value, int size, int color) {
        TextView v = new TextView(a);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(a.regular);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        v.setLayoutDirection(a.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        v.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        v.setGravity(Gravity.START);
        v.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        v.setPadding(0, dp(5), 0, dp(5));
        return v;
    }

    TextView heading(String value) {
        TextView v = text(value, 26, a.ink);
        v.setTypeface(a.heading);
        return v;
    }

    GradientDrawable background(int fill, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        return g;
    }

    LinearLayout card(LinearLayout parent) {
        LinearLayout c = column();
        c.setBackground(background(a.surface, 24));
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        parent.addView(c, lp);
        return c;
    }

    Button button(LinearLayout parent, String label, Runnable action) {
        Button b = new Button(a);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(a.accent);
        b.setTypeface(a.regular);
        b.setMinHeight(dp(48));
        GradientDrawable shape = background(a.surface, 24);
        b.setBackgroundTintList(null);
        b.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf((a.accent & 0x00ffffff) | 0x33000000), shape, null));
        b.setElevation(0);
        b.setStateListAnimator(null);
        b.setTextColor(
                new ColorStateList(
                        new int[][] {new int[] {android.R.attr.state_enabled}, new int[] {}},
                        new int[] {a.accent, a.muted}));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(5);
        lp.bottomMargin = dp(5);
        parent.addView(b, lp);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    void leadingIcon(Button button, String name) {
        android.graphics.drawable.Drawable icon = Icons.get(a, name, a.accent);
        button.setCompoundDrawablesRelative(icon, null, null, null);
        button.setCompoundDrawablePadding(dp(8));
        button.setGravity(Gravity.CENTER);
        button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        button.setSingleLine(true);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    int width =
                            icon.getBounds().width()
                                    + button.getCompoundDrawablePadding()
                                    + Math.round(
                                            button.getPaint()
                                                    .measureText(button.getText().toString()));
                    int inset = Math.max(dp(16), (right - left - width) / 2);
                    if (button.getPaddingStart() != inset || button.getPaddingEnd() != inset)
                        button.setPaddingRelative(
                                inset, button.getPaddingTop(), inset, button.getPaddingBottom());
                });
    }

    Button bindAction(Button button, String id) {
        if (!CompanionContract.INSTANCE.getActions().contains(id))
            throw new IllegalArgumentException("Unknown companion action: " + id);
        button.setTag(R.id.companion_action, id);
        return button;
    }

    LinearLayout horizontal(LinearLayout parent) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    void square(View view) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.setMarginStart(dp(3));
        lp.setMarginEnd(dp(3));
        view.setLayoutParams(lp);
    }

    void flex(View view) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMarginStart(dp(3));
        lp.setMarginEnd(dp(3));
        view.setLayoutParams(lp);
    }

    Button iconButton(LinearLayout parent, String icon, String label, Runnable action) {
        Button button = button(parent, "", action);
        square(button);
        button.setContentDescription(label);
        button.setTooltipText(label);
        button.setForeground(Icons.get(a, icon, a.accent));
        button.setForegroundGravity(Gravity.CENTER);
        button.setForegroundTintList(
                new ColorStateList(
                        new int[][] {new int[] {android.R.attr.state_enabled}, new int[] {}},
                        new int[] {a.accent, a.muted}));
        button.setPadding(0, 0, 0, 0);
        button.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf((a.accent & 0x00ffffff) | 0x22000000),
                        background(Color.TRANSPARENT, 24),
                        null));
        return button;
    }

    LinearLayout navigationRow(
            LinearLayout parent, String icon, String title, String subtitle, Runnable action) {
        LinearLayout row = horizontal(parent);
        row.setMinimumHeight(dp(72));
        row.setPadding(dp(18), dp(10), dp(18), dp(10));
        row.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf((a.accent & 0x00ffffff) | 0x22000000),
                        background(a.surface, 20),
                        null));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
        lp.bottomMargin = dp(8);
        ImageView glyph = new ImageView(a);
        glyph.setImageDrawable(Icons.get(a, icon, a.accent));
        glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams image = new LinearLayout.LayoutParams(dp(26), dp(26));
        image.setMarginEnd(dp(14));
        row.addView(glyph, image);
        LinearLayout labels = column();
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        labels.addView(text(title, 16, a.ink));
        if (!subtitle.isEmpty()) labels.addView(text(subtitle, 12, a.muted));
        ImageView arrow = new ImageView(a);
        arrow.setImageDrawable(Icons.get(a, "back", a.muted));
        arrow.setRotation(a.ar ? 0 : 180);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(20), dp(20)));
        row.setFocusable(true);
        row.setContentDescription(title + (subtitle.isEmpty() ? "" : ", " + subtitle));
        row.setOnClickListener(v -> action.run());
        return row;
    }

    String sectionIcon(String id) {
        switch (id) {
            case "prayer":
            case "prayer-day":
            case "offsets":
                return "compass";
            case "reminders":
            case "tests":
                return "bell";
            case "appearance":
                return "mihrab";
            case "display":
                return "sun";
            case "radio":
                return "radio";
            case "network":
                return "wifi";
            case "favourites":
                return "star";
            case "device":
                return "tv";
            default:
                return "gear";
        }
    }

    boolean motionEnabled() {
        return android.animation.ValueAnimator.areAnimatorsEnabled()
                && (a.phoneUi == null
                        || a.phoneUi.settings == null
                        || !a.phoneUi.settings.value("display.reduce-motion").equals("true"));
    }

    void disableContent(View view) {
        view.setEnabled(false);
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
                disableContent(((ViewGroup) view).getChildAt(i));
    }
}

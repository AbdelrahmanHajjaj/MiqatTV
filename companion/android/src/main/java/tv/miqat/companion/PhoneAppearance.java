package tv.miqat.companion;

import android.app.AlertDialog;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONObject;

/** Shared real artwork composition for the live prayer hero and appearance preview. */
final class PhoneAppearance {
    private final MainActivity a;
    private String artworkId = "";
    private Bitmap artwork;

    PhoneAppearance(MainActivity activity) {
        a = activity;
    }

    LinearLayout hero(LinearLayout parent, PhoneStore settings) throws Exception {
        FrameLayout frame = new FrameLayout(a);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.bottomMargin = a.views.dp(16);
        parent.addView(frame, size);
        JSONObject palette =
                a.catalog
                        .data
                        .getJSONObject("themes")
                        .getJSONObject(settings.value("appearance.theme"))
                        .getJSONObject("colors");
        int ground = Color.parseColor(palette.getString("lapis-900")),
                surface = Color.parseColor(palette.getString("lapis-800"));
        String id = settings.value("appearance.background");
        if (!id.equals("plain")) {
            if (!id.equals(artworkId)) {
                try (java.io.InputStream in = a.getAssets().open("backgrounds/" + id + ".jpg")) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    Bitmap next = BitmapFactory.decodeStream(in, null, options);
                    if (next == null) throw new java.io.IOException("Invalid artwork");
                    artwork = next;
                    artworkId = id;
                }
            }
        }
        LinearLayout content = a.views.column();
        content.setPadding(a.views.dp(20), a.views.dp(20), a.views.dp(20), a.views.dp(20));
        content.setBackground(
                new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] {
                            (ground & 0xffffff) | 0x99000000, (ground & 0xffffff) | 0xe5000000
                        }));
        frame.addView(content, new FrameLayout.LayoutParams(-1, -2));
        Drawable shape = a.views.background(surface, 28);
        frame.setBackground(
                id.equals("plain")
                        ? shape
                        : new LayerDrawable(new Drawable[] {shape, new Backdrop(artwork)}));
        frame.setClipToOutline(true);
        return content;
    }

    void preview(LinearLayout parent, PhoneStore settings) throws Exception {
        parent.addView(a.views.text(a.t("setup_review"), 13, a.muted));
        LinearLayout content = hero(parent, settings);
        content.setTag("phone.appearance.preview");
        a.phoneUi.home.nextPrayer(content, System.currentTimeMillis(), false);
        JSONObject colors =
                a.catalog
                        .data
                        .getJSONObject("themes")
                        .getJSONObject(settings.value("appearance.theme"))
                        .getJSONObject("colors");
        for (int i = 0; i < content.getChildCount(); i++)
            if (content.getChildAt(i) instanceof TextView) {
                String key = i == 0 ? "mist-400" : i == 1 ? "plaster-100" : "gold-400";
                ((TextView) content.getChildAt(i))
                        .setTextColor(Color.parseColor(colors.getString(key)));
            }
    }

    void carousel(LinearLayout parent, String id, boolean guided) throws Exception {
        TextView heading =
                a.views.text(a.t(a.phoneUi.data.controls.get(id).getString("label")), 14, a.muted);
        heading.setTag("phone." + id + ".edit");
        heading.setMinHeight(a.views.dp(48));
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setFocusable(true);
        if (!guided) parent.addView(heading);
        java.util.List<String> options = a.phoneUi.data.options(a.phoneUi.data.controls.get(id));
        HorizontalScrollView scroll = new HorizontalScrollView(a);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setTag("phone." + id);
        heading.setOnClickListener(v -> {
            try {
                choose(id, options);
            } catch (Exception invalid) {
                a.notice("invalid");
            }
        });
        LinearLayout track = new LinearLayout(a);
        track.setPadding(a.views.dp(2), a.views.dp(4), a.views.dp(2), a.views.dp(8));
        scroll.addView(track);
        parent.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        int selected = options.indexOf(a.phoneUi.settings.value(id));
        long now = System.currentTimeMillis();
        tv.miqat.core.PrayerSelection next =
                tv.miqat.companion.core.PhonePrayerRules.INSTANCE.nextPrayer(
                        a.phoneUi.settings.day(now, 0).times,
                        a.phoneUi.settings.day(now, 1).times,
                        now);
        for (String option : options) {
            PhoneStore candidate =
                    a.phoneUi.settings.changed(java.util.Collections.singletonMap(id, option));
            LinearLayout tile = a.views.column();
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(a.views.dp(guided ? 150 : 104), -2);
            size.setMarginEnd(a.views.dp(10));
            track.addView(tile, size);
            LinearLayout art = hero(tile, candidate);
            if (option.equals(a.phoneUi.settings.value(id))) art.setTag("phone.appearance.preview");
            art.setPadding(a.views.dp(10), a.views.dp(12), a.views.dp(10), a.views.dp(12));
            art.setMinimumHeight(a.views.dp(guided ? 240 : 140));
            JSONObject colors =
                    a.catalog
                            .data
                            .getJSONObject("themes")
                            .getJSONObject(candidate.value("appearance.theme"))
                            .getJSONObject("colors");
            int ink = Color.parseColor(colors.getString("plaster-100")),
                    accent = Color.parseColor(colors.getString("gold-400"));
            art.addView(a.views.text(a.t("phone_next"), 11, ink));
            if (next != null) {
                art.addView(a.views.text(a.phoneUi.data.prayerName(next.getNext(), a.ar), 12, ink));
                art.addView(
                        a.views.text(a.phoneUi.time(next.getNextTime()), guided ? 20 : 14, accent));
            }
            TextView name = a.views.text(a.phoneUi.value(id, option), 12, a.ink);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tile.addView(name);
            tile.setTag("phone." + id + ".option." + option);
            tile.setContentDescription(name.getText());
            tile.setFocusable(true);
            tile.setSelected(option.equals(a.phoneUi.settings.value(id)));
            tile.setAlpha(tile.isSelected() ? 1f : .7f);
            if (tile.isSelected()) {
                GradientDrawable border = a.views.background(Color.TRANSPARENT, 24);
                border.setStroke(a.views.dp(2), a.accent);
                tile.setForeground(border);
            }
            tile.setOnClickListener(v -> {
                if (guided) a.phoneUi.save(id, option);
                else
                    try {
                        choose(id, options, option);
                    } catch (Exception invalid) {
                        a.notice("invalid");
                    }
            });
        }
        scroll.post(() -> {
            int limit = Math.max(0, track.getWidth() - scroll.getWidth());
            int offset = Math.max(0, selected) * a.views.dp(guided ? 160 : 114);
            scroll.scrollTo(Math.max(0, Math.min(limit, a.ar ? limit - offset : offset)), 0);
        });
    }

    void choose(String id, java.util.List<String> options) throws Exception {
        choose(id, options, a.phoneUi.settings.value(id));
    }

    private void choose(String id, java.util.List<String> options, String initial)
            throws Exception {
        LinearLayout layout = a.views.column();
        layout.setPadding(a.views.dp(16), a.views.dp(8), a.views.dp(16), a.views.dp(8));
        layout.setLayoutDirection(a.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        LinearLayout sample = a.views.column();
        layout.addView(sample);
        String[] selected = {initial};
        Runnable refresh =
                () -> {
                    try {
                        sample.removeAllViews();
                        preview(
                                sample,
                                a.phoneUi.settings.changed(
                                        java.util.Collections.singletonMap(id, selected[0])));
                    } catch (Exception invalid) {
                        a.notice("invalid");
                    }
                };
        refresh.run();
        HorizontalScrollView scroller = new HorizontalScrollView(a);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setTag("phone.appearance.choices");
        LinearLayout choices = new LinearLayout(a);
        scroller.addView(choices);
        for (String option : options) {
            Button choice =
                    a.views.button(
                            choices,
                            a.phoneUi.value(id, option),
                            () -> {
                                selected[0] = option;
                                refresh.run();
                                for (int i = 0; i < choices.getChildCount(); i++) {
                                    View item = choices.getChildAt(i);
                                    item.setSelected(item.getTag().equals(option));
                                    item.setAlpha(item.isSelected() ? 1f : .55f);
                                }
                            });
            choice.setTag(option);
            choice.setSelected(option.equals(initial));
            choice.setAlpha(choice.isSelected() ? 1f : .55f);
            choice.setLayoutParams(new LinearLayout.LayoutParams(a.views.dp(150), -2));
        }
        layout.addView(scroller);
        ScrollView overflow = new ScrollView(a);
        overflow.addView(layout);
        AlertDialog dialog =
                a.views
                        .dialog()
                        .setTitle(a.t(a.phoneUi.data.controls.get(id).getString("label")))
                        .setView(overflow)
                        .setNegativeButton(a.t("cancel"), null)
                        .setPositiveButton(a.t("apply"), (d, w) -> a.phoneUi.save(id, selected[0]))
                        .create();
        a.phoneUi.showEditor(dialog);
        dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.getWindow().setLayout(-1, -2);
    }

    /** A background must not give a wrap-content card the bitmap's intrinsic height. */
    private static final class Backdrop extends Drawable {
        private final Bitmap bitmap;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Rect source = new Rect();

        Backdrop(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;
            if (bounds.height() > bounds.width()) {
                int sky = bitmap.getPixel(bitmap.getWidth() / 2, 0);
                canvas.drawColor(sky);
                float height = bounds.width() * (float) bitmap.getHeight() / bitmap.getWidth();
                RectF destination =
                        new RectF(bounds.left, bounds.bottom - height, bounds.right, bounds.bottom);
                canvas.drawBitmap(bitmap, null, destination, paint);
                return;
            }
            float scale =
                    Math.max(
                            (float) bounds.width() / bitmap.getWidth(),
                            (float) bounds.height() / bitmap.getHeight());
            int width = Math.min(bitmap.getWidth(), Math.round(bounds.width() / scale)),
                    height = Math.min(bitmap.getHeight(), Math.round(bounds.height() / scale));
            int x = (bitmap.getWidth() - width) / 2, y = (bitmap.getHeight() - height) / 2;
            source.set(x, y, x + width, y + height);
            canvas.drawBitmap(bitmap, source, bounds, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}

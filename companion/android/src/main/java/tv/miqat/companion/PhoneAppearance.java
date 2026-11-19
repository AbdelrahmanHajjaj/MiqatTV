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

    void choose(String id, java.util.List<String> options) throws Exception {
        LinearLayout layout = a.views.column();
        layout.setPadding(a.views.dp(16), a.views.dp(8), a.views.dp(16), a.views.dp(8));
        layout.setLayoutDirection(a.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        LinearLayout sample = a.views.column();
        layout.addView(sample);
        String[] selected = {a.phoneUi.settings.value(id)};
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
        String[] labels = new String[options.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = a.phoneUi.value(id, options.get(i));
        ListView choices = new ListView(a);
        choices.setTag("phone.appearance.choices");
        choices.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        ArrayAdapter<String> adapter = a.views.choices(java.util.Arrays.asList(labels));
        choices.setAdapter(adapter);
        choices.setItemChecked(options.indexOf(selected[0]), true);
        choices.setSelection(options.indexOf(selected[0]));
        layout.addView(
                choices,
                new LinearLayout.LayoutParams(
                        -1,
                        Math.min(
                                a.views.dp(240),
                                a.getResources().getDisplayMetrics().heightPixels / 3)));
        ScrollView scroller = new ScrollView(a);
        scroller.addView(layout);
        AlertDialog dialog =
                a.views
                        .dialog()
                        .setTitle(a.t(a.phoneUi.data.controls.get(id).getString("label")))
                        .setView(scroller)
                        .setNegativeButton(a.t("cancel"), null)
                        .setPositiveButton(a.t("apply"), (d, w) -> a.phoneUi.save(id, selected[0]))
                        .create();
        choices.setOnItemClickListener((parent, view, position, item) -> {
            selected[0] = options.get(position);
            refresh.run();
        });
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

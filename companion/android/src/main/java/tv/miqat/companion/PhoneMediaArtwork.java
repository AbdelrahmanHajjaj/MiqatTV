package tv.miqat.companion;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;

import org.json.JSONObject;

/** Selected phone scene and canonical arch for the Adhan screen and media sessions. */
final class PhoneMediaArtwork extends LayerDrawable {
    PhoneMediaArtwork(Context context, PhoneStore settings) throws Exception {
        this(context, settings, false);
    }

    private PhoneMediaArtwork(Context context, PhoneStore settings, boolean systemShade) throws Exception {
        super(layers(context, settings, systemShade));
    }

    static Bitmap radioCover(Context context, PhoneStore settings) throws Exception {
        return new PhoneMediaArtwork(context, settings, true).cover();
    }

    private static Drawable[] layers(Context context, PhoneStore settings, boolean systemShade) throws Exception {
        JSONObject colors = settings.data.colors(settings.value("appearance.theme"));
        int ground = Color.parseColor(colors.getString("lapis-900"));
        int accent = Color.parseColor(colors.getString("gold-400"));
        PhoneBackdrop.Artwork photo = null;
        try {
            photo = PhoneBackdrop.read(context, settings.value("appearance.background"));
        } catch (java.io.IOException unavailable) {
            // Artwork failure must not prevent an otherwise valid adhan.
            android.util.Log.w("MiqatArtwork", "Selected background unavailable", unavailable);
        }
        Drawable scene = photo == null ? new ColorDrawable(ground) : new PhoneBackdrop(photo);
        // Android's media card applies its own contrast scrim over this bitmap.
        int topShade = systemShade ? 0x22000000 : 0x55000000;
        int bottomShade = systemShade ? 0x55000000 : 0xbb000000;
        return new Drawable[] {
            new ColorDrawable(ground),
            scene,
            new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {(ground & 0xffffff) | topShade, (ground & 0xffffff) | bottomShade}),
            new Arch(ground, accent)
        };
    }

    Bitmap cover() {
        Bitmap cover = Bitmap.createBitmap(512, 288, Bitmap.Config.ARGB_8888);
        setBounds(0, 0, cover.getWidth(), cover.getHeight());
        draw(new Canvas(cover));
        return cover;
    }

    private static final class Arch extends Drawable {
        private final int ground, accent;
        private final Path outer = new Path(), inner = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int alpha = 255;

        Arch(int ground, int accent) {
            this.ground = ground;
            this.accent = accent;
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            float width = bounds.width() * .90f, height = bounds.height() * .90f;
            MiqatShape.set(outer, width, height, false);
            MiqatShape.set(inner, width, height, true);
            outer.offset(bounds.left + bounds.width() * .05f, bounds.top + bounds.height() * .05f);
            inner.offset(bounds.left + bounds.width() * .05f, bounds.top + bounds.height() * .05f);
            paint.setStrokeWidth(Math.max(1f, Math.min(bounds.width(), bounds.height()) * .003f));
        }

        @Override
        public void draw(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color(ground, 70));
            canvas.drawPath(outer, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(color(accent, 230));
            canvas.drawPath(outer, paint);
            paint.setColor(color(accent, 110));
            canvas.drawPath(inner, paint);
        }

        private int color(int rgb, int opacity) {
            return (rgb & 0xffffff) | ((opacity * alpha / 255) << 24);
        }

        @Override
        public void setAlpha(int value) {
            alpha = value;
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

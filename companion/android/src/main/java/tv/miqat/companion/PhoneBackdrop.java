package tv.miqat.companion;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** A background must not give a wrap-content card the bitmap's intrinsic height. */
final class PhoneBackdrop extends Drawable {
    private final Bitmap bitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect source = new Rect();

    static Bitmap read(android.content.Context context, String id) throws java.io.IOException {
        if (id.equals("plain")) return null;
        try (java.io.InputStream input = context.getAssets().open("backgrounds/" + id + ".jpg")) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) throw new java.io.IOException("Invalid background artwork");
            return bitmap;
        }
    }

    PhoneBackdrop(Bitmap bitmap) {
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
            float fadeEnd = destination.top + height * .25f;
            paint.setShader(
                    new LinearGradient(
                            bounds.left,
                            destination.top,
                            bounds.left,
                            fadeEnd,
                            sky,
                            sky & 0xffffff,
                            Shader.TileMode.CLAMP));
            canvas.drawRect(bounds.left, destination.top, bounds.right, fadeEnd, paint);
            paint.setShader(null);
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

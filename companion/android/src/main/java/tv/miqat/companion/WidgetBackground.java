package tv.miqat.companion;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;

import org.json.JSONObject;

/** Bounded widget background image; foreground text stays native and accessible. */
final class WidgetBackground {
    private WidgetBackground() {}

    static Bitmap create(
            Context context,
            PhoneStore settings,
            WidgetOptions options,
            JSONObject colors,
            int width,
            int height)
            throws Exception {
        width = Math.max(1, Math.min(1200, width));
        height = Math.max(1, Math.min(1200, height));
        float scale = Math.min(2f, 384f / Math.max(width, height));
        Bitmap image =
                Bitmap.createBitmap(
                        Math.max(1, Math.round(width * scale)),
                        Math.max(1, Math.round(height * scale)),
                        Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.scale(scale, scale);
        int dimension =
                options.shape.equals("square")
                        ? R.dimen.widget_corner_square
                        : options.shape.equals("soft")
                                ? R.dimen.widget_corner_soft
                                : R.dimen.widget_corner_rounded;
        float radius =
                context.getResources().getDimension(dimension)
                        / context.getResources().getDisplayMetrics().density;
        radius = Math.min(radius, Math.min(width, height) / 4f);
        Path outline = new Path();
        outline.addRoundRect(0, 0, width, height, radius, radius, Path.Direction.CW);
        canvas.clipPath(outline);
        canvas.drawColor(Color.parseColor(colors.getString("lapis-800")));
        String scene =
                options.scene.equals("app")
                        ? settings.value("appearance.background")
                        : options.scene;
        if (!scene.equals("plain")) {
            try {
                PhoneBackdrop backdrop = new PhoneBackdrop(PhoneBackdrop.read(context, scene));
                backdrop.setBounds(0, 0, width, height);
                backdrop.draw(canvas);
                int ground = Color.parseColor(colors.getString("lapis-900"));
                GradientDrawable shade =
                        new GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM,
                                new int[] {
                                    (ground & 0xffffff) | 0x99000000,
                                    (ground & 0xffffff) | 0xe5000000
                                });
                shade.setBounds(0, 0, width, height);
                shade.draw(canvas);
            } catch (java.io.IOException unavailable) {
                android.util.Log.w("MiqatWidget", "Widget background unavailable", unavailable);
            }
        }
        return image;
    }
}

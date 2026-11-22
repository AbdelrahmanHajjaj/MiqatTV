package tv.miqat.home;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import org.json.JSONObject;
import java.util.*;

/** Local themes; one bounded screen-resolution background shares the existing foreground clock tick. */
final class Appearance {
    static final String[] PRESETS = ThemeCatalog.PRESETS;
    private static final int[] BASE = {Tokens.LAPIS_950, Tokens.LAPIS_900, Tokens.LAPIS_800, Tokens.LAPIS_700,
        Tokens.GOLD_400, Tokens.GOLD_200, Tokens.PLASTER_100, Tokens.MIST_400};
    private static String loaded = "";
    private static boolean reduceMotion;
    static long motion(long duration) { return reduceMotion ? 0 : duration; }
    static final class Style {
        final int[] colors = BASE.clone();
        String mode = ThemeCatalog.DEFAULT_BACKGROUND;
        int lighting = 35;
        ThemeSurface surface = new ThemeSurface("classic", colors);
    }
    private static Style current = new Style();
    static ThemeSurface surface() { return current.surface; }
    static android.graphics.drawable.GradientDrawable panel(int color, float radius) {
        ThemeSurface s = surface();
        android.graphics.drawable.GradientDrawable result = new android.graphics.drawable.GradientDrawable();
        result.setColor(s.fill(color));
        result.setCornerRadius(Ui.px(s.radius(radius)));
        if (s.isPanel(color) && s.stroke > 0) result.setStroke(Ui.ipx(s.stroke), s.edge);
        return result;
    }
    static android.graphics.drawable.GradientDrawable prayerPanel(int color) {
        android.graphics.drawable.GradientDrawable result = panel(color, Tokens.RADIUS_CELL);
        result.setCornerRadius(Ui.px(Math.min(surface().radius(Tokens.RADIUS_CELL), Tokens.SURFACE_PRAYER_RADIUS_MAX)));
        return result;
    }
    private static String bitmapKey = "";
    private static Bitmap bitmap;

    static String key(Context c) {
        return Prefs.of(c).getString("theme", "mihrab") + ":" + Prefs.of(c).getString("theme_json", "")
            + ":" + Prefs.of(c).getString("background", ThemeCatalog.DEFAULT_BACKGROUND) + ":" + Prefs.of(c).getInt("lighting", -1) + ":" + Prefs.of(c).getBoolean("focus_lighting", false);
    }
    static int tint(int color, Map<Integer, Integer> colors) {
        return (color & 0xff000000) | (colors.getOrDefault(color | 0xff000000, color) & 0xffffff);
    }
    static boolean reduced(Context c) { return Prefs.of(c).getBoolean("reduce_motion", false); }

    static int cycleMinutes(Context c) { return AppearanceCycle.minutes(Prefs.of(c).getInt("appearance_cycle", 0)); }

    /** Called only by unobstructed, foreground Home on its existing tick. Never wakes the TV. */
    static boolean cycleIfDue(Context c) {
        android.content.SharedPreferences prefs = Prefs.of(c);
        int minutes = cycleMinutes(c);
        if (minutes == 0) return false;
        long now = System.currentTimeMillis(), at = prefs.getLong("appearance_cycle_at", 0);
        if (AppearanceCycle.needsStart(now, at, minutes)) {
            prefs.edit().putLong("appearance_cycle_at", AppearanceCycle.next(now, minutes)).apply();
            return false;
        }
        if (!AppearanceCycle.due(now, at, minutes)) return false;
        load(c);
        Random random = new Random();
        int theme = AppearanceCycle.pick(PRESETS.length - 1, Arrays.asList(PRESETS).indexOf(prefs.getString("theme", "mihrab")), random);
        int background = AppearanceCycle.pick(ThemeSpec.BACKGROUNDS.length, Arrays.asList(ThemeSpec.BACKGROUNDS).indexOf(current.mode), random);
        prefs.edit().putString("theme", PRESETS[theme]).putString("background", ThemeSpec.BACKGROUNDS[background])
            .putLong("appearance_cycle_at", AppearanceCycle.next(now, minutes)).apply();
        return true;
    }

    static JSONObject checked(String json) throws Exception {
        if (json.length() > 16384) throw new IllegalArgumentException("Theme is too large");
        JSONObject object = new JSONObject(json);
        Set<String> known = new HashSet<>(Arrays.asList("version", "colors", "background", "lighting", "surface"));
        for (Iterator<String> it = object.keys(); it.hasNext();) if (!known.contains(it.next())) throw new IllegalArgumentException("Unknown theme field");
        if (!(object.get("version") instanceof Integer) || object.getInt("version") != 1) throw new IllegalArgumentException("Expected theme version 1");
        if (object.has("lighting") && !(object.get("lighting") instanceof Integer)) throw new IllegalArgumentException("Lighting must be an integer");
        if (object.has("background") && !(object.get("background") instanceof String)) throw new IllegalArgumentException("Background must be text");
        if (object.has("surface") && !(object.get("surface") instanceof String)) throw new IllegalArgumentException("Surface must be text");
        ThemeSpec.surface(object.optString("surface", "classic"));
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (int i = 0; i < BASE.length; i++) colors.put(ThemeSpec.COLORS[i], BASE[i]);
        JSONObject changes = object.has("colors") ? object.getJSONObject("colors") : new JSONObject();
        for (Iterator<String> it = changes.keys(); it.hasNext();) {
            String name = it.next();
            if (!(changes.get(name) instanceof String)) throw new IllegalArgumentException("Colour must be text");
            colors.put(name, ThemeSpec.color(changes.getString(name)));
        }
        ThemeSpec.validate(colors, object.optString("background", ThemeCatalog.DEFAULT_BACKGROUND), object.optInt("lighting", 35), object.optString("surface", "classic"));
        return object;
    }

    static void importTheme(Context c, String json) throws Exception {
        String checked = checked(json).toString();
        if (!Prefs.of(c).edit().putString("theme_json", checked).putString("theme", "custom")
                .remove("background").remove("lighting").commit()) throw new java.io.IOException("Could not save theme");
    }

    private static byte[] read(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] bytes = new byte[4096]; int n;
        while ((n = in.read(bytes)) != -1) {
            if (out.size() + n > 16384) throw new java.io.IOException("Theme too large");
            out.write(bytes, 0, n);
        }
        return out.toByteArray();
    }

    /** Resolves a preview without changing preferences or the active global palette. */
    static Style style(Context c, String name, String background, int light) {
        Style style = new Style();
        try {
            JSONObject object;
            if ("custom".equals(name)) object = checked(Prefs.of(c).getString("theme_json", "{}"));
            else if (Arrays.asList(PRESETS).contains(name) && !"mihrab".equals(name)) {
                try (java.io.InputStream in = c.getAssets().open("themes/" + name + ".json")) {
                    object = checked(new String(read(in), java.nio.charset.StandardCharsets.UTF_8));
                }
            } else object = checked("{\"version\":1}");
            JSONObject changes = object.optJSONObject("colors");
            for (int i = 0; changes != null && i < BASE.length; i++)
                if (changes.has(ThemeSpec.COLORS[i])) style.colors[i] = ThemeSpec.color(changes.getString(ThemeSpec.COLORS[i]));
            style.mode = object.optString("background", ThemeCatalog.DEFAULT_BACKGROUND); style.lighting = object.optInt("lighting", 35);
            style.surface = new ThemeSurface(object.optString("surface", "classic"), style.colors);
        } catch (Exception ignored) { /* Invalid saved data falls back to the original Mihrab. */ }
        if (Arrays.asList(ThemeSpec.BACKGROUNDS).contains(background)) style.mode = background;
        if (light >= 0 && light <= 100) style.lighting = light;
        return style;
    }

    static void load(Context c) {
        reduceMotion = reduced(c);
        String key = key(c);
        if (key.equals(loaded)) return;
        current = style(c, Prefs.of(c).getString("theme", "mihrab"),
            Prefs.of(c).getString("background", ThemeCatalog.DEFAULT_BACKGROUND), Prefs.of(c).getInt("lighting", -1));
        Map<Integer, Integer> colors = new HashMap<>();
        for (int i = 0; i < BASE.length; i++) colors.put(BASE[i], current.colors[i]);
        Palette.apply(colors);
        loaded = key;
        bitmapKey = "";
    }

    static String backgroundKey(Context c) {
        load(c);
        return loaded + ("daylight".equals(current.mode) ? ":" + System.currentTimeMillis() / 60000 : "");
    }

    static Drawable background(Context c) {
        load(c);
        if ("lattice".equals(current.mode)) return Ui.starLattice(c);
        if ("solid".equals(current.mode)) return new android.graphics.drawable.ColorDrawable(current.colors[1]);
        android.util.DisplayMetrics metrics = c.getResources().getDisplayMetrics();
        BackgroundSize size = new BackgroundSize(metrics.widthPixels, metrics.heightPixels);
        String key = backgroundKey(c) + ":" + size.width + "x" + size.height;
        if (bitmap == null || !key.equals(bitmapKey)) { bitmap = scene(c, current, size.width, size.height); bitmapKey = key; }
        return drawable(c, bitmap);
    }

    static Drawable preview(Context c, Style style) {
        if ("lattice".equals(style.mode)) return Ui.starLattice(c, style.colors[1], (style.colors[4] & 0xffffff) | 0x17000000, Tokens.BACKGROUND_TILE_SIZE / 4);
        if ("solid".equals(style.mode)) return new android.graphics.drawable.ColorDrawable(style.colors[1]);
        return drawable(c, scene(c, style, Math.round(Tokens.BACKGROUND_PREVIEW_WIDTH), Math.round(Tokens.BACKGROUND_PREVIEW_HEIGHT)));
    }

    private static Drawable drawable(Context c, Bitmap bitmap) {
        BitmapDrawable result = new BitmapDrawable(c.getResources(), bitmap);
        result.setFilterBitmap(true);
        return result;
    }

    private static Bitmap scene(Context c, Style style, int width, int height) {
        Bitmap next = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(next);
        // Keep the artwork's 480x270 coordinates, rasterising edges at the destination size.
        canvas.scale(width / Tokens.BACKGROUND_PREVIEW_WIDTH, height / Tokens.BACKGROUND_PREVIEW_HEIGHT);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (Arrays.asList(ThemeCatalog.SCENES).contains(style.mode)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (1920 / (options.inSampleSize * 2) >= width
                    && 1080 / (options.inSampleSize * 2) >= height) options.inSampleSize *= 2;
            try (java.io.InputStream in = c.getAssets().open("backgrounds/" + style.mode + ".jpg")) {
                Bitmap landscape = BitmapFactory.decodeStream(in, null, options);
                if (landscape != null) {
                    p.setFilterBitmap(true);
                    canvas.drawBitmap(landscape, null, new RectF(0, 0, 480, 270), p);
                    landscape.recycle();
                    p.setColor(0x33000000);
                    canvas.drawRect(0, 0, 480, 270, p);
                    return next;
                }
            } catch (java.io.IOException ignored) { /* Fall back to the theme ground. */ }
        }
        float hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + Calendar.getInstance().get(Calendar.MINUTE) / 60f;
        float daylight = "daylight".equals(style.mode) ? Math.max(.15f, (float)Math.sin((hour - 6) / 12 * Math.PI)) : .7f;
        int glow = blend(style.colors[1], style.colors[4], style.lighting / 100f * .45f * daylight);
        p.setShader(new LinearGradient(0, 0, 480, 270, new int[]{style.colors[0], style.colors[1], glow}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 480, 270, p);
        p.setShader(new RadialGradient(360, 190, 210, glow, 0, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 480, 270, p);
        p.setShader(null);
        int pattern = 0;
        switch (style.mode) {
            case "arches": pattern = R.drawable.bg_arches; break;
            case "eight_point_stars": pattern = R.drawable.bg_eight_point_stars; break;
            case "mashrabiya": pattern = R.drawable.bg_mashrabiya; break;
            case "arabesque": pattern = R.drawable.bg_arabesque; break;
            case "star_tiles": pattern = R.drawable.bg_star_tiles; break;
            case "rosettes": pattern = R.drawable.bg_rosettes; break;
            case "octagon_lattice": pattern = R.drawable.bg_octagon_lattice; break;
            case "corner_medallions": pattern = R.drawable.bg_corner_medallions; break;
        }
        if (pattern != 0) {
            // Decode only the selected mask, downsampled for the small settings preview.
            // Bypass the drawable cache so cycling eight patterns cannot retain eight HD masks.
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inSampleSize = 1;
            while (Tokens.BACKGROUND_MAX_WIDTH / (options.inSampleSize * 2) >= width
                    && Tokens.BACKGROUND_MAX_HEIGHT / (options.inSampleSize * 2) >= height) options.inSampleSize *= 2;
            Bitmap lines = BitmapFactory.decodeResource(c.getResources(), pattern, options);
            if (lines != null) {
                p.setFilterBitmap(true);
                p.setColorFilter(new PorterDuffColorFilter(style.colors[4], PorterDuff.Mode.SRC_IN));
                p.setAlpha(Math.round(255 * Tokens.BACKGROUND_PATTERN_OPACITY) + style.lighting / 6);
                canvas.drawBitmap(lines, null, new RectF(0, 0, 480, 270), p);
                lines.recycle(); // The software canvas copied the mask into the final cached image.
            }
            return next;
        }
        p.setColor(blend(style.colors[0], glow, .18f));
        if ("mountains".equals(style.mode)) {
            for (int layer = 0; layer < 3; layer++) {
                p.setColor(blend(style.colors[0], glow, .5f - layer * .18f));
                Path peaks = new Path(); peaks.moveTo(0, 270);
                int[][] ridge = {{0,202},{72,155},{148,183},{218,112},{304,186},{390,146},{480,192}};
                for (int[] point : ridge) peaks.lineTo(point[0], point[1] + layer * 24);
                peaks.lineTo(480, 270); peaks.close(); canvas.drawPath(peaks, p);
            }
        } else if ("dunes".equals(style.mode)) {
            for (int layer = 0; layer < 3; layer++) {
                p.setColor(blend(style.colors[0], glow, .55f - layer * .18f));
                Path dune = new Path(); dune.moveTo(0, 194 + layer * 24);
                dune.cubicTo(100, 132 + layer * 24, 178, 255 - layer * 10, 288, 198 + layer * 18);
                dune.cubicTo(370, 156 + layer * 28, 422, 205 + layer * 18, 480, 184 + layer * 26);
                dune.lineTo(480, 270); dune.lineTo(0, 270); dune.close(); canvas.drawPath(dune, p);
            }
        } else if ("halo".equals(style.mode)) {
            p.setShader(new RadialGradient(380, 84, 172,
                new int[]{glow, blend(style.colors[1], style.colors[4], style.lighting / 500f), 0},
                new float[]{0, .5f, 1}, Shader.TileMode.CLAMP));
            canvas.drawCircle(380, 84, 172, p); p.setShader(null);
        } else if ("night_sky".equals(style.mode)) {
            p.setColor(blend(style.colors[1], style.colors[6], .24f + style.lighting / 100f * .7f * .18f));
            for (int i = 0; i < 28; i++) canvas.drawCircle((i * 73 + 11) % 480, 12 + (i * 47 + 19) % 156, i % 5 == 0 ? .8f : .4f, p);
        }
        p.setColor(blend(style.colors[0], glow, .18f));
        Path ridge = new Path(); ridge.moveTo(0, 234); ridge.cubicTo(100, 208, 175, 255, 278, 226);
        ridge.cubicTo(374, 196, 418, 225, 480, 204); ridge.lineTo(480, 270); ridge.lineTo(0, 270); ridge.close();
        if (!"mountains".equals(style.mode) && !"dunes".equals(style.mode) && !"halo".equals(style.mode)) canvas.drawPath(ridge, p);
        return next;
    }
    private static int blend(int a, int b, float f) {
        return Color.rgb((int)(Color.red(a) * (1 - f) + Color.red(b) * f),
            (int)(Color.green(a) * (1 - f) + Color.green(b) * f), (int)(Color.blue(a) * (1 - f) + Color.blue(b) * f));
    }
}

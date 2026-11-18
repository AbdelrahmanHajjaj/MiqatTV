package tv.miqat.home;

// Generated from shared/design geometry by tools/branding.mjs. Do not edit.
final class MiqatShape {
    private MiqatShape() {}
    static void set(android.graphics.Path path, float width, float height, boolean inner) {
        float x = width / 600f, y = height / 900f;
        if (inner) {
        path.reset();
        path.moveTo(22f * x, 896f * y);
        path.lineTo(22f * x, 328f * y);
        path.cubicTo(22f * x, 278f * y, 54f * x, 242f * y, 110f * x, 236f * y);
        path.cubicTo(114f * x, 126f * y, 240f * x, 132f * y, 300f * x, 34f * y);
        path.cubicTo(360f * x, 132f * y, 486f * x, 126f * y, 490f * x, 236f * y);
        path.cubicTo(546f * x, 242f * y, 578f * x, 278f * y, 578f * x, 328f * y);
        path.lineTo(578f * x, 896f * y);
        } else {
        path.reset();
        path.moveTo(4f * x, 896f * y);
        path.lineTo(4f * x, 326f * y);
        path.cubicTo(4f * x, 270f * y, 38f * x, 224f * y, 92f * x, 218f * y);
        path.cubicTo(98f * x, 104f * y, 238f * x, 108f * y, 300f * x, 4f * y);
        path.cubicTo(362f * x, 108f * y, 502f * x, 104f * y, 508f * x, 218f * y);
        path.cubicTo(562f * x, 224f * y, 596f * x, 270f * y, 596f * x, 326f * y);
        path.lineTo(596f * x, 896f * y);
        path.close();
        }
    }

    static void tile(android.graphics.Path path, float width, float height) {
        float x = width / 96f, y = height / 96f;
        path.reset();
        path.moveTo(48f * x, 22f * y);
        path.lineTo(55f * x, 41f * y);
        path.lineTo(74f * x, 48f * y);
        path.lineTo(55f * x, 55f * y);
        path.lineTo(48f * x, 74f * y);
        path.lineTo(41f * x, 55f * y);
        path.lineTo(22f * x, 48f * y);
        path.lineTo(41f * x, 41f * y);
        path.close();
        path.moveTo(12f * x, 9f * y);
        path.lineTo(15f * x, 12f * y);
        path.lineTo(12f * x, 15f * y);
        path.lineTo(9f * x, 12f * y);
        path.close();
        path.moveTo(84f * x, 81f * y);
        path.lineTo(87f * x, 84f * y);
        path.lineTo(84f * x, 87f * y);
        path.lineTo(81f * x, 84f * y);
        path.close();
    }

}

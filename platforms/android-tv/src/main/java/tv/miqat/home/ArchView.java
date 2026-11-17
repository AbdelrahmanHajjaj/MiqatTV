package tv.miqat.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** The mihrab: a 600×900 Miqat shaped arch. Symmetric, so it needs no mirroring in Arabic. */
final class ArchView extends View {
    static final int REST = 0, FOCUSED = 1, APPROACHING = 2;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outer = new Path();
    private final Path innerLine = new Path();
    private int state = REST;
    private final ThemeSurface surface;

    ArchView(Context c) {
        super(c);
        surface = Appearance.surface();
        fill.setColor(surface.fill(Palette.LAPIS_800));
        stroke.setStyle(Paint.Style.STROKE);
        inner.setStyle(Paint.Style.STROKE);
        inner.setColor(Palette.ARCH_INNER);
        inner.setStrokeWidth(Ui.px(Tokens.SURFACE_ARCH_INNER));
    }

    void setState(int s) {
        if (s == state) return;
        state = s;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        MiqatShape.set(outer, w, h, false);
        MiqatShape.set(innerLine, w, h, true);
    }

    @Override
    protected void onDraw(Canvas c) {
        // border.arch-rest / arch-focused / arch-approaching
        if (state == APPROACHING) {
            stroke.setColor(Palette.GOLD_200);
            stroke.setStrokeWidth(Ui.px(6));
        } else if (state == FOCUSED) {
            stroke.setColor(Palette.PLASTER_100);
            stroke.setStrokeWidth(Ui.px(5));
        } else {
            stroke.setColor(surface.archEdge());
            stroke.setStrokeWidth(Ui.px(surface.archStroke()));
        }
        c.drawPath(outer, fill);
        if (surface.classic() || "frame".equals(surface.name)) c.drawPath(innerLine, inner);
        if (stroke.getStrokeWidth() > 0) c.drawPath(outer, stroke);
    }
}

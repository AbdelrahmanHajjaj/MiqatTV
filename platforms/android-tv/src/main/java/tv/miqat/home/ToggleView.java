package tv.miqat.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Same family as the phone: inset track, accent thumb when on, mist thumb when off. */
final class ToggleView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean on, focused;

    ToggleView(Context c, boolean on, boolean focused) {
        super(c);
        this.on = on;
        this.focused = focused;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), r = h / 2;
        p.setColor(focused ? Palette.LAPIS_900 : Palette.LAPIS_700);
        c.drawRoundRect(0, 0, w, h, r, r, p);
        float knob = h * Tokens.LAYOUT_SETTINGS_TOGGLE_KNOB / (2 * Tokens.LAYOUT_SETTINGS_TOGGLE_HEIGHT);
        float inset = h * Tokens.LAYOUT_SETTINGS_TOGGLE_INSET / Tokens.LAYOUT_SETTINGS_TOGGLE_HEIGHT;
        boolean atEnd = on;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float cx = (atEnd != rtl) ? w - inset - knob : inset + knob;
        p.setColor(on ? Palette.GOLD_400 : Palette.MIST_400);
        c.drawCircle(cx, r, knob, p);
    }
}

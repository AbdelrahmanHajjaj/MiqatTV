package tv.miqat.companion;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** Deliberate upward release for touch; standard activation remains available to accessibility. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic view; never inflated from XML.
final class SwipeStop extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent, ground;
    private final boolean motion;
    private float startX, startY, progress;
    private boolean dragging;
    private ValueAnimator returning;

    SwipeStop(Context context, int accent, int ground, boolean motion) {
        super(context);
        this.accent = accent;
        this.ground = ground;
        this.motion = motion;
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float travel() {
        return Math.max(dp(48), getHeight() - dp(64));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float center = getWidth() / 2f, radius = dp(28);
        float bottom = getHeight() - dp(32), top = bottom - travel();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor((ground & 0xffffff) | 0x99000000);
        canvas.drawRoundRect(
                center - radius,
                top - radius,
                center + radius,
                bottom + radius,
                radius,
                radius,
                paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor((accent & 0xffffff) | 0x66000000);
        canvas.drawRoundRect(
                center - radius,
                top - radius,
                center + radius,
                bottom + radius,
                radius,
                radius,
                paint);
        float thumb = bottom - progress * travel();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent);
        canvas.drawCircle(center, thumb, radius, paint);
        paint.setColor(ground);
        paint.setStrokeWidth(dp(3));
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 2; i++) {
            float y = thumb + dp(-3 + 8 * i);
            canvas.drawLine(center - dp(7), y, center, y - dp(6), paint);
            canvas.drawLine(center, y - dp(6), center + dp(7), y, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            float thumb = getHeight() - dp(32);
            if (Math.hypot(event.getX() - getWidth() / 2f, event.getY() - thumb) > dp(36))
                return false;
            if (returning != null) returning.cancel();
            startX = event.getX();
            startY = event.getY();
            progress = 0;
            dragging = true;
            invalidate();
            return true;
        }
        if (!dragging) return false;
        if (action == MotionEvent.ACTION_CANCEL
                || event.getPointerCount() != 1
                || Math.abs(event.getX() - startX) > dp(40)) {
            reset();
            return true;
        }
        progress = Math.max(0, Math.min(1, (startY - event.getY()) / travel()));
        invalidate();
        if (action == MotionEvent.ACTION_UP) {
            dragging = false;
            if (progress >= .85f) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                performClick();
            } else reset();
        }
        return true;
    }

    private void reset() {
        dragging = false;
        if (returning != null) returning.cancel();
        if (progress == 0 || !motion || !ValueAnimator.areAnimatorsEnabled()) {
            progress = 0;
            invalidate();
            return;
        }
        returning = ValueAnimator.ofFloat(progress, 0);
        returning.setDuration(160);
        returning.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidate();
        });
        returning.start();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.Button.class.getName());
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) reset();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (returning != null) returning.cancel();
        dragging = false;
        super.onDetachedFromWindow();
    }
}

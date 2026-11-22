package tv.miqat.companion;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Transformation;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Checks real sheet layout and its configured entrance transform. */
final class PhoneDialogCheck {
    static void run(Instrumentation test) throws Exception {
        MainActivity a = LiveUiCheck.launch(test);
        try { check(test, a); }
        finally { PhoneUiCheck.on(test, a::finish); }
    }

    static void check(Instrumentation test, MainActivity a) throws Exception {
        AlertDialog[] active = {null};
        int[][] frames = new int[16][3];
        CountDownLatch drawn = new CountDownLatch(1);
        try {
            PhoneUiCheck.on(test, () -> {
                AlertDialog dialog = a.views.dialog().setTitle(a.t("coming_soon"))
                        .setMessage(a.t("phone_quran")).setPositiveButton(a.t("done"), null).create();
                active[0] = dialog;
                View decor = dialog.getWindow().getDecorView();
                decor.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    int count;
                    public boolean onPreDraw() {
                        int[] location = new int[2];
                        decor.getLocationOnScreen(location);
                        frames[count][0] = location[0];
                        frames[count][1] = location[1];
                        frames[count][2] = decor.getWidth();
                        if (++count == frames.length) {
                            decor.getViewTreeObserver().removeOnPreDrawListener(this);
                            drawn.countDown();
                        } else decor.postInvalidateOnAnimation();
                        return true;
                    }
                });
                dialog.show();
            });
            PhoneUiCheck.check(drawn.await(4, TimeUnit.SECONDS), "Sheet did not draw its entrance frames");
            PhoneUiCheck.on(test, () -> {
                for (int[] frame : frames)
                    PhoneUiCheck.check(java.util.Arrays.equals(frame, frames[0]),
                            "Sheet shifted during entrance: " + java.util.Arrays.deepToString(frames));
                int style = active[0].getWindow().getAttributes().windowAnimations;
                if (style == 0) return;
                TypedArray attributes = active[0].getContext().obtainStyledAttributes(style,
                        new int[]{android.R.attr.windowEnterAnimation});
                int resource = attributes.getResourceId(0, 0);
                attributes.recycle();
                if (resource == 0) return;
                Animation animation = AnimationUtils.loadAnimation(active[0].getContext(), resource);
                animation.initialize(frames[0][2], active[0].getWindow().getDecorView().getHeight(),
                        frames[0][2], a.getResources().getDisplayMetrics().heightPixels);
                animation.setStartTime(0);
                for (long time : new long[]{0, 16, 64, 128}) {
                    Transformation transformation = new Transformation();
                    animation.getTransformation(time, transformation);
                    float[] matrix = new float[9];
                    transformation.getMatrix().getValues(matrix);
                    PhoneUiCheck.check(Math.abs(matrix[Matrix.MSCALE_X] - 1) < .0001f
                                    && Math.abs(matrix[Matrix.MTRANS_X]) < .0001f,
                            "Sheet entrance moves horizontally: " + java.util.Arrays.toString(matrix));
                }
            });
        } finally {
            if (active[0] != null) PhoneUiCheck.on(test, active[0]::dismiss);
        }
    }
}

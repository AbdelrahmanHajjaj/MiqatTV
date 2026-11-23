package tv.miqat.companion;

import android.view.View;
import android.widget.*;

/** Foreground controls for explicit OS-delivery checks, separate from real prayer configuration. */
final class PhoneTestUi {
    static void render(MainActivity a, LinearLayout parent) {
        boolean pending = PhoneAlertTest.pending(a), running = PhonePlayback.currentId().startsWith("phone:test:");
        if (!pending && !running) for (boolean adhan : new boolean[] {true, false}) {
            View action = a.views.navigationRow(parent, adhan ? "mihrab" : "bell",
                    label(a, adhan ? "tests.phone-adhan" : "tests.phone-notification"), a.t("phone_test_delay"), () -> {
                        String result = PhoneAlertTest.schedule(a, adhan);
                        if (!PhoneAlertTest.pending(a)) a.notice(result);
                        a.render();
                    });
            action.setTag(adhan ? "phone.test.adhan" : "phone.test.notification");
            action.setTag(R.id.companion_action, adhan ? "tests.phone-adhan" : "tests.phone-notification");
        }
        if (pending || running) {
            TextView status = a.views.text("", 13, a.accent);parent.addView(status);
            status.setTag("phone.test.pending");
            Runnable refresh = new Runnable() {
                public void run() {
                    if (!status.isAttachedToWindow()) return;
                    boolean waiting = PhoneAlertTest.pending(a), playing = PhonePlayback.currentId().startsWith("phone:test:");
                    if (!waiting && !playing) { a.render();return; }
                    long seconds = PhoneAlertTest.remainingSeconds(a);
                    status.setText(a.t(playing ? "phone_test_running" : seconds == 0 ? "phone_test_waiting" : "phone_test_pending")
                            .replace("%s", a.phoneUi.digits(Long.toString(seconds))));
                    status.postDelayed(this, 1000);
                }
            };
            status.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(View v) { status.post(refresh); }
                public void onViewDetachedFromWindow(View v) { status.removeCallbacks(refresh); }
            });
            if (status.isAttachedToWindow()) status.post(refresh);
            View cancel = a.views.navigationRow(parent, "close", label(a, "tests.phone-cancel"), "", () -> {PhoneAlertTest.cancel(a);a.render();});
            cancel.setTag("phone.test.cancel");cancel.setTag(R.id.companion_action,"tests.phone-cancel");
            if (!PhoneAlarms.exact(a)) parent.addView(a.views.text(a.t("phone_alerts_approximate"), 12, a.muted));
        } else {
            String result = PhoneStore.preferences(a).getString("test-result", "");
            if (!result.isEmpty()) parent.addView(a.views.text(a.t(result), 13, a.muted));
        }
    }
    private static String label(MainActivity a, String id) {
        return a.t(a.phoneUi.data.actionLabel(id));
    }
}

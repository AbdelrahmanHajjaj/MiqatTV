package tv.miqat.companion;

import android.app.*;
import android.view.*;
import android.widget.*;

/** Native behavior behind the fidelity fixes, including pre-first-frame dialog geometry. */
final class PhonePolishCheck {
    static void run(Instrumentation test, MainActivity a, String lang, boolean capture) throws Exception {
        PhoneUiCheck.on(test, () -> {
            a.tab("phone-settings");
            a.body.findViewWithTag("phone.language").performClick();
            PhoneUiCheck.check(a.phoneUi.choiceDialog != null, "Language must open its compact picker");
            ListView choices = a.phoneUi.choiceDialog.getWindow().getDecorView().findViewWithTag("phone.choice.list");
            PhoneUiCheck.check(choices.getCount() == 3, "Language picker must retain all supported modes");
            a.phoneUi.choiceDialog.dismiss();
            AlertDialog dialog = a.views.dialog().setTitle(a.t("coming_soon"))
                    .setPositiveButton(a.t("done"), null).create();
            PhoneUiCheck.check((dialog.getWindow().getAttributes().gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM,
                    "Sheet was centered before its first visible frame");
            dialog.show();
            PhoneUiCheck.check((dialog.getWindow().getAttributes().gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM,
                    "Showing the sheet changed its placement");
            dialog.dismiss();
            try {
                for (String id : new String[]{"appearance.background", "appearance.theme"})
                    PhoneUiCheck.check(a.phoneUi.data.options(a.phoneUi.data.controls.get(id)).get(0)
                                    .equals(a.phoneUi.data.defaults.get(id)),
                            "Default style must be the first carousel option: " + id);
            } catch (Exception invalid) { throw new AssertionError(invalid); }
            a.phoneUi.open("appearance");
            boolean before = a.phoneUi.settings.value("appearance.home-background").equals("true");
            a.body.findViewWithTag("phone.appearance.home-background.edit").performClick();
            try {
                PhoneUiCheck.check(PhoneStore.read(a).value("appearance.home-background").equals(Boolean.toString(before)),
                        "Home background toggle saved before Apply");
            } catch (Exception invalid) { throw new AssertionError(invalid); }
            a.getWindow().getDecorView().findViewWithTag("appearance.cancel").performClick();
            a.phoneUi.open("fajr");
        });
        PhoneUiCheck.on(test, () -> {
            for (int day = 0; day < 7; day++) {
                View cell = a.body.findViewWithTag("phone.call.fajr.weekdays." + day);
                LinearLayout.LayoutParams size = (LinearLayout.LayoutParams)cell.getLayoutParams();
                PhoneUiCheck.check(cell.getWidth() == cell.getHeight() && size.width == a.views.dp(48)
                                && cell.getParent() == a.body.findViewWithTag("phone.call.fajr.weekdays"),
                        "Weekday targets must stay square on a single scrollable row");
                View track = (View)cell.getParent(), viewport = (View)track.getParent();
                if (viewport.getWidth() >= 7 * a.views.dp(48)) {
                    android.graphics.Rect visible = new android.graphics.Rect();
                    PhoneUiCheck.check(cell.getGlobalVisibleRect(visible)
                                    && visible.width() == cell.getWidth(),
                            "Weekday target clipped at page inset");
                }
            }
            if (a.toast != null) a.toast.cancel();
        });
        stableScroll(test, a);
        PhoneUiCheck.capture(test, a, lang + "-polish-prayer", capture);
        PhoneUiCheck.on(test, () -> {
            a.phoneUi.open("days:fajr");
            if (!((Switch)a.body.findViewWithTag("phone.day.2")).isChecked())
                a.body.findViewWithTag("phone.day.2").performClick();
        });
        PhoneUiCheck.capture(test, a, lang + "-polish-overrides", capture);
        PhoneUiCheck.on(test, () -> a.body.findViewWithTag("phone.day.2").performClick());
        if (capture) review(test, a, lang);
    }

    private static void stableScroll(Instrumentation test, MainActivity a) {
        for (String action : new String[]{"plus", "plus", "minus", "typed"}) {
            int[] firstFrame = {-1}, expected = {-1};
            PhoneUiCheck.on(test, () -> {
                a.scroll.scrollTo(0, a.views.dp(100));
                expected[0] = a.scroll.getScrollY();
                View root = a.contentRoot;
                if (action.equals("typed")) {
                    a.body.findViewWithTag("phone.call.fajr.before.edit").performClick();
                    ((android.widget.EditText) a.body.findViewWithTag("phone.call.fajr.before.input")).setText("7");
                } else a.body.findViewWithTag("phone.call.fajr.before." + action).performClick();
                PhoneUiCheck.check(a.contentRoot == root, "Setting edit replaced the window container");
                android.widget.ScrollView current = a.scroll;
                current.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override public boolean onPreDraw() {
                        current.getViewTreeObserver().removeOnPreDrawListener(this);
                        firstFrame[0] = current.getScrollY();
                        return true;
                    }
                });
                current.invalidate();
            });
            android.os.SystemClock.sleep(80);
            PhoneUiCheck.on(test, () -> PhoneUiCheck.check(firstFrame[0] == expected[0]
                            && a.scroll.getScrollY() == expected[0],
                    "Prayer setting jumped before/after first frame: " + action + " " + firstFrame[0] + "/" + expected[0]));
        }
        PhoneUiCheck.on(test, () -> a.scroll.scrollTo(0, 0));
    }

    private static void review(Instrumentation test, MainActivity a, String lang) throws Exception {
        PhoneStore saved = PhoneStore.read(a);
        android.content.SharedPreferences prefs = PhoneStore.preferences(a);
        boolean hadComplete = prefs.contains("onboarding-complete"), complete = prefs.getBoolean("onboarding-complete", false);
        try {
            java.util.Map<String,String> changes = new java.util.LinkedHashMap<>();
            changes.put("prayer.location-ready", "true");
            changes.put("appearance.theme", "mihrab");
            changes.put("appearance.home-background", "true");
            saved.changed(changes).save(a);
            prefs.edit().putBoolean("onboarding-complete", true).commit();
            PhoneUiCheck.on(test, () -> { a.tab("prayers"); });
            PhoneUiCheck.on(test, () -> {
                for (String prayer : PhoneStore.PRAYERS) {
                    View name = a.body.findViewWithTag("phone.prayer." + prayer + ".name");
                    PhoneUiCheck.check(name != null && name.getWidth() > 0, "Home hid a prayer name: " + prayer);
                }
            });
            PhoneUiCheck.capture(test, a, lang + "-polish-home", true);
            PhoneUiCheck.on(test, () -> a.body.findViewWithTag("phone.today").performClick());
            PhoneUiCheck.on(test, () -> {
                for (String prayer : PhoneStore.PRAYERS) {
                    TextView time = a.phoneUi.choiceDialog.getWindow().getDecorView()
                            .findViewWithTag("calendar.prayer." + prayer + ".time");
                    LinearLayout row = (LinearLayout)time.getParent();
                    View name = row.getChildAt(1);
                    PhoneUiCheck.check(name.getWidth() > 0 && time.getWidth() > 0
                                    && (name.getRight() <= time.getLeft() || time.getRight() <= name.getLeft()),
                            "Calendar hid or overlapped a prayer name: " + prayer);
                }
            });
            PhoneUiCheck.capture(test, a, lang + "-polish-calendar", true);
            PhoneUiCheck.on(test, () -> { a.phoneUi.choiceDialog.dismiss(); a.phoneUi.open("appearance"); });
            for (String scene : new String[]{"miqat_signature", "dusk_peaks", "aurora_sky"}) {
                PhoneUiCheck.on(test, () -> a.phoneUi.save("appearance.background", scene));
                PhoneUiCheck.capture(test, a, lang + "-polish-scene-" + scene, true);
            }
            PhoneUiCheck.on(test, () -> a.getWindow().getDecorView().findViewWithTag("appearance.cancel").performClick());
        } finally {
            saved.save(a);
            android.content.SharedPreferences.Editor restore = prefs.edit();
            if (hadComplete) restore.putBoolean("onboarding-complete", complete); else restore.remove("onboarding-complete");
            PhoneUiCheck.check(restore.commit(), "Cannot restore completed-home review fixture");
            PhoneUiCheck.on(test, a::render);
        }
    }
}

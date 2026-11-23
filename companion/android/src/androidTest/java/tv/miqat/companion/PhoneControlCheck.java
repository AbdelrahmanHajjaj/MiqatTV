package tv.miqat.companion;

import android.app.Instrumentation;
import android.widget.EditText;
import java.util.LinkedHashMap;
import java.util.Map;

/** Each numeric change survives moving to another control or page. No TV or playback. */
final class PhoneControlCheck {
    static void run(Instrumentation test) throws Exception {
        android.content.Context context = test.getTargetContext();
        PhoneStore original = PhoneStore.read(context);
        MainActivity a = null;
        try {
            Map<String,String> fixture = new LinkedHashMap<>();
            fixture.put("reminders.enabled", "false");
            fixture.put("call.fajr.enabled", "true");
            fixture.put("call.fajr.mode", "adhan");
            fixture.put("call.fajr.offset", "0");
            fixture.put("call.fajr.before", "0");
            fixture.put("call.fajr.after", "0");
            original.changed(fixture).save(context);
            a = LiveUiCheck.launch(test);
            check(test, a);
        } finally {
            if (a != null) PhoneUiCheck.on(test, a::finish);
            original.save(context);
            PhoneAlarms.rebuild(context);
        }
    }

    static void check(Instrumentation test, MainActivity a) throws Exception {
        PhoneStore original = PhoneStore.read(a);
        try {
            PhoneUiCheck.on(test, () -> {
                Map<String,String> values = new LinkedHashMap<>();
                values.put("call.fajr.enabled", "true");
                for (String suffix : new String[]{"offset", "before", "after"}) values.put("call.fajr." + suffix, "0");
                a.phoneUi.save(values, false);
                a.phoneUi.open("fajr");
                for (String suffix : new String[]{"offset", "before", "after"})
                    a.body.findViewWithTag("phone.call.fajr." + suffix + ".plus").performClick();
                verify(a, "call.fajr.offset", 1);
                verify(a, "call.fajr.before", 1);
                verify(a, "call.fajr.after", 1);
                preview(a, "call.fajr.offset", 0, 0);
                preview(a, "call.fajr.before", 0, -1);
                preview(a, "call.fajr.after", 0, 1);
                a.body.findViewWithTag("phone.call.fajr.before.edit").performClick();
                EditText input = a.body.findViewWithTag("phone.call.fajr.before.input");
                input.setText("٧");
                verify(a, "call.fajr.before", 7);
                preview(a, "call.fajr.before", 0, -7);
                PhoneUiCheck.check(input == a.body.findViewWithTag("phone.call.fajr.before.input"),
                        "Typing replaced the active field");
                a.body.findViewWithTag("phone.call.fajr.before.edit").performClick();
                PhoneUiCheck.check(input == a.body.findViewWithTag("phone.call.fajr.before.input")
                                && input.getText().toString().equals("٧"),
                        "Tapping an active number restored its stale value");
                input.setText("9999");
                verify(a, "call.fajr.before", 7);
                preview(a, "call.fajr.before", 0, -7);
                PhoneUiCheck.check(input.getError() != null, "Out-of-range entry lacked validation");
                a.body.findViewWithTag("phone.call.fajr.after.plus").performClick();
                verify(a, "call.fajr.before", 7);
                verify(a, "call.fajr.after", 2);
                a.phoneUi.pending = "call.fajr.before";
                a.phoneUi.draft = "9";
                a.render();
                ((EditText) a.body.findViewWithTag("phone.call.fajr.before.input"))
                        .onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
                verify(a, "call.fajr.before", 9);
                a.phoneUi.open("default-audio");
                a.phoneUi.save("reminders.volume", "70");
                a.body.findViewWithTag("phone.reminders.volume.minus").performClick();
                a.phoneUi.open("calculation");
                a.phoneUi.save("prayer.sunrise-offset", "0");
                a.body.findViewWithTag("phone.prayer.sunrise-offset.plus").performClick();
                verify(a, "reminders.volume", 65);
                verify(a, "prayer.sunrise-offset", 1);
                preview(a, "prayer.sunrise-offset", 1, 0);
                verify(a, "call.fajr.before", 9);
                PhoneUiCheck.check(a.getWindow().getDecorView().findViewWithTag("phone.prayer.sunrise-offset.save") == null,
                        "Step controls still require Done");
                a.phoneUi.open("appearance");
                a.phoneUi.save("display.clock", "24");
                a.phoneUi.save("appearance.theme", "dusk");
                PhoneUiCheck.check(a.getWindow().getDecorView().findViewWithTag("appearance.apply") == null
                                && a.getWindow().getDecorView().findViewWithTag("appearance.cancel") == null,
                        "Appearance still requires page confirmation");
                a.back();
                PhoneUiCheck.check(!a.section.equals("appearance"), "Back did not leave Appearance");
                PhoneUiCheck.check(a.phoneUi.settings.value("display.clock").equals("24")
                                && a.phoneUi.settings.value("appearance.theme").equals("dusk"),
                        "Leaving Appearance discarded immediate changes");
            });
        } finally {
            original.save(a);
            PhoneAlarms.rebuild(a);
            PhoneUiCheck.on(test, a::render);
        }
    }

    private static void preview(MainActivity a, String id, int prayer, int minutes) {
        PhoneStore settings = a.phoneUi.settings;
        android.widget.TextView preview = a.body.findViewWithTag("phone." + id + ".preview");
        String expected = settings.locationReady
                ? a.phoneUi.time(settings.day(System.currentTimeMillis(), 0).times[prayer] + minutes * 60_000L)
                : "—";
        PhoneUiCheck.check(preview.getText().toString().equals(expected), "Incorrect time preview for " + id);
    }

    private static void verify(MainActivity a, String id, int expected) {
        try {
            PhoneUiCheck.check(PhoneStore.read(a).number(id) == expected,
                    "Switching numeric controls lost " + id + " without Done");
        } catch (Exception invalid) { throw new AssertionError(invalid); }
    }
}

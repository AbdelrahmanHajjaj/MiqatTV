package tv.miqat.companion;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.JSONObject;

/** Real bound phone views and persistence; no network or TV is needed. */
final class PhoneUiCheck {
    static void run(Instrumentation test,boolean capture)throws Exception {
        tv.miqat.companion.about.UpdateMetadataCheck.run();
        Context context=test.getTargetContext();SharedPreferences prefs=PhoneStore.preferences(context);
        Map<String,?> original=new LinkedHashMap<>(prefs.getAll());String language=context.getSharedPreferences("companion",0).getString("language","system");
        MainActivity[] current={null};
        try {
            PhoneAlarms.cancel(context);prefs.edit().clear().putBoolean("onboarding-seen",true).commit();
            for(String lang:new String[]{"en","ar"}) {
                context.getSharedPreferences("companion",0).edit().putString("language",lang).commit();
                MainActivity a=LiveUiCheck.launch(test);current[0]=a;
                on(test,()->{
                    check(a.page.equals("prayers"),"Standalone prayer must be the default");
                    check(a.body.findViewWithTag("phone.countdown")!=null,"Calculated next prayer missing");
                    for(String prayer:PhoneStore.PRAYERS)check(a.body.findViewWithTag("phone.prayer."+prayer)!=null,"Daily overview missing "+prayer);
                    check(a.getWindow().getDecorView().findViewWithTag("nav.phone-settings").isEnabled(),"Phone settings must not need a TV");
                    check(a.body.getLayoutDirection()==(lang.equals("ar")?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR),"Phone prayer direction");
                });
                capture(test,a,lang+"-phone-home",capture);
                String installedVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
                on(test, () -> {
                    a.tab("phone-settings");
                    a.body.findViewWithTag("phone.section.about").performClick();
                    TextView version = a.body.findViewWithTag("phone.about.version");
                    check(version.getText().toString().contains(installedVersion), "About must show the installed phone version");
                    check(a.body.findViewWithTag("phone.about.github").isEnabled(), "GitHub feedback must be available");
                    check(a.body.findViewWithTag("phone.about.check-updates").isEnabled(), "Sideloaded phone has a manual update check");
                    Switch automatic = a.body.findViewWithTag("phone.about.auto-updates.edit");
                    check(automatic != null && !automatic.isChecked(), "Automatic updates must require opt-in");
                    automatic.performClick();
                    check(a.phoneUi.settings.value("about.auto-updates").equals("true"), "Update opt-in must save immediately");
                    a.body.findViewWithTag("phone.about.auto-updates.edit").performClick();
                    check(a.phoneUi.settings.value("about.auto-updates").equals("false"), "Update opt-out must save immediately");
                    tv.miqat.companion.about.UpdateMetadataCheck.automaticOffer(a, a.views.dialog(), a.ar);
                    check(!a.body.findViewWithTag("phone.about.email").isEnabled(), "Email feedback must be disabled");
                });
                if (test instanceof CompanionCheck && ((CompanionCheck) test).mapNetwork)
                    PhoneDialogCheck.updatePopup(test, a, capture);
                on(test, () -> {
                    check(a.phoneUi.back() && a.body.findViewWithTag("phone.section.about") != null, "Back must return to settings");
                    a.tab("prayers");
                });
                PhoneCalendarCheck.run(test,a);
                if(test instanceof CompanionCheck && ((CompanionCheck)test).mapNetwork) PhoneCalendarCheck.mapNetwork(test,a,capture);
                on(test,()->{
                    a.phoneUi.open("permissions");Switch enabled=a.body.findViewWithTag("phone.reminders.enabled.edit");
                    if(!a.phoneUi.settings.locationReady){enabled.performClick();check(a.section.equals("calculation")&&!a.phoneUi.settings.enabled,"Alerts require location confirmation");}
                    a.phoneUi.open("calculation");if(!a.phoneUi.settings.locationReady)a.getWindow().getDecorView().findViewWithTag("phone.location.confirm").performClick();check(a.phoneUi.settings.locationReady,"Location confirmation not saved");check(a.getWindow().getDecorView().findViewWithTag("phone.location.confirm")==null,"Confirmed location retains a redundant confirmation");
                    advancedLocationDraft(a);
                    a.phoneUi.open("default-audio");
                    EditText visibleNumber = a.body.findViewWithTag("phone.reminders.volume.input");
                    check(visibleNumber != null && !visibleNumber.isFocusable()
                            && a.body.findViewWithTag("phone.reminders.volume.minus") != null
                            && a.body.findViewWithTag("phone.reminders.volume.plus") != null,
                            "Number and +/- must remain visible without opening a second line");
                    a.body.findViewWithTag("phone.reminders.volume.edit").performClick();
                    EditText number=a.body.findViewWithTag("phone.reminders.volume.input");
                    number.setText("٣٥");
                    check(a.phoneUi.settings.volume==35 && number==a.body.findViewWithTag("phone.reminders.volume.input"),
                            "Valid Arabic numeric entry must save without replacing its field");
                    check(a.getWindow().getDecorView().findViewWithTag("phone.reminders.volume.save")==null,
                            "Numeric controls must not require an extra Done button");
                    number.setText("37");
                    check(a.phoneUi.settings.volume==35 && number.getError()!=null,"Off-step number accepted");
                    number.setText("35");
                    number.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
                    a.phoneUi.open("fajr");a.phoneUi.save("call.fajr.mode","adhan");check(!a.body.findViewWithTag("phone.call.fajr.volume.edit").isEnabled(),"Inherited audio must not expose an active override");
                    check(!a.body.findViewWithTag("phone.call.fajr.length-percent.edit").isEnabled()
                            && a.body.findViewWithTag("phone.length.preview").isEnabled()
                            && a.body.findViewWithTag("phone.length.stop").isEnabled(),
                            "Inherited length must keep its recording preview available while its slider is disabled");
                    a.body.findViewWithTag("phone.call.fajr.default-audio.edit").performClick();a.body.findViewWithTag("phone.call.fajr.volume.edit").performClick();((EditText)a.body.findViewWithTag("phone.call.fajr.volume.input")).setText("١٥");check(a.phoneUi.settings.audioVolume(0)==15&&a.phoneUi.settings.volume==35,"Per-prayer audio changed default level");a.phoneUi.save("call.fajr.default-audio","true");
                    String mode=a.phoneUi.settings.value("call.fajr.mode");
                    check(a.body.findViewWithTag("phone.call.fajr.mode.choices."+mode).isSelected(),"Inline mode choice must show saved value");
                    a.body.findViewWithTag("phone.call.fajr.mode.choices.notification").performClick();check(a.phoneUi.settings.modes[0]==1,"Per-prayer mode not independent/saved");
                    a.phoneUi.open("calculation");a.phoneUi.save("prayer.zone","Asia/Tokyo");check(!a.phoneUi.settings.locationReady,"Changed timezone must need confirmation");
                    a.phoneUi.save("prayer.zone","system");a.getWindow().getDecorView().findViewWithTag("phone.location.confirm").performClick();
                    a.phoneUi.open("appearance");a.phoneUi.save("appearance.theme","dusk");check(a.ground==android.graphics.Color.parseColor("#211C32")&&a.color("lapis-700")==android.graphics.Color.parseColor("#403147"),"Phone theme not applied to all surfaces");
                    boundControls(a);
                    a.phoneUi.save("appearance.theme", a.phoneUi.data.defaults.get("appearance.theme"));
                    a.tab("prayers");
                    try {
                        PhoneSchedule.Day day=a.phoneUi.settings.day(System.currentTimeMillis(),0);
                        a.body.removeAllViews();a.phoneUi.home.render((long)day.times[5]+1000);
                        check(!a.body.findViewWithTag("phone.prayer.fajr").isSelected(),"After Isha, today's past Fajr must not be highlighted as next");
                        a.body.removeAllViews();a.phoneUi.home.render((long)day.times[0]-60000);
                        check(a.body.findViewWithTag("phone.prayer.fajr").isSelected(),"Upcoming Fajr must be highlighted");
                        a.render();
                    }catch(Exception invalid){throw new AssertionError(invalid);}
                    a.body.findViewWithTag("phone.prayer.fajr").performClick();check(a.section.equals("fajr"),"Prayer card did not open its customization");
                    a.phoneUi.open("permissions");check(a.body.findViewWithTag("phone.power-settings")!=null,"Background delivery repair help missing");
                    a.tab("phone-settings");
                });
                String priorQuiet=PhoneStore.read(context).value("quiet.start");
                on(test,()->{a.phoneUi.open("legacy-quiet");a.body.findViewWithTag("phone.quiet.start.edit").performClick();check(a.phoneUi.choiceDialog instanceof android.app.TimePickerDialog,"Quiet hours need a clock editor");((android.app.TimePickerDialog)a.phoneUi.choiceDialog).updateTime(21,30);a.phoneUi.choiceDialog.getButton(-2).performClick();});
                LiveUiCheck.untilUi(test,()->a.phoneUi.choiceDialog==null);check(PhoneStore.read(context).value("quiet.start").equals(priorQuiet),"Clock Cancel saved a draft");
                on(test,()->{a.body.findViewWithTag("phone.quiet.start.edit").performClick();((android.app.TimePickerDialog)a.phoneUi.choiceDialog).updateTime(21,30);a.phoneUi.choiceDialog.getButton(-1).performClick();});
                LiveUiCheck.untilUi(test,()->a.phoneUi.settings.number("quiet.start")==1290);
                on(test,()->{
                    a.phoneUi.open("fajr");
                    check(a.phoneUi.choiceDialog==null,"Weekdays must be visible directly on the prayer page");
                    for(int day=0;day<7;day++) {
                        View button=a.body.findViewWithTag("phone.call.fajr.weekdays."+day);
                        check(button!=null&&button.isSelected(),"Saved weekday missing "+day);
                        check(button.getContentDescription()!=null,"Weekday lacks its full accessible name");
                    }
                    a.body.findViewWithTag("phone.call.fajr.weekdays.0").performClick();
                    check(a.phoneUi.settings.number("call.fajr.weekdays")==126
                            && !a.body.findViewWithTag("phone.call.fajr.weekdays.0").isSelected(),
                            "Inline Sunday toggle did not persist its weekday mask");
                    a.phoneUi.save("call.fajr.weekdays","127");
                    dayOverrides(a);
                    a.tab("phone-settings");check(a.section.isEmpty(),"Phone settings root tab did not return from its child page");
                    check(a.body.findViewWithTag("phone.section.widgets")!=null,"Widget customization needs a settings entry");
                });
                PhoneControlCheck.check(test,a);
                PhonePolishCheck.run(test,a,lang,capture);
                PhoneLibraryCheck.check(test,a,lang,capture);
                PhoneHomeCheck.run(test,a);
                on(test,()->a.tab("phone-settings"));
                capture(test,a,lang+"-phone-settings",capture);
                on(test,()->a.phoneUi.open("audio"));capture(test,a,lang+"-phone-audio",capture);
                testControls(test,a,lang,capture);
                on(test,()->{a.phoneUi.open("default-audio");a.body.findViewWithTag("phone.reminders.voice.edit").performClick();});capture(test,a,lang+"-phone-voice-sheet",capture);
                on(test, () -> check(a.phoneUi.choiceDialog.getWindow().getDecorView()
                                .findViewWithTag("phone.voice.import").getVisibility() == View.VISIBLE,
                        "Device audio selection must be visible before choosing Custom audio"));
                String savedVoice = PhoneStore.read(context).value("reminders.voice");
                java.util.List<String> voices = a.phoneUi.data.options(a.phoneUi.data.controls.get("reminders.voice"));
                int candidateIndex = savedVoice.equals(voices.get(0)) ? 1 : 0;
                String candidateVoice = voices.get(candidateIndex);
                String candidateLabel = a.phoneUi.value("reminders.voice",candidateVoice);
                on(test,()->{
                    a.phoneUi.choiceDialog.getWindow().getDecorView().<ListView>findViewWithTag("phone.voice.list")
                            .performItemClick(null,candidateIndex,candidateIndex);
                    check(a.phoneUi.settings.value("reminders.voice").equals(savedVoice),"Audition selection changed the saved voice");
                    a.phoneUi.choiceDialog.getButton(-2).performClick();
                    a.body.findViewWithTag("phone.reminders.voice.edit").performClick();
                    ((EditText)a.phoneUi.choiceDialog.getWindow().getDecorView().findViewWithTag("phone.voice.search"))
                            .setText(candidateLabel);
                });
                LiveUiCheck.untilUi(test,()->a.phoneUi.choiceDialog.getWindow().getDecorView().<ListView>findViewWithTag("phone.voice.list").getCount()==1);
                on(test,()->{
                    check(!a.phoneUi.choiceDialog.getWindow().getDecorView().<ListView>findViewWithTag("phone.voice.list").isItemChecked(0),
                            "Filtering must not select a different voice automatically");
                    a.phoneUi.choiceDialog.getWindow().getDecorView().<ListView>findViewWithTag("phone.voice.list")
                            .performItemClick(null,0,0);
                    a.phoneUi.choiceDialog.getButton(-1).performClick();
                });
                LiveUiCheck.untilUi(test,()->a.phoneUi.choiceDialog == null && a.phoneUi.settings.value("reminders.voice").equals(candidateVoice));
                on(test,()->{
                    check(a.phoneUi.settings.value("reminders.voice").equals(candidateVoice),"Apply did not save selected voice");
                    a.phoneUi.save("reminders.voice",savedVoice);
                });
                on(test,()->a.phoneUi.open("calculation"));capture(test,a,lang+"-phone-location",capture);
                on(test,()->{a.phoneUi.open("fajr");a.tab("prayers");});capture(test,a,lang+"-phone-themed",capture);
                keyboardFooter(test,a,lang,capture);
                on(test,a::finish);test.waitForIdleSync();current[0]=null;
            }
            check(PhoneStore.read(context).volume==35&&PhoneStore.read(context).modes[0]==1,"Phone settings did not survive reopening");
        }finally{
            if(current[0]!=null)on(test,current[0]::finish);
            PhonePlayback.stop(context);PhoneAlarms.cancel(context);SharedPreferences.Editor restore=prefs.edit().clear();
            for(Map.Entry<String,?> e:original.entrySet()){Object value=e.getValue();if(value instanceof String)restore.putString(e.getKey(),(String)value);else if(value instanceof Boolean)restore.putBoolean(e.getKey(),(Boolean)value);else if(value instanceof Integer)restore.putInt(e.getKey(),(Integer)value);else if(value instanceof Long)restore.putLong(e.getKey(),(Long)value);}
            check(restore.commit(),"Cannot restore phone fixture");context.getSharedPreferences("companion",0).edit().putString("language",language).commit();PhoneAlarms.rebuild(context);
        }
    }
    private static void testControls(Instrumentation test, MainActivity a, String lang, boolean capture) throws Exception {
        check(!PhoneAlertTest.pending(a), "Finish an earlier test before this UI fixture");
        String saved = PhoneStore.preferences(a).getString("settings", null);
        try {
            on(test, () -> {
                View action = a.body.findViewWithTag("phone.test.adhan");
                check(action != null && a.body.findViewWithTag("phone.test.notification") != null,
                        "Phone alert tests are missing");
                check(action.getTag(R.id.companion_action).equals("tests.phone-adhan"),
                        "Phone test is not bound to its canonical action");
                action.requestRectangleOnScreen(new android.graphics.Rect(0,0,action.getWidth(),action.getHeight()),true);
                action.performClick();
                check(!PhonePlayback.playing(), "Phone test skipped its grace period");
                if (PhoneAlertTest.pending(a)) check(a.body.findViewWithTag("phone.test.cancel") != null
                                && a.body.findViewWithTag("phone.test.adhan") == null
                                && a.body.findViewWithTag("phone.test.notification") == null,
                        "Pending test must replace duplicate actions with countdown and Cancel");
            });
            if (PhoneAlertTest.pending(a)) capture(test,a,lang+"-phone-test-pending",capture);
        } finally {
            on(test, () -> {
                View cancel = a.body.findViewWithTag("phone.test.cancel");
                if (cancel != null) cancel.performClick(); else PhoneAlertTest.cancel(a);
                a.render();
            });
        }
        check(!PhoneAlertTest.pending(a) && !PhonePlayback.playing()
                        && java.util.Objects.equals(saved,PhoneStore.preferences(a).getString("settings",null)),
                "Phone test controls changed settings or retained a cancelled test");
    }
    private static void advancedLocationDraft(MainActivity a) {
        Map<String,String> original = new LinkedHashMap<>(a.phoneUi.settings.values);
        a.phoneUi.locationUi.advanced();
        android.app.AlertDialog advanced = a.phoneUi.locationUi.locationDialog;
        View window = advanced.getWindow().getDecorView();
        EditText latitude = window.findViewWithTag("phone_latitude");
        EditText longitude = window.findViewWithTag("phone_longitude");
        latitude.setText("51.5"); longitude.setText("-0.12");
        window.findViewWithTag("phone.location.zone").performClick();
        a.phoneUi.choiceDialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        check(advanced.isShowing() && latitude.getText().toString().equals("51.5"),
                "Cancelling timezone discarded the coordinate draft");
        window.findViewWithTag("phone.location.zone").performClick();
        chooseTimezone(a, "Europe/London");
        check(advanced.isShowing() && a.phoneUi.settings.values.equals(original)
                        && longitude.getText().toString().equals("-0.12"),
                "Choosing timezone saved early or discarded coordinates");
        advanced.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        check(a.phoneUi.settings.values.equals(original), "Advanced Cancel changed location/timezone");
        a.phoneUi.locationUi.advanced();
        advanced = a.phoneUi.locationUi.locationDialog;
        window = advanced.getWindow().getDecorView();
        latitude = window.findViewWithTag("phone_latitude");
        longitude = window.findViewWithTag("phone_longitude");
        latitude.setText("91"); longitude.setText("-0.12");
        window.findViewWithTag("phone.location.zone").performClick();
        chooseTimezone(a, "Europe/London");
        advanced.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        check(advanced.isShowing() && a.phoneUi.settings.values.equals(original),
                "Invalid coordinates partially saved the timezone draft");
        latitude.setText("51.5");
        advanced.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        check(a.phoneUi.settings.latitude == 51.5 && a.phoneUi.settings.longitude == -0.12
                        && a.phoneUi.settings.value("prayer.zone").equals("Europe/London")
                        && !a.phoneUi.settings.locationReady,
                "Advanced Apply did not save coordinates and timezone together");
        check(a.phoneUi.save(original, false), "Could not restore location draft fixture");
    }
    private static void chooseTimezone(MainActivity a, String zone) {
        ListView choices = a.phoneUi.choiceDialog.getWindow().getDecorView()
                .findViewWithTag("phone.choice.list");
        for(int i=0;i<choices.getAdapter().getCount();i++) {
            Choices.Option option = (Choices.Option) choices.getAdapter().getItem(i);
            if(option.value.equals(zone)) { choices.performItemClick(null,i,i); return; }
        }
        throw new AssertionError("Timezone choice missing: " + zone);
    }
    private static void boundControls(MainActivity a) {
        a.phoneUi.open("calculation");
        for(String tag:new String[]{"phone.country","phone.city","phone.location.detect","phone.location.advanced"})
            check(a.getWindow().getDecorView().findViewWithTag(tag)!=null,"Missing labeled location control "+tag);
        for(JSONObject control:a.phoneUi.data.controls.values())
            if(control.optString("section").equals("calculation")
                    && !control.optString("id").equals("prayer.zone")
                    && !control.optString("id").equals("prayer.travel-refresh"))
                check(a.body.findViewWithTag("phone."+control.optString("id"))!=null,"Missing calculation control "+control.optString("id"));
        a.body.findViewWithTag("phone.location.advanced").performClick();
        check(a.phoneUi.locationUi.locationDialog.getWindow().getDecorView()
                        .findViewWithTag("phone.location.zone") != null,
                "Advanced location must expose the timezone editor");
        a.phoneUi.locationUi.locationDialog.getButton(-2).performClick();
        a.phoneUi.open("audio");
        for(String prayer:new String[]{"fajr","dhuhr","asr","maghrib","isha"})
            check(a.body.findViewWithTag("phone.prayer-settings."+prayer)!=null,"Missing prayer settings entry "+prayer);
        for(String id:new String[]{"reminders.full-screen","reminders.volume-keys"})
            check(a.body.findViewWithTag("phone."+id+".edit")!=null,"Missing optional presentation switch "+id);
        check(a.body.findViewWithTag("phone.reminders.duration")==null,"Retired seconds editor must not duplicate relative length");
        a.phoneUi.open("default-audio");
        android.os.Bundle progress=new android.os.Bundle();
        progress.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,75f);
        a.body.findViewWithTag("phone.reminders.length-percent.edit").performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),progress);
        check(a.phoneUi.settings.number("reminders.length-percent")==75 && a.phoneUi.settings.duration==0,"Accessible length edit did not save relative cutoff");

        a.phoneUi.open("default-audio");
        for(String id:new String[]{"reminders.voice","reminders.volume","reminders.length-percent"})
            check(a.body.findViewWithTag("phone."+id)!=null,"Missing default audio control "+id);
        a.phoneUi.open("appearance");
        for(JSONObject control:a.phoneUi.data.controls.values()) if(control.optString("section").equals("appearance")) {
            String id=control.optString("id");
            if(id.equals("appearance.theme")||id.equals("appearance.background"))a.body.findViewWithTag("appearance.tab."+id).performClick();
            check(a.body.findViewWithTag("phone."+id)!=null,"Missing appearance control "+id);
        }
        for(String prayer:new String[]{"fajr","dhuhr","asr","maghrib","isha"}) {
            a.phoneUi.open(prayer);String prefix="phone.call."+prayer+".";
            check(a.body.findViewWithTag(prefix+"enabled.edit") instanceof Switch,"Missing prayer enable switch "+prayer);
            for(String suffix:new String[]{"vibrate.edit","weekdays","offset.input","before.input","after.input","voice.edit","default-audio.edit","volume.input","length-percent.edit"})
                check(a.body.findViewWithTag(prefix+suffix)!=null,"Missing prayer control "+prefix+suffix);
            for(String mode:new String[]{"adhan","notification","silent"})
                check(a.body.findViewWithTag(prefix+"mode.choices."+mode)!=null,"Missing sound choice "+mode);
            check(a.body.findViewWithTag(prefix+"mode.choices.off")==null
                    && a.body.findViewWithTag(prefix+"mode.choices.vibration")==null,"Retired modes leaked into sound choices");
            a.body.findViewWithTag(prefix+"enabled.edit").performClick();
            check(!a.phoneUi.settings.prayerEnabled(Arrays.asList(PhoneStore.PRAYERS).indexOf(prayer))
                    && !a.body.findViewWithTag(prefix+"mode.choices.adhan").isEnabled()
                    && !a.body.findViewWithTag(prefix+"weekdays.0").isEnabled()
                    && !a.body.findViewWithTag(prefix+"offset.plus").isEnabled(),"Disabled prayer left configuration active");
            a.body.findViewWithTag(prefix+"enabled.edit").performClick();
        }
        a.phoneUi.open("permissions");
        check(a.body.findViewWithTag("phone.reminders.enabled.edit")!=null,"Existing global opt-out lost its settings control");
    }

    private static void dayOverrides(MainActivity a) {
        String original=a.phoneUi.settings.value("call.fajr.mode");
        a.phoneUi.open("days:fajr");
        for(int day=0;day<7;day++)check(a.body.findViewWithTag("phone.day."+day)!=null,"Missing day override row "+day);
        a.body.findViewWithTag("phone.day.2").performClick();
        check(a.section.equals("days:fajr"),"Tuesday override must expand without another page");
        check(a.body.findViewWithTag("phone.day.2.sound.silent").isEnabled(),"Enabling Tuesday must expose its controls immediately");
        a.body.findViewWithTag("phone.day.2.sound.silent").performClick();
        a.body.findViewWithTag("phone.day.2.vibrate").performClick();
        check(a.phoneUi.settings.value("call.fajr.day-2-sound").equals("silent")
                && a.phoneUi.settings.vibrateOnDay(0,2)
                && a.phoneUi.settings.value("call.fajr.day-0-sound").equals("inherit")
                && a.phoneUi.settings.value("call.fajr.mode").equals(original),"Day override changed another day or prayer default");
        a.phoneUi.open("fajr");a.phoneUi.open("days:fajr");
        check(a.body.findViewWithTag("phone.day.2.sound.silent").isSelected()
                && ((Switch)a.body.findViewWithTag("phone.day.2.vibrate")).isChecked(),"Day override did not survive re-entry");
        a.body.findViewWithTag("phone.day.2").performClick();
        check(a.phoneUi.settings.value("call.fajr.day-2-sound").equals("inherit")
                && a.body.findViewWithTag("phone.day.2.sound.silent")==null,"Disabling override failed to restore inheritance");
    }

    private static void keyboardFooter(Instrumentation test, MainActivity a,
            String language, boolean capture) throws Exception {
        if (android.os.Build.VERSION.SDK_INT < 30) return;
        check(android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.FINGERPRINT.contains("generic"), "Owned emulator keyboard check");
        String original;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                new android.os.ParcelFileDescriptor.AutoCloseInputStream(test.getUiAutomation()
                        .executeShellCommand("settings get secure show_ime_with_hard_keyboard")),
                java.nio.charset.StandardCharsets.UTF_8))) {
            original = reader.readLine();
        }
        check(original == null || original.equals("null") || original.equals("0")
                || original.equals("1"), "Unexpected keyboard preference; preserve it unchanged");
        try {
            PhoneRuntimeCheck.shell(test, "settings put secure show_ime_with_hard_keyboard 1");
            on(test, () -> {
                a.phoneUi.open("default-audio");
                a.body.findViewWithTag("phone.reminders.volume.edit").performClick();
                EditText input = a.body.findViewWithTag("phone.reminders.volume.input");
                input.requestFocus();
                a.getSystemService(android.view.inputmethod.InputMethodManager.class)
                        .showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            });
            LiveUiCheck.untilUi(test, () -> a.getWindow().getDecorView().getRootWindowInsets()
                    .isVisible(android.view.WindowInsets.Type.ime())
                    && !a.contentRoot.isLayoutRequested() && !a.body.isLayoutRequested());
            capture(test,a,language+"-keyboard-footer",capture);
            on(test, () -> {
                check(!a.getSystemService(android.view.inputmethod.InputMethodManager.class)
                        .isFullscreenMode(), "IME fullscreen extraction hides the app footer");
                android.graphics.Rect available = new android.graphics.Rect();
                a.getWindow().getDecorView().getWindowVisibleDisplayFrame(available);
                for (String tag : new String[] {"phone.reminders.volume.plus",
                        "phone.reminders.volume.minus", "phone.reminders.volume.input"}) {
                    View control = a.getWindow().getDecorView().findViewWithTag(tag);
                    android.graphics.Rect bounds = new android.graphics.Rect();
                    check(control.getGlobalVisibleRect(bounds)
                            && bounds.height() == control.getHeight()
                            && available.contains(bounds), "Keyboard covers " + tag + " bounds=" + bounds + " height=" + control.getHeight() + " frame=" + available + " scroll=" + a.scroll.getHeight() + "/" + a.scroll.getScrollY());
                }
            });
        } finally {
            PhoneRuntimeCheck.shell(test, original == null || original.equals("null")
                    ? "settings delete secure show_ime_with_hard_keyboard"
                    : "settings put secure show_ime_with_hard_keyboard " + original);
            on(test, () -> {
                EditText input = a.body.findViewWithTag("phone.reminders.volume.input");
                if (input != null) input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
            });
        }
    }

    static void on(Instrumentation test,Runnable action){Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});if(failure[0]!=null)throw new AssertionError(failure[0]);test.waitForIdleSync();}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void capture(Instrumentation test,android.content.Context a,String name,boolean enabled)throws Exception {
        if(!enabled)return;
        if(a instanceof MainActivity)on(test,()->{MainActivity activity=(MainActivity)a;if(activity.toast!=null)activity.toast.cancel();});
        android.os.SystemClock.sleep(500);Bitmap screenshot=test.getUiAutomation().takeScreenshot();check(screenshot!=null,"Screenshot unavailable");
        try(FileOutputStream file=new FileOutputStream(new File(a.getExternalFilesDir(null),name+".png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,file);}finally{screenshot.recycle();}
    }
}

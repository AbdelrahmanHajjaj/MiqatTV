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
        Context context=test.getTargetContext();SharedPreferences prefs=PhoneStore.preferences(context);
        Map<String,?> original=new LinkedHashMap<>(prefs.getAll());String language=context.getSharedPreferences("companion",0).getString("language","system");
        MainActivity[] current={null};
        try {
            PhoneAlarms.cancel(context);prefs.edit().clear().commit();
            for(String lang:new String[]{"en","ar"}) {
                context.getSharedPreferences("companion",0).edit().putString("language",lang).commit();
                MainActivity a=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));current[0]=a;
                on(test,()->{
                    check(a.page.equals("prayers"),"Standalone prayer must be the default");
                    check(a.body.findViewWithTag("phone.countdown")!=null,"Calculated next prayer missing");
                    for(String prayer:PhoneStore.PRAYERS)check(a.body.findViewWithTag("phone.prayer."+prayer)!=null,"Daily overview missing "+prayer);
                    check(a.getWindow().getDecorView().findViewWithTag("nav.phone-settings").isEnabled(),"Phone settings must not need a TV");
                    check(a.body.getLayoutDirection()==(lang.equals("ar")?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR),"Phone prayer direction");
                });
                capture(test,a,lang+"-phone-home",capture);
                on(test,()->{
                    a.phoneUi.open("audio");Switch enabled=a.body.findViewWithTag("phone.reminders.enabled.edit");
                    if(!a.phoneUi.settings.locationReady){enabled.performClick();check(a.section.equals("calculation")&&!a.phoneUi.settings.enabled,"Alerts require location confirmation");}
                    a.phoneUi.open("calculation");a.body.findViewWithTag("phone.location.confirm").performClick();check(a.phoneUi.settings.locationReady,"Location confirmation not saved");
                    a.phoneUi.open("audio");
                    check(a.body.findViewWithTag("phone.reminders.volume.input")==null,"Number editor must stay closed until tapped");
                    a.body.findViewWithTag("phone.reminders.volume.edit").performClick();
                    EditText number=a.body.findViewWithTag("phone.reminders.volume.input");String before=a.phoneUi.settings.value("reminders.volume");number.setText("٣٥");
                    a.body.findViewWithTag("phone.reminders.volume.cancel").performClick();check(a.phoneUi.settings.value("reminders.volume").equals(before),"Cancel changed saved volume");
                    a.body.findViewWithTag("phone.reminders.volume.edit").performClick();number=a.body.findViewWithTag("phone.reminders.volume.input");number.setText("٣٥");a.body.findViewWithTag("phone.reminders.volume.save").performClick();check(a.phoneUi.settings.volume==35,"Arabic numeric Save failed");
                    a.body.findViewWithTag("phone.reminders.volume.edit").performClick();number=a.body.findViewWithTag("phone.reminders.volume.input");number.setText("37");a.body.findViewWithTag("phone.reminders.volume.save").performClick();check(a.phoneUi.settings.volume==35,"Off-step number accepted");
                    a.phoneUi.open("fajr");check(!a.body.findViewWithTag("phone.call.fajr.volume.edit").isEnabled(),"Inherited audio must not expose an active override");
                    a.body.findViewWithTag("phone.call.fajr.default-audio.edit").performClick();a.body.findViewWithTag("phone.call.fajr.volume.edit").performClick();((EditText)a.body.findViewWithTag("phone.call.fajr.volume.input")).setText("١٥");a.body.findViewWithTag("phone.call.fajr.volume.save").performClick();check(a.phoneUi.settings.audioVolume(0)==15&&a.phoneUi.settings.volume==35,"Per-prayer audio changed default level");a.phoneUi.save("call.fajr.default-audio","true");
                    a.body.findViewWithTag("phone.call.fajr.mode.edit").performClick();
                    check(a.phoneUi.choiceDialog!=null,"Per-prayer choice sheet missing");
                    ListView modes=a.phoneUi.choiceDialog.getWindow().getDecorView().findViewWithTag("phone.choice.list");check(modes.getCheckedItemPosition()==(a.phoneUi.settings.modes[0]),"Choice sheet must show saved mode");modes.performItemClick(null,1,1);check(a.phoneUi.settings.modes[0]==1,"Per-prayer mode not independent/saved");
                    a.phoneUi.open("calculation");a.phoneUi.save("prayer.zone","Asia/Tokyo");check(!a.phoneUi.settings.locationReady,"Changed timezone must need confirmation");
                    a.phoneUi.save("prayer.zone","system");a.body.findViewWithTag("phone.location.confirm").performClick();
                    a.phoneUi.open("appearance");a.phoneUi.save("appearance.theme","dusk");check(a.ground==android.graphics.Color.parseColor("#211C32")&&a.color("lapis-700")==android.graphics.Color.parseColor("#403147"),"Phone theme not applied to all surfaces");
                    for(JSONObject c:a.phoneUi.data.controls.values()){
                        a.phoneUi.open(c.optString("section"));check(a.body.findViewWithTag("phone."+c.optString("id"))!=null,"Missing phone control "+c.optString("id"));
                    }
                    a.tab("prayers");
                    try {
                        PhoneSchedule.Day day=a.phoneUi.settings.day(System.currentTimeMillis(),0);
                        a.body.removeAllViews();a.phoneUi.home((long)day.times[5]+1000);
                        check(!a.body.findViewWithTag("phone.prayer.fajr").isSelected(),"After Isha, today's past Fajr must not be highlighted as next");
                        a.body.removeAllViews();a.phoneUi.home((long)day.times[0]-60000);
                        check(a.body.findViewWithTag("phone.prayer.fajr").isSelected(),"Upcoming Fajr must be highlighted");
                        a.render();
                    }catch(Exception invalid){throw new AssertionError(invalid);}
                    a.body.findViewWithTag("phone.prayer.fajr").performClick();check(a.section.equals("fajr"),"Prayer card did not open its customization");
                    a.phoneUi.open("permissions");check(a.body.findViewWithTag("phone.power-settings")!=null,"Background delivery repair help missing");
                    a.tab("phone-settings");
                });
                String priorQuiet=PhoneStore.read(context).value("quiet.start");
                on(test,()->{a.phoneUi.open("audio");a.body.findViewWithTag("phone.quiet.start.edit").performClick();check(a.phoneUi.choiceDialog instanceof android.app.TimePickerDialog,"Quiet hours need a clock editor");((android.app.TimePickerDialog)a.phoneUi.choiceDialog).updateTime(21,30);a.phoneUi.choiceDialog.getButton(-2).performClick();});
                LiveUiCheck.untilUi(test,()->a.phoneUi.choiceDialog==null);check(PhoneStore.read(context).value("quiet.start").equals(priorQuiet),"Clock Cancel saved a draft");
                on(test,()->{a.body.findViewWithTag("phone.quiet.start.edit").performClick();((android.app.TimePickerDialog)a.phoneUi.choiceDialog).updateTime(21,30);a.phoneUi.choiceDialog.getButton(-1).performClick();});
                LiveUiCheck.untilUi(test,()->a.phoneUi.settings.number("quiet.start")==1290);
                on(test,()->{a.phoneUi.open("fajr");a.body.findViewWithTag("phone.call.fajr.weekdays.edit").performClick();});
                LiveUiCheck.untilUi(test,()->a.phoneUi.choiceDialog.getListView().isItemChecked(0));
                on(test,()->{check(a.phoneUi.choiceDialog.getListView().getCount()==7,"Weekday editor missing days");check(a.phoneUi.choiceDialog.getListView().isItemChecked(0),"Saved Sunday missing");a.phoneUi.choiceDialog.getListView().performItemClick(null,0,0);check(!a.phoneUi.choiceDialog.getListView().isItemChecked(0),"Weekday tap did not deselect Sunday");a.phoneUi.choiceDialog.getButton(-1).performClick();});
                LiveUiCheck.untilUi(test,()->a.phoneUi.settings.number("call.fajr.weekdays")==126);
                on(test,()->{a.phoneUi.save("call.fajr.weekdays","127");a.tab("phone-settings");check(a.section.isEmpty(),"Phone settings root tab did not return from its child page");});
                capture(test,a,lang+"-phone-settings",capture);
                on(test,()->a.phoneUi.open("audio"));capture(test,a,lang+"-phone-audio",capture);
                on(test,()->a.body.findViewWithTag("phone.reminders.voice.edit").performClick());capture(test,a,lang+"-phone-voice-sheet",capture);
                on(test,()->a.phoneUi.choiceDialog.dismiss());
                on(test,()->a.phoneUi.open("calculation"));capture(test,a,lang+"-phone-location",capture);
                on(test,()->{a.phoneUi.open("fajr");a.tab("prayers");});capture(test,a,lang+"-phone-themed",capture);
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
    static void on(Instrumentation test,Runnable action){Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});if(failure[0]!=null)throw new AssertionError(failure[0]);test.waitForIdleSync();}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void capture(Instrumentation test,MainActivity a,String name,boolean enabled)throws Exception {
        if(!enabled)return;android.os.SystemClock.sleep(500);Bitmap screenshot=test.getUiAutomation().takeScreenshot();check(screenshot!=null,"Screenshot unavailable");
        try(FileOutputStream file=new FileOutputStream(new File(a.getExternalFilesDir(null),name+".png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,file);}finally{screenshot.recycle();}
    }
}

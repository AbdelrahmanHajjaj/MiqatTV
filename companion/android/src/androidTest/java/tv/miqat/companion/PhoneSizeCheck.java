package tv.miqat.companion;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.Rect;
import android.view.*;
import android.widget.TextView;
import java.util.*;

/** Run in each orientation of the owned emulator; app scale combines with OS font scale. */
final class PhoneSizeCheck {
    static void run(Instrumentation test,boolean capture,String expectedOrientation)throws Exception {
        Context c=test.getTargetContext();PhoneStore original=PhoneStore.read(c);String language=c.getSharedPreferences("companion",0).getString("language","system");MainActivity[] active={null};
        float systemScale=c.getResources().getConfiguration().fontScale;
        try {
            for(String lang:new String[]{"en","ar"})for(int size=80;size<=150;size+=10) {
                original.changed(Collections.singletonMap("display.size",Integer.toString(size))).save(c);
                c.getSharedPreferences("companion",0).edit().putString("language",lang).commit();
                MainActivity a=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));active[0]=a;final int expected=size;
                PhoneUiCheck.on(test,()->a.tab("prayers"));
                PhoneUiCheck.on(test,()->{
                    android.content.res.Configuration config=a.getResources().getConfiguration();
                    PhoneUiCheck.check(expectedOrientation.isEmpty()||expectedOrientation.equals(config.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?"landscape":"portrait"),"Emulator did not enter requested orientation: "+expectedOrientation);
                    PhoneUiCheck.check(Math.abs(a.getResources().getConfiguration().fontScale-systemScale*expected/100f)<0.02,"App scale did not combine with Android text scale");
                    View nav=a.getWindow().getDecorView().findViewWithTag("navigation");Rect visible=new Rect();PhoneUiCheck.check(nav.getGlobalVisibleRect(visible)&&visible.height()==nav.getHeight(),"Navigation clipped at "+expected);
                    for(String destination:new String[]{"prayers","tvs","phone-settings"}){
                        TextView button=a.getWindow().getDecorView().findViewWithTag("nav."+destination);
                        PhoneUiCheck.check(button!=null&&button.getHeight()>=a.views.dp(48),"Small navigation touch target");
                        if(button.getLayout()!=null)for(int line=0;line<button.getLayout().getLineCount();line++)PhoneUiCheck.check(button.getLayout().getEllipsisCount(line)==0,"Navigation label truncated");
                    }
                    for(String prayer:PhoneStore.PRAYERS){
                        ViewGroup cell=a.body.findViewWithTag("phone.prayer."+prayer);PhoneUiCheck.check(cell!=null&&cell.getHeight()>=a.views.dp(48),"Prayer card touch target");
                        PhoneUiCheck.check(cell.getTop()>=0&&cell.getBottom()<=((View)cell.getParent()).getHeight(),"Prayer card outline clipped by its row at "+expected);
                        int content=0;for(int i=0;i<cell.getChildCount();i++)content+=cell.getChildAt(i).getHeight();
                        PhoneUiCheck.check(content<=cell.getHeight()-cell.getPaddingTop()-cell.getPaddingBottom(),"Prayer card text clipped at "+expected);
                    }
                    a.phoneUi.open("audio");a.body.findViewWithTag("phone.reminders.volume.edit").performClick();View save=a.body.findViewWithTag("phone.reminders.volume.save");PhoneUiCheck.check(save!=null&&save.isEnabled(),"Numeric save missing at scale");
                    a.tab("prayers");
                    a.phoneUi.save("display.digits","western");PhoneUiCheck.check(a.phoneUi.digits("٠١٢٣").equals("0123"),"Western digits ignored in Arabic locale");
                    a.phoneUi.save("display.digits","eastern");PhoneUiCheck.check(a.phoneUi.digits("0123").equals("٠١٢٣"),"Eastern digits ignored");
                });
                PhoneUiCheck.on(test,()->a.phoneUi.open("audio"));
                PhoneUiCheck.on(test,()->{
                    View preview=a.body.findViewWithTag("phone.reminders.voice.preview"),stop=a.body.findViewWithTag("phone.reminders.voice.stop");ViewGroup row=(ViewGroup)preview.getParent();
                    PhoneUiCheck.check(preview.getWidth()>0&&stop.getWidth()>=a.views.dp(48)&&preview.getLeft()>=0&&preview.getRight()<=row.getWidth()&&stop.getLeft()>=0&&stop.getRight()<=row.getWidth(),"Voice preview/Stop clipped at "+expected);
                });
                if(size==80||size==150){
                    extended(test,a,expected,capture);
                    PhoneUiCheck.on(test,()->a.phoneUi.open("audio"));
                    PhoneUiCheck.on(test,()->a.body.findViewWithTag("phone.quiet.start.edit").performClick());
                    PhoneUiCheck.on(test,()->{Rect bounds=new Rect();View done=a.phoneUi.choiceDialog.getButton(-1);PhoneUiCheck.check(done.getGlobalVisibleRect(bounds)&&bounds.height()==done.getHeight(),"Clock save clipped at "+expected);});
                    PhoneUiCheck.capture(test,a,lang+"-phone-clock-"+size,capture);PhoneUiCheck.on(test,()->a.phoneUi.choiceDialog.dismiss());
                    PhoneUiCheck.on(test,()->{a.phoneUi.open("fajr");a.body.findViewWithTag("phone.call.fajr.weekdays.edit").performClick();});
                    PhoneUiCheck.on(test,()->{Rect bounds=new Rect();View done=a.phoneUi.choiceDialog.getButton(-1);PhoneUiCheck.check(done.getGlobalVisibleRect(bounds)&&bounds.height()==done.getHeight(),"Weekday save clipped at "+expected);});
                    PhoneUiCheck.capture(test,a,lang+"-phone-weekdays-"+size,capture);PhoneUiCheck.on(test,()->a.phoneUi.choiceDialog.dismiss());
                }
                PhoneUiCheck.on(test,()->a.tab("prayers"));
                if(size==80||size==150)PhoneUiCheck.capture(test,a,lang+"-phone-size-"+size,capture);
                PhoneUiCheck.on(test,a::finish);LiveUiCheck.untilUi(test,a::isDestroyed);active[0]=null;
            }
        }finally{if(active[0]!=null)PhoneUiCheck.on(test,active[0]::finish);original.save(c);c.getSharedPreferences("companion",0).edit().putString("language",language).commit();PhoneAlarms.rebuild(c);}
    }
    private static void extended(Instrumentation test, MainActivity a, int size, boolean capture) throws Exception {
        PhoneUiCheck.on(test,()->a.body.findViewWithTag("phone.reminders.voice.edit").performClick());
        PhoneUiCheck.on(test,()->{
            visible(a.phoneUi.choiceDialog.getButton(-1), "Voice Apply clipped at " + size);
            visible(a.phoneUi.choiceDialog.getWindow().getDecorView().findViewWithTag("phone.voice.preview"), "Voice audition clipped at " + size);
            View choices=a.phoneUi.choiceDialog.getWindow().getDecorView().findViewWithTag("phone.voice.list");
            Rect bounds=new Rect();PhoneUiCheck.check(choices.getGlobalVisibleRect(bounds)&&bounds.height()>=a.views.dp(48),"Voice list has no reachable row at " + size);
        });
        PhoneUiCheck.capture(test,a,(a.ar?"ar":"en")+"-journey-voice-"+size,capture);
        PhoneUiCheck.on(test,()->a.phoneUi.choiceDialog.dismiss());
        PhoneStore beforeWidget = PhoneStore.read(a);
        try {
            beforeWidget.changed(Collections.singletonMap("prayer.location-ready","true")).save(a);
            PhoneUiCheck.on(test,()->{a.tab("prayers");a.body.findViewWithTag("widgets.open").performClick();});
            String firstPrayer=a.phoneUi.data.prayerName(0,a.ar),lastPrayer=a.phoneUi.data.prayerName(5,a.ar);
            PhoneUiCheck.on(test,()->{
                TextView times=a.body.findViewById(R.id.widget_times);
                PhoneUiCheck.check(times.getText().toString().contains(firstPrayer) && times.getText().toString().contains(lastPrayer),"Widget preview did not show the actual timetable");
                PhoneUiCheck.check(times.getLayout()!=null&&times.getLayout().getHeight()<=times.getHeight()-times.getCompoundPaddingTop()-times.getCompoundPaddingBottom(),"Widget preview timetable clipped at " + size);
            });
            PhoneUiCheck.capture(test,a,(a.ar?"ar":"en")+"-journey-widget-"+size,capture);
        } finally { beforeWidget.save(a); }
        android.content.SharedPreferences prefs=PhoneStore.preferences(a);
        boolean hadStep=prefs.contains("onboarding-step");int oldStep=prefs.getInt("onboarding-step",0);
        try {
            prefs.edit().putInt("onboarding-step",2).commit();
            PhoneUiCheck.on(test,()->a.navigate("onboarding"));
            PhoneUiCheck.on(test,()->{visible(a.getWindow().getDecorView().findViewWithTag("onboarding.next"),"Onboarding Continue clipped at "+size);visible(a.getWindow().getDecorView().findViewWithTag("onboarding.later"),"Onboarding Later clipped at "+size);});
            PhoneUiCheck.capture(test,a,(a.ar?"ar":"en")+"-journey-appearance-"+size,capture);
            PhoneUiCheck.on(test,()->a.body.findViewWithTag("phone.appearance.background.edit").performClick());
            PhoneUiCheck.on(test,()->visible(a.phoneUi.choiceDialog.getButton(-1),"Appearance Apply clipped at "+size));
            PhoneUiCheck.capture(test,a,(a.ar?"ar":"en")+"-journey-background-"+size,capture);
            PhoneUiCheck.on(test,()->{a.phoneUi.choiceDialog.dismiss();a.tab("tvs");a.body.findViewWithTag("pair.open").performClick();a.body.findViewWithTag("setup.platform.lg").performClick();});
            PhoneUiCheck.capture(test,a,(a.ar?"ar":"en")+"-journey-tv-guide-"+size,capture);
        } finally {
            android.content.SharedPreferences.Editor restore=prefs.edit();if(hadStep)restore.putInt("onboarding-step",oldStep);else restore.remove("onboarding-step");PhoneUiCheck.check(restore.commit(),"Cannot restore onboarding size fixture");
            PhoneUiCheck.on(test,()->{if(a.phoneUi.choiceDialog!=null)a.phoneUi.choiceDialog.dismiss();a.tab("prayers");});
        }
    }
    private static void visible(View view, String message) {
        Rect bounds=new Rect();PhoneUiCheck.check(view!=null&&view.getGlobalVisibleRect(bounds)&&bounds.height()==view.getHeight()&&bounds.width()==view.getWidth(),message);
    }

}

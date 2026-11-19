package tv.miqat.companion;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.Rect;
import android.view.*;
import android.widget.TextView;
import java.util.*;

/** Run in each orientation of the owned emulator; app scale combines with OS font scale. */
final class PhoneSizeCheck {
    static void run(Instrumentation test,boolean capture)throws Exception {
        Context c=test.getTargetContext();PhoneStore original=PhoneStore.read(c);String language=c.getSharedPreferences("companion",0).getString("language","system");MainActivity[] active={null};
        float systemScale=c.getResources().getConfiguration().fontScale;
        try {
            for(String lang:new String[]{"en","ar"})for(int size=80;size<=150;size+=10) {
                original.changed(Collections.singletonMap("display.size",Integer.toString(size))).save(c);
                c.getSharedPreferences("companion",0).edit().putString("language",lang).commit();
                MainActivity a=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));active[0]=a;final int expected=size;
                PhoneUiCheck.on(test,()->{
                    PhoneUiCheck.check(Math.abs(a.getResources().getConfiguration().fontScale-systemScale*expected/100f)<0.02,"App scale did not combine with Android text scale");
                    View nav=a.getWindow().getDecorView().findViewWithTag("navigation");Rect visible=new Rect();PhoneUiCheck.check(nav.getGlobalVisibleRect(visible)&&visible.height()==nav.getHeight(),"Navigation clipped at "+expected);
                    for(String destination:new String[]{"prayers","tvs","phone-settings"}){
                        TextView button=a.getWindow().getDecorView().findViewWithTag("nav."+destination);
                        PhoneUiCheck.check(button!=null&&button.getHeight()>=a.dp(48),"Small navigation touch target");
                        if(button.getLayout()!=null)for(int line=0;line<button.getLayout().getLineCount();line++)PhoneUiCheck.check(button.getLayout().getEllipsisCount(line)==0,"Navigation label truncated");
                    }
                    for(String prayer:PhoneStore.PRAYERS){
                        ViewGroup cell=a.body.findViewWithTag("phone.prayer."+prayer);PhoneUiCheck.check(cell!=null&&cell.getHeight()>=a.dp(48),"Prayer card touch target");
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
                    PhoneUiCheck.check(preview.getWidth()>0&&stop.getWidth()>=a.dp(48)&&preview.getLeft()>=0&&preview.getRight()<=row.getWidth()&&stop.getLeft()>=0&&stop.getRight()<=row.getWidth(),"Voice preview/Stop clipped at "+expected);
                });
                PhoneUiCheck.on(test,()->a.tab("prayers"));
                if(size==80||size==150)PhoneUiCheck.capture(test,a,lang+"-phone-size-"+size,capture);
                PhoneUiCheck.on(test,a::finish);LiveUiCheck.untilUi(test,a::isDestroyed);active[0]=null;
            }
        }finally{if(active[0]!=null)PhoneUiCheck.on(test,active[0]::finish);original.save(c);c.getSharedPreferences("companion",0).edit().putString("language",language).commit();PhoneAlarms.rebuild(c);}
    }
}

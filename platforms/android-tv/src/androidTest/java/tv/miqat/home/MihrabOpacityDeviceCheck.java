package tv.miqat.home;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.KeyEvent;
import java.util.List;
import java.util.Map;

/** Real inline editor, outer draft and Home-fill boundaries; restores test preferences. */
final class MihrabOpacityDeviceCheck {
    static void run(Instrumentation test) {
        SharedPreferences prefs = Prefs.of(test.getTargetContext());
        Map<String, ?> saved = prefs.getAll();
        SettingsActivity[] active = new SettingsActivity[1];
        Bundle result = new Bundle(); int code = Activity.RESULT_CANCELED;
        try {
            prefs.edit().remove("mihrab_opacity").commit();
            check(Prefs.mihrabOpacity(test.getTargetContext()) == 85, "Missing value defaults to recommended 85");
            prefs.edit().putInt("mihrab_opacity", 49).commit();
            check(Prefs.mihrabOpacity(test.getTargetContext()) == 85, "Invalid stored value falls back safely");
            for (String language : new String[]{"en", "ar"}) {
                prefs.edit().putString("locale", language).putString("theme", "mihrab").putInt("mihrab_opacity", 85).commit();
                active[0] = start(test);
                int back = language.equals("ar") ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
                int next = language.equals("ar") ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
                enter(test, active[0]);
                key(test,active[0],KeyEvent.KEYCODE_DPAD_CENTER);
                repeat(test,active[0],back,20);
                check((int) UiContractDeviceCheck.field(active[0], "draft") == 50, "Inline lower bound");
                key(test,active[0],KeyEvent.KEYCODE_BACK);
                check(row(active[0],"appearance.mihrab-opacity").amount() == 85, "Back cancels numeric draft");
                key(test,active[0],KeyEvent.KEYCODE_DPAD_CENTER);
                repeat(test,active[0],next,20);key(test,active[0],KeyEvent.KEYCODE_DPAD_CENTER);
                check(row(active[0],"appearance.mihrab-opacity").amount() == 100, "Upper bound and outer draft");
                check(Prefs.mihrabOpacity(test.getTargetContext()) == 85, "OK waits for Apply");
                key(test,active[0],KeyEvent.KEYCODE_BACK);key(test,active[0],KeyEvent.KEYCODE_BACK);
                test.waitForIdleSync();active[0]=start(test);enter(test,active[0]);
                check(row(active[0],"appearance.mihrab-opacity").amount() == 85, "Leaving page discards unsaved change");
                key(test,active[0],KeyEvent.KEYCODE_DPAD_CENTER);repeat(test,active[0],back,20);key(test,active[0],KeyEvent.KEYCODE_DPAD_CENTER);
                int distance=index(active[0],"appearance.apply")-(int)UiContractDeviceCheck.field(active[0],"rowIndex");
                repeat(test,active[0],KeyEvent.KEYCODE_DPAD_DOWN,distance);key(test,active[0],KeyEvent.KEYCODE_DPAD_CENTER);
                check(Prefs.mihrabOpacity(test.getTargetContext()) == 50, "Apply persists opacity");
                test.runOnMainSync(()->active[0].finish());test.waitForIdleSync();active[0]=start(test);enter(test,active[0]);
                check(row(active[0],"appearance.mihrab-opacity").amount() == 50, "Saved opacity survives re-entry");
                UiContractDeviceCheck.onMain(test,()->{
                    for(int percent:new int[]{50,85,100}) {
                        ArchView arch=new ArchView(active[0],percent);
                        check(((Paint)UiContractDeviceCheck.field(arch,"fill")).getAlpha()==Math.round(percent*255/100f),"Only fill alpha follows preference");
                        check(arch.getAlpha()==1f,"Whole view stays opaque");
                    }
                    check(((Paint)UiContractDeviceCheck.field(new ArchView(active[0]),"fill")).getAlpha()==255,"Non-Home frames remain opaque");
                });
                test.runOnMainSync(()->active[0].finish());test.waitForIdleSync();active[0]=null;
            }
            code=Activity.RESULT_OK;result.putString("stream","Native opacity passed: bilingual 50–100 bounds, numeric Back, outer draft cancellation, Apply/re-entry and fill-only alpha.\n");
        } catch(Throwable error) {result.putString("stream",android.util.Log.getStackTraceString(error));}
        finally {
            if(active[0]!=null)test.runOnMainSync(()->active[0].finish());
            UiScaleDeviceCheck.restore(prefs,saved);
            if(!prefs.getAll().equals(saved)){code=Activity.RESULT_CANCELED;result.putString("stream","Opacity check did not restore preferences.\n");}
        }
        test.finish(code,result);
    }
    private static SettingsActivity start(Instrumentation test) {
        SettingsActivity activity=(SettingsActivity)test.startActivitySync(new Intent(test.getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        test.waitForIdleSync();UiContractDeviceCheck.waitFocus(activity);return activity;
    }
    private static void enter(Instrumentation test,SettingsActivity activity){repeat(test,activity,KeyEvent.KEYCODE_DPAD_DOWN,4);key(test,activity,KeyEvent.KEYCODE_DPAD_CENTER);repeat(test,activity,KeyEvent.KEYCODE_DPAD_DOWN,index(activity,"appearance.mihrab-opacity"));}
    private static int index(SettingsActivity activity,String id){List<?> rows=(List<?>)UiContractDeviceCheck.field(activity,"rows");for(int i=0;i<rows.size();i++)if(((SettingsActivity.Row)rows.get(i)).contract.getId().equals(id))return i;throw new AssertionError("Missing row: "+id);}
    private static SettingsActivity.Row row(SettingsActivity activity,String id){return (SettingsActivity.Row)((List<?>)UiContractDeviceCheck.field(activity,"rows")).get(index(activity,id));}
    private static void repeat(Instrumentation test,SettingsActivity activity,int key,int count){for(int i=0;i<count;i++)key(test,activity,key);}
    private static void key(Instrumentation test,SettingsActivity activity,int key){UiContractDeviceCheck.key(test,activity,key);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

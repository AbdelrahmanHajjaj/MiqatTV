package tv.miqat.companion;

import android.Manifest;
import android.app.Instrumentation;
import android.appwidget.*;
import android.content.ComponentName;
import android.os.Build;
import android.widget.*;
import java.util.Collections;

/** Real AppWidgetService binding/callbacks on the owned emulator, with one temporary host. */
final class WidgetHostCheck {
    static void run(Instrumentation test,MainActivity a)throws Exception {
        if(Build.VERSION.SDK_INT<29)throw new IllegalStateException("Widget host fixture requires API 29+");
        PhoneStore original=PhoneStore.read(a);AppWidgetHost[] host={null};AppWidgetHostView[] view={null};int[] id={0};
        test.getUiAutomation().adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET);
        try {
            PhoneUiCheck.on(test,()->{
                host[0]=new AppWidgetHost(a,41939);host[0].startListening();id[0]=host[0].allocateAppWidgetId();
                AppWidgetManager manager=AppWidgetManager.getInstance(a);
                PhoneUiCheck.check(manager.bindAppWidgetIdIfAllowed(id[0],new ComponentName(a,PrayerWidget.class)),"System rejected the test-owned widget binding");
                view[0]=host[0].createView(a,id[0],manager.getAppWidgetInfo(id[0]));
                a.body.addView(view[0],new LinearLayout.LayoutParams(-1,a.views.dp(300)));
            });
            String fajr=a.phoneUi.data.prayerName(0,a.ar);
            LiveUiCheck.untilUi(test,()->{TextView times=view[0].findViewById(R.id.widget_times);return times!=null&&times.getText().toString().contains(fajr);});
            original.changed(Collections.singletonMap("prayer.location-ready","false")).save(a);
            PrayerWidget.updateAll(a);
            LiveUiCheck.untilUi(test,()->{TextView times=view[0].findViewById(R.id.widget_times);return times!=null&&times.getText().toString().equals(a.t("phone_location_needed"));});
        }finally{
            PhoneUiCheck.on(test,()->{if(host[0]!=null){if(id[0]!=0)host[0].deleteAppWidgetId(id[0]);host[0].stopListening();}if(view[0]!=null)a.body.removeView(view[0]);});
            test.getUiAutomation().dropShellPermissionIdentity();original.save(a);PrayerWidget.updateAll(a);
        }
    }
}

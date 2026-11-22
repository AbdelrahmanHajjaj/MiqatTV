package tv.miqat.companion;

import android.Manifest;
import android.app.Instrumentation;
import android.appwidget.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.util.SizeF;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Real provider bounds and one-cell text, with modern and legacy host options. */
final class WidgetSmallCheck {
    static void run(Instrumentation test,boolean captures) throws Exception {
        Context c=test.getTargetContext();android.content.SharedPreferences prefs=PhoneStore.preferences(c);
        String saved=prefs.getString("settings",null);
        android.content.SharedPreferences ui=c.getSharedPreferences("companion",0);String language=ui.getString("language","system");
        MainActivity a=null;AppWidgetHost host=null;
        try {
            Map<String,String> changes=new LinkedHashMap<>();
            changes.put("prayer.location-ready","true");changes.put("prayer.background-location","false");
            changes.put("prayer.location","cairo");changes.put("prayer.latitude","30.0444");changes.put("prayer.longitude","31.2357");
            changes.put("reminders.enabled","false");changes.put("display.clock","12");
            PhoneStore settings=PhoneStore.read(c).changed(changes);settings.save(c);
            a=LiveUiCheck.launch(test);MainActivity shown=a;
            test.getUiAutomation().adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET);
            host=new AppWidgetHost(a,41940);AppWidgetHost owned=host;
            PhoneUiCheck.on(test,()->{shown.body.removeAllViews();owned.startListening();});
            AppWidgetManager manager=AppWidgetManager.getInstance(c);
            for(String preset:WidgetOptions.LAYOUTS) {
                int id=host.allocateAppWidgetId();
                PhoneUiCheck.check(manager.bindAppWidgetIdIfAllowed(id,PrayerWidget.component(c,preset)),"Cannot bind one-cell widget");
                AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);
                PhoneUiCheck.check(info.minResizeWidth<=shown.views.dp(40)+1 && info.minResizeHeight<=shown.views.dp(40)+1,"Provider still blocks one-cell resizing: "+preset);
                if(preset.equals("next") && Build.VERSION.SDK_INT>=31)
                    PhoneUiCheck.check(info.targetCellWidth==1 && info.targetCellHeight==1,"Next prayer does not start at 1x1");
                final AppWidgetHostView[] placed={null};
                PhoneUiCheck.on(test,()->{placed[0]=owned.createView(shown,id,info);shown.body.addView(placed[0],new LinearLayout.LayoutParams(shown.views.dp(96),shown.views.dp(96)));});
                Bundle bounds=bounds(80,80,true);manager.updateAppWidgetOptions(id,bounds);PrayerWidget.update(c,id);
                LiveUiCheck.untilUi(test,()->{
                    TextView label=placed[0].findViewById(R.id.widget_next_label);
                    return label!=null && !label.getText().toString().isEmpty()
                            && placed[0].findViewById(R.id.widget_title).getVisibility()==View.GONE;
                });
            }
            long now=(long)settings.day(System.currentTimeMillis(),0).times[3]+60000;
            for(String lang:new String[]{"en","ar"}) {
                ui.edit().putString("language",lang).commit();
                String widestPrayer=settings.data.prayerName(4,lang.equals("ar"));
                for(float font:new float[]{1f,2f}) for(int size:new int[]{100,120}) {
                    Configuration config=new Configuration(c.getResources().getConfiguration());config.fontScale=font;
                    Context scaled=c.createConfigurationContext(config);
                    PhoneUiCheck.on(test,()->{
                        for(String preset:WidgetOptions.LAYOUTS) for(int edge:new int[]{40,56,80}) for(boolean modern:new boolean[]{false,true}) {
                            WidgetOptions options=new WidgetOptions();options.layout=preset;options.size=size;options.date=true;
                            String original=options.encode();
                            RemoteViews content=PrayerWidget.responsive(scaled,options,bounds(edge,edge,modern));
                            View view=content.apply(scaled,new FrameLayout(scaled));measure(view,shown,edge,edge);
                            PhoneUiCheck.check(view.findViewById(R.id.widget_title).getVisibility()==View.GONE
                                    && view.findViewById(R.id.widget_prayers).getVisibility()==View.GONE
                                    && view.findViewById(R.id.widget_strip).getVisibility()==View.GONE,"One-cell widget did not prioritize next prayer");
                            PhoneUiCheck.check(view.getContentDescription()!=null && view.getContentDescription().length()>0,"Small widget lost accessible full details");
                            // Stress the longest prayer name and a wide 12-hour clock at actual small bounds.
                            content.setTextViewText(R.id.widget_next_label,widestPrayer);
                            content.setTextViewText(R.id.widget_next_time,tv.miqat.core.DisplayText.INSTANCE.digits(edge<72*font?"11:48":"11:48 PM",lang.equals("ar")));
                            view=content.apply(scaled,new FrameLayout(scaled));measure(view,shown,edge,edge);
                            text(view,R.id.widget_next_label,edge,lang,font);text(view,R.id.widget_next_time,edge,lang,font);
                            PhoneUiCheck.check(options.encode().equals(original),"Resize changed widget settings");
                            if(captures && preset.equals("next") && size==100 && font==1f && modern)
                                capture(c,view,lang+"-widget-"+edge);
                        }
                        WidgetOptions options=new WidgetOptions();
                        for(int[] dimensions:new int[][]{{300,400},{500,140},{80,80}}) {
                            View view=PrayerWidget.responsive(scaled,options,bounds(dimensions[0],dimensions[1],true)).apply(scaled,new FrameLayout(scaled));
                            measure(view,shown,dimensions[0],dimensions[1]);
                            String expected=options.layoutFor(dimensions[0],dimensions[1],font);
                            PhoneUiCheck.check((view.findViewById(R.id.widget_prayers).getVisibility()==View.VISIBLE)==expected.equals("day")
                                    && (view.findViewById(R.id.widget_strip).getVisibility()==View.VISIBLE)==expected.equals("strip"),"Widget did not recover detail after resizing");
                        }
                    });
                }
                PhoneUiCheck.on(test,()->{
                    View preview=new RemoteViews(c.getPackageName(),R.layout.widget_preview_next).apply(MainActivity.localized(c),new FrameLayout(shown));
                    measure(preview,shown,80,80);
                    if(captures)capture(c,preview,lang+"-picker-next-small");
                });
            }
            PhoneUiCheck.on(test,()->{
                View accessible=WidgetRenderer.create(c,new WidgetOptions(),40,40,now).apply(c,new FrameLayout(shown));
                String full=PhoneTime.at(settings,settings.day(now,0).times[4],PhoneAlarms.arabic(c));
                PhoneUiCheck.check(accessible.getContentDescription().toString().contains(full),"One-cell accessibility lost the full prayer time");
            });
            settings.changed(Collections.singletonMap("prayer.location-ready","false")).save(c);
            PhoneUiCheck.on(test,()->{
                View missing=WidgetRenderer.create(c,new WidgetOptions(),40,40,now).apply(c,new FrameLayout(shown));measure(missing,shown,40,40);
                text(missing,R.id.widget_times,40,"missing location",1);
            });
        } finally {
            if(host!=null){AppWidgetHost close=host;PhoneUiCheck.on(test,()->{close.stopListening();close.deleteHost();});}
            test.getUiAutomation().dropShellPermissionIdentity();
            if(a!=null){MainActivity close=a;PhoneUiCheck.on(test,close::finish);}
            android.content.SharedPreferences.Editor restore=prefs.edit();if(saved==null)restore.remove("settings");else restore.putString("settings",saved);
            PhoneUiCheck.check(restore.commit(),"Cannot restore widget settings fixture");
            PhoneUiCheck.check(ui.edit().putString("language",language).commit(),"Cannot restore widget language fixture");
            PhoneAlarms.rebuild(c);PhoneBackgroundLocation.sync(c);
        }
    }
    private static Bundle bounds(int width,int height,boolean modern) {
        Bundle bounds=new Bundle();bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,width);bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,width);
        bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height);bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height);
        if(modern && Build.VERSION.SDK_INT>=31){ArrayList<SizeF> sizes=new ArrayList<>();sizes.add(new SizeF(width,height));bounds.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,sizes);}return bounds;
    }
    private static void measure(View view,MainActivity a,int width,int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(a.views.dp(width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(a.views.dp(height),View.MeasureSpec.EXACTLY));view.layout(0,0,a.views.dp(width),a.views.dp(height));
    }
    private static void text(View view,int id,int edge,String lang,float font) {
        TextView text=view.findViewById(id);
        PhoneUiCheck.check(text.getVisibility()==View.VISIBLE && text.getLayout()!=null && !text.getText().toString().isEmpty(),"Empty small widget text");
        PhoneUiCheck.check(text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom(),"Clipped widget height: "+edge+" "+lang+" font="+font);
        PhoneUiCheck.check(text.getLayout().getLineEnd(text.getLayout().getLineCount()-1)>=text.getText().length(),"Widget cut off its last text line");
        for(int line=0;line<text.getLayout().getLineCount();line++)
            PhoneUiCheck.check(text.getLayout().getEllipsisCount(line)==0 && text.getLayout().getLineWidth(line)<=text.getWidth()-text.getCompoundPaddingLeft()-text.getCompoundPaddingRight()+1,"Clipped widget width: "+edge+" "+lang+" font="+font);
    }
    private static void capture(Context c,View view,String name) {
        Bitmap image=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));
        try(FileOutputStream file=new FileOutputStream(new File(c.getExternalFilesDir(null),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,file);}catch(IOException failure){throw new AssertionError(failure);}finally{image.recycle();}
    }
}

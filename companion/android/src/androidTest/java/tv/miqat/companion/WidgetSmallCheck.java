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
            changes.put("display.digits", "auto");
            PhoneStore settings=PhoneStore.read(c).changed(changes);settings.save(c);
            a=LiveUiCheck.launch(test);MainActivity shown=a;
            test.getUiAutomation().adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET);
            host=new AppWidgetHost(a,41940);AppWidgetHost owned=host;
            PhoneUiCheck.on(test,()->{shown.body.removeAllViews();owned.startListening();});
            AppWidgetManager manager=AppWidgetManager.getInstance(c);
            String[] resizable = {"next", "day", "strip"};
            for(String preset:resizable) {
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
                String hijri = tv.miqat.core.DisplayText.INSTANCE.digits(
                        PhoneTime.hijri(settings, now, lang.equals("ar")), lang.equals("ar"));
                PhoneUiCheck.on(test,()->spacing(c,shown,now,lang,hijri,captures));
                PhoneUiCheck.on(test, () -> countdownCircle(c, shown, settings, now, lang, captures));
                for(float font:new float[]{1f,2f}) for(int size:new int[]{100,120}) {
                    Configuration config=new Configuration(c.getResources().getConfiguration());config.fontScale=font;
                    Context scaled=c.createConfigurationContext(config);
                    PhoneUiCheck.on(test,()->{
                        for(String preset:resizable) for(int edge:new int[]{40,56,80}) for(boolean modern:new boolean[]{false,true}) {
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

    private static void countdownCircle(Context c, MainActivity a, PhoneStore settings, long now,
            String language, boolean captures) {
        WidgetOptions options = new WidgetOptions();
        options.layout = "circle";
        int size = WidgetOptions.CIRCLE_SIZE;
        long maghrib = (long) settings.day(now, 0).times[4];
        int[] arc = new int[2];
        for (int i = 0; i < arc.length; i++) {
            View view = WidgetRenderer.create(c, options, size, size, i == 0 ? now : maghrib - 60_000)
                    .apply(c, new FrameLayout(a));
            measure(view, a, size, size);
            boolean arabic = language.equals("ar");
            try {
                PhoneUiCheck.check(((TextView) view.findViewById(R.id.widget_next_label)).getText()
                                .toString().equals(settings.data.prayerName(4, arabic)),
                        "Circle did not show the next prayer");
            } catch (Exception invalid) {
                throw new AssertionError(invalid);
            }
            text(view, R.id.widget_title, size, language, 1);
            text(view, R.id.widget_next_remaining, size, language, 1);
            Bitmap ring = ((android.graphics.drawable.BitmapDrawable)
                    ((ImageView) view.findViewById(R.id.widget_circle_ring)).getDrawable()).getBitmap();
            for (int x = 0; x < ring.getWidth(); x++)
                for (int y = 0; y < ring.getHeight(); y++)
                    if (Color.alpha(ring.getPixel(x, y)) == 255) arc[i]++;
            if (i == 0 && captures) capture(c, view, language + "-widget-countdown-circle");
        }
        PhoneUiCheck.check(arc[1] > 0 && arc[0] > 3 * arc[1],
                "Circle did not lose progress as the next prayer approached");
    }
    private static void spacing(Context c,MainActivity a,long now,String lang,String hijri,boolean captures) {
        for(float font:new float[]{1f,2f}) for(String shape:WidgetOptions.SHAPES) {
            Configuration config=new Configuration(c.getResources().getConfiguration());config.fontScale=font;
            Context context=c.createConfigurationContext(config);
            WidgetOptions options=new WidgetOptions();options.layout="next";options.shape=shape;
            for(int[] dimensions:new int[][]{{340,80},{340,100},{340,180},{80,320},{80,80}}) {
                int width=dimensions[0],height=dimensions[1];
                View view=WidgetRenderer.create(context,options,width,height,now).apply(context,new FrameLayout(context));measure(view,a,width,height);
                TextView title=view.findViewById(R.id.widget_title),remaining=view.findViewById(R.id.widget_next_remaining);
                if(width>200 || height>200) {
                    PhoneUiCheck.check(title.getVisibility()==View.VISIBLE,"Wide one-row widget hides the Hijri date despite available width");
                    PhoneUiCheck.check(title.getText().toString().equals(hijri),"Widget date is not the real Hijri date");
                    text(view,R.id.widget_title,width,lang,font);inside(view,title);
                    if(width>200)PhoneUiCheck.check(title.getTextSize()>remaining.getTextSize(),"Hijri date must be more prominent than the countdown");
                }
                PhoneUiCheck.check(remaining.getVisibility()==View.VISIBLE && !remaining.getText().toString().isEmpty(),"Available widget space lost its countdown");
                for(int id:new int[]{R.id.widget_next_label,R.id.widget_next_time,R.id.widget_next_remaining}) {
                    text(view,id,width,lang,font);inside(view,view.findViewById(id));
                }
                if(width>200 && height>=100 && font==1f)
                    PhoneUiCheck.check(remaining.getTextSize()/c.getResources().getDisplayMetrics().density>=13,"Countdown is still unnecessarily small");
                if(captures && font==1f && shape.equals("rounded"))capture(c,view,lang+"-widget-spacing-"+width+"x"+height);
            }
            View reused=null;
            for(int[] dimensions:new int[][]{{80,80},{340,80},{80,80},{340,180}}) {
                RemoteViews content=WidgetRenderer.create(context,options,dimensions[0],dimensions[1],now);
                if(reused==null)reused=content.apply(context,new FrameLayout(context));else content.reapply(context,reused);
                measure(reused,a,dimensions[0],dimensions[1]);
                PhoneUiCheck.check((reused.findViewById(R.id.widget_next_wide_row)!=null)==(dimensions[0]>200),"Host reused the previous size's prayer layout");
                for(int id:new int[]{R.id.widget_next_label,R.id.widget_next_time,R.id.widget_next_remaining})text(reused,id,dimensions[0],lang,font);
            }
        }
    }
    private static void inside(View root,TextView text) {
        Rect bounds=new Rect();text.getDrawingRect(bounds);((ViewGroup)root).offsetDescendantRectToMyCoords(text,bounds);
        PhoneUiCheck.check(new Rect(0,0,root.getWidth(),root.getHeight()).contains(bounds),"Widget text leaves actual host bounds: "+text.getText()+" "+bounds);
        Bitmap pixels=Bitmap.createBitmap(text.getWidth(),text.getHeight(),Bitmap.Config.ARGB_8888);text.draw(new Canvas(pixels));
        float radius=Math.min(root.getResources().getDisplayMetrics().density*28,Math.min(root.getWidth(),root.getHeight())/2f);
        try {
            for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++)if(Color.alpha(pixels.getPixel(x,y))>16) {
                float px=bounds.left+x+.5f,py=bounds.top+y+.5f;
                float dx=Math.max(radius-px,px-(root.getWidth()-radius)),dy=Math.max(radius-py,py-(root.getHeight()-radius));
                PhoneUiCheck.check(dx<=0 || dy<=0 || dx*dx+dy*dy<=radius*radius,"Launcher rounding cuts widget text: "+text.getText());
            }
        }finally{pixels.recycle();}
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
        PhoneUiCheck.check(text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom(),
                "Clipped widget height: " + text.getResources().getResourceEntryName(id) + " " + edge
                        + " " + lang + " font=" + font + " text=" + text.getText()
                        + " layout=" + text.getLayout().getHeight() + " view=" + text.getHeight());
        PhoneUiCheck.check(text.getLayout().getLineEnd(text.getLayout().getLineCount()-1)>=text.getText().length(),"Widget cut off its last text line");
        for(int line=0;line<text.getLayout().getLineCount();line++)
            PhoneUiCheck.check(text.getLayout().getEllipsisCount(line)==0 && text.getLayout().getLineWidth(line)<=text.getWidth()-text.getCompoundPaddingLeft()-text.getCompoundPaddingRight()+1,"Clipped widget width: "+edge+" "+lang+" font="+font);
    }
    private static void capture(Context c,View view,String name) {
        Bitmap image=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));
        try(FileOutputStream file=new FileOutputStream(new File(c.getExternalFilesDir(null),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,file);}catch(IOException failure){throw new AssertionError(failure);}finally{image.recycle();}
    }
}

package tv.miqat.companion;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.json.JSONObject;
import java.util.Map;

/** Small illustrative Home, not the TV renderer or a calculated prayer schedule. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic view; never inflated from XML or layout tools.
final class HomePreview extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arch=new Path();
    private static final String[] TIMES={"05:10","12:00","15:15","17:30","19:00"};
    private final Map<String,String> values;
    private final JSONObject colors;
    private final boolean ar;

    HomePreview(Context context,Catalog catalog,Map<String,String> values,boolean ar) {
        this(context,catalog,values,ar,catalog.data.optString("demoSystemLanguage"));
    }
    HomePreview(Context context,Catalog catalog,Map<String,String> values,boolean ar,String systemLanguage) {
        super(context);this.values=values;
        String language=values.get("display.language");
        this.ar="ar".equals(language)||"system".equals(language)&&systemLanguage.equals("ar");
        JSONObject palette=catalog.data.optJSONObject("colors");
        JSONObject theme=catalog.data.optJSONObject("themes").optJSONObject(values.get("appearance.theme"));
        if(theme!=null) {
            JSONObject changes=theme.optJSONObject("colors");
            if(changes!=null) {try{palette=new JSONObject(palette.toString());java.util.Iterator<String> keys=changes.keys();while(keys.hasNext()){String k=keys.next();palette.put(k,changes.getString(k));}}catch(Exception e){throw new IllegalStateException(e);}}
        }
        if("custom".equals(values.get("appearance.theme"))) {
            try {
                JSONObject changes=new JSONObject(values.get("appearance.custom-theme")).getJSONObject("colors");
                palette=new JSONObject(palette.toString());java.util.Iterator<String> keys=changes.keys();while(keys.hasNext()){String k=keys.next();palette.put(k,changes.getString(k));}
            } catch(Exception ignored) { /* No custom colours imported yet: use the canonical base. */ }
        }
        colors=palette;
        setContentDescription(catalog.label(catalog.data.optJSONObject("text").optJSONObject("preview"),ar));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    private int color(String key){return Color.parseColor(colors.optString(key));}
    private void rect(Canvas c,float x,float y,float w,float h,String color){paint.setColor(color(color));paint.setStyle(Paint.Style.FILL);c.drawRoundRect(x,y,x+w,y+h,8,8,paint);}
    private void text(Canvas c,String text,float x,float y,int size,String color){
        if("eastern".equals(values.get("display.digits"))||"auto".equals(values.get("display.digits"))&&ar) {
            StringBuilder localized=new StringBuilder(text);for(int i=0;i<localized.length();i++){char n=localized.charAt(i);if(n>='0'&&n<='9')localized.setCharAt(i,(char)('٠'+n-'0'));}text=localized.toString();
        }
        paint.setColor(color(color));paint.setTextSize(size);paint.setTypeface(Typeface.DEFAULT);paint.setTextAlign(Paint.Align.CENTER);c.drawText(text,x,y,paint);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec);setMeasuredDimension(width,resolveSize(Math.round(width*9f/16f),heightSpec));}
    @Override protected void onDraw(Canvas actual) {
        super.onDraw(actual);int save=actual.save();actual.scale(getWidth()/640f,getHeight()/360f);
        Canvas c=actual;rect(c,0,0,640,360,"lapis-900");
        String background=values.get("appearance.background");
        if(background!=null&&!background.equals("solid")&&!background.equals("plain")&&!background.equals("theme")) {
            paint.setColor(color("gold-400"));paint.setAlpha(28);paint.setStrokeWidth(1);paint.setStyle(Paint.Style.STROKE);
            for(int x=-360;x<640;x+=48)c.drawLine(x,0,x+360,360,paint);
            for(int x=0;x<1000;x+=48)c.drawLine(x,0,x-360,360,paint);
            paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);
        }
        text(c,"12".equals(values.get("display.clock"))?"2:30 PM":"14:30",ar?90:550,45,24,"plaster-100");
        text(c,ar?"غرفة المعيشة":"Living room",320,43,19,"gold-400");
        String layout=values.get("appearance.countdown");
        boolean hidden="hidden".equals(layout),compact="compact".equals(layout);
        String side=values.get("appearance.position");boolean left="left".equals(side)||"auto".equals(side)&&!ar;
        float panelX=left?24:410, center=panelX+103;
        if(!hidden&&!compact) {
            MiqatShape.set(arch,206,183,false);arch.offset(panelX,77);
            paint.setColor(color("lapis-800"));c.drawPath(arch,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(color("gold-400"));c.drawPath(arch,paint);paint.setStyle(Paint.Style.FILL);
            text(c,ar?"العصر":"Asr",center,167,23,"gold-400");text(c,"00:45",center,207,32,"plaster-100");
        }
        if(compact) {float top="bottom".equals(values.get("appearance.compact-position"))?292:76;rect(c,24,top,592,42,"lapis-800");text(c,ar?"العصر  00:45":"Asr  00:45",320,top+28,20,"gold-400");}
        float start=!hidden&&!compact?(left?254:24):24,width=!hidden&&!compact?362:592;
        String prayers=values.get("appearance.prayers");
        if(!"none".equals(prayers)) {
            int count="all".equals(prayers)?5:"both".equals(prayers)?2:1;
            for(int i=0;i<count;i++){float cell=width/count,x=start+(ar?count-1-i:i)*cell;rect(c,x,135,cell-5,55,"lapis-800");text(c,TIMES[i],x+(cell-5)/2,168,15,"plaster-100");}
        }
        int rows="classic".equals(values.get("appearance.favourites"))?1:2;
        for(int r=0;r<rows;r++)for(int i=0;i<4;i++){float cell=width/4;rect(c,start+i*cell,208+r*39,cell-7,30,"lapis-700");}
        text(c,ar?"معاينة تقريبية":"Representative preview",320,337,14,"mist-400");actual.restoreToCount(save);
    }
}

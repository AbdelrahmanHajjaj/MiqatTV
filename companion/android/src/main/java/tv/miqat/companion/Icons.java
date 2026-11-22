package tv.miqat.companion;

import android.content.Context;
import android.graphics.drawable.Drawable;

/** Generated native vectors share the TV/browser's canonical icon geometry. */
final class Icons {
    private Icons(){}
    static int resource(Context context,String name){
        int id;
        switch(name) {
            case "fajr": id=R.drawable.companion_icon_fajr; break;
            case "sunrise": id=R.drawable.companion_icon_sunrise; break;
            case "dhuhr": id=R.drawable.companion_icon_dhuhr; break;
            case "asr": id=R.drawable.companion_icon_asr; break;
            case "maghrib": id=R.drawable.companion_icon_maghrib; break;
            case "isha": id=R.drawable.companion_icon_isha; break;
            default: id=context.getResources().getIdentifier("companion_icon_"+name,"drawable",context.getPackageName());
        }
        if(id==0)throw new IllegalArgumentException("Unknown icon: "+name);
        return id;
    }
    static Drawable get(Context context,String name,int color){
        Drawable icon=context.getDrawable(resource(context,name)).mutate();icon.setTint(color);
        int size=Math.round(24*context.getResources().getDisplayMetrics().density);icon.setBounds(0,0,size,size);return icon;
    }
}

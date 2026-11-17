package tv.miqat.companion;

import android.content.Context;
import android.graphics.drawable.Drawable;

/** Generated native vectors share the TV/browser's canonical icon geometry. */
final class Icons {
    private Icons(){}
    static Drawable get(Context context,String name,int color){
        int id=context.getResources().getIdentifier("companion_icon_"+name,"drawable",context.getPackageName());
        if(id==0)throw new IllegalArgumentException("Unknown icon: "+name);
        Drawable icon=context.getDrawable(id).mutate();icon.setTint(color);
        int size=Math.round(24*context.getResources().getDisplayMetrics().density);icon.setBounds(0,0,size,size);return icon;
    }
}

package tv.miqat.companion;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;
import static tv.miqat.companion.PhoneUiCheck.*;
import static tv.miqat.companion.PhoneRuntimeCheck.await;

final class PhonePresentationCheck {
    private static AccessibilityNodeInfo find(AccessibilityNodeInfo node,String value,boolean id) {
        CharSequence text=node.getText();
        if(node.isVisibleToUser() && (id?value.equals(node.getViewIdResourceName()):text!=null && text.toString().startsWith(value)))
            return AccessibilityNodeInfo.obtain(node);
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;
            try{AccessibilityNodeInfo match=find(child,value,id);if(match!=null)return match;}finally{child.recycle();}
        }
        return null;
    }
    static AccessibilityNodeInfo notificationText(Instrumentation test,String text) {
        android.accessibilityservice.AccessibilityServiceInfo info=test.getUiAutomation().getServiceInfo();
        info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                |android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        test.getUiAutomation().setServiceInfo(info);
        for(android.view.accessibility.AccessibilityWindowInfo window:test.getUiAutomation().getWindows()) {
            AccessibilityNodeInfo root=window.getRoot();
            try {
                if(root!=null && "com.android.systemui".contentEquals(root.getPackageName())) {
                    AccessibilityNodeInfo found=find(root,text,false);if(found!=null)return found;
                }
            } finally {if(root!=null)root.recycle();window.recycle();}
        }
        return null;
    }
    static void expandNotification(Instrumentation test,String title) throws Exception {
        LiveUiCheck.until(()->{AccessibilityNodeInfo node=notificationText(test,title);if(node==null)return false;node.recycle();return true;});
        AccessibilityNodeInfo row=notificationText(test,title);
        try {
            while(row!=null && !String.valueOf(row.getViewIdResourceName()).endsWith("/expandableNotificationRow")) {
                AccessibilityNodeInfo parent=row.getParent();row.recycle();row=parent;
            }
            check(row!=null,"Notification row is missing");
            if(!row.performAction(AccessibilityNodeInfo.ACTION_EXPAND)) {
                AccessibilityNodeInfo button=find(row,"android:id/expand_button",true);
                if(button!=null)try {
                    if("Expand".contentEquals(button.getContentDescription()))check(button.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Notification cannot expand");
                } finally {button.recycle();}
            }
        } finally {if(row!=null)row.recycle();}
    }
    private static android.graphics.Bitmap wave(Instrumentation test,MainActivity a) throws Exception {
        Rect bounds=new Rect();on(test,()->a.body.findViewWithTag("phone.playback-wave").getGlobalVisibleRect(bounds));
        SystemClock.sleep(100);
        android.graphics.Bitmap screen=test.getUiAutomation().takeScreenshot();
        check(screen!=null && !bounds.isEmpty(),"Playing wave did not render");
        try{return android.graphics.Bitmap.createBitmap(screen,bounds.left,bounds.top,bounds.width(),bounds.height());}
        finally{screen.recycle();}
    }
    static void run(Instrumentation test,String selected,boolean capture) throws Exception {
        Context c=test.getTargetContext();
        PhoneStore original=PhoneStore.read(c);
        android.media.AudioManager audio=c.getSystemService(android.media.AudioManager.class);
        int originalMedia=audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        SharedPreferences language=c.getSharedPreferences("companion",0);
        String lang=language.getString("language","system");
        String animation=android.provider.Settings.Global.getString(c.getContentResolver(),"animator_duration_scale");
        check(animation==null || animation.matches("[0-9]+(?:[.][0-9]+)?"),"Unexpected animator scale");
        try {
            PhoneRuntimeCheck.shell(test,"settings put global animator_duration_scale 1");
            await(test,android.animation.ValueAnimator::areAnimatorsEnabled,5000,"Animator setting did not become enabled");
            language.edit().putString("language","en").commit();
            if(android.os.Build.VERSION.SDK_INT>=33)
                check(c.checkSelfPermission(android.Manifest.permission.USE_EXACT_ALARM)==android.content.pm.PackageManager.PERMISSION_GRANTED
                        && PhoneAlarms.exact(c),"Prayer timing should be granted without a manual alarm-access step");
            for (String phase:new String[]{"home","swipe","screen"}) {
                if (!selected.equals("all") && !selected.equals(phase)) continue;
                Map<String,String> edits=new HashMap<>();
                edits.put("reminders.enabled","true");edits.put("prayer.location-ready","true");
                edits.put("quiet.enabled","false");edits.put("reminders.volume",phase.equals("swipe")?"70":"0");
                edits.put("reminders.alarm-mode","false");
                edits.put("call.fajr.default-audio","true");edits.put("call.fajr.enabled","true");
                edits.put("call.fajr.mode","adhan");edits.put("call.fajr.weekdays","127");
                edits.put("reminders.full-screen",Boolean.toString(phase.equals("screen")));
                edits.put("reminders.duration","60");edits.put("display.reduce-motion","false");
                original.changed(edits).save(c);
                MainActivity a=LiveUiCheck.launch(test);
                Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(PhoneAdhanScreen.class.getName(),null,false);
                test.addMonitor(monitor);
                Activity shown=null;
                try {
                    on(test,()->a.tab("prayers"));
                    if(phase.equals("swipe"))audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,1,0);
                    String id="phone:presentation:"+UUID.randomUUID();
                    on(test,()->c.startForegroundService(PhonePlayback.intent(c,0,"almalki",false,id)));
                    await(test,PhonePlayback::outputStarted,10000,"Presentation audio did not start");
                    if (phase.equals("screen")) {
                        shown=test.waitForMonitorWithTimeout(monitor,5000);
                        check(shown instanceof PhoneAdhanScreen,"Foreground Miqat did not open the opted-in Adhan screen");
                        PhoneUiCheck.capture(test,c,"adhan-foreground-screen",capture);
                    } else if (phase.equals("home")) {
                        SystemClock.sleep(1200);
                        on(test,()-> {
                            View wave=a.body.findViewWithTag("phone.playback-wave");
                            check(wave!=null && wave.getVisibility()==View.VISIBLE,"Home prayer card has no playing wave");
                            check("playback.controls".equals(wave.getTag(R.id.companion_action)) && wave.getHeight()>=a.views.dp(48),"Playing controls lack a canonical accessible target");
                            check(a.body.findViewWithTag("phone.stop")==null,"Detached square Stop remains under the card");
                            Rect card=new Rect(),band=new Rect();
                            a.body.findViewWithTag("phone.hero").getGlobalVisibleRect(card);wave.getGlobalVisibleRect(band);
                            check(band.width()>=card.width()-a.views.dp(2) && card.bottom-band.bottom<=a.views.dp(2),
                                    "Playing wave does not span the card's bottom edge: "+band+" card="+card);
                        });
                        PhoneUiCheck.capture(test,c,"adhan-home-wave",capture);
                        android.graphics.Bitmap moving=wave(test,a);SystemClock.sleep(250);
                        android.graphics.Bitmap advanced=wave(test,a);
                        try {check(!moving.sameAs(advanced),"Playing wave did not animate");}finally{moving.recycle();advanced.recycle();}
                        PhoneStore.read(c).changed(Collections.singletonMap("display.reduce-motion","true")).save(c);
                        on(test,a::render);
                        android.graphics.Bitmap still=wave(test,a);SystemClock.sleep(250);
                        android.graphics.Bitmap unchanged=wave(test,a);
                        try {check(still.sameAs(unchanged),"Playing wave ignored reduced motion");}finally{still.recycle();unchanged.recycle();}
                        PhoneStore.read(c).changed(Collections.singletonMap("display.reduce-motion","false")).save(c);
                        on(test,a::render);
                        on(test,()->a.body.findViewWithTag("phone.playback-wave").performClick());
                        shown=test.waitForMonitorWithTimeout(monitor,5000);
                        check(shown instanceof PhoneAdhanScreen,"Playing wave does not open accessible playback controls");
                    } else {
                        check(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)==tv.miqat.companion.core.PhonePrayerRules.INSTANCE.volumeIndex(70,audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)),"Swipe fixture did not acquire its absolute media level");
                        on(test,()->a.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)));
                        await(test,()->!a.phoneForeground,5000,"Playback fixture did not enter the background");
                        PhoneRuntimeCheck.shell(test,"cmd statusbar expand-notifications");
                        LiveUiCheck.until(()-> {
                            AccessibilityNodeInfo shade=test.getUiAutomation().getRootInActiveWindow();
                            if(shade==null)return false;
                            try{return "com.android.systemui".contentEquals(shade.getPackageName());}finally{shade.recycle();}
                        });
                        PhoneUiCheck.capture(test,c,"adhan-notification-shade",capture);
                        Notification notification=Arrays.stream(c.getSystemService(NotificationManager.class).getActiveNotifications())
                                .map(n->n.getNotification()).filter(n->(n.flags&Notification.FLAG_FOREGROUND_SERVICE)!=0).findFirst().get();
                        Rect title=new Rect();
                        LiveUiCheck.until(()-> {
                            AccessibilityNodeInfo node=notificationText(test,"Fajr");
                            if(node==null)return false;
                            try{node.getBoundsInScreen(title);return !title.isEmpty();}finally{node.recycle();}
                        });
                        check(!title.isEmpty(),"Cannot locate the playing notification");
                        PhoneUiCheck.capture(test,c,"adhan-before-notification-swipe",capture);
                        int width=c.getResources().getDisplayMetrics().widthPixels;
                        PhoneRuntimeCheck.shell(test,"input swipe "+width/4+" "+title.centerY()+" "+(width-4)+" "+title.centerY()+" 300");
                        await(test,()->!PhonePlayback.playing(),5000,"Swiping the notification did not stop Adhan");
                        check(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)==1,"Background swipe did not restore prior media volume");
                    }
                } finally {
                    PhoneRuntimeCheck.shell(test,"cmd statusbar collapse");PhonePlayback.stop(c);
                    await(test,()->!PhonePlayback.playing(),5000,"Presentation cleanup did not stop audio");
                    if(phase.equals("swipe")) {
                        MainActivity restore=LiveUiCheck.launch(test);
                        try {audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,originalMedia,0);}
                        finally {on(test,restore::finish);}
                    }
                    if(shown!=null){Activity close=shown;on(test,close::finish);}
                    test.removeMonitor(monitor);on(test,a::finish);
                }
            }
        } finally {
            PhoneRuntimeCheck.shell(test,animation==null?"settings delete global animator_duration_scale":"settings put global animator_duration_scale "+animation);
            original.save(c);language.edit().putString("language",lang).commit();PhoneAlarms.rebuild(c);
        }
    }
}

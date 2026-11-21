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
        CharSequence text=node.getText(),description=node.getContentDescription();
        boolean words=text!=null && text.toString().regionMatches(true,0,value,0,value.length())
                || description!=null && description.toString().regionMatches(true,0,value,0,value.length());
        if(node.isVisibleToUser() && (id?value.equals(node.getViewIdResourceName()):words))
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
            AccessibilityNodeInfo button=find(row,"android:id/expand_button",true);
            check(button!=null,"Notification expansion control is missing");
            try {
                CharSequence state=button.getContentDescription();
                if(state!=null && "Expand".contentEquals(state))check(button.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Notification cannot expand");
                else check(state!=null && "Collapse".contentEquals(state),"Unknown notification expansion state: "+state);
            } finally {button.recycle();}
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
    private static void swipeRight(Instrumentation test,Rect row,int width) {
        float start=row.left+row.width()/4f,y=row.centerY();long down=SystemClock.uptimeMillis();
        for(int step=0;step<=20;step++) {
            int action=step==0?MotionEvent.ACTION_DOWN:step==20?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
            MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,start+(width-4-start)*step/20f,y,0);
            event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            try {check(test.getUiAutomation().injectInputEvent(event,true),"Notification swipe input was rejected");}
            finally {event.recycle();}
            if(step<20)SystemClock.sleep(16);
        }
    }
    static void run(Instrumentation test,String selected,boolean capture) throws Exception {
        check(Arrays.asList("all","home","swipe","screen","actions").contains(selected),"Unknown presentation case");
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
            for (String phase:selected.equals("actions")?new String[]{"actions"}:new String[]{"home","swipe","screen"}) {
                if (!selected.equals("all") && !selected.equals(phase)) continue;
                int prayer=phase.equals("swipe")?2:phase.equals("actions")?3:0;
                String prayerKey=PhoneStore.PRAYERS[prayer];
                String prayerName=PhoneData.get(c).prayerName(prayer,false);
                Map<String,String> edits=new HashMap<>();
                edits.put("reminders.enabled","true");edits.put("prayer.location-ready","true");
                edits.put("quiet.enabled","false");edits.put("reminders.volume",phase.equals("swipe")?"70":"0");
                edits.put("reminders.alarm-mode","false");
                edits.put("call."+prayerKey+".default-audio","true");edits.put("call."+prayerKey+".enabled","true");
                edits.put("call."+prayerKey+".voice","almalki");
                edits.put("call."+prayerKey+".mode","adhan");edits.put("call."+prayerKey+".weekdays","127");
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
                    String id="phone:presentation:"+UUID.randomUUID();long at=System.currentTimeMillis();
                    on(test,()->c.startForegroundService(PhonePlayback.intent(c,prayer,"almalki",false,id).putExtra("prayerTime",(double)at)));
                    await(test,PhonePlayback::outputStarted,10000,"Presentation audio did not start");
                    if (phase.equals("actions")) {
                        PhoneAdhanCheck.stop(test,c,new tv.miqat.core.ReminderEvent(id,"call",prayer,at,at));
                    } else if (phase.equals("screen")) {
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
                        test.getUiAutomation().waitForIdle(300,5000);
                        Rect title=new Rect(),previous=new Rect();long[] stable={SystemClock.elapsedRealtime()};
                        LiveUiCheck.until(()-> {
                            AccessibilityNodeInfo node=notificationText(test,prayerName);
                            if(node==null){stable[0]=SystemClock.elapsedRealtime();return false;}
                            try {
                                while(!String.valueOf(node.getViewIdResourceName()).endsWith("/expandableNotificationRow")) {
                                    AccessibilityNodeInfo parent=node.getParent();node.recycle();node=parent;
                                    check(node!=null,"Playing notification row is missing");
                                }
                                check(node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS),
                                        "Android offers no dismiss action for the playing notification: "+node.getActionList());
                                node.getBoundsInScreen(title);
                                if(title.isEmpty() || !title.equals(previous))stable[0]=SystemClock.elapsedRealtime();
                                previous.set(title);return !title.isEmpty() && SystemClock.elapsedRealtime()-stable[0]>=500;
                            } finally {if(node!=null)node.recycle();}
                        });
                        check(!title.isEmpty(),"Cannot locate the playing notification");
                        PhoneUiCheck.capture(test,c,"adhan-before-notification-swipe",capture);
                        int width=c.getResources().getDisplayMetrics().widthPixels;
                        swipeRight(test,title,width);
                        PhoneUiCheck.capture(test,c,"adhan-after-notification-swipe",capture);
                        try {
                            await(test,()->!PhonePlayback.playing(),5000,"Swiping the notification did not stop Adhan; SDK="+android.os.Build.VERSION.SDK_INT+" bounds="+title+" width="+width+" flags="+notification.flags+" style="+notification.extras.getString("android.template"));
                        } catch(AssertionError failure) {
                            PhoneUiCheck.capture(test,c,"adhan-swipe-failure",true);
                            String notices=Arrays.stream(c.getSystemService(NotificationManager.class).getActiveNotifications())
                                    .map(n->n.getId()+":"+n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE)).collect(java.util.stream.Collectors.joining(","));
                            throw new AssertionError(failure.getMessage()+" active="+notices+" same-session="+id.equals(PhonePlayback.currentId()),failure);
                        }
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

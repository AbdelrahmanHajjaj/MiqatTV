package tv.miqat.companion;

import android.app.*;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import android.media.session.*;
import android.os.*;
import java.util.*;

/** A foreground service only while a selected local adhan is preparing/playing. Never sticky. */
public final class PhonePlayback extends Service {
    static final String STOP="tv.miqat.companion.STOP_ADHAN";
    private static final int NOTIFICATION=42500;
    private static volatile PhonePlayback active;
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private MediaSession media;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean preview,started,defaultAudio;
    private String eventId="";
    private int generation,prayerIndex=-1;
    private double prayerTime;
    private long startedAt;
    static Intent intent(Context c,int prayer,String voice,boolean preview,String id){return new Intent(c,PhonePlayback.class).putExtra("prayer",prayer).putExtra("voice",voice).putExtra("preview",preview).putExtra("id",id);}
    static boolean playing(){return active!=null;}
    static boolean scheduled(){return active!=null&&!active.preview;}
    static boolean outputStarted(){return active!=null&&active.started;}
    static String currentId(){return active==null?"":active.eventId;}
    static void stop(Context c){c.stopService(new Intent(c,PhonePlayback.class));}
    static void stopPreview(Context c){if(active!=null&&active.preview)stop(c);}
    static void applySettings(Context c) {
        PhonePlayback service=active;if(service==null)return;
        service.handler.post(()->{
            if(active!=service)return;
            try {
                PhoneStore latest=PhoneStore.read(c);
                if(!service.preview&&(!latest.enabled||!latest.locationReady||service.prayerIndex<0||!service.allowed(latest,service.prayerIndex))){stop(c);return;}
                if(service.player!=null)service.player.setVolume(latest.audioVolume(service.defaultAudio?-1:service.prayerIndex)/100f,latest.audioVolume(service.defaultAudio?-1:service.prayerIndex)/100f);
                if(service.started){service.handler.removeCallbacksAndMessages(null);service.handler.postDelayed(service::stopSelf,Math.max(0,service.limit(latest)-(SystemClock.elapsedRealtime()-service.startedAt)));}
            }catch(Exception invalid){stop(c);}
        });
    }
    private boolean allowed(PhoneStore settings,int prayer){return settings.eventMode(new tv.miqat.core.ReminderEvent(eventId,"call",prayer,prayerTime,prayerTime))==2;}
    private long limit(PhoneStore settings){int duration=settings.audioDuration(defaultAudio?-1:prayerIndex);return 1000L*(preview?12:duration==0?600:duration);}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null||STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        String id=intent.getStringExtra("id");if(active==this&&Objects.equals(id,eventId))return START_NOT_STICKY;
        release();active=this;preview=intent.getBooleanExtra("preview",false);eventId=id==null?"":id;
        final int token=++generation;prayerIndex=-1;defaultAudio=intent.getBooleanExtra("default-audio",false);prayerTime=intent.getDoubleExtra("prayerTime",System.currentTimeMillis());
        try {
            PhoneAlarms.channels(this);if(!PhoneAlarms.channel(this,PhoneAlarms.PLAYBACK))throw new IllegalStateException("Playback notifications disabled");
            PhoneStore settings=PhoneStore.read(this);int prayer=intent.getIntExtra("prayer",0);
            if(!tv.miqat.core.PrayerRules.INSTANCE.isPrayer(prayer))throw new IllegalArgumentException("Invalid prayer");
            if(!preview&&(!settings.enabled||!settings.locationReady||!allowed(settings,prayer))){stopSelf();return START_NOT_STICKY;}
            prayerIndex=prayer;prayerTime=intent.getDoubleExtra("prayerTime",System.currentTimeMillis());
            String voice=intent.getStringExtra("voice");boolean approved=false;
            for(int i=0;i<settings.data.voices.length();i++)approved|=settings.data.voices.getJSONObject(i).getString("id").equals(voice);
            if(!approved&&!"custom".equals(voice))throw new IllegalArgumentException("Unknown voice");
            media=new MediaSession(this,"Miqat adhan");media.setCallback(new MediaSession.Callback(){@Override public void onStop(){stopSelf();}@Override public void onPause(){stopSelf();}});
            media.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_STOP).setState(PlaybackState.STATE_PLAYING,0,1).build());media.setActive(true);
            String name=settings.data.prayerName(prayer,PhoneAlarms.arabic(this));
            media.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,preview?PhoneAlarms.text(this,"phone_preview"):name).putString(MediaMetadata.METADATA_KEY_ARTIST,"Miqat").build());
            PendingIntent stop=PendingIntent.getService(this,42501,new Intent(this,PhonePlayback.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification notification=new Notification.Builder(this,PhoneAlarms.PLAYBACK).setSmallIcon(R.drawable.companion_icon_mihrab)
                .setContentTitle(preview?PhoneAlarms.text(this,"phone_preview"):String.format(Locale.getDefault(),PhoneAlarms.text(this,"phone_adhan_title"),name))
                .setContentText(PhoneAlarms.text(this,"phone_playing")).setContentIntent(PhoneAlarms.open(this)).setOngoing(true)
                .setCategory(Notification.CATEGORY_TRANSPORT).addAction(new Notification.Action.Builder(R.drawable.companion_icon_close,PhoneAlarms.text(this,"phone_stop"),stop).build())
                .setStyle(new Notification.MediaStyle().setMediaSession(media.getSessionToken()).setShowActionsInCompactView(0)).build();
            startForeground(NOTIFICATION,notification);
            AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            audio=getSystemService(AudioManager.class);focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(false).setOnAudioFocusChangeListener(change->{if(token==generation&&change!=AudioManager.AUDIOFOCUS_GAIN)stopSelf();}).build();
            if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)throw new IllegalStateException("Audio focus unavailable");
            player=new MediaPlayer();player.setAudioAttributes(attributes);player.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);
            boolean defaultVoice = settings.value("call." + PhoneStore.PRAYERS[prayer] + ".voice").equals("inherit");
            if (preview) defaultVoice = intent.getBooleanExtra("default-voice", defaultAudio || defaultVoice);
            if("custom".equals(voice))player.setDataSource(PhoneAudio.file(this,settings,prayer,defaultVoice).getPath());
            else try(AssetFileDescriptor file=getAssets().openFd("adhan/"+voice+".mp3")){player.setDataSource(file.getFileDescriptor(),file.getStartOffset(),file.getLength());}
            float volume=settings.audioVolume(defaultAudio?-1:prayer)/100f;player.setVolume(volume,volume);
            player.setOnCompletionListener(done->stopSelf());player.setOnErrorListener((failed,what,extra)->{failed();return true;});
            player.setOnPreparedListener(ready->{
                try {
                    PhoneStore latest=PhoneStore.read(this);if(!preview&&(!latest.enabled||!latest.locationReady||!allowed(latest,prayer))){stopSelf();return;}
                    ready.setVolume(latest.audioVolume(defaultAudio?-1:prayer)/100f,latest.audioVolume(defaultAudio?-1:prayer)/100f);
                    handler.removeCallbacksAndMessages(null);started=true;startedAt=SystemClock.elapsedRealtime();ready.start();
                    PhoneStore.preferences(this).edit().remove("audio-error").apply();
                    if(!preview&&latest.vibrate(prayer))getSystemService(Vibrator.class).vibrate(VibrationEffect.createWaveform(new long[]{0,300,200,300},-1),attributes);
                    handler.postDelayed(this::stopSelf,limit(latest));
                }catch(Exception invalid){failed();}
            });
            handler.postDelayed(this::failed,10000);player.prepareAsync();
        }catch(Exception error){failed();}
        return START_NOT_STICKY;
    }
    private void failed(){
        PhoneStore.preferences(this).edit().putString("audio-error","phone_audio_failed").apply();
        if(!preview&&prayerIndex>=0)PhoneAlarms.notify(this,new tv.miqat.core.ReminderEvent(eventId,"call",prayerIndex,prayerTime,prayerTime),true);
        stopSelf();
    }
    private void release() {
        handler.removeCallbacksAndMessages(null);started=false;
        if(player!=null){player.setOnPreparedListener(null);player.setOnCompletionListener(null);player.setOnErrorListener(null);player.release();player=null;}
        if(audio!=null&&focus!=null)audio.abandonAudioFocusRequest(focus);focus=null;audio=null;
        if(media!=null){media.setActive(false);media.release();media=null;}
        Vibrator vibrator=getSystemService(Vibrator.class);if(vibrator!=null)vibrator.cancel();
    }
    @Override public void onDestroy(){release();if(active==this)active=null;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}

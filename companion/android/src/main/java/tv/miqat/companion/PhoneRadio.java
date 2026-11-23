package tv.miqat.companion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;

/** Owns one user-started live stream. Service, player and Adhan callbacks run on the main thread. */
public final class PhoneRadio extends Service {
    static final String CHANNEL = "phone-radio";
    static final String STATION_PREFERENCE = "phone-radio-station";
    private static final int NOTIFICATION = 42600;
    private static final String PLAY = "tv.miqat.companion.PLAY_RADIO",
            PAUSE = "tv.miqat.companion.PAUSE_RADIO", STOP = "tv.miqat.companion.STOP_RADIO";
    private static final AudioAttributes ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private static final long TIMEOUT = 12000;
    private static final int MAX_RETRIES = 4;
    private static final long STABLE_PLAYBACK = 30000;
    private static PhoneRadio active;

    private enum State {
        CONNECTING("phone_radio_connecting", PlaybackState.STATE_BUFFERING, true),
        LIVE("phone_radio_live", PlaybackState.STATE_PLAYING, true),
        BUFFERING("phone_radio_buffering", PlaybackState.STATE_BUFFERING, true),
        RETRYING("phone_radio_retrying", PlaybackState.STATE_BUFFERING, true),
        PAUSED("phone_radio_paused", PlaybackState.STATE_PAUSED, false),
        ADHAN("phone_radio_adhan", PlaybackState.STATE_PAUSED, false),
        FAILED("phone_radio_failed", PlaybackState.STATE_ERROR, false);

        final String label;
        final int playback;
        final boolean engaged;

        State(String label, int playback, boolean engaged) {
            this.label = label;
            this.playback = playback;
            this.engaged = engaged;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::retry;
    private final Runnable reconnect = this::connect;
    private final Runnable resetRetries = () -> retries = 0;
    private final Runnable updateNotice = this::postNotification;
    private State state = State.PAUSED;
    private JSONObject station;
    private Uri session;
    private MediaSession media;
    private AudioFocusRequest focus;
    private MediaPlayer player;
    private int retries, focusGeneration;
    private boolean foreground;
    private long lastNotice;
    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) pause();
        }
    };

    static boolean hasSession() {
        return active != null;
    }

    static boolean engaged() {
        return hasSession() && active.state.engaged;
    }

    static String status() {
        return hasSession() ? active.state.label : "";
    }

    static void pause() {
        if (engaged()) active.suspend(State.PAUSED);
    }

    static void pauseForAdhan() {
        if (engaged()) active.suspend(State.ADHAN);
        else if (hasSession()) active.publish();
        else MainActivity.refreshRadio();
    }

    static void adhanEnded() {
        if (hasSession()) active.publish();
        else MainActivity.refreshRadio();
    }

    static void stop() {
        if (hasSession()) {
            PhoneRadio radio = active;
            radio.clearSession();
            radio.stopSelf();
        }
    }

    static boolean notifications(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                PhoneAlarms.text(context, "phone_radio"), NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
        NotificationChannel current = manager.getNotificationChannel(CHANNEL);
        return current != null && current.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    static String stationName(JSONObject station, boolean ar) {
        return station.optString(ar ? "ar" : "en") + " · " + station.optString(ar ? "whereAr" : "whereEn");
    }

    static String selected(Context context, PhoneData data) {
        String id = context.getSharedPreferences("companion", MODE_PRIVATE).getString(STATION_PREFERENCE, "");
        return data.station(id) == null ? data.stations.optJSONObject(0).optString("id") : id;
    }

    @Override public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisy, filter);
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.getAction() == null) selectAndPlay(intent.getStringExtra("station"));
            else if (session != null && session.equals(intent.getData())) {
                switch (intent.getAction()) {
                    case PLAY: play(); break;
                    case PAUSE: pause(); break;
                    case STOP: stop(); break;
                    default: break;
                }
            }
        }
        if (active != this) {
            if (active == null) getSystemService(NotificationManager.class).cancel(NOTIFICATION);
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    private void selectAndPlay(String id) {
        if (PhonePlayback.playing() || !notifications(this)) return;
        JSONObject selected;
        try {
            PhoneData data = PhoneData.get(this);
            if (!selected(this, data).equals(id)) return;
            selected = data.station(id);
        } catch (Exception invalid) {
            android.util.Log.w("Miqat", "Radio catalog unavailable", invalid);
            return;
        }
        if (media == null || !selected.optString("id").equals(station.optString("id"))) {
            clearSession();
            station = selected;
            session = Uri.fromParts("miqat-radio", UUID.randomUUID().toString(), null);
            media = new MediaSession(this, "Miqat radio");
            media.setPlaybackToLocal(ATTRIBUTES);
            media.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, stationName(station, PhoneAlarms.arabic(this)))
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, PhoneAlarms.text(this, "phone_radio")).build());
            media.setSessionActivity(openPlayer());
            MediaSession owner = media;
            media.setCallback(new MediaSession.Callback() {
                @Override public void onPlay() { if (media == owner) play(); }
                @Override public void onPause() { if (media == owner) pause(); }
                @Override public void onStop() { if (media == owner) stop(); }
                @Override public void onCustomAction(String action, Bundle extras) {
                    if (media == owner && STOP.equals(action)) stop();
                }
            }, handler);
            active = this;
        }
        play();
    }

    private void play() {
        if (state.engaged || PhonePlayback.playing() || !notifications(this)) return;
        state = State.CONNECTING;
        retries = 0;
        try {
            updateMedia();
            handler.removeCallbacks(updateNotice);
            startForeground(NOTIFICATION, notification());
            foreground = true;
            lastNotice = SystemClock.uptimeMillis();
            MainActivity.refreshRadio();
            int generation = ++focusGeneration;
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(ATTRIBUTES)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(change -> {
                        if (generation == focusGeneration && change < 0) pause();
                    }, handler).build();
            if (getSystemService(AudioManager.class).requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                suspend(State.PAUSED);
                return;
            }
            connect();
        } catch (IllegalStateException | SecurityException unavailable) {
            suspend(State.FAILED);
        }
    }

    private void connect() {
        if (!state.engaged || PhonePlayback.playing()) return;
        MediaPlayer next = new MediaPlayer();
        player = next;
        try {
            next.setAudioAttributes(ATTRIBUTES);
            next.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            next.setOnPreparedListener(ready -> {
                if (player != ready || !state.engaged || PhonePlayback.playing()) return;
                try {
                    ready.start();
                    onAir();
                } catch (IllegalStateException unavailable) {
                    retry();
                }
            });
            next.setOnErrorListener((failed, what, extra) -> {
                if (player == failed) retry();
                return true;
            });
            next.setOnCompletionListener(ended -> { if (player == ended) retry(); });
            next.setOnInfoListener((current, what, extra) -> {
                if (current != player) return false;
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START && state == State.LIVE) {
                    handler.removeCallbacks(resetRetries);
                    state = State.BUFFERING;
                    handler.postDelayed(timeout, TIMEOUT);
                    publish();
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END && state == State.BUFFERING) {
                    onAir();
                }
                return false;
            });
            next.setDataSource(station.optString("url"));
            next.prepareAsync();
            handler.postDelayed(timeout, TIMEOUT);
        } catch (IOException | IllegalArgumentException | IllegalStateException | SecurityException unavailable) {
            retry();
        }
    }

    private void onAir() {
        handler.removeCallbacks(timeout);
        state = State.LIVE;
        if (retries > 0) handler.postDelayed(resetRetries, STABLE_PLAYBACK);
        publish();
    }

    private void retry() {
        if (!state.engaged) return;
        if (retries >= MAX_RETRIES) {
            suspend(State.FAILED);
            return;
        }
        releaseStream();
        state = State.RETRYING;
        publish();
        handler.postDelayed(reconnect, 1500L * ++retries);
    }

    private void suspend(State reason) {
        releaseStream();
        releaseFocus();
        state = reason;
        if (foreground) {
            stopForeground(STOP_FOREGROUND_DETACH);
            foreground = false;
        }
        publish();
    }

    private void releaseStream() {
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(reconnect);
        handler.removeCallbacks(resetRetries);
        MediaPlayer old = player;
        player = null;
        if (old != null) old.release();
    }

    private void releaseFocus() {
        focusGeneration++;
        if (focus != null) getSystemService(AudioManager.class).abandonAudioFocusRequest(focus);
        focus = null;
    }

    private void publish() {
        updateMedia();
        // Coalesce rapid player and Adhan transitions so SystemUI retains the latest action.
        handler.removeCallbacks(updateNotice);
        handler.postAtTime(updateNotice, Math.max(SystemClock.uptimeMillis(), lastNotice + 650));
        MainActivity.refreshRadio();
    }

    private void postNotification() {
        getSystemService(NotificationManager.class).notify(NOTIFICATION, notification());
        lastNotice = SystemClock.uptimeMillis();
    }

    private void updateMedia() {
        long actions = PlaybackState.ACTION_STOP | (state.engaged
                ? PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                : PhonePlayback.playing() ? 0 : PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE);
        media.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .addCustomAction(STOP, PhoneAlarms.text(this, "phone_radio_stop"),
                        R.drawable.companion_icon_stop)
                .setState(state.playback, PlaybackState.PLAYBACK_POSITION_UNKNOWN, state == State.LIVE ? 1 : 0)
                .build());
        media.setActive(true);
    }

    private Notification notification() {
        boolean transport = state.engaged || !PhonePlayback.playing();
        Notification.MediaStyle style = new Notification.MediaStyle()
                .setMediaSession(media.getSessionToken())
                .setShowActionsInCompactView(transport ? new int[] {0, 1} : new int[] {0});
        Notification.Builder notice = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.companion_icon_mihrab)
                .setContentTitle(stationName(station, PhoneAlarms.arabic(this)))
                .setContentText(PhoneAlarms.text(this, state.label))
                .setContentIntent(openPlayer())
                .setDeleteIntent(command(STOP))
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setStyle(style);
        if (Build.VERSION.SDK_INT >= 31)
            notice.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        if (transport)
            notice.addAction(new Notification.Action.Builder(
                        state.engaged ? R.drawable.companion_icon_pause : R.drawable.companion_icon_play,
                        PhoneAlarms.text(this, state.engaged ? "phone_pause" : "phone_resume"),
                        command(state.engaged ? PAUSE : PLAY)).build());
        return notice.addAction(new Notification.Action.Builder(R.drawable.companion_icon_stop,
                        PhoneAlarms.text(this, "phone_radio_stop"), command(STOP)).build())
                .build();
    }

    private PendingIntent openPlayer() {
        return PendingIntent.getActivity(this, NOTIFICATION,
                new Intent(this, MainActivity.class).putExtra("phone-radio", true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent command(String action) {
        return PendingIntent.getService(this, 0,
                new Intent(this, PhoneRadio.class).setAction(action).setData(session), PendingIntent.FLAG_IMMUTABLE);
    }

    private void clearSession() {
        if (media == null) return;
        handler.removeCallbacks(updateNotice);
        releaseStream();
        releaseFocus();
        media.release();
        media = null;
        station = null;
        session = null;
        state = State.PAUSED;
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE);
        foreground = false;
        getSystemService(NotificationManager.class).cancel(NOTIFICATION);
        if (active == this) active = null;
        MainActivity.refreshRadio();
    }

    @Override public void onDestroy() {
        unregisterReceiver(noisy);
        clearSession();
        super.onDestroy();
    }
}

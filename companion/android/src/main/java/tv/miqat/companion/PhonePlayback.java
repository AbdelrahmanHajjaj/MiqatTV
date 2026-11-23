package tv.miqat.companion;

import android.app.*;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import android.media.session.*;
import android.os.*;

import java.util.*;

/**
 * A foreground service only during a selected local adhan session, including user pause. Never
 * sticky.
 */
public final class PhonePlayback extends Service {
    static final String STOP = "tv.miqat.companion.STOP_ADHAN";
    static final String PAUSE = "tv.miqat.companion.PAUSE_ADHAN",
            RESUME = "tv.miqat.companion.RESUME_ADHAN";
    private static final int NOTIFICATION = 42500;
    private static volatile PhonePlayback active;
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private AudioAttributes outputAttributes;
    private PhoneVolume volume;
    private MediaSession media;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Handler progressClock=new Handler(Looper.getMainLooper());
    private final Runnable progressTick=new Runnable() {
        public void run() {
            if(active!=PhonePlayback.this || !started || paused)return;
            try {publishPlayback();progressClock.postDelayed(this,1000);}
            catch(Exception failure){failed();}
        }
    };
    private boolean preview, started, paused, defaultAudio, lengthPreview, testing;
    private String eventId = "", customHash = "", recordingName = "";
    private android.graphics.Bitmap artwork;
    private int generation, prayerIndex = -1, previewPercent = 100, previewCapSeconds;
    private double prayerTime;
    private long resumedAt, playedMs;

    static Intent intent(Context c, int prayer, String voice, boolean preview, String id) {
        return new Intent(c, PhonePlayback.class)
                .putExtra("prayer", prayer)
                .putExtra("voice", voice)
                .putExtra("preview", preview)
                .putExtra("id", id);
    }

    static boolean playing() {
        return active != null;
    }

    static boolean scheduled() {
        return active != null && !active.preview;
    }

    static boolean outputStarted() {
        return active != null && active.started && !active.paused;
    }

    static boolean paused() {
        return active != null && active.paused;
    }

    static String currentId() {
        return active == null ? "" : active.eventId;
    }

    static void openControls(Activity activity) {
        PhonePlayback service=active;
        if(service!=null && service.prayerIndex>=0)
            activity.startActivity(PhoneAdhanScreen.intent(activity,service.eventId,service.prayerIndex).putExtra("manual",true));
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, PhonePlayback.class));
    }

    static void stopPreview(Context c) {
        if (active != null && active.preview) stop(c);
    }

    static void applySettings(Context c) {
        PhonePlayback service = active;
        if (service == null) return;
        service.handler.post(() -> {
            if (active != service) return;
            try {
                PhoneStore latest = PhoneStore.read(c);
                if (!attributes(latest).equals(service.outputAttributes)) {
                    stop(c);
                    return;
                }
                if (!service.preview
                        && (service.prayerIndex < 0 || !service.allowed(latest, service.prayerIndex))) {
                    stop(c);
                    return;
                }
                if (service.player != null && service.started && !service.paused)
                    service.volume.apply(service.player,latest.audioVolume(service.defaultAudio ? -1 : service.prayerIndex));
                if (service.started) service.armLimit(latest);

            } catch (Exception invalid) {
                stop(c);
            }
        });
    }

    private boolean allowed(PhoneStore settings, int prayer) {
        if (testing) return PhoneAlertTest.allowed(this, eventId);
        return settings.enabled && settings.locationReady && settings.eventMode(
                        new tv.miqat.core.ReminderEvent(
                                eventId, "call", prayer, prayerTime, prayerTime))
                == 2;
    }

    private long limit(PhoneStore settings) {
        long duration;
        if (preview && !lengthPreview) duration = player.getDuration();
        else duration = tv.miqat.companion.core.PhonePrayerRules.INSTANCE.cutoffMillis(
                player.getDuration(),
                lengthPreview ? previewPercent : settings.audioPercent(defaultAudio ? -1 : prayerIndex),
                lengthPreview ? 0 : settings.audioDuration(defaultAudio ? -1 : prayerIndex));
        return previewCapSeconds > 0 ? Math.min(duration, previewCapSeconds * 1000L) : duration;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (STOP.equals(action) || PAUSE.equals(action) || RESUME.equals(action)) {
            if (active == this && eventId.equals(intent.getStringExtra("id"))) {
                if (STOP.equals(action)) stopSelf();
                else if (PAUSE.equals(action)) pauseOutput();
                else resumeOutput();
            } else if (active != this) stopSelf();
            return START_NOT_STICKY;
        }
        String id = intent.getStringExtra("id");
        if (active == this && Objects.equals(id, eventId)) return START_NOT_STICKY;
        String testToken = intent.getStringExtra("test-token");
        if (testToken != null) {
            if (!testToken.equals(id) || scheduled() || !PhoneAlertTest.starting(this, id)) {
                PhoneAlertTest.finished(this, testToken, "phone_stop_first");
                if (active != this) stopSelf();
                return START_NOT_STICKY;
            }
        }
        release();
        testing = testToken != null;
        active = this;
        preview = intent.getBooleanExtra("preview", false);
        lengthPreview = preview && intent.getBooleanExtra("length-preview", false);
        previewPercent = intent.getIntExtra("length-percent", 100);
        previewCapSeconds = preview ? intent.getIntExtra("preview-cap-seconds", 0)
                : testing ? intent.getIntExtra("test-cap-seconds", 0) : 0;
        customHash = preview ? intent.getStringExtra("custom-audio-hash") : "";
        if (customHash == null) customHash = "";
        eventId = id == null ? "" : id;
        final int token = ++generation;
        prayerIndex = -1;
        defaultAudio = intent.getBooleanExtra("default-audio", false);
        prayerTime = intent.getDoubleExtra("prayerTime", System.currentTimeMillis());
        try {
            if (previewCapSeconds < 0 || previewCapSeconds > 20 || !customHash.isEmpty() && !customHash.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Invalid audition input");
            if (lengthPreview && (previewPercent < 5 || previewPercent > 100 || previewPercent % 5 != 0))
                throw new IllegalArgumentException("Invalid preview length");
            PhoneAlarms.channels(this);
            PhoneStore settings = PhoneStore.read(this);
            if (!PhoneAlarms.channel(
                    this,
                    PhoneAlarms.playbackChannel(
                            this,
                            !preview && settings.value("reminders.full-screen").equals("true"))))
                throw new IllegalStateException("Playback notifications disabled");
            int prayer = intent.getIntExtra("prayer", 0);
            if (!tv.miqat.core.PrayerRules.INSTANCE.isPrayer(prayer))
                throw new IllegalArgumentException("Invalid prayer");
            if (!preview && !allowed(settings, prayer)) {
                stopSelf();
                return START_NOT_STICKY;
            }
            prayerIndex = prayer;
            prayerTime = intent.getDoubleExtra("prayerTime", System.currentTimeMillis());
            String voice = intent.getStringExtra("voice");
            boolean approved = false;
            for (int i = 0; i < settings.data.voices.length(); i++)
                approved |= settings.data.voices.getJSONObject(i).getString("id").equals(voice);
            if (!approved && !"custom".equals(voice))
                throw new IllegalArgumentException("Unknown voice");
            boolean defaultVoice =
                    settings.value("call." + PhoneStore.PRAYERS[prayer] + ".voice")
                            .equals("inherit");
            if (preview || testing)
                defaultVoice =
                        intent.getBooleanExtra("default-voice", defaultAudio || defaultVoice);
            recordingName = settings.data.voiceName(voice, PhoneAlarms.arabic(this));
            if ("custom".equals(voice)) {
                if (customHash.isEmpty())
                    customHash = settings.value("audio.custom." + (defaultVoice ? "default" : PhoneStore.PRAYERS[prayer]));
                try {
                    synchronized (PhoneStore.LOCK) {
                        recordingName = PhoneAudioLibrary.read(this).getOrDefault(customHash, recordingName);
                    }
                } catch (Exception unavailable) {
                    android.util.Log.w("Miqat", "Recording label unavailable", unavailable);
                }
            }
            startSession(settings);
            preparePlayer(settings, prayer, voice, token);
        } catch (Exception error) {
            failed();
        }
        return START_NOT_STICKY;
    }

    private void startSession(PhoneStore settings) throws Exception {
        media = new MediaSession(this, "Miqat adhan");
        media.setCallback(
                new MediaSession.Callback() {
                    @Override
                    public void onStop() {
                        stopSelf();
                    }

                    @Override
                    public void onPause() {
                        pauseOutput();
                    }

                    @Override
                    public void onPlay() {
                        resumeOutput();
                    }

                    @Override
                    public void onCustomAction(String action, Bundle extras) {
                        if (STOP.equals(action)) stopSelf();
                    }
                });
        updateMediaState(0);
        media.setActive(true);
        artwork = new AdhanArtwork(this, settings).cover();
        media.setMetadata(
                new MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, title(settings))
                        .putString(
                                MediaMetadata.METADATA_KEY_ARTIST,
                                recordingName)
                        .putString(MediaMetadata.METADATA_KEY_ALBUM, "Miqat")
                        .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
                        .build());
        startForeground(NOTIFICATION, notification(settings));
    }

    private PendingIntent command(String action, int code) {
        return PendingIntent.getService(
                this,
                code,
                new Intent(this, PhonePlayback.class)
                        .setAction(action)
                        .putExtra("id", eventId)
                        .setData(
                                new android.net.Uri.Builder()
                                        .scheme("miqat")
                                        .authority("adhan")
                                        .appendPath(eventId)
                                        .build()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private String title(PhoneStore settings) throws Exception {
        if (testing) return PhoneAlarms.text(this, "phone_test_adhan");
        if (preview) return PhoneAlarms.text(this, "phone_preview");
        boolean ar = PhoneAlarms.arabic(this);
        return settings.data.prayerName(prayerIndex, ar)
                + " \u00b7 "
                + PhoneTime.at(settings, prayerTime, ar);
    }

    private Notification notification(PhoneStore settings) throws Exception {
        boolean screen = !preview && settings.value("reminders.full-screen").equals("true");
        PendingIntent open =
                screen
                        ? PhoneAdhanScreen.pending(this, eventId, prayerIndex)
                        : PhoneAlarms.open(this);
        Bundle sessionExtras=new Bundle();sessionExtras.putParcelable(Notification.EXTRA_MEDIA_SESSION,media.getSessionToken());
        android.widget.RemoteViews expanded = new android.widget.RemoteViews(getPackageName(), R.layout.adhan_notification);
        expanded.setInt(R.id.adhan_notice_content, "setLayoutDirection",
                PhoneAlarms.arabic(this) ? android.view.View.LAYOUT_DIRECTION_RTL : android.view.View.LAYOUT_DIRECTION_LTR);
        expanded.setTextViewText(R.id.adhan_notice_title, title(settings));
        int prayerIcon=Icons.resource(this,PhoneStore.PRAYERS[prayerIndex]);
        expanded.setTextViewCompoundDrawablesRelative(R.id.adhan_notice_title,prayerIcon,0,0,0);
        expanded.setTextViewText(R.id.adhan_notice_recording, recordingName);
        expanded.setImageViewResource(R.id.adhan_notice_pause,
                paused ? R.drawable.companion_icon_play : R.drawable.companion_icon_pause);
        expanded.setContentDescription(R.id.adhan_notice_pause, PhoneAlarms.text(this, paused ? "phone_resume" : "phone_pause"));
        expanded.setContentDescription(R.id.adhan_notice_stop, PhoneAlarms.text(this, "phone_stop"));
        expanded.setOnClickPendingIntent(R.id.adhan_notice_pause, command(paused ? RESUME : PAUSE, 42502));
        expanded.setOnClickPendingIntent(R.id.adhan_notice_stop, command(STOP, 42501));
        Notification.Builder notice =
                new Notification.Builder(this, PhoneAlarms.playbackChannel(this, screen))
                        .setSmallIcon(R.drawable.companion_icon_mihrab)
                        .setContentTitle(title(settings))
                        .setContentText(recordingName)
                        .setColor(
                                android.graphics.Color.parseColor(
                                        settings.data
                                                .colors(settings.value("appearance.theme"))
                                                .getString("gold-400")))
                        .setContentIntent(open)
                        .setDeleteIntent(command(STOP, 42501))
                        .setOngoing(false)
                        .setOnlyAlertOnce(true)
                        .setCategory(
                                screen
                                        ? Notification.CATEGORY_ALARM
                                        : Notification.CATEGORY_TRANSPORT)
                        // Active MediaStyle notifications are protected from swipe dismissal.
                        .addExtras(sessionExtras)
                        .setCustomBigContentView(expanded)
                        .setStyle(new Notification.DecoratedCustomViewStyle());
        if(started && player!=null)notice.setProgress((int)limit(settings),player.getCurrentPosition(),false);
        if (screen && !paused && PhoneAlarms.fullScreenAllowed(this))
            notice.setFullScreenIntent(open, true);
        return notice.build();
    }

    private void armLimit(PhoneStore settings) {
        handler.removeCallbacksAndMessages(null);
        if (!paused)
            handler.postDelayed(
                    this::stopSelf,
                    Math.max(
                            0,
                            limit(settings)
                                    - playedMs
                                    - (SystemClock.elapsedRealtime() - resumedAt)));
    }

    private void updateMediaState(long position) {
        media.setPlaybackState(
                new PlaybackState.Builder()
                        .setActions(
                                PlaybackState.ACTION_STOP
                                        | PlaybackState.ACTION_PLAY
                                        | PlaybackState.ACTION_PAUSE)
                        // Android 13+ derives extra notification buttons from session custom
                        // actions.
                        .addCustomAction(
                                STOP,
                                PhoneAlarms.text(this, "phone_stop"),
                                R.drawable.companion_icon_close)
                        .setState(
                                paused ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_PLAYING,
                                position,
                                paused ? 0 : 1)
                        .build());
    }

    private void publishPlayback() throws Exception {
        updateMediaState(player.getCurrentPosition());
        getSystemService(NotificationManager.class)
                .notify(NOTIFICATION, notification(PhoneStore.read(this)));
    }

    private void pauseOutput() {
        if (!started || paused) return;
        try {
            playedMs += SystemClock.elapsedRealtime() - resumedAt;
            paused = true;
            handler.removeCallbacksAndMessages(null);
            player.pause();
            progressClock.removeCallbacksAndMessages(null);
            volume.release();
            audio.abandonAudioFocusRequest(focus);
            publishPlayback();
        } catch (Exception failure) {
            failed();
        }
    }

    private void resumeOutput() {
        if (!started || !paused) return;
        try {
            PhoneStore settings = PhoneStore.read(this);
            if (!PhoneAlarms.notifications(this)
                    || !preview && !allowed(settings, prayerIndex)) {
                stopSelf();
                return;
            }
            if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                stopSelf();
                return;
            }
            volume.apply(player,settings.audioVolume(defaultAudio ? -1 : prayerIndex));
            player.start();
            paused = false;
            resumedAt = SystemClock.elapsedRealtime();
            armLimit(settings);
            publishPlayback();
            progressClock.postDelayed(progressTick,1000);
        } catch (Exception failure) {
            failed();
        }
    }

    static AudioAttributes attributes(PhoneStore settings) {
        return new AudioAttributes.Builder()
                .setUsage(settings.value("reminders.alarm-mode").equals("true")
                        ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    private void preparePlayer(
            PhoneStore settings, int prayer, String voice, int token)
            throws Exception {
        AudioAttributes attributes = attributes(settings);
        outputAttributes = attributes;
        volume = new PhoneVolume(this,attributes);
        media.setPlaybackToLocal(attributes);
        audio = getSystemService(AudioManager.class);
        focus =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(attributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener(change -> {
                            if (token == generation
                                    && !paused
                                    && change != AudioManager.AUDIOFOCUS_GAIN) stopSelf();
                        })
                        .build();
        player = new MediaPlayer();
        player.setAudioAttributes(attributes);
        player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        if ("custom".equals(voice))
            player.setDataSource(PhoneAudio.file(this, customHash).getPath());
        else
            try (AssetFileDescriptor file = getAssets().openFd("adhan/" + voice + ".mp3")) {
                player.setDataSource(
                        file.getFileDescriptor(), file.getStartOffset(), file.getLength());
            }
        PhoneRadio.pauseForAdhan();
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            throw new IllegalStateException("Audio focus unavailable");
        player.setVolume(0,0);
        player.setOnCompletionListener(done -> stopSelf());
        player.setOnErrorListener((failed, what, extra) -> {
            failed();
            return true;
        });
        player.setOnPreparedListener(ready -> startOutput(ready, prayer, attributes));
        handler.postDelayed(this::failed, 10000);
        player.prepareAsync();
    }

    private void startOutput(MediaPlayer ready, int prayer, AudioAttributes attributes) {
        try {
            PhoneStore latest = PhoneStore.read(this);
            if (!preview && !allowed(latest, prayer)) {
                stopSelf();
                return;
            }
            beginOutput(ready, latest, prayer, attributes);
        } catch (Exception invalid) {
            failed();
        }
    }

    private void beginOutput(MediaPlayer ready, PhoneStore settings, int prayer, AudioAttributes attributes) {
        try {
            settings = PhoneStore.read(this);
            if (!PhoneAlarms.notifications(this) || !preview && !allowed(settings, prayer)) {
                stopSelf();
                return;
            }
            handler.removeCallbacksAndMessages(null);
            volume.apply(ready,settings.audioVolume(defaultAudio ? -1 : prayer));
            ready.start();
            started = true;
            paused = false;
            playedMs = 0;
            resumedAt = SystemClock.elapsedRealtime();
            publishPlayback();
            progressClock.postDelayed(progressTick,1000);
            MainActivity.showPlayingAdhan();
            PhoneStore.preferences(this).edit().remove("audio-error").apply();
            Vibrator vibrator = getSystemService(Vibrator.class);
            if (!preview && !testing && vibrator != null && vibrator.hasVibrator()
                    && settings.eventVibrate(new tv.miqat.core.ReminderEvent(
                            eventId, "call", prayer, prayerTime, prayerTime)))
                vibrator.vibrate(VibrationEffect.createWaveform(new long[] {0, 120, 100, 120}, -1), attributes);
            armLimit(settings);
        } catch (Exception invalid) {
            failed();
        }
    }

    private void failed() {
        PhoneStore.preferences(this).edit().putString("audio-error", "phone_audio_failed").apply();
        if (testing) {
            PhoneAlarms.notifyTest(this, true);
            PhoneAlertTest.finished(this, eventId, "phone_test_failed");
        } else if (!preview && prayerIndex >= 0)
            PhoneAlarms.notify(
                    this,
                    new tv.miqat.core.ReminderEvent(
                            eventId, "call", prayerIndex, prayerTime, prayerTime),
                    true);
        stopSelf();
    }

    private void release() {
        if (testing) {
            PhoneAlertTest.finished(this, eventId, "phone_test_finished");
            testing = false;
        }
        handler.removeCallbacksAndMessages(null);
        progressClock.removeCallbacksAndMessages(null);
        started = false;
        paused = false;
        playedMs = 0;
        artwork = null;
        recordingName = "";
        if (player != null) {
            player.setOnPreparedListener(null);
            player.setOnCompletionListener(null);
            player.setOnErrorListener(null);
            player.release();
            player = null;
        }
        if (volume != null) {
            try { volume.release(); }
            catch (Exception restricted) { PhoneStore.preferences(this).edit().putString("audio-error","phone_audio_failed").apply(); }
            volume = null;
        }
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        focus = null;
        audio = null;
        if (media != null) {
            media.setActive(false);
            media.release();
            media = null;
        }
        Vibrator vibrator = getSystemService(Vibrator.class);
        if (vibrator != null) vibrator.cancel();
    }

    @Override
    public void onDestroy() {
        release();
        if (active == this) active = null;
        PhoneRadio.adhanEnded();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}

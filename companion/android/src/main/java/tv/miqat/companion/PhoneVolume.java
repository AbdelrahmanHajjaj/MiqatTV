package tv.miqat.companion;

import android.content.*;
import android.media.*;
import org.json.*;
import java.io.IOException;

/** Temporarily owns only the selected stream level; user changes relinquish that ownership. */
final class PhoneVolume {
    private static final String RESTORE = "audio-level-restore";
    private final Context context;
    private final AudioManager audio;
    private final int stream;
    private int percent = -1;

    PhoneVolume(Context context, AudioAttributes attributes) {
        this.context = context;
        audio = context.getSystemService(AudioManager.class);
        stream = attributes.getVolumeControlStream();
    }

    void apply(MediaPlayer player, int next) throws Exception {
        synchronized (PhoneVolume.class) {
            if (percent == next) return;
            player.setVolume(0,0);
            restore(context);
            percent = -1;
            if (next == 0 || audio.isVolumeFixed()) {
                player.setVolume(next / 100f,next / 100f);
            } else {
                int before = audio.getStreamVolume(stream);
                int target = tv.miqat.companion.core.PhonePrayerRules.INSTANCE.volumeIndex(
                        next,audio.getStreamMaxVolume(stream));
                if (before != target) {
                    save(context,stream,before,target);
                    audio.setStreamVolume(stream,target,0);
                    int actual = audio.getStreamVolume(stream);
                    if (actual != target) save(context,stream,before,actual);
                }
                player.setVolume(1,1);
            }
            percent = next;
        }
    }

    void release() throws Exception {
        synchronized (PhoneVolume.class) {
            percent = -1;
            restore(context);
        }
    }

    static synchronized void recover(Context context) throws Exception {
        // shortcut: process death restores at the next Miqat entry; add a deadline task if needed.
        if (!PhonePlayback.playing()) restore(context);
    }

    private static void save(Context context, int stream, int before, int applied) throws Exception {
        String record = new JSONObject().put("stream",stream).put("before",before).put("applied",applied).toString();
        if (!PhoneStore.preferences(context).edit().putString(RESTORE,record).commit())
            throw new IOException("Cannot preserve previous volume");
    }

    private static void restore(Context context) throws Exception {
        SharedPreferences prefs = PhoneStore.preferences(context);
        String record = prefs.getString(RESTORE,null);
        if (record == null) return;
        AudioManager audio = context.getSystemService(AudioManager.class);
        int stream, before, applied;
        try {
            JSONObject data = new JSONObject(record);
            if (data.length()!=3 || !(data.get("stream") instanceof Integer)
                    || !(data.get("before") instanceof Integer) || !(data.get("applied") instanceof Integer))
                throw new JSONException("Invalid volume recovery");
            stream=data.getInt("stream");before=data.getInt("before");applied=data.getInt("applied");
            if (stream!=AudioManager.STREAM_MUSIC && stream!=AudioManager.STREAM_ALARM)
                throw new JSONException("Invalid volume stream");
            int max=audio.getStreamMaxVolume(stream);
            if (before<0 || before>max || applied<0 || applied>max)
                throw new JSONException("Invalid volume bounds");
        } catch (JSONException invalid) {
            if (!prefs.edit().remove(RESTORE).commit()) throw new IOException("Cannot discard invalid volume recovery",invalid);
            return;
        }
        if (audio.getStreamVolume(stream)==applied && before!=applied) {
            audio.setStreamVolume(stream,before,0);
            if (audio.getStreamVolume(stream)!=before) throw new IOException("Previous volume is currently restricted");
        }
        if (!prefs.edit().remove(RESTORE).commit()) throw new IOException("Cannot finish volume restoration");
    }
}

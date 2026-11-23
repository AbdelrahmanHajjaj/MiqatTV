package tv.miqat.companion;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;

import org.json.JSONObject;

/** Optional view of the existing playback session; leaving the view never owns playback. */
public final class PhoneAdhanScreen extends Activity {
    private final Handler clock = new Handler(Looper.getMainLooper());
    private String eventId;
    private boolean volumeKeys;
    private int ink, accent, ground;
    LinearLayout content;
    AlertDialog closeDialog;
    private final Runnable session =
            new Runnable() {
                public void run() {
                    if (!current()) finish();
                    else {
                        clock.postDelayed(this, 500);
                    }
                }
            };

    static Intent intent(Context c, String id, int prayer) {
        return new Intent(c, PhoneAdhanScreen.class)
                .putExtra("id", id)
                .putExtra("prayer", prayer)
                .setData(
                        new android.net.Uri.Builder()
                                .scheme("miqat")
                                .authority("adhan")
                                .appendPath(id)
                                .build());
    }

    static PendingIntent pending(Context c, String id, int prayer) {
        return PendingIntent.getActivity(
                c,
                42503,
                intent(c, id, prayer),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(MainActivity.localized(base));
    }

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        eventId = getIntent().getStringExtra("id");
        if (!current()) {
            finish();
            return;
        }
        try {
            PhoneStore settings = PhoneStore.read(this);
            setVolumeControlStream(PhonePlayback.attributes(settings).getVolumeControlStream());
            if (!getIntent().getBooleanExtra("manual",false) && !settings.value("reminders.full-screen").equals("true")) {
                finish();
                return;
            }
            volumeKeys = settings.value("reminders.volume-keys").equals("true");
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true);
                setTurnScreenOn(true);
            } else
                getWindow()
                        .addFlags(
                                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            JSONObject colors = settings.data.colors(settings.value("appearance.theme"));
            ground = Color.parseColor(colors.getString("lapis-900"));
            ink = Color.parseColor(colors.getString("plaster-100"));
            accent = Color.parseColor(colors.getString("gold-400"));
            LinearLayout shell = new LinearLayout(this);
            shell.setOrientation(LinearLayout.VERTICAL);
            shell.setBackground(new PhoneMediaArtwork(this, settings));
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            content = new LinearLayout(this);
            content.setTag("adhan.screen");
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER);
            int padding = Math.round(getResources().getDisplayMetrics().density * 24);
            content.setPadding(padding, padding, padding, padding);
            content.setLayoutDirection(
                    PhoneAlarms.arabic(this)
                            ? View.LAYOUT_DIRECTION_RTL
                            : View.LAYOUT_DIRECTION_LTR);
            scroll.addView(content);
            label(
                    content,
                    eventId != null && eventId.startsWith("phone:test:")
                            ? PhoneAlarms.text(this, "phone_test_adhan")
                            : settings.data.prayerName(
                                    getIntent().getIntExtra("prayer", 0), PhoneAlarms.arabic(this)),
                    32,
                    accent);
            label(content, PhoneAlarms.text(this, "phone_swipe_stop"), 14, ink);
            LinearLayout footer = new LinearLayout(this);
            footer.setOrientation(LinearLayout.VERTICAL);
            footer.setGravity(Gravity.CENTER_HORIZONTAL);
            footer.setPadding(padding, 0, padding, padding / 2);
            footer.setTag("adhan.actions");
            shell.addView(footer, new LinearLayout.LayoutParams(-1, -2));
            SwipeStop stop =
                    new SwipeStop(
                            this,
                            accent,
                            ground,
                            !settings.value("display.reduce-motion").equals("true"));
            stop.setTag("adhan.stop-close");
            stop.setContentDescription(PhoneAlarms.text(this, "phone_stop_close"));
            stop.setOnClickListener(v -> stopAndClose());
            float density = getResources().getDisplayMetrics().density;
            footer.addView(
                    stop,
                    new LinearLayout.LayoutParams(
                            Math.round(80 * density), Math.round(136 * density)));
            setContentView(shell);
        } catch (Exception unavailable) {
            finish();
        }
    }

    private TextView label(LinearLayout parent, String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER);
        text.setTypeface(getResources().getFont(R.font.alexandria_regular));
        int padding = Math.round(getResources().getDisplayMetrics().density * 12);
        text.setPadding(0, padding, 0, padding);
        parent.addView(text, new LinearLayout.LayoutParams(-1, -2));
        return text;
    }

    private boolean current() {
        return eventId != null
                && eventId.equals(PhonePlayback.currentId())
                && PhonePlayback.scheduled();
    }

    private void stopAndClose() {
        if (current()) PhonePlayback.stop(this);
        finish();
    }

    @Override
    public void onBackPressed() {
        closeDialog =
                new AlertDialog.Builder(this)
                        .setMessage(PhoneAlarms.text(this, "phone_stop_close"))
                        .setPositiveButton(
                                PhoneAlarms.text(this, "phone_stop_close"),
                                (dialog, which) -> stopAndClose())
                        .setNegativeButton(PhoneAlarms.text(this, "phone_keep_playing"), null)
                        .show();
        CompanionViews.bottomDialog(closeDialog, ground, accent);
    }

    @Override
    public boolean onKeyDown(int key, KeyEvent event) {
        if (volumeKeys && volumeKey(key)) {
            if (event.getRepeatCount() == 0) stopAndClose();
            return true;
        }
        return super.onKeyDown(key, event);
    }

    @Override
    public boolean onKeyUp(int key, KeyEvent event) {
        return volumeKeys && volumeKey(key) || super.onKeyUp(key, event);
    }

    private boolean volumeKey(int key) {
        return key == KeyEvent.KEYCODE_VOLUME_UP
                || key == KeyEvent.KEYCODE_VOLUME_DOWN
                || key == KeyEvent.KEYCODE_VOLUME_MUTE;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) clock.post(session);
    }

    @Override
    protected void onPause() {
        clock.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        clock.removeCallbacksAndMessages(null);
        if (closeDialog != null) closeDialog.dismiss();
        super.onDestroy();
    }
}

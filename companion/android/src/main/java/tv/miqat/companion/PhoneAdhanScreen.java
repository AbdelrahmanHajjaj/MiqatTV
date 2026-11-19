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
    private TextView state;
    LinearLayout content;
    AlertDialog closeDialog;
    private final Runnable session =
            new Runnable() {
                public void run() {
                    if (!current()) finish();
                    else {
                        state.setText(
                                PhoneAlarms.text(
                                        PhoneAdhanScreen.this,
                                        PhonePlayback.paused() ? "phone_paused" : "phone_playing"));
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
            if (!settings.value("reminders.full-screen").equals("true")) {
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
            JSONObject colors =
                    new Catalog(this)
                            .data
                            .getJSONObject("themes")
                            .getJSONObject(settings.value("appearance.theme"))
                            .getJSONObject("colors");
            ground = Color.parseColor(colors.getString("lapis-900"));
            ink = Color.parseColor(colors.getString("plaster-100"));
            accent = Color.parseColor(colors.getString("gold-400"));
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setBackgroundColor(ground);
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
            ImageView mark = new ImageView(this);
            mark.setImageResource(R.drawable.miqat_icon);
            mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            content.addView(mark, new LinearLayout.LayoutParams(padding * 3, padding * 3));
            state = label(PhoneAlarms.text(this, "phone_playing"), 18, ink);
            label(
                    settings.data.prayerName(
                            getIntent().getIntExtra("prayer", 0), PhoneAlarms.arabic(this)),
                    32,
                    accent);
            label(PhoneAlarms.text(this, "phone_screen_hint"), 14, ink);
            Button stop = new Button(this);
            stop.setTag("adhan.stop-close");
            stop.setText(PhoneAlarms.text(this, "phone_stop_close"));
            stop.setTextColor(ground);
            android.graphics.drawable.GradientDrawable shape =
                    new android.graphics.drawable.GradientDrawable();
            shape.setColor(accent);
            shape.setCornerRadius(padding);
            stop.setBackground(shape);
            stop.setElevation(0);
            stop.setStateListAnimator(null);
            stop.setMinHeight(padding * 2);
            stop.setAllCaps(false);
            stop.setOnClickListener(v -> stopAndClose());
            content.addView(stop, new LinearLayout.LayoutParams(-1, -2));
            setContentView(scroll);
        } catch (Exception unavailable) {
            finish();
        }
    }

    private TextView label(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER);
        text.setTypeface(getResources().getFont(R.font.alexandria_regular));
        int padding = Math.round(getResources().getDisplayMetrics().density * 12);
        text.setPadding(0, padding, 0, padding);
        content.addView(text, new LinearLayout.LayoutParams(-1, -2));
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
        if (state != null) clock.post(session);
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

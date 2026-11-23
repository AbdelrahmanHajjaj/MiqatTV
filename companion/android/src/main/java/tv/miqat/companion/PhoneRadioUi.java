package tv.miqat.companion;

import android.app.AlertDialog;
import android.content.Intent;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class PhoneRadioUi {
    private final MainActivity a;
    private final PhoneData data;
    AlertDialog popup;
    private LinearLayout station;
    private Button homePlay, play, stop;
    private TextView status;
    private String displayedStation;

    PhoneRadioUi(MainActivity a, PhoneData data) {
        this.a = a;
        this.data = data;
    }

    void addHomeControls(LinearLayout toolbar) {
        a.views.iconButton(toolbar, "radio", a.t("phone_radio"), this::show)
                .setTag("phone.radio.open");
        homePlay = a.views.iconButton(toolbar, "play", label("radio.play"), this::toggle);
        homePlay.setTag("phone.radio.quick");
        refresh();
    }

    void show() {
        if (popup != null) return;
        LinearLayout body = a.views.column();
        body.setTag("radio.popup");
        body.setLayoutDirection(a.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        body.setPadding(a.views.dp(16), a.views.dp(8), a.views.dp(16), a.views.dp(12));
        LinearLayout title = a.views.horizontal(body);
        TextView heading = a.views.text(a.t("phone_radio"), 22, a.ink);
        heading.setTypeface(a.heading);
        title.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        a.views.iconButton(title, "close", a.t("done"), this::dismiss).setTag("radio.close");
        station = a.views.column();
        body.addView(station);
        LinearLayout controls = a.views.horizontal(body);
        controls.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        status = a.views.text("", 14, a.muted);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setTag("radio.status");
        controls.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        play = a.views.iconButton(controls, "play", label("radio.play"), this::toggle);
        stop = a.views.iconButton(controls, "stop", label("radio.stop"), PhoneRadio::stop);
        stop.setTag("radio.stop");
        popup = a.views.dialog().setView(body).create();
        popup.setOnDismissListener(ignored -> {
            popup = null;
            station = null;
            play = null;
            stop = null;
            status = null;
            displayedStation = null;
        });
        refresh();
        popup.show();
    }

    void dismiss() {
        if (popup != null) popup.dismiss();
    }

    void refresh() {
        boolean playing = PhoneRadio.engaged();
        boolean allowed = PhoneRadio.notifications(a);
        String action = playing ? "radio.pause" : "radio.play";
        String description = allowed || playing ? label(action) : a.t("phone_radio_allow_notifications");
        if (homePlay != null) {
            homePlay.setVisibility(PhoneRadio.hasSession() ? View.VISIBLE : View.GONE);
            updateIcon(homePlay, playing, description);
        }
        if (popup == null) return;
        String selected = selected();
        if (!selected.equals(displayedStation)) {
            displayedStation = selected;
            String name = PhoneRadio.stationName(data.station(selected), a.ar);
            station.removeAllViews();
            View row = a.views.navigationRow(station, "radio", name, "", this::pickStation);
            row.setTag("radio.station");
            row.setContentDescription(label("radio.station") + ", " + name);
        }
        String message = PhoneRadio.status();
        status.setText(message.isEmpty() ? "" : a.t(message));
        play.setTag(action);
        updateIcon(play, playing, description);
        stop.setVisibility(PhoneRadio.hasSession() ? View.VISIBLE : View.GONE);
    }

    private void updateIcon(Button button, boolean playing, String label) {
        if (!label.contentEquals(button.getContentDescription())) {
            button.setContentDescription(label);
            button.setTooltipText(label);
            button.setForeground(Icons.get(a, playing ? "pause" : "play", a.accent));
        }
        button.setEnabled(playing || !PhonePlayback.playing());
    }

    private String label(String id) {
        return a.t(data.actionLabel(id));
    }

    private String selected() {
        return PhoneRadio.selected(a, data);
    }

    private void pickStation() {
        List<Choices.Option> stations = new ArrayList<>();
        for (int i = 0; i < data.stations.length(); i++) {
            JSONObject option = data.stations.optJSONObject(i);
            stations.add(new Choices.Option(option.optString("id"), PhoneRadio.stationName(option, a.ar)));
        }
        a.phoneUi.picker(label("radio.station"), stations, selected(), id -> {
            if (selected().equals(id)) return;
            a.prefs().edit().putString(PhoneRadio.STATION_PREFERENCE, id).apply();
            PhoneRadio.stop();
            refresh();
        });
    }

    private void toggle() {
        if (PhoneRadio.engaged()) PhoneRadio.pause();
        else if (!PhoneRadio.notifications(a)) {
            a.startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, PhoneRadio.CHANNEL));
        } else if (!PhonePlayback.playing()) {
            a.startService(new Intent(a, PhoneRadio.class).putExtra("station", selected()));
        }
    }
}

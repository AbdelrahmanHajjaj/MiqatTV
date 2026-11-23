package tv.miqat.companion;

import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class PhoneRadioUi {
    private final MainActivity a;
    private final PhoneData data;
    private Button station, play, stop;
    private TextView status;

    PhoneRadioUi(MainActivity a, PhoneData data) {
        this.a = a;
        this.data = data;
    }

    void render() {
        station = a.views.button(a.body, "", this::pickStation);
        station.setTag("radio.station");
        status = a.views.text("", 14, a.muted);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setTag("radio.status");
        a.body.addView(status);
        play = a.views.button(a.body, "", this::toggle);
        stop = a.views.button(a.body, label("radio.stop"), PhoneRadio::stop);
        stop.setTag("radio.stop");
        refresh();
    }

    void refresh() {
        if (station == null) return;
        JSONObject selected = data.station(selected());
        String name = PhoneRadio.stationName(selected, a.ar);
        station.setText(name);
        station.setContentDescription(label("radio.station") + ", " + name);
        boolean playing = PhoneRadio.engaged();
        boolean blocked = PhonePlayback.playing();
        boolean allowed = PhoneRadio.notifications(a);
        String message = PhoneRadio.status();
        status.setText(message.isEmpty() ? "" : a.t(message));
        status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        play.setTag(playing ? "radio.pause" : "radio.play");
        play.setText(playing ? label("radio.pause")
                : allowed ? label("radio.play") : a.t("phone_radio_allow_notifications"));
        play.setEnabled(playing || !blocked);
        stop.setVisibility(PhoneRadio.hasSession() ? View.VISIBLE : View.GONE);
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
            a.startActivity(new Intent(PhoneAlarms.notifications(a)
                    ? Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS : Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, PhoneRadio.CHANNEL));
        } else if (!PhonePlayback.playing()) {
            a.startService(new Intent(a, PhoneRadio.class).putExtra("station", selected()));
        }
    }
}

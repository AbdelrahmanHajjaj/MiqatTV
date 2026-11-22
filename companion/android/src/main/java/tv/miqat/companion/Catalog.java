package tv.miqat.companion;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import tv.miqat.companion.core.DemoTv;
import tv.miqat.companion.core.SettingSpec;
import tv.miqat.core.UiContract;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Catalog {
    final JSONObject data;

    Catalog(Context context) throws Exception {
        try (InputStream input = context.getAssets().open("catalog.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            data = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    JSONArray controls(String profile) {
        return data.optJSONObject("profiles").optJSONArray(profile);
    }

    JSONObject control(String profile, String id) {
        JSONArray list = controls(profile);
        for (int i = 0; i < list.length(); i++)
            if (list.optJSONObject(i).optString("id").equals(id)) return list.optJSONObject(i);
        throw new IllegalArgumentException("Unknown setting: " + id);
    }

    List<SettingSpec> specs(String profile) {
        return specs(controls(profile));
    }

    static List<SettingSpec> specs(JSONArray controls) {
        List<SettingSpec> out = new ArrayList<>();
        for (int i = 0; i < controls.length(); i++) {
            JSONObject c = controls.optJSONObject(i);
            if (!c.optBoolean("available")
                    || c.optString("kind").equals("action")
                    || c.optString("section").equals("tests")) continue;
            String id = c.optString("id"), kind = c.optString("kind");
            int min = 0, max = c.optInt("maximum", 0), step = 1;
            if (kind.equals("number")) {
                min = UiContract.INSTANCE.minimum(id);
                max = UiContract.INSTANCE.maximum(id);
                step = UiContract.INSTANCE.numberStep(id);
            }
            List<String> options = new ArrayList<>();
            JSONArray choices = c.optJSONArray("options");
            for (int n = 0; n < choices.length(); n++)
                options.add(choices.optJSONObject(n).optString("value"));
            out.add(
                    new SettingSpec(
                            id,
                            kind,
                            c.optString("section").equals("appearance") ? "appearance" : "",
                            options,
                            min,
                            max,
                            step));
        }
        return out;
    }

    void install(LanTv tv) throws Exception {
        data.getJSONObject("profiles").put(tv.key, tv.controls());
    }

    Map<String, String> defaults(String profile) {
        Map<String, String> out = new LinkedHashMap<>();
        for (SettingSpec spec : specs(profile))
            out.put(spec.getId(), control(profile, spec.getId()).optString("default"));
        return out;
    }

    DemoTv demo(String profile, Map<String, String> stored) {
        Map<String, String> values = defaults(profile);
        List<SettingSpec> specs = specs(profile);
        for (SettingSpec spec : specs)
            if (stored.containsKey(spec.getId())) {
                try {
                    spec.validate(stored.get(spec.getId()));
                    values.put(spec.getId(), stored.get(spec.getId()));
                } catch (IllegalArgumentException ignored) {
                    /* Demo catalog updates retain valid saved values. */
                }
            }
        Map<String, Map<String, String>> cities = new LinkedHashMap<>();
        JSONArray list = data.optJSONArray("cities");
        for (int i = 0; i < list.length(); i++) {
            JSONObject c = list.optJSONObject(i);
            Map<String, String> defaults = new LinkedHashMap<>();
            defaults.put("prayer.method", c.optString("method"));
            defaults.put("prayer.asr", c.optString("asr"));
            cities.put(c.optString("id"), defaults);
        }
        return new DemoTv(specs, values, cities);
    }

    String label(JSONObject label, boolean ar) {
        return label.optString(ar ? "ar" : "en");
    }

    static boolean retiredVoice(String id, String value) {
        return (id.equals("reminders.voice") || id.equals("reminders.fajr-voice"))
                && value.equals("alafasy");
    }

    String value(String profile, String id, String value, boolean ar) {
        if (retiredVoice(id, value))
            return label(data.optJSONObject("text").optJSONObject("tv_retired_voice"), ar);
        JSONObject c = control(profile, id);
        JSONArray choices = c.optJSONArray("options");
        for (int i = 0; i < choices.length(); i++) {
            JSONObject choice = choices.optJSONObject(i);
            if (choice.optString("value").equals(value)) return label(choice, ar);
        }
        return value;
    }
}

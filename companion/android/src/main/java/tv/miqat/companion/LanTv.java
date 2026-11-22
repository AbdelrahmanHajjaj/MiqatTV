package tv.miqat.companion;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import tv.miqat.companion.core.*;
import tv.miqat.companion.lan.*;

import java.io.IOException;
import java.util.*;

/** Blocking adapter called only by the phone's IO worker. It never writes from connect/read. */
final class LanTv implements TvSettings {
    final String key;
    Phones.Phone phone;
    volatile boolean online;
    volatile boolean needsRefresh = true;
    String systemLanguage = "";
    JSONObject remote = new JSONObject();
    boolean remoteUncertain;
    private final Catalog canonical;
    private final Phones store;
    private final LanClient client;
    private JSONArray controls = new JSONArray();
    private List<SettingSpec> specs = new ArrayList<>();
    private SettingsSnapshot last = new SettingsSnapshot(Collections.emptyMap());

    LanTv(Context context, Phones store, Phones.Phone phone, Catalog canonical) throws Exception {
        this.canonical = canonical;
        this.store = store;
        this.phone = phone;
        key = "tv-" + phone.pin;
        LanConfig config = new LanConfig(context);
        boolean debug =
                (context.getApplicationInfo().flags
                                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)
                        != 0;
        client =
                new LanClient(
                        new Endpoint(phone.url, phone.pin, config.port, debug),
                        config,
                        phone.certificate);
    }

    @Override
    public List<SettingSpec> getSpecs() {
        return specs;
    }

    JSONArray controls() {
        return controls;
    }

    SettingsSnapshot cached() {
        return last;
    }

    @Override
    public synchronized boolean connect() {
        try {
            phone = PairRequest.complete(phone, client, store);
            online = true;
            return true;
        } catch (Exception unavailable) {
            online = false;
            needsRefresh = true;
            return false;
        }
    }

    @Override
    public synchronized SettingsSnapshot read() {
        try {
            LanClient.Result result = client.call("GET", "/v1/settings", null, phone.credential());
            if (result.status != 200) throw new IOException("Settings unavailable");
            SettingsSnapshot snapshot = accept(result.body);
            online = true;
            needsRefresh = false;
            return snapshot;
        } catch (Exception failure) {
            online = false;
            needsRefresh = true;
            throw new IllegalStateException("TV settings unavailable", failure);
        }
    }

    synchronized JSONObject readRemote() throws Exception {
        try {
            LanClient.Result result = client.call("GET", "/v1/remote", null, phone.credential());
            if (result.status == 404) {
                remote =
                        new JSONObject()
                                .put("commands", new JSONArray())
                                .put("inputs", new JSONArray());
                online = true;
                remoteUncertain = false;
                return remote;
            }
            if (result.status != 200) throw new IOException("Remote unavailable");
            JSONArray commands = result.body.getJSONArray("commands"),
                    inputs = result.body.getJSONArray("inputs");
            Set<String> allowed = new HashSet<>(), seen = new HashSet<>();
            JSONArray source = canonical.data.getJSONArray("commands");
            for (int i = 0; i < source.length(); i++) allowed.add(source.getString(i));
            for (int i = 0; i < commands.length(); i++) {
                String id = commands.getString(i);
                if (!allowed.contains(id) || !seen.add(id))
                    throw new IOException("Invalid command capability");
            }
            seen.clear();
            if (inputs.length() > 128) throw new IOException("Invalid input inventory");
            for (int i = 0; i < inputs.length(); i++) {
                JSONObject input = inputs.getJSONObject(i);
                String value = Json.string(input, "value"), label = Json.string(input, "label");
                if (value.isEmpty()
                        || value.length() > 2048
                        || label.isEmpty()
                        || label.length() > 255
                        || !seen.add(value)) throw new IOException("Invalid TV input");
            }
            remote = result.body;
            online = true;
            remoteUncertain = false;
            return remote;
        } catch (Exception error) {
            online = false;
            needsRefresh = true;
            throw error;
        }
    }

    synchronized JSONObject command(String id, String value) throws Exception {
        boolean stopping = id.equals("radio.stop") || id.equals("tests.cancel");
        if (!online || remoteUncertain && !stopping)
            throw new IllegalStateException("Refresh remote first");
        boolean offered = false;
        JSONArray commands = remote.optJSONArray("commands");
        if (commands != null)
            for (int i = 0; i < commands.length(); i++)
                if (id.equals(commands.getString(i))) offered = true;
        if (!offered || value.length() > 2048 || !id.equals("device.inputs") && !value.isEmpty())
            throw new IllegalArgumentException("Unsupported command");
        if (!value.isEmpty()) {
            boolean input = false;
            JSONArray inputs = remote.getJSONArray("inputs");
            for (int i = 0; i < inputs.length(); i++)
                if (value.equals(inputs.getJSONObject(i).getString("value"))) input = true;
            if (!input) throw new IllegalArgumentException("Unknown input");
        }
        try {
            LanClient.Result result =
                    client.call(
                            "POST",
                            "/v1/command",
                            new JSONObject().put("id", id).put("value", value),
                            phone.credential());
            if (result.status != 200) throw new IOException("Command result not confirmed");
            String status = Json.string(result.body, "status");
            if (!Arrays.asList("accepted", "busy", "unavailable", "uncertain").contains(status))
                throw new IOException("Invalid command result");
            remoteUncertain = status.equals("uncertain");
            return result.body;
        } catch (Exception error) {
            online = false;
            needsRefresh = true;
            remoteUncertain = true;
            return Json.object("status", "uncertain");
        }
    }

    void restore(String cache) {
        try {
            accept(Json.read(cache));
        } catch (Exception invalid) {
            controls = new JSONArray();
            specs = new ArrayList<>();
            last = new SettingsSnapshot(Collections.emptyMap());
        }
        needsRefresh = true;
    }

    String cache() throws Exception {
        return new JSONObject()
                .put("profile", phone.profile)
                .put("systemLanguage", systemLanguage)
                .put("controls", controls)
                .put("values", new JSONObject(last.getValues()))
                .toString();
    }

    private SettingsSnapshot accept(JSONObject reply) throws Exception {
        String profile = Json.string(reply, "profile");
        if (!profile.equals(phone.profile)) throw new IOException("TV platform changed");
        JSONArray offered = reply.getJSONArray("controls");
        JSONObject values = reply.getJSONObject("values");
        List<SettingSpec> parsed = Catalog.specs(offered);
        Set<String> ids = new HashSet<>();
        Map<String, String> next = new LinkedHashMap<>();
        for (int i = 0; i < offered.length(); i++) {
            JSONObject control = offered.getJSONObject(i);
            String id = Json.string(control, "id");
            if (!id.matches("[a-z]+(?:\\.[a-z][a-z0-9-]*)+") || !ids.add(id))
                throw new IOException("Invalid settings catalog");
            if (!(control.get("available") instanceof Boolean))
                throw new IOException("Invalid capability");
            boolean remote = id.startsWith("remote."), apps = id.equals("favourites.order");
            JSONObject template = canonical.control(profile, remote ? "remote.settings" : id);
            for (String field : new String[] {"kind", "section", "save"})
                if (!Json.string(control, field).equals(template.getString(field)))
                    throw new IOException("Invalid setting contract");
            if (!control.optString("route").equals(template.optString("route"))
                    || control.optInt("maximum") != template.optInt("maximum"))
                throw new IOException("Invalid setting contract");
            if (remote
                    && !id.matches(
                            "remote\\.(settings-shortcut|settings|input|tv|red|green|yellow|blue|key-[0-9]+)"))
                throw new IOException("Unknown remote key");
            JSONObject title = control.getJSONObject("title");
            Json.string(title, "ar");
            Json.string(title, "en");
            JSONArray options = control.getJSONArray("options");
            Set<String> optionIds = new HashSet<>();
            Set<String> allowed = new HashSet<>();
            JSONArray choices = template.getJSONArray("options");
            for (int j = 0; j < choices.length(); j++)
                allowed.add(choices.getJSONObject(j).getString("value"));
            for (int j = 0; j < options.length(); j++) {
                JSONObject option = options.getJSONObject(j);
                String value = Json.string(option, "value");
                if (!optionIds.add(value)
                        || !(allowed.contains(value)
                                || Catalog.retiredVoice(id, value)
                                || (remote || apps)
                                        && value.length() <= 255
                                        && value.matches(
                                                "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")))
                    throw new IOException("Invalid choice");
                Json.string(option, "ar");
                Json.string(option, "en");
            }
        }
        for (SettingSpec spec : parsed) {
            String value = Json.string(values, spec.getId());
            if (!spec.getId().equals("prayer.facing") || !value.equals("-1")) spec.validate(value);
            next.put(spec.getId(), value);
        }
        if (next.size() != values.length()) throw new IOException("Unsupported settings payload");
        systemLanguage = Json.string(reply, "systemLanguage");
        controls = offered;
        specs = parsed;
        last = new SettingsSnapshot(next);
        return last;
    }

    @Override
    public synchronized SaveResult apply(
            Map<String, String> expected, Map<String, String> changes) {
        if (!online || needsRefresh) throw new IllegalStateException("Refresh settings first");
        for (Map.Entry<String, String> edit : changes.entrySet()) {
            SettingSpec found = null;
            for (SettingSpec spec : specs) if (spec.getId().equals(edit.getKey())) found = spec;
            if (found == null) throw new IllegalArgumentException("Unknown setting");
            found.validate(edit.getValue());
        }
        try {
            JSONObject request =
                    new JSONObject()
                            .put("expected", new JSONObject(expected))
                            .put("changes", new JSONObject(changes));
            LanClient.Result result =
                    client.call("POST", "/v1/settings", request, phone.credential());
            if (result.status != 200) throw new IOException("Write outcome not confirmed");
            String status = Json.string(result.body, "status");
            if (!Arrays.asList("saved", "conflict", "busy", "uncertain").contains(status))
                throw new IOException("Unknown write result");
            SettingsSnapshot actual = accept(result.body);
            List<String> conflicts = new ArrayList<>();
            JSONArray list = result.body.getJSONArray("conflicts");
            for (int i = 0; i < list.length(); i++) {
                Object id = list.get(i);
                if (!(id instanceof String) || !actual.getValues().containsKey((String) id))
                    throw new IOException("Unknown conflict");
                conflicts.add((String) id);
            }
            if (status.equals("uncertain")) needsRefresh = true;
            return new SaveResult(status, actual, conflicts);
        } catch (Exception uncertain) {
            online = false;
            needsRefresh = true;
            return new SaveResult("uncertain", last, Collections.emptyList());
        }
    }

    synchronized Phones.Phone rename(String name) throws Exception {
        phone =
                store.update(
                        phone,
                        current ->
                                new Phones.Phone(
                                        current.url,
                                        current.pin,
                                        current.certificate,
                                        current.id,
                                        current.secret,
                                        name,
                                        current.profile,
                                        current.pending));
        return phone;
    }
}

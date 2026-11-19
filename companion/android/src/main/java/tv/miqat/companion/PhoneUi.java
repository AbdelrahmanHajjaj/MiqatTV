package tv.miqat.companion;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import org.json.*;

import tv.miqat.companion.core.CompanionContract;
import tv.miqat.companion.core.SettingSpec;

import java.text.SimpleDateFormat;
import java.util.*;

/** Independent phone screens; TV settings never enter this controller. */
final class PhoneUi {
    final MainActivity a;
    PhoneData data;
    PhoneStore settings;
    final PhoneLocationUi locationUi;
    AlertDialog choiceDialog;
    final PhoneHome home;
    String pending = "", draft = "";
    final PhoneSound sound;
    final PhoneAppearance appearance;
    private final ArrayList<Bundle> history = new ArrayList<>();

    PhoneUi(MainActivity a) {
        this.a = a;
        home = new PhoneHome(a, this);
        locationUi = new PhoneLocationUi(a, this);
        appearance = new PhoneAppearance(a);
        sound = new PhoneSound(a, this);
    }

    void restore(Bundle b) {
        ArrayList<Bundle> saved = b.getParcelableArrayList("phone-history");
        if (saved != null)
            for (Bundle place : saved)
                if (history.size() < 32
                        && place != null
                        && CompanionContract.INSTANCE
                                .getScreens()
                                .contains(place.getString("page", ""))) history.add(place);

        sound.audioImport = b.getString("phone-audio-import", "");
        locationUi.advancedLocation = b.getBoolean("phone-location-advanced");
        home.dayOffset = Math.max(-366, Math.min(366, b.getInt("phone-day")));
        pending = b.getString("phone-pending", "");
        draft = b.getString("phone-draft", "");
        locationUi.latitude = b.getString("phone-lat", "");
        locationUi.longitude = b.getString("phone-lng", "");
    }

    void save(Bundle b) {
        b.putParcelableArrayList("phone-history", new ArrayList<>(history));
        b.putString("phone-audio-import", sound.audioImport);
        b.putBoolean("phone-location-advanced", locationUi.advancedLocation);
        b.putInt("phone-day", home.dayOffset);
        b.putString("phone-pending", pending);
        b.putString("phone-draft", draft);
        b.putString("phone-lat", locationUi.latitude);
        b.putString("phone-lng", locationUi.longitude);
    }

    void stop() {
        home.stop();
        locationUi.cancelLocation();
        if (choiceDialog != null) choiceDialog.dismiss();
    }

    void palette() {
        try {
            data = PhoneData.get(a);
            settings = PhoneStore.read(a);
            JSONObject c =
                    a.catalog
                            .data
                            .getJSONObject("themes")
                            .getJSONObject(settings.value("appearance.theme"))
                            .getJSONObject("colors");
            a.ground = Color.parseColor(c.getString("lapis-900"));
            a.surface = Color.parseColor(c.getString("lapis-800"));
            a.accent = Color.parseColor(c.getString("gold-400"));
            a.ink = Color.parseColor(c.getString("plaster-100"));
            a.muted = Color.parseColor(c.getString("mist-400"));
            a.getWindow().setStatusBarColor(a.ground);
            a.getWindow().setNavigationBarColor(a.ground);
        } catch (Exception invalid) {
            settings = null;
        }
    }

    void render() {
        if (settings == null) {
            a.body.addView(a.views.text(a.t("phone_settings_failed"), 16, a.ink));
            reset(a.body);
            return;
        }
        try {
            if (a.page.equals("prayers")) home.render(System.currentTimeMillis());
            else preferences();
        } catch (Exception invalid) {
            a.body.addView(a.views.text(a.t("phone_settings_failed"), 16, a.ink));
            reset(a.body);
        }
        home.start();
    }

    String digits(String value) {
        String mode = settings.value("display.digits");
        boolean eastern = mode.equals("eastern") || mode.equals("auto") && a.ar;
        return tv.miqat.core.DisplayText.INSTANCE.digits(value, eastern);
    }

    Locale locale() {
        return new Locale(a.ar ? "ar" : "en");
    }

    String time(double stamp) {
        SimpleDateFormat f =
                new SimpleDateFormat(
                        settings.value("display.clock").equals("12") ? "h:mm a" : "HH:mm",
                        locale());
        f.setTimeZone(settings.zone());
        return digits(f.format(new Date((long) stamp)));
    }

    String location() throws Exception {
        String id = settings.value("prayer.location");
        if (id.equals("custom")) return a.t("phone_custom_location");
        if (id.equals("detected")) return a.t("phone_current_location");
        return id.startsWith("detected:")
                ? String.format(
                        locale(),
                        a.t("phone_near_city"),
                        a.catalog.label(data.city(id.substring(9)), a.ar))
                : a.catalog.label(data.city(id), a.ar);
    }

    void resetNavigation() {
        pending = "";
        history.clear();
        locationUi.cancelLocation();
    }

    void open(String section) {
        if (a.page.equals("phone-settings") && a.section.equals(section)) return;
        int existing = -1;
        for (int i = 0; i < history.size(); i++)
            if (history.get(i).getString("page", "").equals("phone-settings")
                    && history.get(i).getString("section", "").equals(section)) existing = i;
        if (existing >= 0) history.subList(existing, history.size()).clear();
        else {
            Bundle place = new Bundle();
            place.putString("page", a.page);
            place.putString("section", a.section);
            history.add(place);
        }
        if (!section.equals("calculation")) locationUi.cancelLocation();
        pending = "";
        a.section = section;
        a.page = "phone-settings";
        a.render();
    }

    boolean back() {
        if (!pending.isEmpty()) {
            String id = pending;
            pending = "";
            a.render();
            editorFocus(id);
            return true;
        }
        if (history.isEmpty()) return false;
        Bundle place = history.remove(history.size() - 1);
        locationUi.cancelLocation();
        a.page = place.getString("page", "prayers");
        a.section = place.getString("section", "");
        a.render();
        return true;
    }

    void preferences() throws Exception {
        if (a.section.isEmpty()) {
            a.body.addView(a.views.text(a.t("phone_independent"), 14, a.muted));
            for (String section :
                    new String[] {"calculation", "audio", "appearance", "permissions"})
                a.views
                        .navigationRow(
                                a.body,
                                section.equals("calculation")
                                        ? "compass"
                                        : section.equals("appearance") ? "mihrab" : "bell",
                                a.t("phone_" + section),
                                "",
                                () -> open(section))
                        .setTag("phone.section." + section);
            prayerRows();
            a.views.navigationRow(
                    a.body, "gear", a.t("setup_again"), "", () -> a.onboarding.open());
            a.views.navigationRow(
                    a.body, "gear", a.t("preferences"), "", () -> a.navigate("preferences"));
            reset(a.body);
            return;
        }
        if (a.section.equals("permissions")) {
            permissions();
            return;
        }
        if (a.section.equals("audio")) {
            audio();
            return;
        }
        if (a.section.equals("calculation")) locationUi.locationEditor();
        if (a.section.equals("appearance")) appearance.preview(a.body, settings);
        for (JSONObject c : data.controls.values())
            if (c.getString("section").equals(a.section)) control(c);
        if (a.section.equals("calculation")) locationUi.confirmLocation();
    }

    private void prayerRows() throws Exception {
        a.body.addView(a.views.heading(a.t("phone_prayer_settings")));
        for (int i = 0; i < 6; i++)
            if (i != 1) {
                final String id = PhoneStore.PRAYERS[i];
                String summary =
                        value("call." + id + ".mode", settings.value("call." + id + ".mode"))
                                + " · "
                                + value("call." + id + ".voice", settings.voice(i));
                a.views
                        .navigationRow(
                                a.body, "bell", data.prayerName(i, a.ar), summary, () -> open(id))
                        .setTag("phone.prayer-settings." + id);
            }
    }

    private void audio() throws Exception {
        a.views.navigationRow(
                a.body,
                "bell",
                a.t(PhoneAlarms.status(a, settings)),
                a.t("phone_permissions"),
                () -> open("permissions"));
        for (JSONObject c : data.controls.values())
            if (c.getString("section").equals("audio") && !c.getString("id").startsWith("quiet."))
                control(c);
        a.body.addView(a.views.text(a.t("phone_volume_hint"), 13, a.muted));
        a.body.addView(a.views.text(a.t("phone_duration_hint"), 13, a.muted));
        prayerRows();
        a.body.addView(a.views.text(a.t("phone_custom_audio_hint"), 13, a.muted));
        a.body.addView(a.views.heading(a.t("phone_quiet_title")));
        for (JSONObject c : data.controls.values())
            if (c.getString("section").equals("audio") && c.getString("id").startsWith("quiet."))
                control(c);
        a.body.addView(a.views.text(a.t("phone_quiet_hint"), 13, a.muted));
    }

    String sectionTitle() {
        if (Arrays.asList(PhoneStore.PRAYERS).contains(a.section))
            try {
                return data.prayerName(Arrays.asList(PhoneStore.PRAYERS).indexOf(a.section), a.ar);
            } catch (Exception ignored) {
            }
        return a.t("phone_" + (a.section.isEmpty() ? "settings" : a.section));
    }

    String value(String id, String value) throws Exception {
        if (id.endsWith(".mode") || id.endsWith(".high-latitude") || value.equals("inherit"))
            return a.t("phone_" + value);
        if (id.equals("display.digits") && value.equals("auto")) return a.t("phone_auto");
        if (id.equals("prayer.zone")) return value.equals("system") ? a.t("phone_system") : value;
        if (id.endsWith(".duration") && value.equals("0")) return a.t("phone_full");
        if (id.equals("appearance.background") && value.equals("plain")) return a.t("phone_plain");
        if (id.endsWith(".volume") || id.equals("display.size")) return digits(value) + "%";
        if (value.equals("custom")) return a.t("phone_custom");
        if (id.equals("quiet.mode")) return a.t("phone_" + value);
        if (id.equals("quiet.start") || id.equals("quiet.end")) {
            int minute = Integer.parseInt(value), hour = minute / 60;
            return digits(
                    settings.value("display.clock").equals("12")
                            ? String.format(
                                    Locale.US,
                                    "%d:%02d %s",
                                    hour % 12 == 0 ? 12 : hour % 12,
                                    minute % 60,
                                    new java.text.DateFormatSymbols(locale())
                                            .getAmPmStrings()[hour / 12])
                            : String.format(Locale.US, "%02d:%02d", hour, minute % 60));
        }
        if (id.endsWith(".weekdays")) {
            int mask = Integer.parseInt(value);
            if (mask == 127) return a.t("phone_every_day");
            if (mask == 0) return a.t("phone_no_days");
            java.util.List<String> names = new ArrayList<>();
            String[] weekdays = new java.text.DateFormatSymbols(locale()).getShortWeekdays();
            for (int i = 0; i < 7; i++) if ((mask & (1 << i)) != 0) names.add(weekdays[i + 1]);
            return android.text.TextUtils.join(" · ", names);
        }
        if (id.endsWith(".voice")) {
            for (int i = 0; i < data.voices.length(); i++) {
                JSONObject voice = data.voices.getJSONObject(i);
                if (voice.getString("id").equals(value)) return a.catalog.label(voice, a.ar);
            }
        }
        String source = id.startsWith("call.") ? "reminders.voice" : id;
        try {
            String label = a.catalog.value("android", source, value, a.ar);
            return id.equals("display.digits") ? label : digits(label);
        } catch (Exception absent) {
            return digits(value);
        }
    }

    void control(JSONObject c) throws Exception {
        String id = c.getString("id"),
                label = a.t(c.getString("label")),
                saved = settings.value(id);
        SettingSpec spec = data.spec(c);
        ColorStateList selection =
                new ColorStateList(
                        new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                        new int[] {a.accent, a.muted});
        LinearLayout row = a.views.column();
        row.setPadding(a.views.dp(16), a.views.dp(10), a.views.dp(16), a.views.dp(10));
        row.setBackground(a.views.background(a.surface, 18));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.bottomMargin = a.views.dp(6);
        a.body.addView(row, size);
        row.setTag("phone." + id);
        if (c.getString("kind").equals("toggle")) {
            Switch toggle = new Switch(a);
            toggle.setText(label);
            toggle.setTextColor(a.ink);
            toggle.setTextSize(15);
            toggle.setTypeface(a.regular);
            toggle.setMinHeight(a.views.dp(48));
            toggle.setChecked(saved.equals("true"));
            toggle.setThumbTintList(selection);
            toggle.setTrackTintList(
                    new ColorStateList(
                            new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                            new int[] {
                                (a.accent & 0xffffff) | 0x66000000,
                                (a.muted & 0xffffff) | 0x44000000
                            }));
            toggle.setContentDescription(label);
            row.addView(toggle);
            toggle.setTag("phone." + id + ".edit");
            toggle.setOnCheckedChangeListener((button, on) -> {
                if (id.equals("reminders.enabled") && on && !settings.locationReady) {
                    a.notice("phone_location_needed");
                    open("calculation");
                    return;
                }
                save(id, Boolean.toString(on));
            });
            return;
        }
        TextView title = a.views.text(label, 13, a.muted);
        title.setPadding(0, 0, 0, a.views.dp(2));
        row.addView(title);
        if (c.getString("kind").equals("number")) {
            boolean inherited =
                    id.startsWith("call.")
                            && (id.endsWith(".volume") || id.endsWith(".duration"))
                            && settings.value(
                                            id.substring(0, id.lastIndexOf('.') + 1)
                                                    + "default-audio")
                                    .equals("true");
            if (inherited) {
                summary(
                        row,
                        id,
                        value(
                                id,
                                Integer.toString(
                                        id.endsWith(".volume")
                                                ? settings.volume
                                                : settings.duration)),
                        () -> {});
                a.views.disableContent(row);
                row.setAlpha(0.55f);
                return;
            }
            if (pending.equals(id)) number(row, spec, saved, label);
            else
                summary(
                        row,
                        id,
                        value(id, saved),
                        () -> {
                            if (c.optString("editor").equals("time")) {
                                int minute = Integer.parseInt(saved);
                                showEditor(
                                        new TimePickerDialog(
                                                a,
                                                (view, hour, minutes) ->
                                                        save(
                                                                id,
                                                                Integer.toString(
                                                                        hour * 60 + minutes)),
                                                minute / 60,
                                                minute % 60,
                                                settings.value("display.clock").equals("24")));
                                return;
                            }
                            if (c.optString("editor").equals("weekdays")) {
                                weekdays(id, saved, label);
                                return;
                            }
                            pending = id;
                            draft = saved;
                            a.render();
                            View editor = a.body.findViewWithTag("phone." + id);
                            editor.post(() ->
                                    editor.requestRectangleOnScreen(
                                            new Rect(0, 0, editor.getWidth(), editor.getHeight()),
                                            false));
                        });
            return;
        }
        summary(
                row,
                id,
                value(id, saved),
                () -> {
                    try {
                        choose(id, data.options(c));
                    } catch (Exception invalid) {
                        a.notice("invalid");
                    }
                });
        if (id.endsWith(".voice")) {
            LinearLayout actions = a.views.horizontal(row);
            Button preview =
                    a.views.button(
                            actions,
                            a.t("phone_preview"),
                            () -> sound.preview(id, settings.value(id)));
            a.views.flex(preview);
            preview.setTag("phone." + id + ".preview");
            a.views
                    .iconButton(actions, "close", a.t("phone_stop"), () -> PhonePlayback.stop(a))
                    .setTag("phone." + id + ".stop");
        }
    }

    void summary(LinearLayout row, String id, String value, Runnable edit) {
        TextView selected = a.views.text(value + "  ⌄", 16, a.ink);
        selected.setPadding(0, 0, 0, 0);
        selected.setMinHeight(a.views.dp(48));
        selected.setGravity(Gravity.CENTER_VERTICAL);
        selected.setTag("phone." + id + ".edit");
        selected.setContentDescription(
                a.t(data.controls.get(id).optString("label")) + ", " + value);
        selected.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf((a.accent & 0xffffff) | 0x22000000), null, null));
        selected.setFocusable(true);
        selected.setOnClickListener(v -> edit.run());
        row.addView(selected, new LinearLayout.LayoutParams(-1, -2));
    }

    void showEditor(AlertDialog dialog) {
        if (choiceDialog != null) choiceDialog.dismiss();
        choiceDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (choiceDialog == dialog) choiceDialog = null;
        });
        dialog.show();
        a.views.styleDialog(dialog);
    }

    void weekdays(String id, String saved, String label) {
        int mask = Integer.parseInt(saved);
        boolean[] selected = new boolean[7];
        String[] names = new String[7],
                days = new java.text.DateFormatSymbols(locale()).getWeekdays();
        for (int i = 0; i < 7; i++) {
            names[i] = days[i + 1];
            selected[i] = (mask & (1 << i)) != 0;
        }
        showEditor(
                a.views
                        .dialog()
                        .setTitle(label)
                        .setMultiChoiceItems(
                                names, selected, (dialog, index, on) -> selected[index] = on)
                        .setNegativeButton(a.t("cancel"), null)
                        .setPositiveButton(
                                a.t("done"),
                                (dialog, which) -> {
                                    int result = 0;
                                    for (int i = 0; i < 7; i++) if (selected[i]) result |= 1 << i;
                                    save(id, Integer.toString(result));
                                })
                        .create());
    }

    void number(LinearLayout row, SettingSpec spec, String saved, String label) {
        String id = spec.getId();
        boolean editing = pending.equals(id);
        LinearLayout track = a.views.horizontal(row);
        EditText input = new EditText(a);
        input.setSingleLine(true);
        input.setTextColor(a.ink);
        input.setTypeface(a.regular);
        input.setGravity(Gravity.CENTER);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(editing ? draft : saved);
        input.setTextDirection(View.TEXT_DIRECTION_LTR);
        input.setContentDescription(label);
        input.setTag("phone." + id + ".input");
        Runnable changed =
                () -> {
                    pending = id;
                    draft = input.getText().toString();
                };
        Button minus =
                a.views.button(
                        track,
                        "−",
                        () -> {
                            try {
                                input.setText(
                                        spec.adjust(normalize(input.getText().toString()), -1));
                                changed.run();
                            } catch (Exception invalid) {
                                input.setError(a.t("invalid"));
                            }
                        });
        a.views.square(minus);
        minus.setContentDescription(a.t("decrease") + " " + label);
        track.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        input.addTextChangedListener(
                MainActivity.watcher(text -> {
                    pending = id;
                    draft = text;
                }));
        Button plus =
                a.views.button(
                        track,
                        "+",
                        () -> {
                            try {
                                input.setText(
                                        spec.adjust(normalize(input.getText().toString()), 1));
                                changed.run();
                            } catch (Exception invalid) {
                                input.setError(a.t("invalid"));
                            }
                        });
        a.views.square(plus);
        plus.setContentDescription(a.t("increase") + " " + label);
        LinearLayout actions = a.views.horizontal(row);
        Button apply =
                a.views.button(
                        actions,
                        a.t("done"),
                        () -> {
                            try {
                                String next = normalize(input.getText().toString());
                                spec.validate(next);
                                if ((Integer.parseInt(next) - spec.getMinimum()) % spec.getStep()
                                        != 0) throw new IllegalArgumentException();
                                save(id, next);
                            } catch (Exception invalid) {
                                input.setError(a.t("invalid"));
                            }
                        });
        a.views.flex(apply);
        apply.setTag("phone." + id + ".save");
        Button cancel =
                a.views.button(
                        actions,
                        a.t("cancel"),
                        () -> {
                            pending = "";
                            a.render();
                            editorFocus(id);
                        });
        a.views.flex(cancel);
        cancel.setTag("phone." + id + ".cancel");
    }

    void editorFocus(String id) {
        ((android.view.inputmethod.InputMethodManager)
                        a.getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(a.getWindow().getDecorView().getWindowToken(), 0);
        View field = a.body.findViewWithTag("phone." + id + ".edit");
        if (field != null) field.requestFocus();
    }

    static String normalize(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.trim().toCharArray()) {
            int d = Character.digit(c, 10);
            out.append(d >= 0 ? (char) ('0' + d) : c == '٫' ? '.' : c == '−' ? '-' : c);
        }
        return out.toString();
    }

    void choose(String id, java.util.List<String> options) {
        if (id.equals("appearance.theme") || id.equals("appearance.background")) {
            try {
                appearance.choose(id, options);
            } catch (Exception invalid) {
                a.notice("invalid");
            }
            return;
        }
        try {
            java.util.List<Choices.Option> list = new ArrayList<>();
            for (String v : options) list.add(new Choices.Option(v, value(id, v)));
            if (id.endsWith(".voice")) sound.choose(id, list);
            else
                picker(
                        a.t(data.controls.get(id).getString("label")),
                        list,
                        settings.value(id),
                        choice -> save(id, choice));
        } catch (Exception invalid) {
            a.notice("invalid");
        }
    }

    void picker(String title, java.util.List<Choices.Option> options, java.util.function.Consumer<String> picked) {
        picker(title, options, "", picked);
    }

    void picker(
            String title, java.util.List<Choices.Option> options, String selected, java.util.function.Consumer<String> picked) {
        if (choiceDialog != null) choiceDialog.dismiss();
        LinearLayout layout = a.views.column();
        layout.setPadding(a.views.dp(16), a.views.dp(8), a.views.dp(16), a.views.dp(8));
        layout.setLayoutDirection(a.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        EditText search = new EditText(a);
        search.setSingleLine(true);
        search.setTextColor(a.ink);
        search.setHintTextColor(a.muted);
        search.setTypeface(a.regular);
        search.setHint(a.t("phone_search"));
        search.setContentDescription(a.t("phone_search"));
        if (options.size() > 8) layout.addView(search);
        ListView list = new ListView(a);
        list.setTag("phone.choice.list");
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        ArrayAdapter<Choices.Option> adapter = a.views.choices(options);
        list.setAdapter(adapter);
        int index = -1;
        for (int i = 0; i < options.size(); i++)
            if (options.get(i).value.equals(selected)) index = i;
        if (index >= 0) {
            list.setItemChecked(index, true);
            list.setSelection(index);
        }
        int height =
                Math.min(
                        a.getResources().getDisplayMetrics().heightPixels / 2,
                        a.views.dp(60) * options.size());
        layout.addView(list, new LinearLayout.LayoutParams(-1, height));
        AlertDialog dialog =
                a.views
                        .dialog()
                        .setTitle(title)
                        .setView(layout)
                        .setNegativeButton(a.t("cancel"), null)
                        .create();
        choiceDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (choiceDialog == dialog) choiceDialog = null;
        });
        search.addTextChangedListener(
                MainActivity.watcher(text -> {
                    adapter.getFilter().filter(text);
                    list.clearChoices();
                }));
        list.setOnItemClickListener((parent, view, position, item) -> {
            Choices.Option option = adapter.getItem(position);
            dialog.dismiss();
            if (option != null) picked.accept(option.value);
        });
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(a.views.background(a.surface, 24));
        dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.getWindow().setLayout(-1, -2);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(a.accent);
    }

    void save(String id, String value) {
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put(id, value);
        if (id.equals("prayer.zone")) changes.put("prayer.location-ready", "false");
        save(changes, id.equals("display.size"));
        if (!id.equals("display.size")) editorFocus(id);
        if (id.equals("prayer.travel-refresh")) {
            if (value.equals("true")) {
                locationUi.travelAttempt = 0;
                locationUi.refreshTravel();
            } else if (locationUi.travelRequest) locationUi.cancelLocation();
        }
        if (!a.page.equals("onboarding")
                && id.equals("reminders.enabled")
                && value.equals("true")
                && (!PhoneAlarms.notifications(a) || !PhoneAlarms.exact(a))) open("permissions");
    }

    void save(Map<String, String> changes, boolean recreate) {
        try {
            PhoneStore.read(a).changed(changes).save(a);
            pending = "";
            PhoneAlarms.rebuild(a);
            if (recreate) a.recreate();
            else a.render();
        } catch (Exception failure) {
            a.notice("phone_save_failed");
        }
    }

    void permissions() {
        a.body.addView(a.views.text(a.t(PhoneAlarms.status(a, settings)), 18, a.ink));
        a.body.addView(a.views.text(a.t("phone_permission_info"), 15, a.muted));
        if (!PhoneAlarms.notifications(a))
            a.views.button(
                    a.body,
                    a.t("phone_permission_notifications"),
                    () -> {
                        if (Build.VERSION.SDK_INT >= 33
                                && a.checkSelfPermission(
                                                android.Manifest.permission.POST_NOTIFICATIONS)
                                        != android.content.pm.PackageManager.PERMISSION_GRANTED)
                            a.requestPermissions(
                                    new String[] {android.Manifest.permission.POST_NOTIFICATIONS},
                                    75);
                        else
                            system(
                                    new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(
                                                    Settings.EXTRA_APP_PACKAGE,
                                                    a.getPackageName()));
                    });
        if (!PhoneAlarms.exact(a))
            a.views.button(
                    a.body,
                    a.t("phone_permission_exact"),
                    () ->
                            system(
                                    new Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            android.net.Uri.parse(
                                                    "package:" + a.getPackageName()))));
        a.views.button(
                a.body,
                a.t("phone_channel_settings"),
                () ->
                        system(
                                new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName())));
        a.body.addView(a.views.heading(a.t("phone_power_title")));
        a.body.addView(a.views.text(a.t("phone_power_info"), 14, a.muted));
        a.views
                .button(
                        a.body,
                        a.t("phone_app_settings"),
                        () ->
                                system(
                                        new Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                android.net.Uri.parse(
                                                        "package:" + a.getPackageName()))))
                .setTag("phone.power-settings");
        if (!a.page.equals("onboarding"))
            a.views.button(a.body, a.t("phone_audio"), () -> open("audio"));
    }

    void system(Intent intent) {
        try {
            a.startActivity(intent);
        } catch (ActivityNotFoundException missing) {
            a.notice("phone_permission_missing");
        }
    }

    void reset(LinearLayout parent) {
        a.views.button(
                parent,
                a.t("phone_reset"),
                () ->
                        a.views
                                .dialog()
                                .setMessage(a.t("phone_reset_confirm"))
                                .setNegativeButton(a.t("cancel"), null)
                                .setPositiveButton(
                                        a.t("reset"),
                                        (d, w) -> {
                                            try {
                                                PhonePlayback.stop(a);
                                                PhoneAlarms.cancel(a);
                                                if (!PhoneStore.preferences(a)
                                                        .edit()
                                                        .clear()
                                                        .commit())
                                                    throw new IllegalStateException();
                                                PhoneAudio.cleanup(a);
                                                resetNavigation();
                                                a.section = "";
                                                a.page = "onboarding";
                                                a.recreate();
                                            } catch (Exception failure) {
                                                a.notice("phone_save_failed");
                                            }
                                        })
                                .show());
    }
}

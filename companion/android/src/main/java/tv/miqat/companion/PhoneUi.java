package tv.miqat.companion;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import org.json.*;

import tv.miqat.companion.about.AboutMiqat;
import tv.miqat.companion.about.AboutUpdates;
import tv.miqat.companion.core.CompanionContract;
import tv.miqat.companion.core.SettingSpec;

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
        appearance.restore(b);
        locationUi.background.restore(b);
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
        pending = b.getString("phone-pending", "");
        draft = b.getString("phone-draft", "");
        locationUi.latitude = b.getString("phone-lat", "");
        locationUi.longitude = b.getString("phone-lng", "");
        locationUi.selectedCountry = b.getString("phone-country", "");
        locationUi.locationPermissionPending = b.getBoolean("phone-location-permission-pending");
    }

    void save(Bundle b) {
        appearance.save(b);
        locationUi.background.save(b);
        b.putParcelableArrayList("phone-history", new ArrayList<>(history));
        b.putString("phone-audio-import", sound.audioImport);
        b.putBoolean("phone-location-advanced", locationUi.advancedLocation);
        b.putString("phone-pending", pending);
        b.putString("phone-draft", draft);
        b.putString("phone-lat", locationUi.latitude);
        b.putString("phone-lng", locationUi.longitude);
        b.putString("phone-country", locationUi.selectedCountry);
        b.putBoolean("phone-location-permission-pending",locationUi.locationPermissionPending);
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
            a.setVolumeControlStream(PhonePlayback.attributes(settings).getVolumeControlStream());
            JSONObject c =
                    a.catalog
                            .data
                            .getJSONObject("themes")
                            .getJSONObject(
                                    a.page.equals("onboarding")
                                                    && (a.onboarding == null
                                                            || !a.onboarding.style())
                                            ? "mihrab"
                                            : settings.value("appearance.theme"))
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
        return PhoneTime.at(settings, stamp, a.ar);
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
        PhonePlayback.stopPreview(a);
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
        pending = "";
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
            LinearLayout group = a.views.navigationGroup(a.body);
            for (String section :
                    new String[] {"calculation", "audio", "fasting", "appearance", "permissions"})
                a.views
                        .navigationRow(
                                group,
                                section.equals("calculation")
                                        ? "compass"
                                        : section.equals("appearance") ? "mihrab" : "bell",
                                a.t("phone_" + section),
                                "",
                                () -> open(section))
                        .setTag("phone.section." + section);
            a.views
                    .navigationRow(
                            group, "mihrab", a.t("phone_widgets"), "", () -> a.navigate("widgets"))
                    .setTag("phone.section.widgets");
            a.languageChoices(group);
            a.views.navigationRow(
                    group, "gear", a.t("setup_again"), "", () -> a.onboarding.open());
            a.views.navigationRow(group, "gear", a.t(data.actionLabel("about.open")), "",
                    () -> open("about")).setTag("phone.section.about");
            reset(a.body);
            return;
        }
        if (a.section.startsWith("days:") || a.section.startsWith("day:")) {
            new PhoneDayOverrides(a, this).render(a.section.split(":")[1]);
            return;
        }
        if (Arrays.asList(PhoneStore.PRAYERS).contains(a.section) && !a.section.equals("sunrise")) {
            prayerEditor();
            return;
        }
        if (a.section.equals("legacy-quiet")) {
            a.body.addView(a.views.text(a.t("phone_legacy_quiet_body"), 14, a.muted));
            for (JSONObject c : data.controls.values())
                if (c.getString("id").startsWith("quiet.")) control(c);
            return;
        }
        if (a.section.equals("default-audio")) {
            for (String id :
                    new String[] {
                        "reminders.voice", "reminders.volume", "reminders.length-percent"
                    }) control(data.controls.get(id));
            return;
        }
        if (a.section.equals("permissions")) {
            permissions();
            return;
        }
        if (a.section.equals("about")) {
            about();
            return;
        }
        if (a.section.equals("audio")) {
            audio();
            return;
        }
        if (a.section.equals("fasting")) {
            fasting();
            return;
        }
        if (a.section.equals("calculation")) locationUi.locationEditor();
        if (a.section.equals("appearance")) appearance.editor(a.body, false);
        for (JSONObject c : data.controls.values())
            if (c.getString("section").equals(a.section)
                    && !c.getString("id").startsWith("appearance.")
                    && !c.getString("id").equals("prayer.travel-refresh")
                    && !c.getString("id").equals("prayer.zone")
                    && !c.getString("id").equals("prayer.background-location")) control(c);
        if (a.section.equals("calculation")) {
            locationUi.advancedRow();
            locationUi.confirmLocation();
        }
    }

    private void about() {
        TextView version = a.views.text(a.t("aboutVersion") + " \u2066" + AboutMiqat.version(a) + "\u2069", 18, a.ink);
        version.setTag("phone.about.version");
        a.body.addView(version);
        a.body.addView(a.views.text(a.t("aboutDeveloper"), 16, a.ink));
        a.body.addView(a.views.text(a.t("aboutSadaqah"), 14, a.muted));
        if (AboutUpdates.available(a)) {
            String title = a.t(data.actionLabel("about.check-updates"));
            a.views.button(a.body, title, () -> AboutUpdates.show(a, a.ar, new AboutUpdates.Labels(
                    title, a.t("aboutCheckingUpdates"), a.t("aboutNoUpdate"), a.t("aboutNewUpdate"),
                    a.t("aboutUpdateFailed"), a.t("aboutViewRelease"), a.t("back")),
                    this::openAboutLink)).setTag("phone.about.check-updates");
        }
        a.views.button(a.body, a.t(data.actionLabel("about.github")),
                () -> openAboutLink(AboutMiqat.FEEDBACK_URL)).setTag("phone.about.github");
        Button email = a.views.button(a.body, a.t(data.actionLabel("about.email")), () -> {});
        email.setEnabled(false);
        email.setTag("phone.about.email");
    }

    private void openAboutLink(String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException missing) {
            a.notice("aboutBrowserUnavailable");
        }
    }

    private void fasting() throws Exception {
        control(data.controls.get("fasting.eve"));
        if (settings.value("fasting.eve").equals("true"))
            control(data.controls.get("fasting.eve-time"));
        control(data.controls.get("fasting.suhoor"));
        if (settings.value("fasting.suhoor").equals("true"))
            control(data.controls.get("fasting.suhoor-minutes"));
        control(data.controls.get("fasting.iftar"));
        a.body.addView(a.views.heading(a.t("phone_fasting_days")));
        for (String id : new String[] {"ramadan", "ashura", "arafah", "white-days", "monday-thursday"})
            control(data.controls.get("fasting." + id));
        if (!PhoneFasting.preferences(settings).getEnabled()) return;
        if (!settings.locationReady)
            a.views.button(a.body, a.t("phone_location_needed"), () -> open("calculation"));
        if (!PhoneAlarms.notifications(a))
            a.views.button(a.body, a.t("phone_permission_notifications"), this::requestNotifications);
        else if (!PhoneAlarms.channel(a, PhoneAlarms.FASTING))
            a.views.button(a.body, a.t("phone_channel_settings"), () -> system(
                    new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName())
                            .putExtra(Settings.EXTRA_CHANNEL_ID, PhoneAlarms.FASTING)));
        if (!PhoneAlarms.exact(a))
            a.views.button(a.body, a.t("phone_permission_exact"), this::requestExact);
    }

    private void prayerRows() throws Exception {
        a.body.addView(a.views.heading(a.t("phone_prayer_settings")));
        LinearLayout group = a.views.navigationGroup(a.body);
        for (int i = 0; i < 6; i++)
            if (i != 1) {
                final String id = PhoneStore.PRAYERS[i];
                String mode = settings.value("call." + id + ".mode");
                String summary =
                        value("call." + id + ".mode", mode.equals("vibration") ? "silent" : mode)
                                + " · "
                                + value(
                                        "call." + id + ".weekdays",
                                        settings.value("call." + id + ".weekdays"))
                                + (settings.vibrate(i) ? " · " + a.t("phone_vibrate") : "");
                a.views
                        .navigationRow(
                                group,
                                id,
                                data.prayerName(i, a.ar),
                                settings.prayerEnabled(i) ? summary : a.t("off"),
                                () -> open(id))
                        .setTag("phone.prayer-settings." + id);
            }
    }

    private void audio() throws Exception {
        prayerRows();
        a.views.navigationRow(
                a.body, "bell", a.t("phone_default_audio_title"), "", () -> open("default-audio"));
        control(data.controls.get("reminders.alarm-mode"));
        a.body.addView(a.views.text(a.t("phone_alarm_sound_hint"), 12, a.muted));
        control(data.controls.get("reminders.full-screen"));
        control(data.controls.get("reminders.volume-keys"));
        if (settings.value("reminders.full-screen").equals("true")
                && !PhoneAlarms.fullScreenAllowed(a))
            a.views.navigationRow(
                    a.body,
                    "gear",
                    a.t("phone_full_screen_access"),
                    "",
                    () ->
                            system(
                                    new Intent(
                                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                            android.net.Uri.parse(
                                                    "package:" + a.getPackageName()))));
        a.views.navigationRow(
                a.body,
                "gear",
                a.t("phone_permissions"),
                a.t(PhoneAlarms.status(a, settings)),
                () -> open("permissions"));
        PhoneTestUi.render(a, a.body);
        if (settings.value("quiet.enabled").equals("true"))
            a.views.navigationRow(
                    a.body,
                    "clock",
                    a.t("phone_legacy_quiet"),
                    a.t("phone_legacy_quiet_summary"),
                    () -> open("legacy-quiet"));
    }

    private void prayerEditor() throws Exception {
        int prayer = Arrays.asList(PhoneStore.PRAYERS).indexOf(a.section);
        String base = "call." + a.section + ".";
        LinearLayout heading = a.views.horizontal(a.body);
        ImageView glyph = new ImageView(a);
        glyph.setImageDrawable(Icons.get(a, a.section, a.accent));
        heading.addView(glyph, new LinearLayout.LayoutParams(a.views.dp(28), a.views.dp(28)));
        Switch enabled =
                a.views.toggle(
                        heading,
                        data.prayerName(prayer, a.ar),
                        settings.prayerEnabled(prayer),
                        on -> {
                            PhonePlayback.stopPreview(a);
                            Map<String, String> changes = new LinkedHashMap<>();
                            changes.put(base + "enabled", Boolean.toString(on));
                            if (on && settings.value(base + "mode").equals("off"))
                                changes.put(base + "mode", "adhan");
                            save(changes, false);
                        });
        a.views.flex(enabled);
        enabled.setTag("phone." + base + "enabled.edit");
        int start = a.body.getChildCount();
        for (String suffix : new String[] {"mode", "vibrate", "weekdays"})
            control(data.controls.get(base + suffix));
        a.views
                .navigationRow(
                        a.body,
                        "clock",
                        a.t("phone_day_overrides"),
                        "",
                        () -> open("days:" + a.section))
                .setTag("phone.day-overrides");
        for (String suffix :
                new String[] {
                    "offset",
                    "before",
                    "after",
                    "voice",
                    "default-audio",
                    "volume",
                    "length-percent"
                }) {
            int first = a.body.getChildCount();
            control(data.controls.get(base + suffix));
            if (Arrays.asList("voice", "default-audio", "volume", "length-percent").contains(suffix)
                    && !settings.value(base + "mode").equals("adhan"))
                for (int i = first; i < a.body.getChildCount(); i++) {
                    a.views.disableContent(a.body.getChildAt(i));
                    a.body.getChildAt(i).setAlpha(.4f);
                }
        }
        refreshPrayerTimePreviews(base + "offset");
        if (!settings.prayerEnabled(prayer)) {
            for (int i = start; i < a.body.getChildCount(); i++) {
                a.views.disableContent(a.body.getChildAt(i));
                a.body.getChildAt(i).setAlpha(.38f);
            }
        }
    }

    String sectionTitle() {
        if (a.section.equals("about")) return a.t(data.actionLabel("about.open"));
        if (a.section.startsWith("days:") || a.section.startsWith("day:"))
            return a.t("phone_day_overrides");
        if (a.section.equals("default-audio")) return a.t("phone_default_audio_title");
        if (a.section.equals("legacy-quiet")) return a.t("phone_legacy_quiet");
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
        if (value.equals("custom")) {
            if (id.endsWith(".voice")) {
                try { return PhoneAudio.name(a, settings, id); }
                catch (Exception unavailable) { return a.t("phone_custom"); }
            }
            return a.t("phone_custom");
        }
        if (id.equals("quiet.mode")) return a.t("phone_" + value);
        if (id.equals("quiet.start") || id.equals("quiet.end") || id.equals("fasting.eve-time")) {
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
        if (id.endsWith(".voice")) return data.voiceName(value, a.ar);
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
        if (id.equals("prayer.background-location")) {
            locationUi.background.control(a.body);
            return;
        }
        if (id.endsWith(".length-percent")) {
            sound.length(id);
            return;
        }
        if (c.optString("editor").equals("weekdays")) {
            weekdayButtons(id, saved, label);
            return;
        }
        if (id.startsWith("call.") && id.endsWith(".mode")) {
            a.body.addView(a.views.text(label, 14, a.ink));
            java.util.List<Choices.Option> choices = new ArrayList<>();
            for (String mode : new String[] {"adhan", "notification", "silent"})
                choices.add(new Choices.Option(mode, a.t("phone_" + mode)));
            String selected =
                    saved.equals("vibration") ? "silent" : saved.equals("off") ? "adhan" : saved;
            a.views.segmentedChoices(
                    a.body,
                    "phone." + id + ".choices",
                    choices,
                    selected,
                    v -> {
                        Map<String, String> changes = new LinkedHashMap<>();
                        changes.put(id, v);
                        if (saved.equals("vibration"))
                            changes.put(id.replace(".mode", ".vibrate"), "true");
                        save(changes, false);
                    });
            return;
        }
        SettingSpec spec = data.spec(c);
        LinearLayout row = a.views.column();
        row.setPadding(a.views.dp(4), a.views.dp(2), a.views.dp(4), a.views.dp(2));
        row.setMinimumHeight(a.views.dp(52));

        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.bottomMargin = a.views.dp(2);
        a.body.addView(row, size);
        row.setTag("phone." + id);
        if (c.getString("kind").equals("toggle")) {
            Switch toggle =
                    a.views.toggle(
                            row,
                            label,
                            saved.equals("true")
                                    || id.startsWith("call.")
                                            && id.endsWith(".vibrate")
                                            && settings.value(id.replace(".vibrate", ".mode"))
                                                    .equals("vibration"),
                            on -> {
                                if (id.startsWith("call.")
                                        && id.endsWith(".vibrate")
                                        && settings.value(id.replace(".vibrate", ".mode"))
                                                .equals("vibration")) {
                                    Map<String, String> changes = new LinkedHashMap<>();
                                    changes.put(id, Boolean.toString(on));
                                    changes.put(id.replace(".vibrate", ".mode"), "silent");
                                    save(changes, false);
                                    return;
                                }
                                if (id.equals("reminders.enabled")
                                        && on
                                        && !settings.locationReady) {
                                    a.notice("phone_location_needed");
                                    if (!a.page.equals("onboarding")) open("calculation");
                                    return;
                                }
                                save(id, Boolean.toString(on));
                            });
            toggle.setTag("phone." + id + ".edit");
            return;
        }
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = a.views.text(label, 14, a.ink);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (c.getString("kind").equals("number")) {
            if (c.optString("editor").equals("time")) {
                summary(
                        row,
                        id,
                        value(id, saved),
                        () -> {
                            int minute = Integer.parseInt(saved);
                            showEditor(
                                    new TimePickerDialog(
                                            a,
                                            (view, h, m) -> save(id, Integer.toString(h * 60 + m)),
                                            minute / 60,
                                            minute % 60,
                                            settings.value("display.clock").equals("24")));
                        });
                return;
            }
            if (prayerTimePreview(id)) {
                LinearLayout labels = a.views.column();
                row.removeView(title);
                row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
                labels.addView(title, new LinearLayout.LayoutParams(-1, -2));
                TextView preview = a.views.text("", 13, a.accent);
                preview.setTag("phone." + id + ".preview");
                labels.addView(preview);
                if (id.equals("prayer.sunrise-offset")) refreshPrayerTimePreviews(id);
            }
            number(row, spec, saved, label);
            if (id.startsWith("call.")
                    && id.endsWith(".volume")
                    && settings.value(id.substring(0, id.lastIndexOf('.') + 1) + "default-audio")
                            .equals("true")) {
                a.views.disableContent(row);
                row.setAlpha(.4f);
            }
            return;
        }
        java.util.List<String> options = data.options(c);
        if (options.size() <= 5 && !id.endsWith(".voice")) {
            java.util.List<Choices.Option> choices = new ArrayList<>();
            for (String option : options)
                choices.add(new Choices.Option(option, value(id, option)));
            row.setOrientation(LinearLayout.VERTICAL);
            title.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            if (id.equals("prayer.high-latitude"))
                a.views.verticalChoices(
                        row,
                        "phone." + id + ".choices",
                        choices,
                        saved,
                        choice -> save(id, choice));
            else
                a.views.segmentedChoices(
                        row,
                        "phone." + id + ".choices",
                        choices,
                        saved,
                        choice -> save(id, choice));
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
    }

    void summary(LinearLayout row, String id, String value, Runnable edit) {
        TextView selected = a.views.text(value + "  ›", 14, a.muted);
        selected.setPadding(0, 0, 0, 0);
        selected.setMinHeight(a.views.dp(48));
        selected.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        selected.setMaxLines(2);
        selected.setEllipsize(android.text.TextUtils.TruncateAt.END);
        selected.setTag("phone." + id + ".edit");
        selected.setContentDescription(
                a.t(data.controls.get(id).optString("label")) + ", " + value);
        selected.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf((a.accent & 0xffffff) | 0x22000000), null, null));
        selected.setFocusable(true);
        selected.setOnClickListener(v -> edit.run());
        row.addView(
                selected,
                new LinearLayout.LayoutParams(
                        row.getOrientation() == LinearLayout.HORIZONTAL ? 0 : -1,
                        -2,
                        row.getOrientation() == LinearLayout.HORIZONTAL ? 1 : 0));
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

    private void weekdayButtons(String id, String saved, String label) {
        a.body.addView(a.views.text(label, 14, a.ink));
        a.body.setClipChildren(false);
        a.body.setClipToPadding(false);
        HorizontalScrollView viewport = new HorizontalScrollView(a);
        viewport.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(-1, -2);
        space.setMargins(-a.views.dp(8), 0, -a.views.dp(8), 0);
        a.body.addView(viewport, space);
        LinearLayout track = new LinearLayout(a);
        track.setGravity(Gravity.CENTER_VERTICAL);
        track.setTag("phone." + id);
        viewport.addView(track);
        int mask = Integer.parseInt(saved);
        String[] names = new java.text.DateFormatSymbols(locale()).getWeekdays();
        for (int n = 0; n < 7; n++) {
            final int day = (n + 1) % 7;
            boolean on = (mask & (1 << day)) != 0;
            String name = names[day + 1];
            Button button =
                    a.views.button(
                            track,
                            a.t("phone_weekday_letters").split(",")[day],
                            () -> save(id, Integer.toString(mask ^ (1 << day))));
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(a.views.dp(48), a.views.dp(48));
            cell.setMargins(0, a.views.dp(3), 0, a.views.dp(3));
            button.setLayoutParams(cell);
            button.setPadding(0, 0, 0, 0);
            button.setTextSize(14);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setContentDescription(name);
            button.setSelected(on);
            button.setTag("phone." + id + "." + day);
            button.setBackground(new InsetDrawable(a.views.background(on ? a.accent : a.surface, 28), a.views.dp(6)));
            button.setPadding(0, 0, 0, 0);
            button.setTextColor(on ? a.ground : a.ink);
            button.setAccessibilityDelegate(
                    new View.AccessibilityDelegate() {
                        @Override
                        public void onInitializeAccessibilityNodeInfo(
                                View v, android.view.accessibility.AccessibilityNodeInfo info) {
                            super.onInitializeAccessibilityNodeInfo(v, info);
                            info.setCheckable(true);
                            info.setChecked(on);
                        }
                    });
        }
    }

    void number(LinearLayout row, SettingSpec spec, String saved, String label) {
        String id = spec.getId();
        boolean editing = pending.equals(id);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(a.views.dp(12), a.views.dp(6), a.views.dp(12), a.views.dp(6));
        row.setBackground(a.views.background(a.surface, 18));
        LinearLayout track = a.views.horizontal(row);
        track.setLayoutParams(new LinearLayout.LayoutParams(a.views.dp(144), -2));
        track.setBackground(a.views.background(a.ground, 14));
        EditText input = new EditText(a);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN
                | android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        input.setSingleLine(true);
        input.setTextColor(a.ink);
        input.setTextSize(14);
        input.setTypeface(a.regular);
        input.setGravity(Gravity.CENTER);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(editing ? draft : saved);
        input.setTextDirection(View.TEXT_DIRECTION_LTR);
        input.setContentDescription(label);
        input.setTag("phone." + id + ".input");
        track.setTag("phone." + id + ".edit");
        track.setOnClickListener(v -> {
            if (editing) return;
            pending = id;
            draft = saved;
            a.render();
        });
        input.setFocusableInTouchMode(editing);
        input.setFocusable(editing);
        input.setMinWidth(0);
        input.setPadding(0, 0, 0, 0);
        if (!editing) input.setBackground(null);
        input.setOnClickListener(v -> {
            if (!editing) {
                pending = id;
                draft = saved;
                a.render();
                View next = a.body.findViewWithTag("phone." + id + ".input");
                if (next != null) next.requestFocus();
            }
        });
        for (int direction : new int[] {-1, 1}) {
            if (direction == 1)
                track.addView(input, new LinearLayout.LayoutParams(0, a.views.dp(48), 1));
            Button button =
                    a.views.button(
                            track,
                            direction < 0 ? "−" : "+",
                            () -> {
                                try {
                                    save(id, spec.adjust(numberValue(spec, input.getText().toString()), direction));
                                } catch (Exception invalid) {
                                    input.setError(a.t("invalid"));
                                }
                            });
            a.views.square(button);
            button.setLayoutParams(new LinearLayout.LayoutParams(a.views.dp(48), a.views.dp(48)));
            button.setBackground(new RippleDrawable(
                    android.content.res.ColorStateList.valueOf((a.accent & 0xffffff) | 0x33000000),
                    a.views.background(Color.TRANSPARENT, 14), null));
            button.setTag("phone." + id + (direction < 0 ? ".minus" : ".plus"));
            button.setContentDescription(
                    a.t(direction < 0 ? "decrease" : "increase") + " " + label);
        }
        if (!editing) return;
        input.addTextChangedListener(MainActivity.watcher(text -> {
            pending = id;
            draft = text;
            if (!input.isEnabled()) return;
            try {
                String next = numberValue(spec, text);
                if (!saveTypedNumber(id, next)) {
                    input.setError(a.t("phone_save_failed"));
                    return;
                }
                input.setError(null);
                if (prayerTimePreview(id)) refreshPrayerTimePreviews(id);
            } catch (Exception invalid) {
                input.setError(text.isEmpty() || normalize(text).equals("-") ? null : a.t("invalid"));
            }
        }));
        input.setOnEditorActionListener((field, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
            try {
                String next = numberValue(spec, input.getText().toString());
                if (!saveTypedNumber(id, next)) {
                    input.setError(a.t("phone_save_failed"));
                    return true;
                }
                pending = "";
                a.render();
                editorFocus(id);
            } catch (Exception invalid) { input.setError(a.t("invalid")); }
            return true;
        });
    }

    private boolean saveTypedNumber(String id, String next) {
        return settings.value(id).equals(next)
                || save(Collections.singletonMap(id, next), id.equals("display.size"), id.equals("display.size"));
    }

    private static boolean prayerTimePreview(String id) {
        return id.equals("prayer.sunrise-offset") || id.startsWith("call.")
                && (id.endsWith(".offset") || id.endsWith(".before") || id.endsWith(".after"));
    }

    private void refreshPrayerTimePreviews(String id) {
        boolean sunrise = id.equals("prayer.sunrise-offset");
        int prayer = sunrise ? 1
                : Arrays.asList(PhoneStore.PRAYERS).indexOf(id.substring(5, id.lastIndexOf('.')));
        String base = "call." + PhoneStore.PRAYERS[prayer] + ".";
        double[] times = settings.locationReady ? settings.day(System.currentTimeMillis(), 0).times : null;
        for (String key : sunrise ? new String[] {id}
                : new String[] {base + "offset", base + "before", base + "after"}) {
            TextView preview = a.body.findViewWithTag("phone." + key + ".preview");
            if (preview == null) continue;
            int minutes = key.endsWith(".before") ? -settings.before[prayer]
                    : key.endsWith(".after") ? settings.after[prayer] : 0;
            boolean isOffset = key.endsWith("offset");
            preview.setText(times == null || !isOffset && minutes == 0
                    ? "—" : time(times[prayer] + minutes * 60_000L));
        }
    }

    private static String numberValue(SettingSpec spec, String text) {
        String next = normalize(text);
        spec.validate(next);
        int value = Integer.parseInt(next);
        if ((value - spec.getMinimum()) % spec.getStep() != 0) throw new IllegalArgumentException();
        return Integer.toString(value);
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

    void picker(
            String title,
            java.util.List<Choices.Option> options,
            java.util.function.Consumer<String> picked) {
        picker(title, options, "", picked);
    }

    void picker(
            String title,
            java.util.List<Choices.Option> options,
            String selected,
            java.util.function.Consumer<String> picked) {
        if (choiceDialog != null) choiceDialog.dismiss();
        LinearLayout layout = a.views.column();
        layout.setPadding(a.views.dp(16), a.views.dp(8), a.views.dp(16), a.views.dp(8));
        layout.setLayoutDirection(a.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        EditText search = a.views.searchField();
        search.setTag("phone.choice.search");
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
        a.views.filterChoices(search, list, adapter, () -> selected);
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
        if (id.endsWith(".length-percent"))
            changes.put(id.replace(".length-percent", ".duration"), "0");
        if (id.equals("prayer.zone")) {
            changes.put("prayer.location-ready", "false");
            changes.put("prayer.background-location", "false");
            changes.put("prayer.travel-refresh", "false");
        }
        if (!save(changes, id.equals("display.size"))) return;
        if (id.equals("reminders.enabled")
                && !PhoneStore.preferences(a)
                        .edit()
                        .putBoolean(
                                "onboarding-enable-with-permission", Boolean.parseBoolean(value))
                        .commit()) a.notice("phone_save_failed");
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
                && !PhoneAlarms.notifications(a)) open("permissions");
    }

    boolean save(Map<String, String> changes, boolean recreate) {
        return save(changes, recreate, true);
    }

    private boolean save(Map<String, String> changes, boolean recreate, boolean redraw) {
        try {
            synchronized (PhoneStore.LOCK) {
                if (changes.keySet().stream().anyMatch(id -> id.startsWith("prayer."))
                        && !PhoneBackgroundLocation.cancel(a))
                    throw new java.io.IOException("Could not cancel earlier location request");
                PhoneStore next = PhoneStore.read(a).changed(changes);
                next.save(a);
                settings = next;
            }
            if (redraw) pending = "";
            PhoneAlarms.rebuild(a);
            PhoneBackgroundLocation.sync(a);
            if (redraw) {
                if (recreate) a.recreate();
                else a.render();
            }
            return true;
        } catch (Exception failure) {
            a.notice("phone_save_failed");
            return false;
        }
    }

    void permissions() {
        try {
            control(data.controls.get("reminders.enabled"));
        } catch (Exception invalid) {
            a.notice("invalid");
        }
        a.body.addView(a.views.text(a.t(PhoneAlarms.status(a, settings)), 18, a.ink));
        if (!PhoneAlarms.notifications(a))
            a.views.button(
                    a.body, a.t("phone_permission_notifications"), this::requestNotifications);
        if (!PhoneAlarms.exact(a))
            a.views.button(a.body, a.t("phone_permission_exact"), this::requestExact);
        a.views.button(
                a.body,
                a.t("phone_channel_settings"),
                () ->
                        system(
                                new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName())));
        a.body.addView(a.views.heading(a.t("phone_power_title")));
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

    void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED)
            a.requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 75);
        else
            system(
                    new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
    }

    void requestExact() {
        if (Build.VERSION.SDK_INT < 31) return;
        system(
                new Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:" + a.getPackageName())));
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

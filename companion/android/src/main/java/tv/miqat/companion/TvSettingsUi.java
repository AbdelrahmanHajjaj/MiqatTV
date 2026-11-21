package tv.miqat.companion;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import org.json.*;

import tv.miqat.companion.core.*;

import java.util.*;

/** TV setting snapshots and drafts; never edits the independent phone preference store. */
final class TvSettingsUi {
    private final MainActivity a;
    SettingsSnapshot snapshot;
    SettingsDraft appearance;
    private String pendingId = "", pendingValue = "";
    private Map<String, String> pendingBase;
    AlertDialog editor;
    LinearLayout content;

    TvSettingsUi(MainActivity activity) {
        a = activity;
    }

    boolean hasPending() {
        return !pendingId.isEmpty();
    }

    void resumeEdit() {
        if (!pendingId.isEmpty() && editable())
            a.getWindow().getDecorView().post(() -> edit(pendingId));
    }

    void restore(Bundle state) {
        if (state.containsKey("snapshot"))
            snapshot = new SettingsSnapshot(MainActivity.decode(state.getString("snapshot")));
        if (state.containsKey("draft-base")) {
            appearance =
                    new SettingsDraft(
                            new SettingsSnapshot(
                                    MainActivity.decode(state.getString("draft-base"))));
            for (Map.Entry<String, String> e :
                    MainActivity.decode(state.getString("draft-edits")).entrySet())
                appearance.set(spec(e.getKey()), e.getValue());
        }
        pendingId = state.getString("pending-id", "");
        pendingValue = state.getString("pending-value", "");
        if (state.containsKey("pending-base"))
            pendingBase = MainActivity.decode(state.getString("pending-base"));
    }

    void saveState(Bundle state) {
        if (snapshot != null)
            state.putString("snapshot", MainActivity.encode(snapshot.getValues()));
        if (appearance != null) {
            state.putString("draft-base", MainActivity.encode(appearance.getBase().getValues()));
            state.putString("draft-edits", MainActivity.encode(appearance.getChanges()));
        }
        if (!pendingId.isEmpty()) {
            state.putString("pending-id", pendingId);
            state.putString("pending-value", pendingValue);
            state.putString("pending-base", MainActivity.encode(pendingBase));
        }
    }

    SettingSpec spec(String id) {
        for (SettingSpec s : a.backend().getSpecs()) if (s.getId().equals(id)) return s;
        throw new IllegalArgumentException("Unsupported setting: " + id);
    }

    boolean editable() {
        return !a.networkBusy
                && a.online()
                && snapshot != null
                && (a.real() == null || !a.real().needsRefresh);
    }

    String cacheKey() {
        return "cache-" + (a.real() == null ? a.selected : a.real().key);
    }

    boolean isPending(String id) {
        return id.equals(pendingId) && pendingBase != null;
    }

    void readSettings() {
        if (a.real() != null) {
            LanTv tv = a.real();
            a.networkMessage = "reading_settings";
            a.network(
                    () -> {
                        if (tv.connect()) tv.read();
                        return tv.cached();
                    },
                    actual -> {
                        a.cacheReal(tv);
                        if (a.real() == tv) {
                            snapshot = actual.getValues().isEmpty() ? null : actual;
                            if (snapshot != null && !tv.needsRefresh) {
                                if (appearance != null
                                        && appearance.getChanges().entrySet().stream()
                                                .allMatch(edit ->
                                                        edit.getValue()
                                                                .equals(
                                                                        snapshot.getValues()
                                                                                .get(
                                                                                        edit
                                                                                                .getKey()))))
                                    appearance = null;
                                if (!pendingId.isEmpty()
                                        && pendingValue.equals(snapshot.getValues().get(pendingId)))
                                    clearPending();
                            }
                            a.render();
                        }
                    },
                    () -> {
                        a.cacheReal(tv);
                        if (a.real() == tv) {
                            snapshot = tv.cached().getValues().isEmpty() ? null : tv.cached();
                            a.render();
                            a.notice("read_failed");
                        }
                    });
            return;
        }
        if (a.tv().connect()) {
            snapshot = a.tv().read();
            a.prefs()
                    .edit()
                    .putString("cache-" + a.selected, MainActivity.encode(snapshot.getValues()))
                    .apply();
        } else {
            String cache = a.prefs().getString("cache-" + a.selected, null);
            snapshot = cache == null ? null : new SettingsSnapshot(MainActivity.decode(cache));
        }
        if (appearance != null && appearance.getChanges().isEmpty()) appearance = null;
    }

    void renderSettings() {
        if (!a.online() || a.real() != null && a.real().needsRefresh) {
            a.body.addView(
                    a.views.text(a.t(snapshot == null ? "no_cache" : "stale"), 14, a.accent));
            a.views.bindAction(
                    a.views.button(
                            a.body,
                            a.t(a.real() == null ? "reconnect" : "refresh"),
                            () -> {
                                if (a.real() == null) {
                                    a.tv().setOnline(true);
                                    a.prefs()
                                            .edit()
                                            .putBoolean("online-" + a.selected, true)
                                            .apply();
                                }
                                readSettings();
                                a.render();
                            }),
                    "connect");
        }
        if (snapshot == null) return;
        if (a.section.equals("appearance")) {
            if (appearance == null) appearance = new SettingsDraft(snapshot);
            a.body.addView(a.views.text(a.t("preview"), 13, a.muted));
            HomePreview preview =
                    new HomePreview(
                            a,
                            a.catalog,
                            appearance.getValues(),
                            a.ar,
                            a.real() == null
                                    ? a.catalog.data.optString("demoSystemLanguage")
                                    : a.real().systemLanguage);
            preview.setTag("appearance-preview");
            a.body.addView(preview, new LinearLayout.LayoutParams(-1, -2));
            if (!appearance.getChanges().isEmpty())
                a.body.addView(a.views.text(a.t("unsaved"), 13, a.accent));
        }
        EditText search = new EditText(a);
        search.setImeOptions(
                search.getImeOptions()
                        | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);
        search.setSingleLine(true);
        search.setHint(a.t("search"));
        search.setText(a.query);
        search.setTextSize(15);
        search.setTextColor(a.ink);
        search.setHintTextColor(a.muted);
        search.setPadding(a.views.dp(18), a.views.dp(12), a.views.dp(18), a.views.dp(12));
        search.setBackground(a.views.background(a.surface, 28));
        android.graphics.drawable.Drawable searchIcon =
                a.getDrawable(android.R.drawable.ic_menu_search).mutate();
        searchIcon.setTint(a.muted);
        searchIcon.setBounds(0, 0, a.views.dp(22), a.views.dp(22));
        search.setCompoundDrawablesRelative(searchIcon, null, null, null);
        search.setCompoundDrawablePadding(a.views.dp(12));
        LinearLayout.LayoutParams searchLayout = new LinearLayout.LayoutParams(-1, a.views.dp(56));
        searchLayout.bottomMargin = a.views.dp(12);
        a.body.addView(search, searchLayout);
        content = a.views.column();
        a.body.addView(content);
        populateSettings();
        search.addTextChangedListener(
                MainActivity.watcher(value -> {
                    a.query = value;
                    populateSettings();
                }));
    }

    void populateSettings() {
        content.removeAllViews();
        if (a.section.isEmpty() && a.query.isEmpty()) {
            for (String id :
                    new String[] {
                        "prayer",
                        "reminders",
                        "prayer-day",
                        "offsets",
                        "appearance",
                        "display",
                        "radio",
                        "favourites",
                        "device",
                        "network",
                        "tests"
                    }) {
                a.views
                        .navigationRow(
                                content,
                                a.views.sectionIcon(id),
                                a.sectionTitle(id),
                                "",
                                () -> openSection(id))
                        .setTag("section." + id);
            }
            return;
        }
        JSONArray all = a.catalog.controls(a.profile());
        int count = 0;
        for (int i = 0; i < all.length(); i++) {
            JSONObject c = all.optJSONObject(i);
            String id = c.optString("id"), group = c.optString("section");
            if (!a.section.isEmpty() && !group.equals(a.section)) continue;
            if (!a.query.isEmpty()
                    && !a.title(c)
                            .toLowerCase(Locale.ROOT)
                            .contains(a.query.toLowerCase(Locale.ROOT))) continue;
            if (id.equals("appearance.apply")
                    || id.equals("appearance.cancel")
                    || id.equals("appearance.home")) continue;
            if (group.equals("remote") && !a.section.equals("remote")) continue;
            count++;
            String kind = c.optString("kind");
            if (c.optBoolean("available") && (kind.equals("action") || group.equals("tests"))) {
                LinearLayout action = a.views.navigationRow(
                        content, a.views.sectionIcon(group), a.title(c), "", () -> {
                            if (editable()) action(c);
                        });
                action.setTag(id);
                action.setEnabled(editable());
                View activate = action.getChildAt(action.getChildCount() - 1);
                activate.setTag(id + ".activate");
                activate.setEnabled(editable());
                activate.setOnClickListener(v -> {if (editable()) action(c);});
                continue;
            }
            LinearLayout row = a.views.card(content);
            row.setTag(id);
            LinearLayout top = a.views.horizontal(row);
            TextView label = a.views.text(a.title(c), 16, a.ink);
            if (c.optString("label").startsWith("prayerNames:")
                    && (id.startsWith("offsets.") || id.startsWith("prayer.adhan-"))) {
                String prayer = id.startsWith("offsets.") ? id.substring(8) : id.substring(13);
                label.setCompoundDrawablesRelative(Icons.get(a, prayer, a.accent), null, null, null);
                label.setCompoundDrawablePadding(a.views.dp(10));
            }
            top.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            if (!c.optBoolean("available")) {
                row.addView(a.views.text(a.t("unavailable"), 12, a.muted));
                continue;
            }
            String value = values().get(id);
            if (kind.equals("toggle")) {
                Switch toggle = new Switch(a);
                toggle.setMinHeight(a.views.dp(48));
                toggle.setTextColor(a.accent);
                toggle.setTypeface(a.regular);
                toggle.setThumbTintList(
                        new ColorStateList(
                                new int[][] {
                                    new int[] {android.R.attr.state_checked}, new int[] {}
                                },
                                new int[] {a.accent, a.muted}));
                toggle.setTrackTintList(
                        ColorStateList.valueOf((a.muted & 0x00ffffff) | 0x55000000));
                toggle.setChecked(value.equals("true"));
                toggle.setEnabled(editable());
                toggle.setTag(id + ".edit");
                toggle.setContentDescription(a.title(c) + ", " + display(id, value));
                top.addView(toggle, new LinearLayout.LayoutParams(-2, a.views.dp(48)));
                toggle.setOnClickListener(v -> {
                    String next = String.valueOf(toggle.isChecked());
                    toggle.setChecked(value.equals("true"));
                    if (c.optString("save").equals("appearance")) {
                        if (appearance == null) {
                            openSection("appearance");
                            return;
                        }
                        appearance.set(spec(id), next);
                        a.render();
                    } else
                        save(snapshot.getValues(), Collections.singletonMap(id, next), false);
                });
                continue;
            }
            if (kind.equals("choice")) {
                Choices.add(a, row, c, spec(id), isPending(id) ? pendingValue : value);
                continue;
            }
            if (kind.equals("number")
                    && tv.miqat.core.UiContract.INSTANCE.numberEditor(id).equals("inline")) {
                numberControls(top, c, spec(id), value);
                continue;
            }
            row.addView(a.views.text(display(id, value), 14, a.accent));
            Button b =
                    a.views.iconButton(
                            top,
                            "edit",
                            a.t("edit"),
                            () -> {
                                if (c.optString("save").equals("appearance")
                                        && !a.section.equals("appearance")) {
                                    openSection("appearance");
                                    return;
                                }
                                edit(id);
                            });
            b.setContentDescription(a.title(c) + ", " + display(id, value));
            b.setEnabled(editable());
            b.setTag(id + ".edit");
        }
        if (count == 0) content.addView(a.views.text(a.t("no_results"), 15, a.muted));
    }

    void openSection(String target) {
        a.stopAudio();
        clearPending();
        if (appearance != null
                && !appearance.getChanges().isEmpty()
                && !target.equals("appearance")) {
            a.confirmDiscard(() -> {
                appearance = null;
                a.section = target;
                a.render();
            });
            return;
        }
        if (!target.equals("appearance")) appearance = null;
        a.section = target;
        a.query = "";
        a.render();
    }

    Map<String, String> values() {
        return appearance != null && a.section.equals("appearance")
                ? appearance.getValues()
                : snapshot.getValues();
    }

    String display(String id, String value) {
        if (value == null || value.isEmpty()) return a.t("empty");
        if (id.equals("prayer.facing") && value.equals("-1")) return a.t("not_set");
        SettingSpec spec = spec(id);
        if (spec.getKind().equals("toggle")) return a.t(value.equals("true") ? "on" : "off");
        if (spec.getKind().equals("list")) {
            List<String> labels = new ArrayList<>();
            for (String item : value.split(","))
                labels.add(a.catalog.value(a.profile(), id, item, a.ar));
            return String.join(" · ", labels);
        }
        if (spec.getKind().equals("text")) return value.isEmpty() ? a.t("empty") : a.t("done");
        return a.catalog.value(a.profile(), id, value, a.ar);
    }

    void edit(String id) {
        if (!editable()) return;
        if (!pendingId.isEmpty() && !pendingId.equals(id)) {
            clearPending();
            a.render();
        }
        a.stopAudio();
        JSONObject c = a.catalog.control(a.profile(), id);
        SettingSpec s = spec(id);
        if (s.getKind().equals("choice")) {
            View field = a.body.findViewWithTag(id + ".dropdown");
            if (field != null) {
                field.requestFocus();
                ((AutoCompleteTextView) field).showDropDown();
            }
            return;
        }
        boolean restoring = id.equals(pendingId) && pendingBase != null;
        if (!restoring) {
            pendingId = id;
            pendingValue = values().get(id);
            pendingBase =
                    new LinkedHashMap<>(
                            appearance != null && s.getGroup().equals("appearance")
                                    ? appearance.getBase().getValues()
                                    : snapshot.getValues());
            if (id.equals("prayer.facing") && pendingValue.equals("-1")) pendingValue = "0";
        }
        if (s.getKind().equals("number")
                && tv.miqat.core.UiContract.INSTANCE.numberEditor(id).equals("inline")) {
            editNumber(c, s);
            return;
        }
        LinearLayout box = a.views.column();
        box.setTag("editor");
        box.setPadding(a.views.dp(20), a.views.dp(4), a.views.dp(20), a.views.dp(4));
        AlertDialog.Builder builder =
                a.views
                        .dialog()
                        .setTitle(a.title(c))
                        .setView(box)
                        .setNegativeButton(a.t("cancel"), (d, w) -> clearPending());
        switch (s.getKind()) {
            case "toggle":
                {
                    Switch toggle = new Switch(a);
                    toggle.setText(a.title(c));
                    toggle.setMinHeight(a.views.dp(48));
                    toggle.setChecked(pendingValue.equals("true"));
                    toggle.setOnCheckedChangeListener((v, on) -> pendingValue = String.valueOf(on));
                    box.addView(toggle);
                    break;
                }
            case "number":
                {
                    box.addView(a.views.text(s.getMinimum() + " – " + s.getMaximum(), 14, a.muted));
                    EditText input =
                            input(
                                    box,
                                    a.title(c),
                                    pendingValue,
                                    InputType.TYPE_CLASS_NUMBER
                                            | InputType.TYPE_NUMBER_FLAG_SIGNED);
                    a.views.button(box, "−", () -> adjust(s, input, -1));
                    a.views.button(box, "+", () -> adjust(s, input, 1));
                    break;
                }
            case "list":
                listEditor(box, s);
                break;
            default:
                box.addView(
                        a.views.text(
                                a.t(
                                        id.equals("appearance.custom-theme")
                                                ? "theme_format"
                                                : "redirect_format"),
                                13,
                                a.muted));
                if (id.equals("appearance.custom-theme")) themeEditor(box);
                else redirectEditor(box);
        }
        if (a.real() == null
                && !s.getGroup().equals("appearance")
                && !s.getKind().equals("text")
                && !s.getKind().equals("list"))
            a.views.button(box, a.t("simulate_conflict"), () -> simulateConflict(s));
        if (s.getKind().equals("list") || s.getKind().equals("text")) {
            ScrollView listScroll = new ScrollView(a);
            listScroll.addView(box);
            builder.setView(listScroll);
        }
        builder.setPositiveButton(a.t("done"), null);
        editor = builder.create();
        editor.setOnCancelListener(d -> clearPending());
        editor.setOnDismissListener(d -> {
            if (editor == d) {
                a.stopAudio();
                editor = null;
            }
        });
        editor.show();
        a.views.styleDialog(editor);
        a.views.bindAction(editor.getButton(-1), "save-edit");
        editor.getButton(-1).setOnClickListener(v -> commitPending());
    }

    void selectChoice(String id, String value) {
        if (!editable()) return;
        SettingSpec s = spec(id);
        s.validate(value);
        a.stopAudio();
        if (s.getGroup().equals("appearance") && !a.section.equals("appearance")) {
            openSection("appearance");
            return;
        }
        if (!isPending(id)) {
            clearPending();
            pendingId = id;
            pendingBase =
                    new LinkedHashMap<>(
                            s.getGroup().equals("appearance")
                                    ? appearance.getBase().getValues()
                                    : snapshot.getValues());
        }
        pendingValue = value;
        if (id.equals("reminders.voice") || id.equals("reminders.fajr-voice")) {
            a.render();
            return;
        }
        commitPending();
    }

    void commitPending() {
        if (pendingId.isEmpty() || !editable()) return;
        String id = pendingId;
        SettingSpec s = spec(id);
        try {
            validateSpecial(s, pendingValue);
            s.validate(pendingValue);
        } catch (Exception e) {
            a.notice("invalid");
            return;
        }
        if (s.getKind().equals("number"))
            pendingValue = String.valueOf(Integer.parseInt(pendingValue));
        if (s.getGroup().equals("appearance")) {
            appearance.set(s, pendingValue);
            if (id.equals("appearance.custom-theme") && !pendingValue.isEmpty())
                appearance.set(spec("appearance.theme"), "custom");
            if (editor != null) editor.dismiss();
            clearPending();
            a.render();
        } else {
            Map<String, String> changes = new LinkedHashMap<>();
            changes.put(id, pendingValue);
            Map<String, String> base = new LinkedHashMap<>(pendingBase);
            if (editor != null) editor.dismiss();
            save(base, changes, false);
        }
    }

    EditText input(LinearLayout parent, String label, String value, int type) {
        EditText input = new EditText(a);
        input.setImeOptions(
                input.getImeOptions() | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);

        input.setId(View.generateViewId());
        input.setInputType(type);
        input.setText(value);
        input.setHint(label);
        TextView labelView = a.views.text(label, 14, a.muted);
        labelView.setLabelFor(input.getId());
        parent.addView(labelView);
        parent.addView(input);
        input.addTextChangedListener(MainActivity.watcher(v -> pendingValue = v));
        return input;
    }

    void adjust(SettingSpec s, EditText input, int direction) {
        try {
            input.setText(s.adjust(input.getText().toString(), direction));
        } catch (IllegalArgumentException e) {
            input.setError(a.t("invalid"));
        }
    }

    void clearPending() {
        pendingId = "";
        pendingValue = "";
        pendingBase = null;
    }

    void editNumber(JSONObject c, SettingSpec spec) {
        String id = spec.getId();
        LinearLayout row = a.body.findViewWithTag(id);
        row.removeAllViews();
        LinearLayout top = a.views.horizontal(row);
        TextView title = a.views.text(a.title(c), 16, a.ink);
        if (c.optString("label").startsWith("prayerNames:")) {
            title.setCompoundDrawablesRelative(
                    Icons.get(a, id.substring(8), a.accent), null, null, null);
            title.setCompoundDrawablePadding(a.views.dp(10));
        }
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout track = a.views.horizontal(top);
        track.setTag(id + ".number-editor");
        track.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.5f));
        EditText input = new EditText(a);
        input.setImeOptions(
                input.getImeOptions() | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);

        input.setId(View.generateViewId());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(pendingValue);
        input.setGravity(Gravity.CENTER);
        input.setTextDirection(View.TEXT_DIRECTION_LTR);
        input.setTag(id + ".number-input");
        title.setLabelFor(input.getId());
        input.addTextChangedListener(MainActivity.watcher(value -> pendingValue = value));
        Button minus = a.views.button(track, "−", () -> adjust(spec, input, -1));
        a.views.square(minus);
        minus.setContentDescription(a.t("decrease") + " " + a.title(c));
        track.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        Button plus = a.views.button(track, "+", () -> adjust(spec, input, 1));
        a.views.square(plus);
        plus.setContentDescription(a.t("increase") + " " + a.title(c));
        a.actionHost.removeAllViews();
        LinearLayout actions = a.views.actionBar(a.actionHost);
        Button cancel =
                a.views.secondaryAction(
                        a.views.secondaryActions(actions),
                        a.t("cancel"),
                        () -> {
                            clearPending();
                            a.render();
                        });
        cancel.setTag(id + ".cancel");
        Button done =
                a.views.bindAction(
                        a.views.primaryAction(
                                actions,
                                a.t("done"),
                                () -> {
                                    try {
                                        spec.validate(pendingValue);
                                    } catch (IllegalArgumentException e) {
                                        input.setError(a.t("invalid"));
                                        return;
                                    }
                                    String next = String.valueOf(Integer.parseInt(pendingValue));
                                    if (spec.getGroup().equals("appearance")) {
                                        appearance.set(spec, next);
                                        clearPending();
                                        a.render();
                                    } else
                                        save(
                                                new LinkedHashMap<>(pendingBase),
                                                Collections.singletonMap(id, next),
                                                false);
                                }),
                        "save-edit");
        done.setTag(id + ".save");
        input.requestFocus();
    }

    void numberControls(LinearLayout row, JSONObject c, SettingSpec s, String value) {
        String id = s.getId();
        LinearLayout track = a.views.horizontal(row);
        track.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.5f));
        Button minus = a.views.button(track, "−", () -> stepNumber(id, -1));
        a.views.square(minus);
        minus.setEnabled(editable() && Integer.parseInt(value) > s.getMinimum());
        minus.setTag(id + ".minus");
        minus.setContentDescription(a.t("decrease") + " " + a.title(c));
        Button amount = a.views.button(track, display(id, value), () -> edit(id));
        a.views.flex(amount);
        amount.setTag(id + ".edit");
        amount.setEnabled(editable());
        amount.setContentDescription(a.title(c) + ", " + display(id, value));
        Button plus = a.views.button(track, "+", () -> stepNumber(id, 1));
        a.views.square(plus);
        plus.setEnabled(editable() && Integer.parseInt(value) < s.getMaximum());
        plus.setTag(id + ".plus");
        plus.setContentDescription(a.t("increase") + " " + a.title(c));
    }

    void stepNumber(String id, int direction) {
        if (!editable()) return;
        SettingSpec s = spec(id);
        if (s.getGroup().equals("appearance")) {
            if (appearance == null) {
                openSection("appearance");
                return;
            }
            appearance.set(s, s.adjust(values().get(id), direction));
            a.render();
        } else {
            edit(id);
            EditText field = a.body.findViewWithTag(id + ".number-input");
            adjust(s, field, direction);
        }
    }

    void listEditor(LinearLayout parent, SettingSpec spec) {
        LinearLayout items = a.views.column();
        parent.addView(items);
        Runnable[] refresh = new Runnable[1];
        refresh[0] =
                () -> {
                    items.removeAllViews();
                    List<String> order = new ArrayList<>();
                    if (!pendingValue.isEmpty())
                        order.addAll(Arrays.asList(pendingValue.split(",")));
                    for (int i = 0; i < order.size(); i++) {
                        final int index = i;
                        String item = order.get(i);
                        LinearLayout row = a.views.horizontal(a.views.card(items));
                        row.addView(
                                a.views.text(
                                        a.catalog.value(a.profile(), spec.getId(), item, a.ar),
                                        15,
                                        a.ink), new LinearLayout.LayoutParams(0, -2, 1));
                        LinearLayout actions = a.views.horizontal(row);
                        actions.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
                        Button earlier =
                                a.views.button(
                                        actions,
                                        "↑",
                                        () -> {
                                            Collections.swap(order, index, index - 1);
                                            pendingValue = String.join(",", order);
                                            refresh[0].run();
                                        });
                        a.views.square(earlier);
                        earlier.setEnabled(index > 0);
                        earlier.setContentDescription(a.t("earlier"));
                        Button later =
                                a.views.button(
                                        actions,
                                        "↓",
                                        () -> {
                                            Collections.swap(order, index, index + 1);
                                            pendingValue = String.join(",", order);
                                            refresh[0].run();
                                        });
                        a.views.square(later);
                        later.setEnabled(index + 1 < order.size());
                        later.setContentDescription(a.t("later"));
                        Button remove =
                                a.views.button(
                                        actions,
                                        a.t("remove"),
                                        () -> {
                                            order.remove(index);
                                            pendingValue = String.join(",", order);
                                            refresh[0].run();
                                        });
                        a.views.square(remove);
                        remove.setText("×");
                        remove.setContentDescription(a.t("remove") + " · "
                                + a.catalog.value(a.profile(), spec.getId(), item, a.ar));
                    }
                    Button add = a.views.button(items, a.t("add"), () -> {});
                    add.setOnClickListener(view -> {
                        List<String> choices = new ArrayList<>(spec.getOptions());
                        choices.removeAll(order);
                        chooseApp(
                                view,
                                choices,
                                picked -> {
                                    order.add(picked);
                                    pendingValue = String.join(",", order);
                                    refresh[0].run();
                                });
                    });
                    add.setEnabled(
                            order.size() < spec.getMaximum()
                                    && order.size() < spec.getOptions().size());
                };
        refresh[0].run();
    }

    void validateSpecial(SettingSpec s, String value) throws Exception {
        if (s.getId().equals("appearance.custom-theme") && !value.isEmpty()) {
            JSONObject theme = new JSONObject(tv.miqat.companion.lan.Json.read(value).toString());
            tv.miqat.companion.lan.Json.keys(
                    theme, "version", "colors", "background", "lighting", "surface");
            if (!(theme.get("version") instanceof Integer) || theme.getInt("version") != 1)
                throw new IllegalArgumentException();
            if (theme.has("lighting")
                    && (!(theme.get("lighting") instanceof Integer)
                            || theme.getInt("lighting") < 0
                            || theme.getInt("lighting") > 100))
                throw new IllegalArgumentException();
            if (theme.has("background")) {
                String background = tv.miqat.companion.lan.Json.string(theme, "background");
                if (background.equals("theme")
                        || !spec("appearance.background").getOptions().contains(background))
                    throw new IllegalArgumentException();
            }
            if (theme.has("surface")) {
                String surfaceName = tv.miqat.companion.lan.Json.string(theme, "surface");
                boolean found = false;
                JSONArray surfaces = a.catalog.data.getJSONArray("themeSurfaces");
                for (int i = 0; i < surfaces.length(); i++)
                    if (surfaces.getString(i).equals(surfaceName)) found = true;
                if (!found) throw new IllegalArgumentException();
            }
            JSONObject colors =
                    theme.has("colors") ? theme.getJSONObject("colors") : new JSONObject();
            Iterator<String> keys = colors.keys();
            List<String> allowed = new ArrayList<>();
            JSONArray palette = a.catalog.data.optJSONArray("themeKeys");
            for (int i = 0; i < palette.length(); i++) allowed.add(palette.optString(i));
            while (keys.hasNext()) {
                String key = keys.next();
                if (!allowed.contains(key) || !colors.getString(key).matches("#[0-9a-fA-F]{6}"))
                    throw new IllegalArgumentException();
            }
        }
        if (s.getId().equals("device.redirects") && !value.isEmpty()) {
            List<String> apps = spec("favourites.order").getOptions();
            Set<String> sources = new HashSet<>();
            Map<String, String> targets = new HashMap<>();
            for (String pair : value.split(",", -1)) {
                String[] parts = pair.trim().split("=", -1);
                if (parts.length != 2
                        || parts[0].equals(parts[1])
                        || !apps.contains(parts[0])
                        || !apps.contains(parts[1])
                        || !sources.add(parts[0])) throw new IllegalArgumentException();
                targets.put(parts[0], parts[1]);
            }
            for (String source : targets.keySet()) {
                Set<String> visited = new HashSet<>();
                String next = source;
                while (targets.containsKey(next)) {
                    if (!visited.add(next)) throw new IllegalArgumentException();
                    next = targets.get(next);
                }
            }
        }
    }

    void themeEditor(LinearLayout parent) {
        JSONObject theme;
        try {
            theme = new JSONObject(pendingValue);
        } catch (Exception e) {
            theme = new JSONObject();
        }
        JSONObject palette = theme.optJSONObject("colors");
        if (palette == null) palette = new JSONObject();
        final JSONObject draft = palette;
        final JSONObject result = theme;
        JSONArray keys = a.catalog.data.optJSONArray("themeKeys");
        for (int i = 0; i < keys.length(); i++) {
            String key = keys.optString(i);
            EditText field = new EditText(a);
            field.setImeOptions(
                    field.getImeOptions()
                            | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);
            field.setId(View.generateViewId());
            field.setSingleLine(true);
            field.setTextDirection(View.TEXT_DIRECTION_LTR);
            JSONObject selectedTheme =
                    a.catalog
                            .data
                            .optJSONObject("themes")
                            .optJSONObject(values().get("appearance.theme"));
            String fallback =
                    selectedTheme != null
                            ? selectedTheme
                                    .optJSONObject("colors")
                                    .optString(
                                            key,
                                            a.catalog.data.optJSONObject("colors").optString(key))
                            : a.catalog.data.optJSONObject("colors").optString(key);
            field.setText(draft.optString(key, fallback));
            try {
                draft.put(key, field.getText().toString());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            TextView label = a.views.text(a.t(key), 14, a.muted);
            label.setLabelFor(field.getId());
            parent.addView(label);
            parent.addView(field);
            field.addTextChangedListener(
                    MainActivity.watcher(value -> {
                        try {
                            draft.put(key, value);
                            result.put("version", 1);
                            result.put("colors", draft);
                            pendingValue = result.toString();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }));
        }
        try {
            result.put("version", 1);
            result.put("colors", draft);
            pendingValue = result.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    void redirectEditor(LinearLayout parent) {
        LinearLayout items = a.views.column();
        parent.addView(items);
        Runnable[] refresh = new Runnable[1];
        refresh[0] =
                () -> {
                    items.removeAllViews();
                    List<String> pairs = new ArrayList<>();
                    if (!pendingValue.isEmpty())
                        pairs.addAll(Arrays.asList(pendingValue.split(",")));
                    for (int i = 0; i < pairs.size(); i++) {
                        final int index = i;
                        String[] pair = pairs.get(i).split("=");
                        LinearLayout row = a.views.card(items);
                        row.addView(
                                a.views.text(
                                        a.catalog.value(
                                                        a.profile(),
                                                        "favourites.order",
                                                        pair[0],
                                                        a.ar)
                                                + " → "
                                                + a.catalog.value(
                                                        a.profile(),
                                                        "favourites.order",
                                                        pair[1],
                                                        a.ar),
                                        14,
                                        a.ink));
                        a.views.button(
                                row,
                                a.t("remove"),
                                () -> {
                                    pairs.remove(index);
                                    pendingValue = String.join(",", pairs);
                                    refresh[0].run();
                                });
                    }
                    List<String> apps = spec("favourites.order").getOptions();
                    List<String> names = new ArrayList<>();
                    for (String app : apps)
                        names.add(a.catalog.value(a.profile(), "favourites.order", app, a.ar));
                    Spinner[] pickers = new Spinner[2];
                    for (int i = 0; i < 2; i++) {
                        TextView label =
                                a.views.text(
                                        a.t(i == 0 ? "source_app" : "target_app"), 14, a.muted);
                        items.addView(label);
                        Spinner picker = new Spinner(a, Spinner.MODE_DROPDOWN);
                        picker.setId(View.generateViewId());
                        picker.setMinimumHeight(a.views.dp(48));
                        picker.setAdapter(
                                new ArrayAdapter<>(
                                        a, android.R.layout.simple_spinner_dropdown_item, names));
                        picker.setPopupBackgroundDrawable(a.views.background(a.surface, 12));
                        if (!apps.isEmpty()) picker.setSelection(Math.min(i, apps.size() - 1));
                        label.setLabelFor(picker.getId());
                        items.addView(picker);
                        pickers[i] = picker;
                    }
                    a.views
                            .button(
                                    items,
                                    a.t("add"),
                                    () -> {
                                        pairs.add(
                                                apps.get(pickers[0].getSelectedItemPosition())
                                                        + "="
                                                        + apps.get(
                                                                pickers[1]
                                                                        .getSelectedItemPosition()));
                                        pendingValue = String.join(",", pairs);
                                        refresh[0].run();
                                    })
                            .setEnabled(apps.size() > 1);
                };
        refresh[0].run();
    }

    void chooseApp(View anchor, List<String> apps, java.util.function.Consumer<String> picked) {
        List<String> names = new ArrayList<>();
        for (String app : apps)
            names.add(a.catalog.value(a.profile(), "favourites.order", app, a.ar));
        ListPopupWindow dropdown = new ListPopupWindow(a);
        dropdown.setAnchorView(anchor);
        dropdown.setWidth(anchor.getWidth());
        dropdown.setHeight(-2);
        dropdown.setModal(true);
        dropdown.setBackgroundDrawable(a.views.background(a.surface, 12));
        dropdown.setAdapter(
                new ArrayAdapter<>(a, android.R.layout.simple_dropdown_item_1line, names));
        dropdown.setOnItemClickListener((parent, view, index, id) -> {
            dropdown.dismiss();
            picked.accept(apps.get(index));
        });
        dropdown.show();
    }

    void simulateConflict(SettingSpec s) {
        Map<String, String> now = a.tv().read().getValues();
        String value = now.get(s.getId()), next = value;
        if (s.getKind().equals("toggle")) next = String.valueOf(!value.equals("true"));
        else if (s.getKind().equals("choice")) {
            int i = s.getOptions().indexOf(value);
            next = s.getOptions().get((i + 1) % s.getOptions().size());
        } else if (s.getKind().equals("number"))
            next = s.adjust(value, value.equals(String.valueOf(s.getMaximum())) ? -1 : 1);
        a.tv().apply(now, Collections.singletonMap(s.getId(), next));
        persistDemo();
        a.notice("sim_action");
    }

    void save(Map<String, String> base, Map<String, String> changes, boolean isAppearance) {
        if (!editable()) {
            a.notice("disconnected");
            return;
        }
        if (a.real() != null) {
            LanTv tv = a.real();
            Map<String, String> baseline = new LinkedHashMap<>(base),
                    patch = new LinkedHashMap<>(changes);
            a.network(
                    () -> tv.apply(baseline, patch),
                    result -> {
                        a.cacheReal(tv);
                        if (a.real() == tv) saveResult(result, baseline, patch, isAppearance);
                    },
                    () -> {
                        tv.needsRefresh = true;
                        tv.online = false;
                        a.render();
                        a.info(a.t("settings"), a.t("uncertain"));
                    });
            return;
        }
        saveResult(a.tv().apply(base, changes), base, changes, isAppearance);
    }

    void saveResult(
            SaveResult result,
            Map<String, String> base,
            Map<String, String> changes,
            boolean isAppearance) {
        if (result.getStatus().equals("saved")) {
            snapshot = result.getSnapshot();
            if (a.real() == null) {
                persistDemo();
                a.prefs()
                        .edit()
                        .putString(cacheKey(), MainActivity.encode(snapshot.getValues()))
                        .apply();
            }
            if (isAppearance) appearance = null;
            clearPending();
            a.render();
            a.notice(a.real() == null ? "saved" : "saved_tv");
        } else if (result.getStatus().equals("uncertain") || result.getStatus().equals("busy")) {
            snapshot = result.getSnapshot();
            a.render();
            a.info(a.t("settings"), a.t(result.getStatus()));
        } else if (result.getStatus().equals("failed")) {
            a.views
                    .dialog()
                    .setMessage(a.t("failed"))
                    .setNegativeButton(
                            a.t("cancel"),
                            (d, w) -> {
                                if (!isAppearance) {
                                    clearPending();
                                    a.render();
                                }
                            })
                    .setPositiveButton(a.t("done"), (d, w) -> save(base, changes, isAppearance))
                    .setOnCancelListener(d -> {
                        if (!isAppearance) {
                            clearPending();
                            a.render();
                        }
                    })
                    .show();
        } else {
            StringBuilder detail = new StringBuilder(a.t("conflict"));
            for (String id : result.getConflicts())
                detail.append("\n\n")
                        .append(a.title(a.catalog.control(a.profile(), id)))
                        .append("\n")
                        .append(a.t("current"))
                        .append(": ")
                        .append(display(id, result.getSnapshot().getValues().get(id)))
                        .append("\n")
                        .append(a.t("your_edit"))
                        .append(": ")
                        .append(display(id, changes.getOrDefault(id, base.get(id))));
            a.views
                    .dialog()
                    .setMessage(detail)
                    .setNegativeButton(
                            a.t("keep_current"),
                            (d, w) -> {
                                snapshot = result.getSnapshot();
                                if (a.real() == null)
                                    a.prefs()
                                            .edit()
                                            .putString(
                                                    cacheKey(),
                                                    MainActivity.encode(snapshot.getValues()))
                                            .apply();
                                if (isAppearance) appearance = null;
                                clearPending();
                                a.render();
                            })
                    .setPositiveButton(
                            a.t("replace"),
                            (d, w) -> save(result.getSnapshot().getValues(), changes, isAppearance))
                    .show();
        }
    }

    void persistDemo() {
        a.prefs()
                .edit()
                .putString("demo-" + a.selected, MainActivity.encode(a.tv().read().getValues()))
                .apply();
    }

    void applyAppearance() {
        if (appearance == null || appearance.getChanges().isEmpty() || !editable()) return;
        if ("custom".equals(appearance.getValues().get("appearance.theme"))
                && appearance.getValues().get("appearance.custom-theme").isEmpty()) {
            edit("appearance.custom-theme");
            return;
        }
        save(appearance.getBase().getValues(), appearance.getChanges(), true);
    }

    void action(JSONObject c) {
        String route = c.optString("route"), id = c.optString("id");
        if (a.remoteUi.commandOffered(id)) {
            a.remoteUi.sendCommand(id, "");
            return;
        }
        if (c.optString("section").equals("tests") || route.equals("tv-action")) {
            a.info(a.title(c), a.t(a.real() == null ? "sim_action" : "tv_step"));
            return;
        }
        switch (route) {
            case "reset-appearance":
                {
                    if (appearance == null) appearance = new SettingsDraft(snapshot);
                    a.section = "appearance";
                    a.query = "";
                    Map<String, String> defaults = a.catalog.defaults(a.profile());
                    for (SettingSpec s : a.backend().getSpecs())
                        if (CompanionContract.INSTANCE
                                .getAppearanceLayoutControls()
                                .contains(s.getId())) appearance.set(s, defaults.get(s.getId()));
                    a.render();
                    break;
                }
            case "reset-offsets":
                {
                    Map<String, String> changes = new LinkedHashMap<>();
                    for (SettingSpec s : a.backend().getSpecs())
                        if (s.getId().startsWith("offsets.")) changes.put(s.getId(), "0");
                    save(snapshot.getValues(), changes, false);
                    break;
                }
            case "health":
                a.info(a.title(c), a.t(a.real() == null ? "health" : "tv_step"));
                break;
            case "today":
                {
                    if (a.real() != null) {
                        a.info(a.title(c), a.t("tv_step"));
                        break;
                    }
                    StringBuilder times = new StringBuilder(a.t("sample_times"));
                    for (int i = 0; i < 6; i++)
                        times.append("\n")
                                .append(
                                        a.catalog
                                                .data
                                                .optJSONObject("prayerNames")
                                                .optJSONArray(a.ar ? "ar" : "en")
                                                .optString(i))
                                .append("  ")
                                .append(
                                        new String[] {
                                                    "05:10", "06:30", "12:00", "15:15", "17:30",
                                                    "19:00"
                                                }
                                                [i]);
                    a.info(a.title(c), times.toString());
                    break;
                }
            case "tv-step":
            case "inputs":
            case "apps":
                a.info(a.title(c), a.t("tv_step"));
                break;
            case "overview":
                a.navigate("overview");
                break;
            case "apply-appearance":
                applyAppearance();
                break;
            case "cancel-appearance":
                appearance = null;
                a.section = "";
                a.render();
                break;
            default:
                if (route.equals("settings")) {
                    a.section = "";
                    a.render();
                } else openSection(route);
        }
    }
}

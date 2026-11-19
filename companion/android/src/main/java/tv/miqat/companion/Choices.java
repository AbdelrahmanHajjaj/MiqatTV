package tv.miqat.companion;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONObject;

import tv.miqat.companion.core.SettingSpec;
import tv.miqat.core.UiContract;

import java.util.ArrayList;
import java.util.List;

/** Native touch bindings for the shared segmented/carousel/catalog choice policy. */
final class Choices {
    private Choices() {}

    static final class Option {
        final String value, label;

        Option(String value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    static void add(
            MainActivity a, LinearLayout row, JSONObject control, SettingSpec spec, String value) {
        String id = spec.getId(),
                editor =
                        id.startsWith("remote.")
                                ? "list"
                                : UiContract.INSTANCE.choiceEditor(
                                        control.optString("choiceSource", id));
        List<String> visible = new ArrayList<>(spec.getOptions());
        visible.removeIf(option -> Catalog.retiredVoice(id, option));
        if (editor.equals("segments")) {
            HorizontalScrollView viewport = new HorizontalScrollView(a);
            viewport.setHorizontalScrollBarEnabled(false);
            RadioGroup track = new RadioGroup(a);
            track.setOrientation(LinearLayout.HORIZONTAL);
            track.setPadding(a.views.dp(4), a.views.dp(4), a.views.dp(4), a.views.dp(4));
            track.setBackground(a.views.background(a.ground, 24));
            track.setTag(id + ".segments");
            for (String option : visible) {
                RadioButton chip = new RadioButton(a);
                chip.setButtonDrawable(null);
                chip.setMinHeight(a.views.dp(48));
                chip.setMinWidth(a.views.dp(72));
                chip.setPadding(a.views.dp(14), 0, a.views.dp(14), 0);
                chip.setTextSize(13);
                chip.setTypeface(a.regular);
                chip.setGravity(Gravity.CENTER);
                chip.setText(a.catalog.value(a.profile(), id, option, a.ar));
                chip.setChecked(option.equals(value));
                chip.setTextColor(option.equals(value) ? a.ground : a.muted);
                chip.setBackground(
                        a.views.background(option.equals(value) ? a.ink : Color.TRANSPARENT, 22));
                chip.setEnabled(a.settingsUi.editable());
                chip.setContentDescription(a.title(control) + ", " + chip.getText());
                chip.setTag(id + ".option." + option);
                track.addView(chip, new RadioGroup.LayoutParams(-2, -2));
                chip.setOnClickListener(v -> {
                    if (!option.equals(value)) a.settingsUi.selectChoice(id, option);
                });
            }
            viewport.addView(track);
            row.addView(viewport);
            viewport.setTag(id + ".edit");
            viewport.setEnabled(a.settingsUi.editable());
        } else if (editor.equals("carousel")) {
            int index = visible.indexOf(value);
            LinearLayout track = a.views.horizontal(row);
            track.setTag(id + ".edit");
            track.setEnabled(a.settingsUi.editable());
            Button previous =
                    a.views.button(
                            track,
                            a.ar ? "›" : "‹",
                            () -> a.settingsUi.selectChoice(id, visible.get(index - 1)));
            a.views.square(previous);
            previous.setTag(id + ".previous");
            previous.setContentDescription(a.t("previous") + " " + a.title(control));
            previous.setEnabled(a.settingsUi.editable() && index > 0);
            TextView selected =
                    a.views.text(a.catalog.value(a.profile(), id, value, a.ar), 16, a.ink);
            selected.setGravity(Gravity.CENTER);
            selected.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            selected.setMinHeight(a.views.dp(48));
            selected.setBackground(a.views.background(a.color("lapis-700"), 24));
            selected.setPadding(a.views.dp(8), a.views.dp(8), a.views.dp(8), a.views.dp(8));
            track.addView(selected, new LinearLayout.LayoutParams(0, -2, 1));
            selected.setTag(id + ".value");
            Button next =
                    a.views.button(
                            track,
                            a.ar ? "‹" : "›",
                            () -> a.settingsUi.selectChoice(id, visible.get(index + 1)));
            a.views.square(next);
            next.setTag(id + ".next");
            next.setContentDescription(a.t("next") + " " + a.title(control));
            next.setEnabled(a.settingsUi.editable() && index + 1 < visible.size());
        } else {
            AutoCompleteTextView field = new AutoCompleteTextView(a);
            field.setId(View.generateViewId());
            field.setTag(id + ".dropdown");
            field.setSingleLine(true);
            field.setThreshold(0);
            field.setSelectAllOnFocus(true);
            field.setTextSize(15);
            field.setTypeface(a.regular);
            field.setTextColor(a.accent);
            field.setMinHeight(a.views.dp(48));
            field.setHint(a.t("search"));
            List<Option> options = new ArrayList<>();
            for (String option : visible)
                options.add(new Option(option, a.catalog.value(a.profile(), id, option, a.ar)));
            ArrayAdapter<Option> adapter =
                    new ArrayAdapter<>(a, android.R.layout.simple_dropdown_item_1line, options);
            field.setAdapter(adapter);
            field.setText(a.catalog.value(a.profile(), id, value, a.ar), false);
            field.setEnabled(a.settingsUi.editable());
            field.setDropDownBackgroundDrawable(a.views.background(a.surface, 12));
            field.setOnClickListener(v -> field.showDropDown());
            field.setOnItemClickListener((parent, view, position, item) -> {
                Option option = (Option) parent.getItemAtPosition(position);
                field.dismissDropDown();
                ((android.view.inputmethod.InputMethodManager)
                                a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(field.getWindowToken(), 0);
                a.settingsUi.selectChoice(id, option.value);
            });
            field.setOnFocusChangeListener((view, focused) -> {
                if (!focused) field.setText(a.catalog.value(a.profile(), id, value, a.ar), false);
            });
            ((TextView) ((LinearLayout) row.getChildAt(0)).getChildAt(0))
                    .setLabelFor(field.getId());
            LinearLayout anchor = a.views.horizontal(row);
            anchor.addView(field, new LinearLayout.LayoutParams(0, -2, 1));
            Button open =
                    a.views.button(
                            anchor,
                            "⌄",
                            () -> {
                                adapter.getFilter().filter(null, count -> field.showDropDown());
                                field.requestFocus();
                            });
            a.views.square(open);
            open.setContentDescription(a.title(control));
            open.setEnabled(a.settingsUi.editable());
            open.setTag(id + ".edit");
            if (id.equals("reminders.voice") || id.equals("reminders.fajr-voice")) {
                LinearLayout affordance = a.views.horizontal(row);
                Button preview =
                        a.views.bindAction(
                                a.views.iconButton(
                                        affordance,
                                        "play",
                                        a.t("preview_audio"),
                                        () -> a.playAudio(value)),
                                "preview-audio");
                preview.setTag(id + ".preview");
                Button stop =
                        a.views.bindAction(
                                a.views.iconButton(
                                        affordance, "stop", a.t("stop_audio"), a::stopAudio),
                                "stop-audio");
                if (a.settingsUi.isPending(id)) {
                    row.addView(a.views.text(a.t("voice_draft"), 12, a.muted));
                    a.actionHost.removeAllViews();
                    LinearLayout actions = a.views.actionBar(a.actionHost);
                    LinearLayout secondary = a.views.secondaryActions(actions);
                    Button done =
                            a.views.bindAction(
                                    a.views.primaryAction(
                                            actions, a.t("done"), a.settingsUi::commitPending),
                                    "save-edit");
                    done.setTag(id + ".save");
                    Button cancel =
                            a.views.secondaryAction(
                                    secondary,
                                    a.t("cancel"),
                                    () -> {
                                        a.stopAudio();
                                        a.settingsUi.clearPending();
                                        a.render();
                                    });
                    cancel.setTag(id + ".cancel");
                }
            }
        }
    }
}

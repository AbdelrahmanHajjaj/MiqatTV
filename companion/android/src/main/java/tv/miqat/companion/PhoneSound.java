package tv.miqat.companion;

import android.app.AlertDialog;
import android.content.Intent;
import android.view.*;
import android.widget.*;

import java.util.*;

/** Draft voice selection, bounded audition and private audio import. */
final class PhoneSound {
    private final MainActivity a;
    private final PhoneUi ui;
    String audioImport = "";

    PhoneSound(MainActivity activity, PhoneUi phoneUi) {
        a = activity;
        ui = phoneUi;
    }

    void length(String id) {
        String base = id.substring(0, id.lastIndexOf('.') + 1);
        boolean defaults = id.startsWith("reminders.");
        int prayer = defaults ? 0 : Arrays.asList(PhoneStore.PRAYERS).indexOf(id.split("\\.")[1]);
        boolean inherited = !defaults && ui.settings.value(base + "default-audio").equals("true");
        int percent =
                inherited ? ui.settings.number("reminders.length-percent") : ui.settings.number(id);
        LinearLayout panel = a.views.card(a.body);
        panel.setTag("phone." + id);
        TextView label =
                a.views.text(a.t("phone_length") + " · " + ui.digits(percent + "%"), 14, a.ink);
        panel.addView(label);
        LinearLayout track = a.views.horizontal(panel);
        SeekBar slider =
                new SeekBar(a) {
                    @Override
                    public boolean performAccessibilityAction(int action, android.os.Bundle args) {
                        if (!isEnabled()) return false;
                        if (action
                                == android.view.accessibility.AccessibilityNodeInfo
                                        .AccessibilityAction.ACTION_SET_PROGRESS
                                        .getId()) {
                            String key =
                                    android.view.accessibility.AccessibilityNodeInfo
                                            .ACTION_ARGUMENT_PROGRESS_VALUE;
                            if (args == null || !args.containsKey(key)) return false;
                            float requested = args.getFloat(key);
                            if (!Float.isFinite(requested)) return false;
                            PhonePlayback.stopPreview(a);
                            ui.save(
                                    id,
                                    Integer.toString(
                                            Math.max(
                                                    5,
                                                    Math.min(
                                                            100, Math.round(requested / 5f) * 5))));
                            return true;
                        }
                        boolean handled = super.performAccessibilityAction(action, args);
                        if (handled
                                && (action
                                                == android.view.accessibility.AccessibilityNodeInfo
                                                        .ACTION_SCROLL_FORWARD
                                        || action
                                                == android.view.accessibility.AccessibilityNodeInfo
                                                        .ACTION_SCROLL_BACKWARD)) {
                            PhonePlayback.stopPreview(a);
                            ui.save(
                                    id,
                                    Integer.toString(
                                            Math.max(5, Math.round(getProgress() / 5f) * 5)));
                        }
                        return handled;
                    }
                };
        slider.setMin(5);
        slider.setMax(100);
        slider.setKeyProgressIncrement(5);
        slider.setProgress(percent);
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(a.accent));
        slider.setThumbTintList(android.content.res.ColorStateList.valueOf(a.accent));
        slider.setContentDescription(a.t("phone_length"));
        slider.setTag("phone." + id + ".edit");
        track.addView(slider, new LinearLayout.LayoutParams(0, a.views.dp(48), 1));
        slider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                        int value = Math.max(5, Math.round(progress / 5f) * 5);
                        label.setText(a.t("phone_length") + " · " + ui.digits(value + "%"));
                        if (user) PhonePlayback.stopPreview(a);
                    }

                    public void onStartTrackingTouch(SeekBar bar) {
                        PhonePlayback.stopPreview(a);
                    }

                    public void onStopTrackingTouch(SeekBar bar) {
                        ui.save(
                                id,
                                Integer.toString(
                                        Math.max(5, Math.round(bar.getProgress() / 5f) * 5)));
                    }
                });
        slider.setOnKeyListener((v, key, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP
                    && (key == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                            || key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT)) {
                ui.save(
                        id,
                        Integer.toString(Math.max(5, Math.round(slider.getProgress() / 5f) * 5)));
                return true;
            }
            return false;
        });
        a.views
                .iconButton(
                        track,
                        "play",
                        a.t("phone_preview_ending"),
                        () -> {
                            try {
                                if (PhonePlayback.scheduled()) {
                                    a.notice("phone_stop_first");
                                    return;
                                }
                                if (!PhoneAlarms.notifications(a)) {
                                    ui.open("permissions");
                                    return;
                                }
                                String voice =
                                        defaults
                                                ? ui.settings.value("reminders.voice")
                                                : ui.settings.voice(prayer);
                                a.startForegroundService(
                                        PhonePlayback.intent(
                                                        a,
                                                        prayer,
                                                        voice,
                                                        true,
                                                        "preview:" + System.nanoTime())
                                                .putExtra("default-audio", defaults)
                                                .putExtra(
                                                        "default-voice",
                                                        defaults
                                                                || ui.settings
                                                                        .value(base + "voice")
                                                                        .equals("inherit"))
                                                .putExtra("ending-preview", true)
                                                .putExtra(
                                                        "ending-percent",
                                                        Math.max(
                                                                5,
                                                                Math.round(
                                                                                slider.getProgress()
                                                                                        / 5f)
                                                                        * 5)));
                            } catch (Exception failed) {
                                a.notice("phone_audio_failed");
                            }
                        })
                .setTag("phone.length.preview");
        a.views
                .iconButton(track, "close", a.t("phone_stop"), () -> PhonePlayback.stopPreview(a))
                .setTag("phone.length.stop");
        panel.addView(a.views.text(a.t("phone_length_hint"), 12, a.muted));
        int oldLimit = ui.settings.audioDuration(defaults ? -1 : prayer);
        if (oldLimit > 0)
            panel.addView(
                    a.views.text(
                            String.format(
                                    ui.locale(),
                                    a.t("phone_legacy_duration"),
                                    ui.digits(Integer.toString(oldLimit))),
                            12,
                            a.accent));
        if (inherited) {
            slider.setEnabled(false);
            slider.setAlpha(.4f);
        }
    }

    void choose(String id, List<Choices.Option> options) throws Exception {
        if (ui.choiceDialog != null) ui.choiceDialog.dismiss();
        String[] candidate = {ui.settings.value(id)};
        boolean shortWindow = a.getResources().getDisplayMetrics().heightPixels
                < a.views.dp(480) * Math.max(1f, a.getResources().getConfiguration().fontScale);
        LinearLayout layout = a.views.column();
        layout.setPadding(a.views.dp(16), a.views.dp(8), a.views.dp(16), a.views.dp(8));
        layout.setLayoutDirection(a.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        EditText search = a.views.searchField();
        search.setTag("phone.voice.search");
        layout.addView(search);
        layout.setFocusableInTouchMode(true);
        layout.requestFocus();
        ListView list = new ListView(a);
        list.setTag("phone.voice.list");
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        ArrayAdapter<Choices.Option> adapter = a.views.choices(options);
        list.setAdapter(adapter);
        a.views.filterChoices(search, list, adapter, () -> candidate[0]);
        int selected = 0;
        for (int i = 0; i < options.size(); i++)
            if (options.get(i).value.equals(candidate[0])) selected = i;
        list.setItemChecked(selected, true);
        list.setSelection(selected);
        layout.addView(
                list,
                new LinearLayout.LayoutParams(
                        -1,
                        shortWindow ? a.views.dp(52) : Math.max(a.views.dp(52), Math.min(
                                a.views.dp(300),
                                a.getResources().getDisplayMetrics().heightPixels / 3) - a.views.dp(64))));
        LinearLayout actions = a.views.horizontal(layout);
        Button play = a.views.button(actions, a.t("phone_listen"), () -> preview(id, candidate[0]));
        a.views.flex(play);
        play.setTag("phone.voice.preview");
        play.setContentDescription(a.t("phone_preview"));
        a.views
                .iconButton(actions, "close", a.t("phone_stop"), () -> PhonePlayback.stopPreview(a))
                .setTag("phone.voice.stop");
        AlertDialog dialog =
                a.views
                        .dialog()
                        .setTitle(shortWindow ? null : a.t(ui.data.controls.get(id).getString("label")))
                        .setView(layout)
                        .setNegativeButton(a.t("cancel"), null)
                        .setPositiveButton(a.t("apply"), (d, which) -> ui.save(id, candidate[0]))
                        .create();
        Button imported =
                a.views.button(
                        shortWindow ? actions : layout,
                        a.t("phone_import_audio"),
                        () -> {
                            dialog.dismiss();
                            importAudio(id);
                        });
        if (shortWindow) {
            a.views.flex(imported);
            search.setContentDescription(a.t(ui.data.controls.get(id).getString("label")) + ", " + a.t("phone_search"));
        }
        imported.setTag("phone.voice.import");
        Runnable update =
                () -> {
                    boolean custom = candidate[0].equals("custom");
                    boolean available =
                            !custom
                                    || !ui.settings
                                            .value("audio.custom." + PhoneAudio.slot(id))
                                            .isEmpty();
                    play.setEnabled(available);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(available);
                };
        list.setOnItemClickListener((parent, view, position, row) -> {
            PhonePlayback.stopPreview(a);
            candidate[0] = adapter.getItem(position).value;
            list.setItemChecked(position, true);
            update.run();
        });
        ui.choiceDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            PhonePlayback.stopPreview(a);
            if (ui.choiceDialog == dialog) ui.choiceDialog = null;
        });
        dialog.show();
        dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.getWindow().setLayout(-1, -2);
        update.run();
    }

    void importAudio(String id) {
        audioImport = id;
        try {
            a.startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .setType("audio/*")
                            .addCategory(Intent.CATEGORY_OPENABLE),
                    79);
        } catch (android.content.ActivityNotFoundException missing) {
            audioImport = "";
            a.notice("phone_permission_missing");
        }
    }

    void audioResult(android.net.Uri uri) {
        String id = audioImport;
        audioImport = "";
        if (uri == null || id.isEmpty()) return;
        a.network(
                () -> {
                    PhoneAudio.importFile(a, a.getContentResolver().openInputStream(uri), id);
                    return true;
                },
                done -> {
                    PhoneAlarms.rebuild(a);
                    a.render();
                },
                () -> {
                    a.notice("phone_custom_audio_failed");
                    a.render();
                });
    }

    void preview(String id, String candidate) {
        if (!PhoneAlarms.notifications(a)) {
            if (ui.choiceDialog != null) ui.choiceDialog.dismiss();
            ui.open("permissions");
            return;
        }
        try {
            if (PhonePlayback.scheduled()) {
                a.notice("phone_stop_first");
                return;
            }
            int prayer =
                    id.startsWith("call.")
                            ? Arrays.asList(PhoneStore.PRAYERS).indexOf(id.split("\\.")[1])
                            : 0;
            boolean defaults = !id.startsWith("call.") || candidate.equals("inherit");
            String voice =
                    candidate.equals("inherit") ? ui.settings.value("reminders.voice") : candidate;
            a.startForegroundService(
                    PhonePlayback.intent(a, prayer, voice, true, "preview:" + System.nanoTime())
                            .putExtra("default-audio", !id.startsWith("call."))
                            .putExtra("default-voice", defaults));
        } catch (Exception error) {
            a.notice("phone_audio_failed");
        }
    }
}

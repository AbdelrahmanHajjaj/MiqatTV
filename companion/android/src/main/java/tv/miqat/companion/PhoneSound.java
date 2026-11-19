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

    void choose(String id, List<Choices.Option> options) throws Exception {
        if (ui.choiceDialog != null) ui.choiceDialog.dismiss();
        String[] candidate = {ui.settings.value(id)};
        LinearLayout layout = a.views.column();
        layout.setPadding(a.views.dp(16), a.views.dp(8), a.views.dp(16), a.views.dp(8));
        layout.setLayoutDirection(a.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        ListView list = new ListView(a);
        list.setTag("phone.voice.list");
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setAdapter(a.views.choices(options));
        int selected = 0;
        for (int i = 0; i < options.size(); i++)
            if (options.get(i).value.equals(candidate[0])) selected = i;
        list.setItemChecked(selected, true);
        list.setSelection(selected);
        layout.addView(
                list,
                new LinearLayout.LayoutParams(
                        -1,
                        Math.min(
                                a.views.dp(300),
                                a.getResources().getDisplayMetrics().heightPixels / 3)));
        LinearLayout actions = a.views.horizontal(layout);
        Button play =
                a.views.button(actions, a.t("phone_listen"), () -> preview(id, candidate[0]));
        a.views.flex(play);
        play.setTag("phone.voice.preview");
        play.setContentDescription(a.t("phone_preview"));
        a.views
                .iconButton(actions, "close", a.t("phone_stop"), () -> PhonePlayback.stopPreview(a))
                .setTag("phone.voice.stop");
        AlertDialog dialog =
                a.views
                        .dialog()
                        .setTitle(a.t(ui.data.controls.get(id).getString("label")))
                        .setView(layout)
                        .setNegativeButton(a.t("cancel"), null)
                        .setPositiveButton(a.t("apply"), (d, which) -> ui.save(id, candidate[0]))
                        .create();
        Button imported =
                a.views.button(
                        layout,
                        a.t("phone_import_audio"),
                        () -> {
                            dialog.dismiss();
                            importAudio(id);
                        });
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
                    imported.setVisibility(custom ? View.VISIBLE : View.GONE);
                };
        list.setOnItemClickListener((parent, view, position, row) -> {
            PhonePlayback.stopPreview(a);
            candidate[0] = options.get(position).value;
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

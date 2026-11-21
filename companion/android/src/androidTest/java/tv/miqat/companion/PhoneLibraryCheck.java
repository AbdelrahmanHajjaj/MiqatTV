package tv.miqat.companion;

import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Actual searchable library choices, with exact preservation of pre-existing private imports. */
final class PhoneLibraryCheck {
    static void run(Instrumentation test, boolean capture) throws Exception {
        android.content.Context c = test.getTargetContext();
        SharedPreferences prefs = c.getSharedPreferences("companion",0);
        String language = prefs.getString("language","system");
        try {
            for (String lang : new String[]{"en","ar"}) {
                prefs.edit().putString("language",lang).commit();
                MainActivity a = LiveUiCheck.launch(test);
                try { check(test,a,lang,capture); }
                finally { PhoneUiCheck.on(test,a::finish); }
            }
        } finally { prefs.edit().putString("language",language).commit(); }
    }

    static void check(Instrumentation test, MainActivity a, String lang, boolean capture) throws Exception {
        PhoneStore saved = PhoneStore.read(a);
        SharedPreferences prefs = PhoneStore.preferences(a);
        boolean hadSettings = prefs.contains("settings");
        String settingsBytes = prefs.getString("settings", null);
        File directory = new File(a.createDeviceProtectedStorageContext().getFilesDir(), "custom-audio");
        boolean hadDirectory = directory.isDirectory();
        Set<String> initialFiles = new HashSet<>();
        File[] existing = directory.listFiles();
        if (existing != null) for (File file : existing) initialFiles.add(file.getName());
        File index = new File(directory, "library.json");
        byte[] initialIndex = index.exists() ? Files.readAllBytes(index.toPath()) : null;
        Set<String> fixtureFiles = new HashSet<>();
        String prefix = "Review " + Long.toString(System.nanoTime() & 0xffffff, 36);
        String firstName = prefix + (lang.equals("ar") ? " أذان الصباح" : " Morning"),
                secondName = prefix + (lang.equals("ar") ? " أذان المساء" : " Evening");
        try {
            Map<String,String> fixture = new LinkedHashMap<>();
            fixture.put("reminders.enabled", "false");
            fixture.put("call.dhuhr.enabled", "true");
            fixture.put("call.dhuhr.mode", "adhan");
            saved.changed(fixture).save(a);
            String first = importFixture(a, "almalki.mp3", "reminders.voice", firstName, fixtureFiles);
            String second = importFixture(a, "chebae_fajr.mp3", "call.fajr.voice", secondName, fixtureFiles);
            PhoneUiCheck.on(test, () -> {
                a.tab("phone-settings");
                a.phoneUi.open("dhuhr");
                a.body.findViewWithTag("phone.call.dhuhr.voice.edit").performClick();
            });
            search(test, a, firstName, 1);
            visibleLabel(test, a, firstName);
            search(test, a, secondName, 1);
            visibleLabel(test, a, secondName);
            search(test, a, prefix, 2);
            PhoneUiCheck.on(test, () -> {
                android.graphics.Rect available = new android.graphics.Rect();
                a.getWindow().getDecorView().getWindowVisibleDisplayFrame(available);
                for (View button : new View[]{a.phoneUi.choiceDialog.getButton(-1),
                        a.phoneUi.choiceDialog.getWindow().getDecorView().findViewWithTag("phone.voice.import")}) {
                    int[] position = new int[2];button.getLocationOnScreen(position);
                    android.graphics.Rect bounds = new android.graphics.Rect(position[0],position[1],
                            position[0]+button.getWidth(),position[1]+button.getHeight());
                    PhoneUiCheck.check(available.contains(bounds),
                            "Voice picker control is outside visible screen: " + bounds + " available=" + available);
                }
            });
            PhoneUiCheck.capture(test, a, lang + "-audio-library-choices", capture);
            PhoneUiCheck.on(test, () -> {
                ListView list = list(a);
                int selected = -1;
                for (int i = 0; i < list.getCount(); i++)
                    if (((Choices.Option) list.getAdapter().getItem(i)).value.equals("library:" + first)) selected = i;
                PhoneUiCheck.check(selected >= 0, "Imported library recording is not selectable");
                list.performItemClick(null, selected, selected);
                a.phoneUi.choiceDialog.getButton(-1).performClick();
            });
            LiveUiCheck.untilUi(test, () -> a.phoneUi.choiceDialog == null);
            PhoneStore actual = PhoneStore.read(a);
            PhoneUiCheck.on(test, () -> {
                PhoneUiCheck.check(actual.value("call.dhuhr.voice").equals("custom")
                                && actual.value("audio.custom.dhuhr").equals(first)
                                && actual.value("audio.custom.fajr").equals(second),
                        "Choosing a library recording did not reuse its identity independently");
                try {
                    PhoneUiCheck.check(a.phoneUi.value("call.dhuhr.voice", "custom").equals(firstName),
                            "Selected prayer recording lost its library name");
                } catch (Exception invalid) { throw new AssertionError(invalid); }
                a.phoneUi.open("audio");
                a.phoneUi.open("dhuhr");
                a.body.findViewWithTag("phone.call.dhuhr.voice.edit").performClick();
            });
            search(test, a, firstName, 1);
            visibleLabel(test, a, firstName);
            PhoneUiCheck.on(test, () -> PhoneUiCheck.check(list(a).isItemChecked(0),
                    "Re-entering the prayer picker lost the selected library recording"));
            PhoneUiCheck.capture(test, a, lang + "-audio-library-selected", capture);
        } finally {
            PhoneUiCheck.on(test, () -> {
                if (a.phoneUi.choiceDialog != null) a.phoneUi.choiceDialog.dismiss();
                PhonePlayback.stopPreview(a);
            });
            SharedPreferences.Editor restore = prefs.edit();
            if (hadSettings) restore.putString("settings", settingsBytes); else restore.remove("settings");
            PhoneUiCheck.check(restore.commit(), "Cannot restore library UI settings fixture");
            synchronized (PhoneStore.LOCK) {
                if (initialIndex == null) Files.deleteIfExists(index.toPath());
                else {
                    File part = File.createTempFile("library-check-", ".part", directory);
                    try {
                        try (FileOutputStream out = new FileOutputStream(part)) {
                            out.write(initialIndex);
                            out.getFD().sync();
                        }
                        Files.move(part.toPath(), index.toPath(), StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                    } finally { Files.deleteIfExists(part.toPath()); }
                }
                for (String file : fixtureFiles)
                    if (!initialFiles.contains(file)) Files.deleteIfExists(new File(directory, file).toPath());
                if (!hadDirectory) {
                    File[] remaining = directory.listFiles();
                    if (remaining != null && remaining.length == 0) Files.deleteIfExists(directory.toPath());
                }
            }
            PhoneAlarms.rebuild(a);
            PhoneUiCheck.on(test, () -> { a.tab("prayers"); a.render(); });
        }
    }

    private static String importFixture(MainActivity a, String asset, String slot, String name,
            Set<String> fixtureFiles) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = a.getAssets().open("adhan/" + asset)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest()) hash.append(String.format(Locale.US, "%02x", value & 255));
        fixtureFiles.add(hash + ".audio");
        PhoneAudio.importFile(a, a.getAssets().open("adhan/" + asset), slot, name);
        return hash.toString();
    }

    private static ListView list(MainActivity a) {
        return a.phoneUi.choiceDialog.getWindow().getDecorView().findViewWithTag("phone.voice.list");
    }

    private static void search(Instrumentation test, MainActivity a, String text, int count) throws Exception {
        PhoneUiCheck.on(test, () -> ((EditText) a.phoneUi.choiceDialog.getWindow().getDecorView()
                .findViewWithTag("phone.voice.search")).setText(text));
        LiveUiCheck.untilUi(test, () -> list(a).getCount() == count);
    }

    private static void visibleLabel(Instrumentation test, MainActivity a, String expected) throws Exception {
        LiveUiCheck.untilUi(test, () -> list(a).getChildCount() > 0);
        PhoneUiCheck.on(test, () -> {
            TextView label = (TextView) list(a).getChildAt(0);
            android.graphics.Rect bounds = new android.graphics.Rect();
            PhoneUiCheck.check(label.getText().toString().equals(expected)
                            && label.getWidth() > 0 && label.getGlobalVisibleRect(bounds)
                            && bounds.width() > 0 && bounds.height() > 0,
                    "Named library entry is not visibly rendered in the native picker");
        });
    }
}

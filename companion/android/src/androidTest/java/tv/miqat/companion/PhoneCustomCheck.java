package tv.miqat.companion;

import android.app.*;
import android.content.*;

import org.json.*;

import tv.miqat.core.ReminderEvent;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Private isolated preferences/files; no TV or audible playback. */
final class PhoneCustomCheck {
    static void run(Instrumentation test) throws Exception {
        Context base = test.getTargetContext().createDeviceProtectedStorageContext();
        File directory =
                Files.createTempDirectory(base.getFilesDir().toPath(), "custom-check-").toFile();
        String key = directory.getName();
        Context c =
                new ContextWrapper(base) {
                    @Override
                    public Context createDeviceProtectedStorageContext() {
                        return this;
                    }

                    @Override
                    public File getFilesDir() {
                        return directory;
                    }

                    @Override
                    public android.content.SharedPreferences getSharedPreferences(
                            String name, int mode) {
                        return base.getSharedPreferences(key + name, mode);
                    }
                };
        try {
            PhoneStore current = PhoneStore.read(c);
            PhoneUiCheck.check(current.value("display.clock").equals("12"),
                    "Fresh phone settings must default to a 12-hour clock");
            Calendar noon = new GregorianCalendar(current.zone());
            noon.clear(); noon.set(2026, 10, 21, 12, 5);
            PhoneUiCheck.check(PhoneTime.formatter(current, Locale.US).format(noon.getTime())
                            .equals("12:05 PM"), "Default clock must include its day period");
            current.changed(Collections.singletonMap("display.clock", "24")).save(c);
            PhoneUiCheck.check(PhoneStore.read(c).value("display.clock").equals("24"),
                    "A saved 24-hour preference must survive reopening");
            Map<String, String> old = new LinkedHashMap<>(current.values);
            old.keySet().retainAll(current.data.idsAt(1));
            old.put("display.clock", "24");
            old.put("reminders.voice", "alafasy");
            old.put("reminders.volume", "35");
            old.put("call.fajr.voice", "inherit");
            PhoneStore.preferences(c)
                    .edit()
                    .putString(
                            "settings",
                            new JSONObject()
                                    .put("version", 1)
                                    .put("values", new JSONObject(old))
                                    .toString())
                    .commit();
            PhoneStore migrated = PhoneStore.read(c);
            PhoneUiCheck.check(
                    migrated.value("display.clock").equals("24")
                            && migrated.value("call.fajr.voice").equals("inherit")
                            && migrated.volume == 35
                            && migrated.value("reminders.voice").equals("almalki")
                            && migrated.number("call.fajr.weekdays") == 127
                            && migrated.value("call.fajr.default-audio").equals("true"),
                    "v1 settings/default migration");
            old.remove("reminders.volume");
            PhoneStore.preferences(c)
                    .edit()
                    .putString(
                            "settings",
                            new JSONObject()
                                    .put("version", 1)
                                    .put("values", new JSONObject(old))
                                    .toString())
                    .commit();
            LanCheck.rejects(() -> PhoneStore.read(c));
            Map<String, String> versionTwo = new LinkedHashMap<>(migrated.values);
            versionTwo.keySet().retainAll(migrated.data.idsAt(2));
            PhoneStore.preferences(c)
                    .edit()
                    .putString(
                            "settings",
                            new JSONObject()
                                    .put("version", 2)
                                    .put("values", new JSONObject(versionTwo))
                                    .toString())
                    .commit();
            PhoneStore upgraded = PhoneStore.read(c);
            PhoneUiCheck.check(
                    upgraded.value("reminders.full-screen").equals("false")
                            && upgraded.volume == 35
                            && upgraded.values.entrySet().containsAll(versionTwo.entrySet()),
                    "v2 upgrade lost settings or enabled an unrequested full-screen alert");
            versionTwo.remove("reminders.volume");
            PhoneStore.preferences(c)
                    .edit()
                    .putString(
                            "settings",
                            new JSONObject()
                                    .put("version", 2)
                                    .put("values", new JSONObject(versionTwo))
                                    .toString())
                    .commit();
            LanCheck.rejects(() -> PhoneStore.read(c));
            migrated.save(c);
            Map<String, String> edits = new LinkedHashMap<>();
            for (String prayer : PhoneStore.PRAYERS)
                if (!prayer.equals("sunrise")) edits.put("call." + prayer + ".mode", "off");
            edits.put("call.fajr.mode", "adhan");
            edits.put("call.fajr.weekdays", "1");
            edits.put("prayer.zone", "Africa/Cairo");
            edits.put("call.fajr.default-audio", "false");
            edits.put("call.fajr.volume", "15");
            edits.put("call.fajr.duration", "30");
            PhoneStore sunday = migrated.changed(edits);
            PhoneUiCheck.check(
                    sunday.audioVolume(0) == 15
                            && sunday.audioDuration(0) == 30
                            && sunday.audioVolume(2) == 35,
                    "Per-prayer audio override leaked");
            Calendar day = new GregorianCalendar(sunday.zone());
            day.clear();
            day.set(2026, 10, 19, 0, 0);
            long now = day.getTimeInMillis();
            double[][] days = new double[8][6];
            for (int d = 0; d < 8; d++) {
                for (int prayer = 0; prayer < 6; prayer++)
                    days[d][prayer] = day.getTimeInMillis() + (5 + prayer * 2) * 3600000.0;
                day.add(Calendar.DATE, 1);
            }
            ReminderEvent next = sunday.nextEvent(days, now, new String[0]);
            day.setTimeInMillis((long) next.getPrayerTime());
            PhoneUiCheck.check(
                    day.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                            && next.getTime() > now + 2 * 86400000L,
                    "Weekday schedule did not plan the next week");
            edits.clear();
            edits.put("quiet.enabled", "true");
            edits.put("quiet.start", "0");
            edits.put("quiet.end", "0");
            edits.put("quiet.mode", "off");
            PhoneStore quiet = sunday.changed(edits);
            PhoneUiCheck.check(
                    !quiet.anyAlerts() && quiet.nextEvent(days, now, new String[0]) == null,
                    "Suppressed events repeatedly rearmed");
            edits.put("quiet.mode", "vibration");
            PhoneUiCheck.check(
                    sunday.changed(edits).eventMode(next) == 3, "Quiet vibration mode not applied");
            PhoneUiCheck.check(
                    PhoneAlarms.requiredChannels(sunday.changed(edits))
                            .equals(Collections.singleton(PhoneAlarms.VIBRATION)),
                    "All-day quiet hours required unused sound channels");
            edits.clear();
            edits.put("call.fajr.mode", "vibration");
            edits.put("call.fajr.before", "5");
            PhoneUiCheck.check(
                    PhoneAlarms.requiredChannels(sunday.changed(edits))
                            .equals(Collections.singleton(PhoneAlarms.VIBRATION)),
                    "Vibration reminders required an audible channel");
            Map<String, String> versionThree = new LinkedHashMap<>(migrated.values);
            versionThree.keySet().retainAll(migrated.data.idsAt(3));
            versionThree.put("reminders.duration", "60");
            versionThree.put("call.fajr.mode", "vibration");
            versionThree.put("quiet.enabled", "true");
            PhoneStore.preferences(c).edit().putString("settings", new JSONObject()
                    .put("version", 3).put("values", new JSONObject(versionThree)).toString()).commit();
            PhoneStore fourth = PhoneStore.read(c);
            PhoneUiCheck.check(fourth.values.entrySet().containsAll(versionThree.entrySet())
                    && fourth.audioPercent(0) == 100 && fourth.audioDuration(0) == 60
                    && fourth.vibrate(0), "v3 upgrade lost legacy sound, seconds or quiet settings");
            Calendar legacyNoon = new GregorianCalendar(fourth.zone());
            legacyNoon.clear();legacyNoon.set(2026, 10, 22, 12, 0);
            ReminderEvent legacyVibration = new ReminderEvent("legacy-vibration", "call", 0,
                    legacyNoon.getTimeInMillis(), legacyNoon.getTimeInMillis());
            PhoneUiCheck.check(fourth.eventMode(legacyVibration) == 3 && fourth.eventVibrate(legacyVibration),
                    "Legacy vibration-only mode lost haptic output after upgrade");
            fourth.save(c);
            PhoneUiCheck.check(new JSONObject(PhoneStore.preferences(c).getString("settings", "")).getInt("version") == 6,
                    "Updated settings did not persist the new schema");
            Map<String, String> versionFour = new LinkedHashMap<>(fourth.values);
            versionFour.keySet().retainAll(fourth.data.idsAt(4));
            versionFour.put("appearance.theme", "frost");
            versionFour.put("reminders.enabled", "false");
            PhoneStore.preferences(c).edit().putString("settings", new JSONObject()
                    .put("version", 4).put("values", new JSONObject(versionFour)).toString()).commit();
            PhoneStore fifth = PhoneStore.read(c);
            PhoneUiCheck.check(fifth.values.entrySet().containsAll(versionFour.entrySet())
                    && fifth.value("appearance.home-background").equals("true"),
                    "v4 upgrade lost saved settings while adding the home background");
            fifth.changed(Collections.singletonMap("appearance.home-background", "false")).save(c);
            PhoneUiCheck.check(PhoneStore.read(c).value("appearance.home-background").equals("false"),
                    "Home background opt-out was not preserved");
            Map<String, String> versionFive = new LinkedHashMap<>(PhoneStore.read(c).values);
            versionFive.keySet().retainAll(fifth.data.idsAt(5));
            versionFive.put("prayer.travel-refresh", "true");
            PhoneStore.preferences(c).edit().putString("settings", new JSONObject()
                    .put("version", 5).put("values", new JSONObject(versionFive)).toString()).commit();
            PhoneStore sixth = PhoneStore.read(c);
            PhoneUiCheck.check(sixth.values.entrySet().containsAll(versionFive.entrySet())
                            && sixth.value("prayer.background-location").equals("false"),
                    "Upgrade must preserve foreground travel without granting background consent");
            sixth.save(c);
            PhoneUiCheck.check(PhoneStore.read(c).value("prayer.background-location").equals("false"),
                    "Background location must remain off after persisting migration");
            Map<String, String> dayEdits = new LinkedHashMap<>();
            dayEdits.put("call.fajr.mode", "adhan");dayEdits.put("call.fajr.enabled", "true");
            dayEdits.put("call.fajr.weekdays", "127");dayEdits.put("quiet.enabled", "false");
            dayEdits.put("call.fajr.day-0-sound", "silent");dayEdits.put("call.fajr.day-0-vibrate", "true");
            dayEdits.put("call.fajr.length-percent", "75");dayEdits.put("call.fajr.default-audio", "false");
            PhoneStore perDay = fourth.changed(dayEdits);
            Calendar sundayDate = new GregorianCalendar(perDay.zone());
            sundayDate.clear();sundayDate.set(2026, 10, 22, 0, 4);
            long prayerAt = sundayDate.getTimeInMillis();
            ReminderEvent onSunday = new ReminderEvent("fixture", "call", 0, prayerAt, prayerAt);
            ReminderEvent beforeSunday = new ReminderEvent("fixture-before", "before", 0, prayerAt - 300000, prayerAt);
            PhoneUiCheck.check(perDay.eventMode(onSunday) == 4 && perDay.eventVibrate(onSunday)
                    && perDay.effectiveMode(0, 1) == 2 && !perDay.vibrateOnDay(0, 1)
                    && perDay.audioPercent(0) == 75 && perDay.audioDuration(0) == 0,
                    "Day sound/vibration or per-prayer percent leaked into defaults");
            PhoneStore disabled = perDay.changed(Collections.singletonMap("call.fajr.enabled", "false"));
            PhoneUiCheck.check(disabled.eventMode(onSunday) == 0 && !disabled.eventVibrate(onSunday),
                    "Day override re-enabled a disabled prayer");
            dayEdits.clear();dayEdits.put("call.fajr.day-0-sound", "adhan");
            dayEdits.put("quiet.enabled", "true");dayEdits.put("quiet.start", "0");
            dayEdits.put("quiet.end", "60");dayEdits.put("quiet.mode", "silent");
            PhoneStore boundary = perDay.changed(dayEdits);
            PhoneUiCheck.check(boundary.eventMode(beforeSunday) == 2 && boundary.eventMode(onSunday) == 4
                    && !boundary.eventVibrate(onSunday),
                    "Legacy quiet hours stopped following actual event time at the date boundary");
            migrated.save(c);
            PhoneAudio.importFile(c, c.getAssets().open("adhan/almalki.mp3"), "reminders.voice", "Library default");
            PhoneStore imported = PhoneStore.read(c);
            String hash = imported.value("audio.custom.default");
            PhoneUiCheck.check(
                    imported.voice(0).equals("custom")
                            && PhoneAudio.file(c, imported, 0, true).isFile(),
                    "Imported global voice not inherited");
            PhoneAudio.importFile(c, c.getAssets().open("adhan/chebae_fajr.mp3"), "call.fajr.voice", "Library Fajr");
            PhoneStore perPrayer = PhoneStore.read(c);
            PhoneUiCheck.check(perPrayer.value("call.fajr.voice").equals("custom")
                            && perPrayer.value("audio.custom.default").equals(hash)
                            && !perPrayer.value("audio.custom.fajr").equals(hash)
                            && PhoneAudio.file(c, perPrayer, 0, false).isFile()
                            && perPrayer.value("call.dhuhr.voice").equals("inherit")
                            && perPrayer.voice(2).equals("custom"),
                    "A custom prayer recording replaced the default or another prayer's inheritance");
            String fajrHash = perPrayer.value("audio.custom.fajr");
            PhoneUiCheck.check(PhoneAudio.library(c).size() == 2
                            && PhoneAudio.name(c, perPrayer, "reminders.voice").equals("Library default")
                            && PhoneAudio.name(c, perPrayer, "call.fajr.voice").equals("Library Fajr"),
                    "Named imports did not retain two reusable library recordings");
            PhoneAudio.select(c, "call.dhuhr.voice", hash);
            PhoneStore reused = PhoneStore.read(c);
            PhoneUiCheck.check(reused.value("audio.custom.dhuhr").equals(hash)
                            && reused.value("call.dhuhr.voice").equals("custom")
                            && reused.value("audio.custom.fajr").equals(fajrHash),
                    "Selecting an existing recording did not preserve other prayer selections");
            PhoneAudio.select(c, "reminders.voice", fajrHash);
            PhoneAudio.select(c, "call.dhuhr.voice", fajrHash);
            PhoneAudio.cleanup(c);
            PhoneUiCheck.check(PhoneAudio.library(c).size() == 2 && PhoneAudio.file(c, hash).isFile(),
                    "Cleanup removed an unselected library recording");
            PhoneAudio.select(c, "reminders.voice", hash);
            File index = new File(directory, "custom-audio/library.json");
            byte[] validIndex = Files.readAllBytes(index.toPath());
            Files.write(index.toPath(), "{invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            LanCheck.rejects(() -> PhoneAudio.cleanup(c));
            LanCheck.rejects(() -> PhoneAudio.importFile(c, c.getAssets().open("adhan/almalki.mp3"),
                    "reminders.voice", "Must not overwrite damaged index"));
            PhoneUiCheck.check(new String(Files.readAllBytes(index.toPath()), java.nio.charset.StandardCharsets.UTF_8)
                                    .equals("{invalid")
                            && PhoneAudio.file(c, hash).isFile() && PhoneAudio.file(c, fajrHash).isFile()
                            && PhoneStore.read(c).value("audio.custom.default").equals(hash),
                    "Damaged library lost its index, recording or previous selection");
            for (String malformed : new String[] {
                    "{\"version\":\"1\",\"entries\":{}}", "{\"version\":1.5,\"entries\":{}}",
                    "{\"version\":1,\"entries\":{\"" + hash + "\":123}}",
                    "{\"version\":1,\"entries\":{\"" + hash + "\":null}}",
                    "{\"version\":1,\"entries\":{}} trailing"}) {
                byte[] invalidIndex = malformed.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                Files.write(index.toPath(), invalidIndex);
                LanCheck.rejects(() -> PhoneAudio.cleanup(c));
                PhoneUiCheck.check(Arrays.equals(invalidIndex, Files.readAllBytes(index.toPath()))
                                && PhoneAudio.file(c, hash).isFile() && PhoneAudio.file(c, fajrHash).isFile(),
                        "Malformed audio metadata was coerced, overwritten or deleted recordings");
            }
            Files.write(index.toPath(), validIndex);
            Files.delete(index.toPath());
            PhoneUiCheck.check(PhoneAudio.library(c).size() == 2
                            && PhoneStore.read(c).value("audio.custom.default").equals(hash)
                            && PhoneStore.read(c).value("audio.custom.fajr").equals(fajrHash),
                    "Legacy slot recordings were not adopted without changing selections");
            LanCheck.rejects(() -> PhoneAudio.select(c, "reminders.voice", "../outside"));
            LanCheck.rejects(() -> PhoneAudio.importFile(c, c.getAssets().open("adhan/almalki.mp3"),
                    "reminders.voice", " "));
            PhoneUiCheck.check(PhoneAudio.library(c).size() == 2
                            && PhoneStore.read(c).value("audio.custom.default").equals(hash),
                    "Invalid library selection or name changed previous audio");
            Context failedSelection = new ContextWrapper(c) {
                @Override public Context createDeviceProtectedStorageContext() { return this; }
                @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                    SharedPreferences delegate = c.getSharedPreferences(name, mode);
                    return (SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                            SharedPreferences.class.getClassLoader(), new Class<?>[] {SharedPreferences.class},
                            (proxy, method, arguments) -> {
                                if (!method.getName().equals("edit")) return method.invoke(delegate, arguments);
                                return java.lang.reflect.Proxy.newProxyInstance(
                                        SharedPreferences.Editor.class.getClassLoader(),
                                        new Class<?>[] {SharedPreferences.Editor.class},
                                        (editor, operation, values) -> operation.getName().equals("commit") ? false
                                                : operation.getReturnType().equals(Void.TYPE) ? null : editor);
                            });
                }
            };
            LanCheck.rejects(() -> PhoneAudio.importFile(failedSelection,
                    c.getAssets().open("adhan/almalki.mp3"), "call.asr.voice", "Durable library name"));
            PhoneUiCheck.check(PhoneStore.read(c).value("audio.custom.default").equals(hash)
                            && PhoneStore.read(c).value("call.asr.voice").equals("inherit")
                            && PhoneAudio.name(c, PhoneStore.read(c), "reminders.voice").equals("Durable library name")
                            && PhoneAudio.library(c).size() == 2,
                    "Failed selection changed settings or lost the durably named library entry");
            LanCheck.rejects(() ->
                    PhoneAudio.importFile(
                            c, new ByteArrayInputStream(new byte[] {1, 2, 3}), "reminders.voice"));
            PhoneUiCheck.check(
                    PhoneStore.read(c).value("audio.custom.default").equals(hash),
                    "Invalid audio lost previous selection");
            LanCheck.rejects(() ->
                    PhoneAudio.importFile(
                            c,
                            new InputStream() {
                                @Override
                                public int read() throws IOException {
                                    throw new IOException("Interrupted fixture copy");
                                }
                            },
                            "reminders.voice"));
            PhoneUiCheck.check(
                    PhoneStore.read(c).value("audio.custom.default").equals(hash),
                    "Failed copy lost previous audio");
            LanCheck.rejects(() ->
                    PhoneAudio.importFile(
                            c,
                            new InputStream() {
                                long remaining = PhoneAudio.MAX_BYTES + 1;

                                @Override
                                public int read() {
                                    return remaining-- > 0 ? 0 : -1;
                                }

                                @Override
                                public int read(byte[] bytes, int offset, int length) {
                                    if (remaining <= 0) return -1;
                                    int n = (int) Math.min(length, remaining);
                                    Arrays.fill(bytes, offset, offset + n, (byte) 0);
                                    remaining -= n;
                                    return n;
                                }
                            },
                            "reminders.voice"));
            PhoneUiCheck.check(
                    PhoneStore.read(c).value("audio.custom.default").equals(hash),
                    "Oversized import lost previous audio");
            PhoneAlarms.channels(c);
            NotificationManager manager = c.getSystemService(NotificationManager.class);
            PhoneUiCheck.check(
                    manager.getNotificationChannel(PhoneAlarms.SILENT).getSound() == null
                            && !manager.getNotificationChannel(PhoneAlarms.SILENT).shouldVibrate(),
                    "Silent channel produces output");
            PhoneUiCheck.check(
                    manager.getNotificationChannel(PhoneAlarms.VIBRATION).getSound() == null
                            && manager.getNotificationChannel(PhoneAlarms.VIBRATION)
                                    .shouldVibrate(),
                    "Vibration channel has sound or no vibration");
        } finally {
            PhoneStore.preferences(c).edit().clear().commit();
            base.deleteSharedPreferences(key + "phone-prayer");
            File audio = new File(directory, "custom-audio");
            File[] files = audio.listFiles();
            if (files != null) for (File file : files) Files.delete(file.toPath());
            Files.deleteIfExists(audio.toPath());
            Files.delete(directory.toPath());
        }
    }
}

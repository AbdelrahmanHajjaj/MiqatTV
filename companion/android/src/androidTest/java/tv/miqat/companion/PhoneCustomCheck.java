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
            Map<String, String> old = new LinkedHashMap<>(current.values);
            old.keySet().retainAll(current.data.idsAt(1));
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
                    migrated.value("call.fajr.voice").equals("inherit")
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
            PhoneUiCheck.check(new JSONObject(PhoneStore.preferences(c).getString("settings", "")).getInt("version") == 4,
                    "Updated settings did not persist the new schema");
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
            PhoneAudio.importFile(c, c.getAssets().open("adhan/almalki.mp3"), "reminders.voice");
            PhoneStore imported = PhoneStore.read(c);
            String hash = imported.value("audio.custom.default");
            PhoneUiCheck.check(
                    imported.voice(0).equals("custom")
                            && PhoneAudio.file(c, imported, 0, true).isFile(),
                    "Imported global voice not inherited");
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

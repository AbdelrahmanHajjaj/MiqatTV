package tv.miqat.companion;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Switch;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;
import tv.miqat.core.ReminderEvent;

final class PhoneFastingCheck {
    static void run(Instrumentation test, boolean capture) throws Exception {
        Context c = test.getTargetContext();
        PhoneStore original = PhoneStore.read(c);
        SharedPreferences prefs = c.getSharedPreferences("companion", 0);
        String language = prefs.getString("language", "system");
        SharedPreferences alarms = PhoneStore.preferences(c);
        String handled = alarms.getString("handled", "[]");
        NotificationManager notices = c.getSystemService(NotificationManager.class);
        try {
            for (String lang : new String[] {"en", "ar"}) {
                prefs.edit().putString("language", lang).commit();
                Map<String, String> fixture = new LinkedHashMap<>();
                fixture.put("prayer.location", "cairo");
                fixture.put("prayer.zone", "Africa/Cairo");
                fixture.put("prayer.location-ready", "true");
                fixture.put("prayer.hijri-offset", "0");
                fixture.put("reminders.enabled", "false");
                for (String id : new String[] {"suhoor", "iftar", "ramadan", "ashura",
                        "arafah", "white-days", "monday-thursday"})
                    fixture.put("fasting." + id, "false");
                fixture.put("fasting.suhoor-minutes", "30");
                original.changed(fixture).save(c);
                alarms.edit().putString("handled", "[]").commit();
                MainActivity a = LiveUiCheck.launch(test);
                try {
                    PhoneCalendarCheck.events(test, a, PhoneStore.read(c));
                    PhoneUiCheck.on(test, () -> {
                        a.tab("phone-settings");
                        a.body.findViewWithTag("phone.section.fasting").performClick();
                        toggle(a, "suhoor");
                        toggle(a, "iftar");
                        toggle(a, "monday-thursday");
                    });
                    PhoneUiCheck.capture(test, a, lang + "-phone-fasting", capture);
                    verifyDelivery(c, alarms, notices, lang.equals("ar"));
                    PhoneUiCheck.on(test, () -> toggle(a, "monday-thursday"));
                    CompanionCheck.check(alarms.getString("next-event", "").isEmpty(),
                            "Disabling the last fasting selection left an alarm armed");
                } finally {
                    PhoneUiCheck.on(test, a::finish);
                }
            }
        } finally {
            original.save(c);
            prefs.edit().putString("language", language).commit();
            alarms.edit().putString("handled", handled).commit();
            PhoneAlarms.rebuild(c);
            notices.cancel(42030);
            notices.cancel(42031);
        }
    }

    private static void toggle(MainActivity a, String id) {
        Switch control = a.body.findViewWithTag("phone.fasting." + id + ".edit");
        CompanionCheck.check(control != null, "Missing fasting control " + id);
        boolean enabled = !control.isChecked();
        control.performClick();
        CompanionCheck.check(a.phoneUi.settings.value("fasting." + id).equals(Boolean.toString(enabled)),
                "Fasting control did not save " + id);
    }

    private static void verifyDelivery(Context c, SharedPreferences alarms, NotificationManager notices,
            boolean arabic) throws Exception {
        PhoneStore settings = PhoneStore.read(c);
        String armed = alarms.getString("next-event", "");
        ReminderEvent next = PhoneFasting.next(settings, System.currentTimeMillis(), new String[0]);
        CompanionCheck.check(!settings.enabled && (next == null ? armed.isEmpty()
                        : PhoneFasting.kind(new JSONObject(armed).getString("kind"))),
                "Fasting reminders were not scheduled independently of prayer alerts");
        ZoneId zone = settings.zone().toZoneId();
        LocalDate date = LocalDate.now(zone).plusDays(2)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        long monday = date.atStartOfDay(zone).toInstant().toEpochMilli();
        for (int i = 0; i < 6; i++) {
            ReminderEvent candidate = PhoneFasting.next(settings, monday, new String[0]);
            if (candidate != null && candidate.getTime() < settings.day(monday, 1).start) break;
            date = date.plusWeeks(1);
            monday = date.atStartOfDay(zone).toInstant().toEpochMilli();
        }
        PhoneSchedule.Day day = settings.day(monday, 0);
        ReminderEvent suhoor = PhoneFasting.next(settings, monday, new String[0]);
        CompanionCheck.check(suhoor.getKind().equals("suhoor")
                        && suhoor.getTime() == day.times[0] - 30 * 60_000,
                "Monday suhoor was not based on local Fajr");
        ReminderEvent iftar = PhoneFasting.next(settings, monday, new String[] {suhoor.getId()});
        CompanionCheck.check(iftar.getKind().equals("iftar") && iftar.getTime() == day.times[4],
                "Monday iftar was not based on local Maghrib");
        for (ReminderEvent event : new ReminderEvent[] {suhoor, iftar}) {
            PhoneAlarms.arm(c, event, System.currentTimeMillis());
            ReminderEvent claim = PhoneAlarms.claim(c, event.getId(), (long) event.getTime());
            CompanionCheck.check(claim != null, "The due fasting reminder could not be claimed");
            CompanionCheck.check(PhoneAlarms.claim(c, event.getId(), (long) event.getTime()) == null,
                    "The same fasting reminder was claimed twice");
            PhoneAlarms.notify(c, claim, false);
        }
        LiveUiCheck.until(() -> hasNotice(notices, 42030, arabic ? "السحور" : "Suhoor")
                && hasNotice(notices, 42031, arabic ? "الإفطار" : "Iftar"));
        notices.cancel(42030);
        notices.cancel(42031);
    }

    private static boolean hasNotice(NotificationManager notices, int id, String title) {
        for (android.service.notification.StatusBarNotification active : notices.getActiveNotifications()) {
            Notification n = active.getNotification();
            if (active.getId() == id && n.getChannelId().equals(PhoneAlarms.FASTING)
                    && n.extras.getCharSequence(Notification.EXTRA_TITLE, "").toString().contains(title))
                return true;
        }
        return false;
    }
}

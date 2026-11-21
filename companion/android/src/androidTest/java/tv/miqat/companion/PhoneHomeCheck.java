package tv.miqat.companion;

import android.app.Instrumentation;
import android.icu.util.IslamicCalendar;
import android.view.View;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.LinkedHashMap;
import java.util.Map;

/** Approved Home controls and real calendar-event links; no location or alarm output is requested. */
final class PhoneHomeCheck {
    static void run(Instrumentation test, MainActivity a) throws Exception {
        PhoneStore saved = PhoneStore.read(a);
        String savedMessage = a.phoneUi.locationUi.locationMessage;
        PhoneUiCheck.on(test, () -> {
            a.tab("prayers");
        });
        PhoneUiCheck.on(test, () -> {
            try {
                JSONArray lines = a.phoneUi.data.source.getJSONArray("prayerVerse");
                StringBuilder canonical = new StringBuilder();
                for (int i = 0; i < lines.length(); i++) {
                    if (i != 0) canonical.append(' ');
                    canonical.append(lines.getString(i));
                }
                TextView verse = a.body.findViewWithTag("phone.verse");
                PhoneUiCheck.check(verse != null && verse.getText().toString().equals(canonical.toString())
                                && verse.getTextDirection() == View.TEXT_DIRECTION_RTL,
                        "Home lost the exact joined TV verse or its Arabic text direction");
                PhoneUiCheck.check(a.body.findViewWithTag("phone.verse.reference") == null,
                        "Home retained a reference label below the verse");
                View hero = a.body.findViewWithTag("phone.hero");
                View date = a.body.findViewWithTag("phone.today");
                View city = a.body.findViewWithTag("phone.location");
                PhoneUiCheck.check(hero != null && hero.findViewWithTag("phone.today") == date
                                && hero.findViewWithTag("phone.location") == city
                                && city.getParent() == date.getParent()
                                && a.body.findViewWithTag("phone.home.location.detect") == null,
                        "Date and one city action must share the prayer card without a duplicate detector");
                if (!PhoneLocation.allowed(a)) {
                    city.performClick();
                    PhoneUiCheck.check(a.page.equals("phone-settings") && a.section.equals("calculation")
                                    && !a.phoneUi.locationUi.locator.running,
                            "Denied Home location must open full settings without requesting a fix");
                    a.tab("prayers");
                }
            } catch (Exception failure) { throw new AssertionError(failure); }
        });
        try {
            Map<String,String> changes = new LinkedHashMap<>();
            changes.put("prayer.location", "cairo");
            changes.put("prayer.location-ready", "true");
            changes.put("prayer.latitude", "30.0444");
            changes.put("prayer.longitude", "31.2357");
            changes.put("prayer.zone", "Africa/Cairo");
            changes.put("prayer.hijri-offset", "0");
            PhoneStore fixture = saved.changed(changes);
            PhoneUiCheck.on(test, () -> {
                try {
                    a.phoneUi.settings = fixture;
                    a.phoneUi.home.stop();
                    IslamicCalendar date = PhoneCalendar.calendar(fixture, System.currentTimeMillis(), a.ar);
                    date.set(android.icu.util.Calendar.MONTH, 11);
                    date.set(android.icu.util.Calendar.DATE, 9);
                    long eventDay = PhoneCalendar.civilStamp(fixture, date);
                    a.body.removeAllViews();
                    a.phoneUi.home.render(eventDay);
                    TextView event = a.body.findViewWithTag("phone.hijri.event");
                    String label = PhoneHijriEvents.label(a, fixture, eventDay, a.ar);
                    PhoneUiCheck.check(event != null && event.getText().toString().contains(label),
                            "Home omitted today's Hijri event");
                    event.performClick();
                    TextView selected = a.phoneUi.choiceDialog.getWindow().getDecorView()
                            .findViewWithTag("calendar.selected.date");
                    PhoneUiCheck.check(selected.getText().toString()
                                    .equals(a.phoneUi.digits(PhoneTime.hijri(fixture, eventDay, a.ar))),
                            "Home event opened a different calendar date");
                    a.phoneUi.choiceDialog.dismiss();
                    date.set(android.icu.util.Calendar.MONTH, 5);
                    date.set(android.icu.util.Calendar.DATE, 11);
                    a.body.removeAllViews();
                    a.phoneUi.home.render(PhoneCalendar.civilStamp(fixture, date));
                    PhoneUiCheck.check(a.body.findViewWithTag("phone.hijri.event") == null,
                            "Ordinary Home dates retained an empty or unrelated event row");
                    for (String message : new String[] {"phone_location_updated", "phone_location_found", "phone_location_working"}) {
                        a.phoneUi.locationUi.locationMessage = message;
                        a.body.removeAllViews();
                        a.phoneUi.home.render(eventDay);
                        PhoneUiCheck.check(a.body.findViewWithTag("phone.location.message") == null
                                        && a.body.findViewWithTag("phone.location") != null,
                                "Home retained location success narration or lost its city action");
                    }
                    a.phoneUi.locationUi.locationMessage = "phone_location_timeout";
                    a.body.removeAllViews();
                    a.phoneUi.home.render(eventDay);
                    TextView failure = a.body.findViewWithTag("phone.location.message");
                    PhoneUiCheck.check(failure != null
                                    && failure.getText().toString().equals(a.t("phone_location_timeout"))
                                    && ((View) a.body.findViewWithTag("phone.hero"))
                                            .findViewWithTag("phone.location.message") == null,
                            "Home must retain location failure feedback outside the prayer card");
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
            PhoneUiCheck.check(PhoneStore.read(a).values.equals(saved.values),
                    "Viewing Home events changed persisted prayer settings");
        } finally {
            PhoneUiCheck.on(test, () -> {
                a.phoneUi.settings = saved;
                a.phoneUi.locationUi.locationMessage = savedMessage;
                a.render();
            });
        }
        if (test instanceof CompanionCheck && ((CompanionCheck) test).captures) review(test, a);
    }

    private static void review(Instrumentation test, MainActivity a) throws Exception {
        PhoneStore saved = PhoneStore.read(a);
        android.content.SharedPreferences prefs = PhoneStore.preferences(a);
        boolean hadComplete = prefs.contains("onboarding-complete");
        boolean complete = prefs.getBoolean("onboarding-complete", false);
        try {
            Map<String,String> changes = new LinkedHashMap<>();
            changes.put("prayer.location", "cairo");
            changes.put("prayer.location-ready", "true");
            changes.put("prayer.latitude", "30.0444");
            changes.put("prayer.longitude", "31.2357");
            changes.put("prayer.zone", "Africa/Cairo");
            changes.put("prayer.hijri-offset", "0");
            changes.put("prayer.travel-refresh", "false");
            changes.put("prayer.background-location", "false");
            changes.put("appearance.theme", "mihrab");
            changes.put("appearance.background", "miqat_signature");
            changes.put("appearance.home-background", "true");
            PhoneStore fixture = saved.changed(changes);
            fixture.save(a);
            PhoneUiCheck.check(prefs.edit().putBoolean("onboarding-complete", true).commit(),
                    "Cannot prepare completed-Home capture fixture");
            PhoneUiCheck.on(test, () -> {
                a.tab("prayers");
                a.render();
                a.phoneUi.home.stop();
                if (a.toast != null) a.toast.cancel();
            });
            String language = a.ar ? "ar" : "en";
            renderReview(test, a, fixture, 5, 11);
            PhoneUiCheck.capture(test, a, language + "-approved-home-ordinary", true);
            renderReview(test, a, fixture, 11, 9);
            PhoneUiCheck.capture(test, a, language + "-approved-home-arafah", true);
            PhoneUiCheck.on(test, () -> {
                a.body.findViewWithTag("phone.hijri.event").performClick();
                a.phoneUi.home.stop();
            });
            PhoneUiCheck.capture(test, a, language + "-approved-calendar-arafah", true);
        } finally {
            PhoneUiCheck.on(test, () -> {
                a.phoneUi.home.stop();
                if (a.phoneUi.choiceDialog != null) a.phoneUi.choiceDialog.dismiss();
            });
            saved.save(a);
            android.content.SharedPreferences.Editor restore = prefs.edit();
            if (hadComplete) restore.putBoolean("onboarding-complete", complete);
            else restore.remove("onboarding-complete");
            PhoneUiCheck.check(restore.commit(), "Cannot restore completed-Home capture fixture");
            PhoneUiCheck.on(test, () -> {
                a.render();
            });
        }
    }

    private static void renderReview(
            Instrumentation test, MainActivity a, PhoneStore fixture, int month, int day) {
        PhoneUiCheck.on(test, () -> {
            try {
                a.phoneUi.settings = fixture;
                a.phoneUi.home.stop();
                IslamicCalendar date = PhoneCalendar.calendar(fixture, System.currentTimeMillis(), a.ar);
                date.set(android.icu.util.Calendar.MONTH, month);
                date.set(android.icu.util.Calendar.DATE, day);
                java.util.Calendar noon = new java.util.GregorianCalendar(fixture.zone());
                noon.setTimeInMillis(PhoneCalendar.civilStamp(fixture, date));
                noon.set(java.util.Calendar.HOUR_OF_DAY, 12);
                a.body.removeAllViews();
                a.phoneUi.home.render(noon.getTimeInMillis());
                a.phoneUi.home.stop();
                a.scroll.scrollTo(0, 0);
            } catch (Exception invalid) { throw new AssertionError(invalid); }
        });
    }
}

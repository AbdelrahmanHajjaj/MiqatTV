package tv.miqat.companion;

import android.app.Instrumentation;
import android.icu.util.IslamicCalendar;
import android.widget.TextView;

import java.util.*;

/** Civil-day/Hijri navigation and bounded map rendering; no map network is requested here. */
final class PhoneCalendarCheck {
    private PhoneCalendarCheck() {}

    static void mapNetwork(Instrumentation test, MainActivity a, boolean capture) throws Exception {
        PhoneStore original = PhoneStore.read(a);
        try {
            Map<String, String> fixture = new LinkedHashMap<>();
            fixture.put("prayer.location-ready", "true");
            fixture.put("prayer.location", "cairo");
            fixture.put("prayer.latitude", "30.0444");
            fixture.put("prayer.longitude", "31.2357");
            fixture.put("reminders.enabled", "false");
            original.changed(fixture).save(a);
            PhoneUiCheck.on(
                    test,
                    () -> {
                        a.navigate("qibla");
                        a.body.findViewWithTag("qibla.mode.map").performClick();
                        PhoneUiCheck.check(
                                a.body.findViewWithTag("qibla.map") == null,
                                "Map loaded before explicit tap");
                        a.body.findViewWithTag("qibla.map.load").performClick();
                    });
            LiveUiCheck.untilUi(
                    test,
                    () -> {
                        android.webkit.WebView map = a.body.findViewWithTag("qibla.map");
                        return map != null && map.getProgress() == 100;
                    });
            PhoneUiCheck.on(
                    test,
                    () -> {
                        android.webkit.WebView map = a.body.findViewWithTag("qibla.map");
                        PhoneUiCheck.check(
                                !map.getSettings().getJavaScriptEnabled()
                                        && !map.getSettings().getAllowFileAccess()
                                        && !map.getSettings().getAllowContentAccess(),
                                "Map expanded device access");
                        PhoneUiCheck.check(
                                ((TextView) a.body.findViewWithTag("qibla.map.status"))
                                                .getText()
                                                .length()
                                        == 0,
                                "Map provider request failed");
                        PhoneUiCheck.check(
                                map.getWidth() > 0 && map.getWidth() == map.getHeight(),
                                "Map viewport must be square so endpoints and attribution fit");
                    });
            PhoneUiCheck.capture(test, a, (a.ar ? "ar" : "en") + "-phone-qibla-map", capture);
            PhoneUiCheck.on(
                    test,
                    () -> {
                        a.tab("prayers");
                        PhoneUiCheck.check(
                                a.body.findViewWithTag("qibla.map") == null,
                                "Map survived leaving its screen");
                    });
        } finally {
            original.save(a);
            PhoneUiCheck.on(test, a::render);
        }
    }

    static void returnsHome(Instrumentation test) throws Exception {
        MainActivity a = LiveUiCheck.launch(test);
        try { verifyReturn(test, a); }
        finally { PhoneUiCheck.on(test, a::finish); }
    }

    private static void verifyReturn(Instrumentation test, MainActivity a) throws Exception {
        for (String close : new String[]{"done", "back", "cancel"}) {
            PhoneUiCheck.on(test, () -> {
                a.tab("prayers");
                a.body.findViewWithTag("phone.today").performClick();
                android.view.View root = a.phoneUi.choiceDialog.getWindow().getDecorView();
                root.findViewWithTag("calendar.selected.previous-day").performClick();
                calendarTimes(a, root, a.phoneUi.settings.day(System.currentTimeMillis(), -1));
                root.findViewWithTag("calendar.selected.next-day").performClick();
                root.findViewWithTag("calendar.selected.previous-day").performClick();
                if (close.equals("done")) a.phoneUi.choiceDialog.getButton(-2).performClick();
                else if (close.equals("cancel")) a.phoneUi.choiceDialog.cancel();
            });
            if (close.equals("back")) {
                // Route physical Back only after Android focuses the calendar window.
                LiveUiCheck.untilUi(test, () -> a.phoneUi.choiceDialog != null
                        && a.phoneUi.choiceDialog.getWindow().getDecorView().hasWindowFocus());
                test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            }
            LiveUiCheck.untilUi(test, () -> a.phoneUi.choiceDialog == null);
            PhoneUiCheck.on(test, () -> {
                PhoneUiCheck.check(a.page.equals("prayers")
                                && a.body.findViewWithTag("phone.hero") != null
                                && a.body.findViewWithTag("phone.countdown") != null
                                && a.body.findViewWithTag("qibla.open") != null
                                && a.body.findViewWithTag("calendar.today") == null,
                        "Closing the Hijri calendar via " + close + " must return to the full live Home");
                PhoneSchedule.Day today = a.phoneUi.settings.day(System.currentTimeMillis(), 0);
                for (int i = 0; i < PhoneStore.PRAYERS.length; i++)
                    PhoneUiCheck.check(((TextView) a.body.findViewWithTag(
                                    "phone.prayer." + PhoneStore.PRAYERS[i] + ".time"))
                                    .getText().toString().equals(a.phoneUi.time(today.times[i])),
                            "Calendar dismissal left another day's prayer times on Home");
            });
        }
    }

    static void run(Instrumentation test, MainActivity a) throws Exception {
        PhoneStore original = PhoneStore.read(a);
        events(test, a, original);
        Map<String, String> changed = new LinkedHashMap<>();
        changed.put("prayer.zone", "America/New_York");
        changed.put("prayer.hijri-offset", "2");
        PhoneStore settings = original.changed(changed);
        long beforeDst = java.time.Instant.parse("2026-03-07T17:00:00Z").toEpochMilli();
        long afterDst = java.time.Instant.parse("2026-03-09T16:00:00Z").toEpochMilli();
        PhoneUiCheck.check(
                PhoneCalendar.offset(settings, beforeDst, afterDst) == 2,
                "Calendar used elapsed hours rather than civil dates across DST");
        IslamicCalendar corrected = PhoneCalendar.calendar(settings, beforeDst, false);
        PhoneUiCheck.check(
                PhoneCalendar.offset(
                                settings, beforeDst, PhoneCalendar.civilStamp(settings, corrected))
                        == 0,
                "Hijri correction changed selected prayer date");
        corrected.set(android.icu.util.Calendar.MONTH, 11);
        corrected.set(android.icu.util.Calendar.DATE, 25);
        int year = corrected.get(android.icu.util.Calendar.YEAR);
        corrected.add(android.icu.util.Calendar.MONTH, 1);
        PhoneUiCheck.check(
                corrected.get(android.icu.util.Calendar.YEAR) == year + 1,
                "Hijri year boundary did not advance");
        corrected.set(android.icu.util.Calendar.DATE, 1);
        int length = corrected.getActualMaximum(android.icu.util.Calendar.DATE);
        PhoneUiCheck.check(length == 29 || length == 30, "Invalid Hijri month length");
        long first = PhoneCalendar.civilStamp(settings, corrected);
        for (int day = 1; day <= length; day++) {
            corrected.set(android.icu.util.Calendar.DATE, day);
            PhoneUiCheck.check(
                    PhoneCalendar.offset(
                                    settings, first, PhoneCalendar.civilStamp(settings, corrected))
                            == day - 1,
                    "Hijri grid skipped or repeated a civil prayer day");
        }
        for (double[] origin :
                new double[][] {
                    {30.0444, 31.2357},
                    {51.5074, -.1278},
                    {-36.85, 174.76},
                    {0, 179.9},
                    {21.45, 39.8}
                }) {
            double[][] route = QiblaMap.route(origin[0], origin[1]);
            for (int i = 1; i < route.length; i++)
                PhoneUiCheck.check(
                        Math.abs(route[i][0] - route[i - 1][0]) < .5,
                        "Qibla line took the long path across the dateline");
            String html = QiblaMap.html(origin[0], origin[1], 0, 0xffffff, "Kaaba");
            int tiles = html.split("<img ", -1).length - 1;
            String svg = html.substring(html.indexOf("<svg"), html.indexOf("</svg>") + 6);
            org.w3c.dom.Document drawing =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance()
                            .newDocumentBuilder()
                            .parse(new org.xml.sax.InputSource(new java.io.StringReader(svg)));
            PhoneUiCheck.check(
                    drawing.getElementsByTagName("polyline").getLength() == 2
                            && drawing.getElementsByTagName("circle").getLength() == 2
                            && ((org.w3c.dom.Element)
                                            drawing.getElementsByTagName("polyline").item(1))
                                    .getAttribute("stroke")
                                    .equals("#0A6060")
                            && html.contains("id='credit'"),
                    "Map route, endpoints and attribution must render independently");
            PhoneUiCheck.check(
                    tiles > 0
                            && tiles <= 9
                            && !html.contains("NaN")
                            && !html.contains("Infinity")
                            && html.contains("© OpenStreetMap contributors")
                            && !html.contains("<script"),
                    "Unbounded or invalid Qibla map document");
        }
        PhoneUiCheck.on(
                test,
                () -> {
                    a.tab("prayers");
                    android.view.View sunrise = a.body.findViewWithTag("phone.prayer.sunrise");
                    PhoneUiCheck.check(
                            !sunrise.isClickable() && !sunrise.isFocusable(),
                            "Sunrise must remain informational in the prayer list");
                });
        PhoneUiCheck.on(test, () -> PhoneUiCheck.check(
                a.body.findViewWithTag("calendar.previous-day") == null
                        && a.body.findViewWithTag("calendar.next-day") == null,
                "Home must keep date browsing inside the calendar"));
        String[] month = new String[1];
        PhoneUiCheck.on(test, () -> {
            a.body.findViewWithTag("phone.today").performClick();
            android.view.View root = a.phoneUi.choiceDialog.getWindow().getDecorView();
            calendarTimes(a, root, a.phoneUi.settings.day(System.currentTimeMillis(), 0));
            month[0] = ((TextView) root.findViewWithTag("calendar.month")).getText().toString();
            root.findViewWithTag("calendar.next").performClick();
            PhoneUiCheck.check(!((TextView) root.findViewWithTag("calendar.month")).getText()
                            .toString().equals(month[0]), "Next Hijri month did not change");
            root.findViewWithTag("calendar.previous").performClick();
            PhoneUiCheck.check(((TextView) root.findViewWithTag("calendar.month")).getText()
                            .toString().equals(month[0]), "Previous month did not restore the month");
            root.findViewWithTag("calendar.day.1").performClick();
            root.findViewWithTag("calendar.selected.previous-day").performClick();
            PhoneUiCheck.check(!((TextView) root.findViewWithTag("calendar.month")).getText()
                            .toString().equals(month[0]), "Previous day did not cross the month boundary");
            root.findViewWithTag("calendar.selected.next-day").performClick();
            PhoneUiCheck.check(((TextView) root.findViewWithTag("calendar.month")).getText()
                            .toString().equals(month[0]), "Next day did not restore the month");
            root.findViewWithTag("calendar.selected.today").performClick();
            calendarTimes(a, root, a.phoneUi.settings.day(System.currentTimeMillis(), 0));
            PhoneUiCheck.check(a.body.findViewWithTag("phone.hero") != null,
                    "Calendar date browsing must leave live Home intact underneath");
            a.phoneUi.choiceDialog.getButton(-2).performClick();
        });
        verifyReturn(test, a);
        PhoneUiCheck.check(
                PhoneStore.read(a).values.equals(original.values),
                "Calendar navigation changed prayer settings");
    }

    static void events(Instrumentation test, MainActivity a, PhoneStore original) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("prayer.zone", "Asia/Riyadh");
        values.put("prayer.hijri-offset", "0");
        PhoneStore fixture = original.changed(values);
        IslamicCalendar date = PhoneCalendar.calendar(fixture, System.currentTimeMillis(), false);
        date.set(android.icu.util.Calendar.MONTH, 11);
        date.set(android.icu.util.Calendar.DATE, 9);
        long arafah = PhoneCalendar.civilStamp(fixture, date);
        String event = PhoneHijriEvents.label(a, fixture, arafah, a.ar);
        PhoneUiCheck.check(!event.isEmpty(), "Arafah event missing from its Hijri date");
        long previous = fixture.day(arafah, -1).start;
        String tarwiyah = PhoneHijriEvents.label(a, fixture, previous, a.ar);
        PhoneHijriEvents.Entry soon = PhoneHijriEvents.upcoming(a, fixture, fixture.day(arafah, -2).start, a.ar);
        PhoneUiCheck.check(soon != null && soon.offset == 1 && soon.label.equals(tarwiyah) && !tarwiyah.isEmpty(),
                "Upcoming Hajj event did not follow the next civil day");
        PhoneUiCheck.check(PhoneHijriEvents.upcoming(a, fixture, fixture.day(arafah, -5).start, a.ar) == null,
                "Home showed an event beyond its three-day horizon");
        PhoneStore adjusted = fixture.changed(Collections.singletonMap("prayer.hijri-offset", "1"));
        String eid = PhoneHijriEvents.label(a, fixture, fixture.day(arafah, 1).start, a.ar);
        PhoneUiCheck.check(!eid.isEmpty() && !eid.equals(event)
                        && PhoneHijriEvents.label(a, adjusted, arafah, a.ar).equals(eid),
                "Events ignored the shared Hijri adjustment");
        PhoneUiCheck.check(Arrays.equals(fixture.day(arafah, 0).times, adjusted.day(arafah, 0).times),
                "Event correction changed calculated prayer times");
        date.set(android.icu.util.Calendar.HOUR_OF_DAY, 0);
        date.set(android.icu.util.Calendar.MINUTE, 30);
        PhoneStore otherZone = fixture.changed(Collections.singletonMap("prayer.zone", "America/New_York"));
        PhoneUiCheck.check(PhoneHijriEvents.label(a, otherZone, date.getTimeInMillis(), a.ar).equals(tarwiyah),
                "Event label used another prayer timezone's day");
        PhoneStore before = a.phoneUi.settings;
        try {
            PhoneUiCheck.on(test, () -> {
                a.phoneUi.settings = fixture;
                PhoneCalendar.show(a, a.phoneUi, arafah);
                android.view.View root = a.phoneUi.choiceDialog.getWindow().getDecorView();
                TextView selected = root.findViewWithTag("calendar.selected.event");
                PhoneUiCheck.check(selected != null && selected.getText().toString().equals(event),
                        "Selected calendar event was not shown above its times");
                android.widget.Button day = root.findViewWithTag("calendar.day.9");
                PhoneUiCheck.check(day.getContentDescription().toString().contains(event)
                                && ((android.graphics.drawable.RippleDrawable)day.getBackground()).getDrawable(0)
                                        instanceof android.graphics.drawable.LayerDrawable,
                        "Event day has no visible marker or accessible event name");
                root.findViewWithTag("calendar.event.10").performClick();
                selected = root.findViewWithTag("calendar.selected.event");
                PhoneUiCheck.check(selected != null && selected.getText().toString().equals(eid),
                        "Month event row did not select its day in place");
                calendarTimes(a, root, fixture.day(arafah, 1));
                root.findViewWithTag("calendar.day.11").performClick();
                selected = root.findViewWithTag("calendar.selected.event");
                PhoneUiCheck.check(selected != null
                                && selected.getText().toString().contains(a.ar ? "أيام التشريق" : "Days of Tashriq"),
                        "The second Eid al-Adha day lost its overlapping Tashriq label");
                root.findViewWithTag("calendar.day.14").performClick();
                PhoneUiCheck.check(root.findViewWithTag("calendar.selected.event") == null,
                        "Ordinary selected date retained a stale event label");
            });
        } finally {
            PhoneUiCheck.on(test, () -> {
                if (a.phoneUi.choiceDialog != null) a.phoneUi.choiceDialog.dismiss();
                a.phoneUi.settings = before;
            });
        }
        PhoneUiCheck.check(PhoneStore.read(a).values.equals(original.values),
                "Browsing calendar events wrote prayer settings");
        date.set(android.icu.util.Calendar.MONTH, 8);
        date.set(android.icu.util.Calendar.DATE, 1);
        long ramadan = PhoneCalendar.civilStamp(fixture, date);
        int length = date.getActualMaximum(android.icu.util.Calendar.DATE);
        for (int day = 1; day <= length; day++) {
            String label = PhoneHijriEvents.label(a, fixture, fixture.day(ramadan, day - 1).start, a.ar);
            int number = day;
            String expected = String.format(Locale.ROOT,
                    a.ar ? "اليوم %d من رمضان" : "Day %d of Ramadan", number);
            String digits = fixture.value("display.digits");
            expected = tv.miqat.core.DisplayText.INSTANCE.digits(expected,
                    digits.equals("eastern") || digits.equals("auto") && a.ar);
            PhoneUiCheck.check(label.startsWith(expected), "Ramadan day is unnumbered: " + day);
        }
        String thirdEid = PhoneHijriEvents.label(a, fixture, fixture.day(ramadan, length + 2).start, a.ar);
        PhoneUiCheck.check(thirdEid.contains(a.ar ? "عيد الفطر" : "Eid al-Fitr"),
                "The third Eid al-Fitr day was missing");
        PhoneUiCheck.check(PhoneHijriEvents.label(a, fixture, fixture.day(ramadan, length + 3).start, a.ar).isEmpty(),
                "Eid al-Fitr extended beyond the third holiday day");
    }

    private static void calendarTimes(
            MainActivity a, android.view.View calendarRoot, PhoneSchedule.Day day) {
        PhoneUiCheck.check(
                calendarRoot.findViewWithTag("calendar.selected-day") != null,
                "Calendar did not show its timetable without another click");
        TextView date = calendarRoot.findViewWithTag("calendar.selected.date");
        PhoneUiCheck.check(
                date.getText()
                        .toString()
                        .equals(
                                a.phoneUi.digits(
                                        PhoneTime.hijri(a.phoneUi.settings, day.start, a.ar))),
                "Combined calendar shows the wrong selected date");
        PhoneUiCheck.check(day.times != null, "Calendar date has no calculated timetable");
        for (int prayer = 0; prayer < PhoneStore.PRAYERS.length; prayer++) {
            TextView time =
                    calendarRoot.findViewWithTag(
                            "calendar.prayer." + PhoneStore.PRAYERS[prayer] + ".time");
            PhoneUiCheck.check(
                    time != null
                            && time.getText().toString().equals(a.phoneUi.time(day.times[prayer])),
                    "Combined calendar timetable did not update: " + PhoneStore.PRAYERS[prayer]);
        }
    }

}

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

    static void run(Instrumentation test, MainActivity a) throws Exception {
        PhoneStore original = PhoneStore.read(a);
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
        tapDayArrow(test, a, "calendar.previous-day", -1);
        tapDayArrow(test, a, "calendar.next-day", 0);
        tapDayArrow(test, a, "calendar.next-day", 1);
        PhoneUiCheck.on(test, () -> a.phoneUi.home.today());
        String[] month = new String[1];
        PhoneUiCheck.on(
                test,
                () -> {
                    a.body.findViewWithTag("phone.today").performClick();
                    PhoneUiCheck.check(
                            a.phoneUi.choiceDialog != null,
                            "Home date did not open Hijri calendar");
                    android.view.View calendarRoot =
                            a.phoneUi.choiceDialog.getWindow().getDecorView();
                    calendarTimes(
                            a, calendarRoot, a.phoneUi.settings.day(System.currentTimeMillis(), 0));
                    month[0] =
                            ((TextView) calendarRoot.findViewWithTag("calendar.month"))
                                    .getText()
                                    .toString();
                    calendarRoot.findViewWithTag("calendar.next").performClick();
                    PhoneUiCheck.check(
                            !((TextView) calendarRoot.findViewWithTag("calendar.month"))
                                    .getText()
                                    .toString()
                                    .equals(month[0]),
                            "Next Hijri month did not change");
                    calendarRoot.findViewWithTag("calendar.previous").performClick();
                    PhoneUiCheck.check(
                            ((TextView) calendarRoot.findViewWithTag("calendar.month"))
                                    .getText()
                                    .toString()
                                    .equals(month[0]),
                            "Previous Hijri month did not restore the month");
                    calendarRoot.findViewWithTag("calendar.day.1").performClick();
                    calendarRoot.findViewWithTag("calendar.selected.previous-day").performClick();
                    PhoneUiCheck.check(
                            !((TextView) calendarRoot.findViewWithTag("calendar.month"))
                                    .getText()
                                    .toString()
                                    .equals(month[0]),
                            "Previous day did not cross the Hijri month boundary");
                    calendarTimes(
                            a,
                            calendarRoot,
                            a.phoneUi.settings.day(
                                    System.currentTimeMillis(), a.phoneUi.home.dayOffset));
                    calendarRoot.findViewWithTag("calendar.selected.next-day").performClick();
                    PhoneUiCheck.check(
                            ((TextView) calendarRoot.findViewWithTag("calendar.month"))
                                    .getText()
                                    .toString()
                                    .equals(month[0]),
                            "Next day did not restore the Hijri month");
                    IslamicCalendar current =
                            PhoneCalendar.calendar(
                                    a.phoneUi.settings, System.currentTimeMillis(), a.ar);
                    int day = current.get(android.icu.util.Calendar.DATE) == 1 ? 2 : 1;
                    a.phoneUi
                            .choiceDialog
                            .getWindow()
                            .getDecorView()
                            .findViewWithTag("calendar.day." + day)
                            .performClick();
                    PhoneUiCheck.check(
                            a.phoneUi.home.dayOffset != 0,
                            "Selecting a Hijri date did not change the prayer day");
                    PhoneUiCheck.check(
                            a.phoneUi.choiceDialog != null && a.phoneUi.choiceDialog.isShowing(),
                            "Selecting a date closed the combined calendar/timetable");
                    calendarTimes(
                            a,
                            calendarRoot,
                            a.phoneUi.settings.day(
                                    System.currentTimeMillis(), a.phoneUi.home.dayOffset));
                    a.phoneUi
                            .choiceDialog
                            .getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                            .performClick();
                });
        PhoneUiCheck.on(
                test,
                () -> {
                    PhoneUiCheck.check(
                            a.phoneUi.choiceDialog == null, "Calendar Done did not close the view");
                    PhoneUiCheck.check(
                            a.body.findViewWithTag("phone.prayer.fajr") != null,
                            "Selected Hijri date did not render calculated prayer times");
                    PhoneSchedule.Day selectedDay =
                            a.phoneUi.settings.day(
                                    System.currentTimeMillis(), a.phoneUi.home.dayOffset);
                    PhoneUiCheck.check(
                            selectedDay.times != null, "Selected date has no calculated timetable");
                    TextView dateLabel = a.body.findViewWithTag("phone.today");
                    PhoneUiCheck.check(
                            dateLabel
                                    .getText()
                                    .toString()
                                    .contains(
                                            a.phoneUi.digits(
                                                    PhoneTime.hijri(
                                                            a.phoneUi.settings,
                                                            selectedDay.start,
                                                            a.ar))),
                            "Calendar date label does not match the selected prayer day");
                    for (int prayer = 0; prayer < PhoneStore.PRAYERS.length; prayer++) {
                        TextView time =
                                a.body.findViewWithTag(
                                        "phone.prayer." + PhoneStore.PRAYERS[prayer] + ".time");
                        PhoneUiCheck.check(
                                time != null
                                        && time.getText()
                                                .toString()
                                                .equals(a.phoneUi.time(selectedDay.times[prayer])),
                                "Calendar prayer time differs from the selected date: "
                                        + PhoneStore.PRAYERS[prayer]);
                    }
                    PhoneUiCheck.check(
                            a.body.findViewWithTag("phone.hero") == null
                                    && a.body.findViewWithTag("phone.status") == null,
                            "Selected-date view retained today's live prayer or alert status");
                    long selectedStamp = a.phoneUi.home.selectedDay;
                    android.os.Bundle savedDay = new android.os.Bundle();
                    a.phoneUi.save(savedDay);
                    PhoneUiCheck.check(
                            selectedStamp != 0
                                    && savedDay.getLong("phone-selected-day") == selectedStamp,
                            "Selected civil date was not included in activity state");
                    Calendar nextCivilDay = new GregorianCalendar(a.phoneUi.settings.zone());
                    nextCivilDay.add(Calendar.DATE, 1);
                    try {
                        a.phoneUi.home.stop();
                        a.body.removeAllViews();
                        a.phoneUi.home.render(nextCivilDay.getTimeInMillis());
                        PhoneUiCheck.check(
                                a.phoneUi.home.selectedDay == selectedStamp
                                        && a.phoneUi.settings.day(
                                                                nextCivilDay.getTimeInMillis(),
                                                                a.phoneUi.home.dayOffset)
                                                        .start
                                                == selectedDay.start,
                                "Selected calendar date drifted when the current civil day"
                                        + " changed");
                        TextView retainedDate = a.body.findViewWithTag("phone.today");
                        PhoneUiCheck.check(
                                retainedDate
                                        .getText()
                                        .toString()
                                        .contains(
                                                a.phoneUi.digits(
                                                        PhoneTime.hijri(
                                                                a.phoneUi.settings,
                                                                selectedDay.start,
                                                                a.ar))),
                                "Midnight changed the selected Hijri date label");
                        for (int prayer = 0; prayer < PhoneStore.PRAYERS.length; prayer++) {
                            android.view.View cell =
                                    a.body.findViewWithTag(
                                            "phone.prayer." + PhoneStore.PRAYERS[prayer]);
                            TextView time =
                                    a.body.findViewWithTag(
                                            "phone.prayer." + PhoneStore.PRAYERS[prayer] + ".time");
                            PhoneUiCheck.check(
                                    time != null
                                            && time.getText()
                                                    .toString()
                                                    .equals(
                                                            a.phoneUi.time(
                                                                    selectedDay.times[prayer])),
                                    "Midnight changed a selected date's calculated prayer time");
                            if (a.phoneUi.home.dayOffset != 0)
                                PhoneUiCheck.check(
                                        !cell.isSelected(),
                                        "Selected date highlighted today's current prayer");
                        }
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    } finally {
                        a.render();
                    }
                    a.body.findViewWithTag("calendar.today").performClick();
                    PhoneUiCheck.check(
                            a.phoneUi.home.dayOffset == 0
                                    && a.phoneUi.home.selectedDay == 0
                                    && a.body.findViewWithTag("phone.hero") != null
                                    && a.body.findViewWithTag("phone.tomorrow") == null,
                            "Return to today failed or duplicate tomorrow Fajr remained");
                    a.body.findViewWithTag("calendar.previous-day").performClick();
                    a.back();
                    PhoneUiCheck.check(
                            a.phoneUi.home.dayOffset == 0
                                    && a.phoneUi.home.selectedDay == 0
                                    && !a.isFinishing(),
                            "Back from a browsed date exited instead of returning to Today");
                });
        PhoneUiCheck.check(
                PhoneStore.read(a).values.equals(original.values),
                "Calendar navigation changed prayer settings");
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

    private static void tapDayArrow(
            Instrumentation test, MainActivity a, String tag, int expectedOffset) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        PhoneUiCheck.on(
                test,
                () -> {
                    android.view.View arrow = a.body.findViewWithTag(tag);
                    arrow.requestRectangleOnScreen(
                            new android.graphics.Rect(0, 0, arrow.getWidth(), arrow.getHeight()),
                            true);
                });
        PhoneUiCheck.on(
                test,
                () -> {
                    android.view.View arrow = a.body.findViewWithTag(tag);
                    PhoneUiCheck.check(
                            arrow.getGlobalVisibleRect(bounds)
                                    && bounds.width() == arrow.getWidth()
                                    && bounds.height() == arrow.getHeight(),
                            "Home day arrow has no complete touch target");
                });
        long downAt = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down =
                android.view.MotionEvent.obtain(
                        downAt,
                        downAt,
                        android.view.MotionEvent.ACTION_DOWN,
                        bounds.centerX(),
                        bounds.centerY(),
                        0);
        android.view.MotionEvent up =
                android.view.MotionEvent.obtain(
                        downAt,
                        downAt + 40,
                        android.view.MotionEvent.ACTION_UP,
                        bounds.centerX(),
                        bounds.centerY(),
                        0);
        try {
            test.sendPointerSync(down);
            test.sendPointerSync(up);
        } finally {
            down.recycle();
            up.recycle();
        }
        test.waitForIdleSync();
        PhoneUiCheck.on(
                test,
                () -> {
                    PhoneUiCheck.check(
                            a.phoneUi.home.dayOffset == expectedOffset
                                    && a.phoneUi.choiceDialog == null,
                            "Touching Home's day arrow opened a calendar instead of changing day");
                    PhoneSchedule.Day day =
                            a.phoneUi.settings.day(System.currentTimeMillis(), expectedOffset);
                    for (int prayer = 0; prayer < PhoneStore.PRAYERS.length; prayer++) {
                        TextView time =
                                a.body.findViewWithTag(
                                        "phone.prayer." + PhoneStore.PRAYERS[prayer] + ".time");
                        PhoneUiCheck.check(
                                time != null
                                        && time.getText()
                                                .toString()
                                                .equals(a.phoneUi.time(day.times[prayer])),
                                "Home day arrow did not update prayer times");
                    }
                });
    }
}

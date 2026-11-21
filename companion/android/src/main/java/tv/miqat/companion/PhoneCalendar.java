package tv.miqat.companion;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.icu.util.IslamicCalendar;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.text.DateFormatSymbols;
import java.util.*;

/** Hijri selection maps back to a civil day; it never writes prayer settings or alarms. */
final class PhoneCalendar {
    private final MainActivity a;
    private final PhoneUi ui;
    private final PhoneStore settings;
    private final long openedAt = System.currentTimeMillis();
    private IslamicCalendar month;
    private long selectedStamp;
    private final LinearLayout content;

    private PhoneCalendar(MainActivity a, PhoneUi ui, long stamp) {
        this.a = a;
        this.ui = ui;
        this.settings = ui.settings;
        selectedStamp = stamp;
        month = calendar(settings, selectedStamp, a.ar);
        month.set(android.icu.util.Calendar.DATE, 1);
        content = a.views.column();
        content.setPadding(a.views.dp(8), a.views.dp(8), a.views.dp(8), a.views.dp(8));
        content.setLayoutDirection(a.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }

    static IslamicCalendar calendar(PhoneStore settings, long stamp, boolean ar) {
        IslamicCalendar date =
                new IslamicCalendar(
                        android.icu.util.TimeZone.getTimeZone(settings.zone().getID()),
                        new Locale(ar ? "ar" : "en"));
        date.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        date.setTimeInMillis(stamp);
        date.set(android.icu.util.Calendar.HOUR_OF_DAY, 12);
        date.add(android.icu.util.Calendar.DATE, settings.number("prayer.hijri-offset"));
        return date;
    }

    static int offset(PhoneStore settings, long today, long selected) {
        Calendar a = new GregorianCalendar(settings.zone());
        Calendar b = new GregorianCalendar(settings.zone());
        a.setTimeInMillis(today);
        b.setTimeInMillis(selected);
        Calendar utc = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(a.get(Calendar.YEAR), a.get(Calendar.MONTH), a.get(Calendar.DATE));
        long first = utc.getTimeInMillis();
        utc.clear();
        utc.set(b.get(Calendar.YEAR), b.get(Calendar.MONTH), b.get(Calendar.DATE));
        return (int) ((utc.getTimeInMillis() - first) / 86400000L);
    }

    static long civilStamp(PhoneStore settings, IslamicCalendar date) {
        IslamicCalendar civil = (IslamicCalendar) date.clone();
        civil.add(android.icu.util.Calendar.DATE, -settings.number("prayer.hijri-offset"));
        return civil.getTimeInMillis();
    }

    static void show(MainActivity a, PhoneUi ui, long stamp) {
        new PhoneCalendar(a, ui, stamp).show();
    }

    private void show() {
        ScrollView scroll = new ScrollView(a);
        scroll.addView(content);
        LinearLayout title = new LinearLayout(a);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(a.views.dp(16), a.views.dp(8), a.views.dp(8), 0);
        title.setLayoutDirection(a.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        TextView name = a.views.text(a.t("phone_calendar"), 20, a.ink);
        name.setTypeface(a.heading);
        title.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        Button today = a.views.button(title, a.t("phone_today"),
                () -> choose(settings.day(System.currentTimeMillis(), 0).start, true));
        today.setTag("calendar.selected.today");
        today.setMinWidth(0);
        today.setMinimumWidth(0);
        today.setTextSize(12);
        today.setLayoutParams(new LinearLayout.LayoutParams(-2, a.views.dp(48)));
        AlertDialog dialog =
                a.views
                        .dialog()
                        .setCustomTitle(title)
                        .setView(scroll)
                        .setNegativeButton(a.t("done"), null)
                        .create();
        render();
        ui.showEditor(dialog);
    }

    private void render() {
        content.removeAllViews();
        monthHeader();
        monthDays();
        selectedDay();
        monthEvents();
    }

    private void monthHeader() {
        LinearLayout header = a.views.horizontal(content);
        Button previous =
                a.views.iconButton(
                        header, "back", a.t("phone_previous_month"), () -> moveMonth(-1));
        previous.setTag("calendar.previous");
        android.icu.text.SimpleDateFormat format =
                new android.icu.text.SimpleDateFormat(
                        "MMMM y",
                        Locale.forLanguageTag((a.ar ? "ar" : "en") + "-u-ca-islamic-umalqura"));
        format.setCalendar((IslamicCalendar) month.clone());
        TextView title = a.views.text(ui.digits(format.format(month.getTime())), 20, a.ink);
        title.setGravity(Gravity.CENTER);
        title.setTag("calendar.month");
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button next =
                a.views.iconButton(header, "back", a.t("phone_next_month"), () -> moveMonth(1));
        next.setRotation(180);
        next.setTag("calendar.next");
        IslamicCalendar before = (IslamicCalendar) month.clone();
        before.add(android.icu.util.Calendar.DATE, -1);
        IslamicCalendar after = (IslamicCalendar) month.clone();
        after.add(android.icu.util.Calendar.MONTH, 1);
        previous.setEnabled(offset(settings, openedAt, civilStamp(settings, before)) >= -366);
        next.setEnabled(offset(settings, openedAt, civilStamp(settings, after)) <= 366);
    }

    private void moveMonth(int direction) {
        month.add(android.icu.util.Calendar.MONTH, direction);
        render();
    }

    private void monthDays() {
        String[] weekdays = new DateFormatSymbols(ui.locale()).getShortWeekdays();
        int weekStart = a.ar ? Calendar.SATURDAY : Calendar.SUNDAY;
        LinearLayout labels = a.views.horizontal(content);
        for (int i = 0; i < 7; i++) {
            TextView label = a.views.text(weekdays[(weekStart - 1 + i) % 7 + 1], 10, a.muted);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, a.views.dp(8), 0, a.views.dp(8));
            labels.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        }
        IslamicCalendar selectedDate = calendar(settings, selectedStamp, a.ar);
        int leading = (month.get(android.icu.util.Calendar.DAY_OF_WEEK) - weekStart + 7) % 7;
        int count = month.getActualMaximum(android.icu.util.Calendar.DATE);
        LinearLayout row = null;
        for (int slot = 0; slot < ((leading + count + 6) / 7) * 7; slot++) {
            if (slot % 7 == 0) row = a.views.horizontal(content);
            int day = slot - leading + 1;
            if (day < 1 || day > count) {
                row.addView(new View(a), new LinearLayout.LayoutParams(0, a.views.dp(48), 1));
                continue;
            }
            IslamicCalendar date = (IslamicCalendar) month.clone();
            date.set(android.icu.util.Calendar.DATE, day);
            long stamp = civilStamp(settings, date);
            int delta = offset(settings, openedAt, stamp);
            Button button =
                    a.views.button(
                            row, ui.digits(Integer.toString(day)), () -> choose(stamp, false));
            button.setTag("calendar.day." + day);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setTextSize(13);
            button.setPadding(0, 0, 0, 0);
            button.setEnabled(delta >= -366 && delta <= 366);
            boolean active =
                    selectedDate.get(android.icu.util.Calendar.YEAR)
                                    == date.get(android.icu.util.Calendar.YEAR)
                            && selectedDate.get(android.icu.util.Calendar.MONTH)
                                    == date.get(android.icu.util.Calendar.MONTH)
                            && selectedDate.get(android.icu.util.Calendar.DATE) == day;
            button.setSelected(active);
            button.setTextColor(active ? a.ground : a.ink);
            String event = PhoneHijriEvents.label(a, settings, stamp, a.ar);
            Drawable shape = a.views.background(active ? a.accent : a.surface, 24);
            if (!event.isEmpty()) {
                LayerDrawable marked = new LayerDrawable(new Drawable[] {
                        shape, a.views.background(active ? a.ground : a.accent, 2)});
                marked.setLayerSize(1, a.views.dp(4), a.views.dp(4));
                marked.setLayerGravity(1, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                marked.setLayerInsetBottom(1, a.views.dp(4));
                shape = marked;
            }
            button.setBackground(
                    new android.graphics.drawable.RippleDrawable(
                            ColorStateList.valueOf((a.accent & 0xffffff) | 0x33000000), shape, null));
            button.setContentDescription(ui.digits(PhoneTime.hijri(settings, stamp, a.ar))
                    + (event.isEmpty() ? "" : ", " + event));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, a.views.dp(48), 1);
            size.setMargins(a.views.dp(1), a.views.dp(2), a.views.dp(1), a.views.dp(2));
            button.setLayoutParams(size);
        }
    }

    private void choose(long stamp, boolean followMonth) {
        int delta = offset(settings, openedAt, stamp);
        if (delta < -366 || delta > 366) return;
        selectedStamp = settings.day(openedAt, delta).start;
        if (followMonth) {
            month = calendar(settings, selectedStamp, a.ar);
            month.set(android.icu.util.Calendar.DATE, 1);
        }
        render();
    }

    private void selectedDay() {
        int delta = offset(settings, openedAt, selectedStamp);
        PhoneSchedule.Day day = settings.day(openedAt, delta);
        LinearLayout timetable = a.views.column();
        timetable.setTag("calendar.selected-day");
        content.addView(timetable, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout heading = a.views.horizontal(timetable);
        Button previous =
                a.views.iconButton(
                        heading,
                        "back",
                        a.t("phone_previous_day"),
                        () -> choose(settings.day(openedAt, delta - 1).start, true));
        previous.setTag("calendar.selected.previous-day");
        previous.setEnabled(delta > -366);
        TextView label =
                a.views.text(ui.digits(PhoneTime.hijri(settings, day.start, a.ar)), 18, a.ink);
        label.setGravity(Gravity.CENTER);
        label.setTag("calendar.selected.date");
        heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        Button next =
                a.views.iconButton(
                        heading,
                        "back",
                        a.t("phone_next_day"),
                        () -> choose(settings.day(openedAt, delta + 1).start, true));
        next.setRotation(180);
        next.setTag("calendar.selected.next-day");
        next.setEnabled(delta < 366);
        java.text.SimpleDateFormat date =
                new java.text.SimpleDateFormat("EEEE, d MMMM yyyy", ui.locale());
        date.setTimeZone(settings.zone());
        TextView civil = a.views.text(ui.digits(date.format(new Date(day.start))), 12, a.muted);
        civil.setGravity(Gravity.CENTER);
        timetable.addView(civil);
        String eventLabel = PhoneHijriEvents.label(a, settings, day.start, a.ar);
        if (!eventLabel.isEmpty()) {
            TextView event = a.views.text(eventLabel, 12, a.accent);
            event.setTag("calendar.selected.event");
            event.setPadding(a.views.dp(10), a.views.dp(6), a.views.dp(10), a.views.dp(6));
            event.setBackground(a.views.background(a.surface, 12));
            LinearLayout.LayoutParams eventSize = new LinearLayout.LayoutParams(-1, -2);
            eventSize.bottomMargin = a.views.dp(8);
            timetable.addView(event, eventSize);
        }
        if (day.times == null) timetable.addView(a.views.text(a.t("phone_unavailable"), 16, a.ink));
        else
            for (int i = 0; i < PhoneStore.PRAYERS.length; i++) {
                LinearLayout row = a.views.horizontal(timetable);
                row.setMinimumHeight(a.views.dp(48));
                row.setPadding(a.views.dp(8), a.views.dp(4), a.views.dp(8), a.views.dp(4));
                row.setBackground(a.views.background(a.surface, 16));
                ((LinearLayout.LayoutParams) row.getLayoutParams()).bottomMargin = a.views.dp(4);
                ImageView glyph = new ImageView(a);
                glyph.setImageDrawable(Icons.get(a, PhoneStore.PRAYERS[i], a.accent));
                glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams iconSize =
                        new LinearLayout.LayoutParams(a.views.dp(22), a.views.dp(22));
                iconSize.setMarginEnd(a.views.dp(10));
                row.addView(glyph, iconSize);
                row.addView(
                        a.views.text(
                                a.catalog
                                        .data
                                        .optJSONObject("prayerNames")
                                        .optJSONArray(a.ar ? "ar" : "en")
                                        .optString(i),
                                15,
                                a.ink),
                        new LinearLayout.LayoutParams(0, -2, 1));
                TextView time = a.views.text(ui.time(day.times[i]), 18, a.accent);
                time.setTag("calendar.prayer." + PhoneStore.PRAYERS[i] + ".time");
                row.addView(time, new LinearLayout.LayoutParams(-2, -2));
            }

    }
    private void monthEvents() {
        boolean heading = false;
        for (int day = 1; day <= month.getActualMaximum(android.icu.util.Calendar.DATE); day++) {
            IslamicCalendar date = (IslamicCalendar) month.clone();
            date.set(android.icu.util.Calendar.DATE, day);
            long stamp = civilStamp(settings, date);
            int delta = offset(settings, openedAt, stamp);
            String event = PhoneHijriEvents.label(a, settings, stamp, a.ar);
            if (event.isEmpty() || delta < -366 || delta > 366) continue;
            if (!heading) {
                content.addView(a.views.heading(a.t("phone_calendar_events")));
                heading = true;
            }
            a.views.navigationRow(content, "clock", event, ui.digits(Integer.toString(day)),
                    () -> choose(stamp, false)).setTag("calendar.event." + day);
        }
    }

}

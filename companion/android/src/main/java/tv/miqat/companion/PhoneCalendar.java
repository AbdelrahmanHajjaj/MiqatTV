package tv.miqat.companion;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.icu.util.IslamicCalendar;
import android.view.Gravity;
import android.widget.*;

import java.text.DateFormatSymbols;
import java.util.*;
import java.util.function.IntConsumer;

/** Hijri selection maps back to a civil day; it never writes prayer settings or alarms. */
final class PhoneCalendar {
    private PhoneCalendar() {}

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

    static void show(MainActivity a, PhoneUi ui, int selectedOffset, IntConsumer selected) {
        long now = System.currentTimeMillis();
        IslamicCalendar month =
                calendar(ui.settings, ui.settings.day(now, selectedOffset).start, a.ar);
        int selectedYear = month.get(android.icu.util.Calendar.YEAR);
        int selectedMonth = month.get(android.icu.util.Calendar.MONTH);
        int selectedDay = month.get(android.icu.util.Calendar.DATE);
        month.set(android.icu.util.Calendar.DATE, 1);
        LinearLayout content = a.views.column();
        content.setPadding(a.views.dp(8), a.views.dp(8), a.views.dp(8), a.views.dp(8));
        content.setLayoutDirection(
                a.ar
                        ? android.view.View.LAYOUT_DIRECTION_RTL
                        : android.view.View.LAYOUT_DIRECTION_LTR);
        ScrollView scroll = new ScrollView(a);
        scroll.addView(content);
        AlertDialog dialog =
                a.views
                        .dialog()
                        .setTitle(a.t("phone_calendar"))
                        .setView(scroll)
                        .setNegativeButton(a.t("cancel"), null)
                        .create();
        Runnable[] render = new Runnable[1];
        render[0] =
                () -> {
                    content.removeAllViews();
                    LinearLayout header = a.views.horizontal(content);
                    Button previous =
                            a.views.iconButton(
                                    header,
                                    "back",
                                    a.t("phone_previous_month"),
                                    () -> {
                                        month.add(android.icu.util.Calendar.MONTH, -1);
                                        render[0].run();
                                    });
                    previous.setTag("calendar.previous");
                    android.icu.text.SimpleDateFormat monthFormat =
                            new android.icu.text.SimpleDateFormat(
                                    "MMMM y",
                                    Locale.forLanguageTag(
                                            (a.ar ? "ar" : "en") + "-u-ca-islamic-umalqura"));
                    monthFormat.setCalendar((IslamicCalendar) month.clone());
                    TextView title =
                            a.views.text(ui.digits(monthFormat.format(month.getTime())), 20, a.ink);
                    title.setGravity(Gravity.CENTER);
                    title.setTag("calendar.month");
                    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
                    Button next =
                            a.views.iconButton(
                                    header,
                                    "back",
                                    a.t("phone_next_month"),
                                    () -> {
                                        month.add(android.icu.util.Calendar.MONTH, 1);
                                        render[0].run();
                                    });
                    next.setRotation(180);
                    next.setTag("calendar.next");
                    IslamicCalendar before = (IslamicCalendar) month.clone();
                    before.add(android.icu.util.Calendar.DATE, -1);
                    IslamicCalendar after = (IslamicCalendar) month.clone();
                    after.add(android.icu.util.Calendar.MONTH, 1);
                    previous.setEnabled(
                            offset(ui.settings, now, civilStamp(ui.settings, before)) >= -366);
                    next.setEnabled(
                            offset(ui.settings, now, civilStamp(ui.settings, after)) <= 366);
                    String[] weekdays = new DateFormatSymbols(ui.locale()).getShortWeekdays();
                    int weekStart = a.ar ? Calendar.SATURDAY : Calendar.SUNDAY;
                    LinearLayout labels = a.views.horizontal(content);
                    for (int i = 0; i < 7; i++) {
                        TextView label =
                                a.views.text(weekdays[(weekStart - 1 + i) % 7 + 1], 10, a.muted);
                        label.setGravity(Gravity.CENTER);
                        label.setPadding(0, a.views.dp(8), 0, a.views.dp(8));
                        labels.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
                    }
                    int leading =
                            (month.get(android.icu.util.Calendar.DAY_OF_WEEK) - weekStart + 7) % 7;
                    int count = month.getActualMaximum(android.icu.util.Calendar.DATE);
                    LinearLayout row = null;
                    for (int slot = 0; slot < ((leading + count + 6) / 7) * 7; slot++) {
                        if (slot % 7 == 0) row = a.views.horizontal(content);
                        int day = slot - leading + 1;
                        if (day < 1 || day > count) {
                            row.addView(
                                    new android.view.View(a),
                                    new LinearLayout.LayoutParams(0, a.views.dp(48), 1));
                            continue;
                        }
                        IslamicCalendar date = (IslamicCalendar) month.clone();
                        date.set(android.icu.util.Calendar.DATE, day);
                        int delta = offset(ui.settings, now, civilStamp(ui.settings, date));
                        Button button =
                                a.views.button(
                                        row,
                                        ui.digits(Integer.toString(day)),
                                        () -> {
                                            dialog.dismiss();
                                            selected.accept(delta);
                                        });
                        button.setTag("calendar.day." + day);
                        button.setMinWidth(0);
                        button.setMinimumWidth(0);
                        button.setTextSize(13);
                        button.setPadding(0, 0, 0, 0);
                        button.setEnabled(delta >= -366 && delta <= 366);
                        boolean active =
                                selectedYear == date.get(android.icu.util.Calendar.YEAR)
                                        && selectedMonth
                                                == date.get(android.icu.util.Calendar.MONTH)
                                        && selectedDay == day;
                        button.setSelected(active);
                        button.setTextColor(active ? a.ground : a.ink);
                        button.setBackground(
                                new android.graphics.drawable.RippleDrawable(
                                        ColorStateList.valueOf((a.accent & 0xffffff) | 0x33000000),
                                        a.views.background(active ? a.accent : a.surface, 24),
                                        null));
                        button.setContentDescription(
                                ui.digits(
                                        PhoneTime.hijri(
                                                ui.settings, civilStamp(ui.settings, date), a.ar)));
                        LinearLayout.LayoutParams size =
                                new LinearLayout.LayoutParams(0, a.views.dp(48), 1);
                        size.setMargins(a.views.dp(1), a.views.dp(2), a.views.dp(1), a.views.dp(2));
                        button.setLayoutParams(size);
                    }
                };
        render[0].run();
        ui.showEditor(dialog);
    }
}

package tv.miqat.companion;

import android.content.res.ColorStateList;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import java.text.SimpleDateFormat;
import java.util.*;

/** Daily prayer rendering and its foreground countdown; no preference or alarm ownership. */
final class PhoneHome {
    private final MainActivity a;
    private final PhoneUi ui;
    int dayOffset;
    // Zero follows today; a browsed date stays fixed across midnight.
    long selectedDay;

    PhoneHome(MainActivity activity, PhoneUi ui) {
        a = activity;
        this.ui = ui;
    }

    private TextView countdown, status, playback;
    private long nextTime, lastTick;
    private int renderedDay;
    private String renderedZone = "";

    private int civilDay(long now) {
        Calendar day = new GregorianCalendar(ui.settings.zone());
        day.setTimeInMillis(now);
        return day.get(Calendar.YEAR) * 400 + day.get(Calendar.DAY_OF_YEAR);
    }

    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable tick =
            new Runnable() {
                public void run() {
                    if (!a.phoneForeground) return;
                    long now = System.currentTimeMillis();
                    if (a.page.equals("prayers") && ui.settings != null) {
                        if (renderedDay != civilDay(now)
                                || !renderedZone.equals(ui.settings.zone().getID())
                                || lastTick != 0 && (now < lastTick || now - lastTick > 5000)) {
                            lastTick = now;
                            a.render();
                            return;
                        }
                        lastTick = now;
                        if (nextTime > 0 && System.currentTimeMillis() >= nextTime) {
                            a.render();
                            return;
                        }
                        if (countdown != null && nextTime > 0) {
                            long minutes =
                                    Math.max(
                                            0,
                                            (nextTime - System.currentTimeMillis() + 59999)
                                                    / 60000);
                            countdown.setText(
                                    String.format(
                                            ui.locale(),
                                            a.t("phone_remaining"),
                                            ui.digits(
                                                    String.format(
                                                            Locale.US,
                                                            "%02d:%02d",
                                                            minutes / 60,
                                                            minutes % 60))));
                        }
                        if (status != null && ui.settings != null)
                            status.setText(a.t(PhoneAlarms.status(a, ui.settings)));
                    }
                    if (playback != null)
                        playback.setVisibility(PhonePlayback.playing() ? View.VISIBLE : View.GONE);
                    clock.postDelayed(this, 1000);
                }
            };

    void start() {
        clock.removeCallbacksAndMessages(null);
        if (a.page.equals("prayers")) clock.post(tick);
        else {
            countdown = null;
            status = null;
            playback = null;
            nextTime = 0;
        }
    }

    void stop() {
        clock.removeCallbacksAndMessages(null);
    }

    void render(long now) throws Exception {
        countdown = null;
        status = null;
        playback = null;
        nextTime = 0;
        if (selectedDay != 0) {
            int offset = PhoneCalendar.offset(ui.settings, now, selectedDay);
            dayOffset = Math.max(-366, Math.min(366, offset));
            if (offset != dayOffset) selectedDay = ui.settings.day(now, dayOffset).start;
        } else if (dayOffset != 0) selectedDay = ui.settings.day(now, dayOffset).start;
        if (a.onboarding.needed()) {
            a.views
                    .navigationRow(
                            a.body, "gear", a.t("setup_resume"), "", () -> a.onboarding.open())
                    .setTag("onboarding.resume");
            ((LinearLayout.LayoutParams)
                                    a.body.findViewWithTag("onboarding.resume").getLayoutParams())
                            .bottomMargin =
                    a.views.dp(16);
        }
        renderedDay = civilDay(now);
        renderedZone = ui.settings.zone().getID();
        lastTick = now;
        PhoneSchedule.Day day = dateNavigation(now);
        int next = -1;
        boolean tomorrowNext = false;
        if (dayOffset == 0) {
            locationRow();
            PhoneSchedule.Day tomorrow = ui.settings.day(now, 1);
            LinearLayout hero = ui.appearance.hero(a.body, ui.settings);
            hero.setPadding(a.views.dp(16), a.views.dp(16), a.views.dp(16), a.views.dp(16));
            hero.setTag("phone.hero");
            tv.miqat.core.PrayerSelection selection = nextPrayer(hero, now, true);
            next = selection == null ? -1 : selection.getNext();
            tomorrowNext = selection != null && selection.getNextTime() >= tomorrow.start;
            alertStatus();
        }
        renderTimes(day, next, tomorrowNext);
        if (dayOffset == 0) destinations();
    }

    private void selectDay(int offset, long now) {
        dayOffset = Math.max(-366, Math.min(366, offset));
        selectedDay = dayOffset == 0 ? 0 : ui.settings.day(now, dayOffset).start;
        a.render();
    }

    private void locationRow() throws Exception {
        LinearLayout controls = a.views.horizontal(a.body);
        controls.setTag("phone.location.row");
        ((LinearLayout.LayoutParams) controls.getLayoutParams()).bottomMargin = a.views.dp(8);
        LinearLayout location =
                a.views.navigationRow(
                        controls, "compass", ui.location(), "", () -> ui.open("calculation"));
        a.views.flex(location);
        location.setBackground(new RippleDrawable(
                ColorStateList.valueOf((a.accent & 0xffffff) | 0x22000000),
                a.views.background(android.graphics.Color.TRANSPARENT, 20), null));
        location.setTag("phone.location");
        Button detect =
                a.views.iconButton(
                        controls, "compass", a.t("phone_use_location"), ui.locationUi::useLocation);
        detect.setTag(ui.settings.locationReady ? "phone.home.location.detect" : "phone.setup");
        detect.setEnabled(!ui.locationUi.locator.running);
        if (!ui.locationUi.locationMessage.isEmpty())
            a.body.addView(a.views.text(a.t(ui.locationUi.locationMessage), 12, a.muted));
    }

    private void alertStatus() {
        String state = PhoneAlarms.status(a, ui.settings);
        LinearLayout alerts = a.views.horizontal(a.body);
        alerts.setPadding(0, 0, 0, a.views.dp(8));
        status = a.views.text(a.t(state), 12, a.muted);
        status.setTag("phone.status");
        status.setPadding(0, 0, 0, 0);
        alerts.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        if (ui.settings.locationReady && state.equals("phone_permission_missing"))
            a.views
                    .iconButton(alerts, "gear", a.t("phone_repair"), () -> ui.open("permissions"))
                    .setTag("phone.setup");
        playback =
                a.views.iconButton(
                        alerts,
                        "stop",
                        a.t("phone_stop"),
                        () -> {
                            PhonePlayback.stop(a);
                            a.render();
                        });
        playback.setTag("phone.stop");
        playback.setVisibility(PhonePlayback.playing() ? View.VISIBLE : View.GONE);
        String audioError = PhoneStore.preferences(a).getString("audio-error", "");
        if (!audioError.isEmpty()) a.body.addView(a.views.text(a.t(audioError), 13, a.accent));
    }

    void today() {
        dayOffset = 0;
        selectedDay = 0;
        a.render();
    }

    private PhoneSchedule.Day dateNavigation(long now) {
        LinearLayout dateBar = a.views.horizontal(a.body);
        ((LinearLayout.LayoutParams) dateBar.getLayoutParams()).bottomMargin = a.views.dp(12);
        Button previous =
                a.views.iconButton(
                        dateBar,
                        "back",
                        a.t("phone_previous_day"),
                        () -> selectDay(dayOffset - 1, now));
        previous.setEnabled(dayOffset > -366);
        previous.setMinimumWidth(0);
        previous.setMinWidth(0);
        previous.setTag("calendar.previous-day");
        PhoneSchedule.Day day = ui.settings.day(now, dayOffset);
        SimpleDateFormat date = new SimpleDateFormat("EEEE", ui.locale());
        date.setTimeZone(ui.settings.zone());
        Button dateLabel =
                a.views.button(
                        dateBar,
                        ui.digits(PhoneTime.hijri(ui.settings, day.start, a.ar)),
                        this::openCalendar);
        a.views.flex(dateLabel);
        dateLabel.setMinimumWidth(0);
        dateLabel.setMinWidth(0);
        dateLabel.setBackground(new RippleDrawable(
                ColorStateList.valueOf((a.accent & 0xffffff) | 0x22000000),
                a.views.background(android.graphics.Color.TRANSPARENT, 20), null));
        dateLabel.setTag("phone.today");
        Button forward =
                a.views.iconButton(
                        dateBar,
                        "back",
                        a.t("phone_next_day"),
                        () -> selectDay(dayOffset + 1, now));
        forward.setRotation(180);
        forward.setEnabled(dayOffset < 366);
        forward.setMinimumWidth(0);
        forward.setMinWidth(0);
        forward.setTag("calendar.next-day");
        dateLabel.append(" · " + ui.digits(date.format(new Date(day.start))));
        dateLabel.setContentDescription(a.t("phone_calendar") + ", " + dateLabel.getText());
        dateLabel.setTextSize(12);
        if (dayOffset != 0 || selectedDay != 0)
            a.views.button(a.body, a.t("phone_today"), this::today).setTag("calendar.today");
        return day;
    }

    private void openCalendar() {
        long openedAt = System.currentTimeMillis();
        long stamp = selectedDay == 0 ? ui.settings.day(openedAt, 0).start : selectedDay;
        PhoneCalendar.show(
                a,
                ui,
                stamp,
                selected -> {
                    long selectedAt = System.currentTimeMillis();
                    selectDay(PhoneCalendar.offset(ui.settings, selectedAt, selected), selectedAt);
                });
    }

    private void renderTimes(PhoneSchedule.Day day, int next, boolean tomorrowNext)
            throws Exception {
        if (day.times == null) {
            a.body.addView(a.views.text(a.t("phone_unavailable"), 16, a.ink));
            return;
        }
        LinearLayout prayers = a.views.column();
        prayers.setBackground(a.views.background(a.surface, 20));
        prayers.setTag("phone.prayers");
        a.body.addView(prayers, new LinearLayout.LayoutParams(-1, -2));
        for (int i = 0; i < PhoneStore.PRAYERS.length; i++) {
            final int prayer = i;
            String name = ui.data.prayerName(i, a.ar), at = ui.time(day.times[i]);
            boolean editable = i != 1;
            LinearLayout row = a.views.horizontal(prayers);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(a.views.dp(64));
            row.setPadding(a.views.dp(14), a.views.dp(10), a.views.dp(14), a.views.dp(10));
            row.setTag("phone.prayer." + PhoneStore.PRAYERS[i]);
            row.setSelected(dayOffset == 0 && !tomorrowNext && i == next);
            GradientDrawable shape = a.views.background(android.graphics.Color.TRANSPARENT, 18);
            if (row.isSelected()) {
                shape.setColor((a.accent & 0xffffff) | 0x16000000);
                shape.setStroke(a.views.dp(2), a.accent);
            }
            row.setBackground(
                    editable
                            ? new RippleDrawable(
                                    ColorStateList.valueOf((a.accent & 0xffffff) | 0x22000000),
                                    shape,
                                    null)
                            : shape);
            ImageView glyph = new ImageView(a);
            glyph.setImageDrawable(Icons.get(a, PhoneStore.PRAYERS[i], a.accent));
            glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams glyphSize =
                    new LinearLayout.LayoutParams(a.views.dp(24), a.views.dp(24));
            glyphSize.setMarginEnd(a.views.dp(12));
            row.addView(glyph, glyphSize);
            TextView label = a.views.text(name, 14, a.ink);
            label.setTag("phone.prayer." + PhoneStore.PRAYERS[i] + ".name");
            label.setTypeface(a.regular, android.graphics.Typeface.BOLD);
            label.setPadding(0, 0, 0, 0);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            TextView stamp = a.views.text(at, 14, row.isSelected() ? a.accent : a.muted);
            stamp.setTag("phone.prayer." + PhoneStore.PRAYERS[i] + ".time");
            stamp.setPaddingRelative(a.views.dp(8), 0, 0, 0);
            stamp.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(stamp, new LinearLayout.LayoutParams(-2, -2));
            if (editable) {
                ImageView chevron = new ImageView(a);
                chevron.setImageDrawable(Icons.get(a, "back", a.muted));
                chevron.setRotation(180);
                chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams arrowSize =
                        new LinearLayout.LayoutParams(a.views.dp(18), a.views.dp(18));
                arrowSize.setMarginStart(a.views.dp(10));
                row.addView(chevron, arrowSize);
                row.setOnClickListener(v -> ui.open(PhoneStore.PRAYERS[prayer]));
            }
            String mode =
                    !editable
                            ? ""
                            : !ui.settings.prayerEnabled(i)
                                    ? a.t("off")
                                    : ui.value(
                                            "call." + PhoneStore.PRAYERS[i] + ".mode",
                                            ui.settings.value(
                                                    "call." + PhoneStore.PRAYERS[i] + ".mode"));
            row.setFocusable(editable);
            row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            row.setContentDescription(name + ", " + at + (mode.isEmpty() ? "" : ", " + mode));
            if (i < PhoneStore.PRAYERS.length - 1) {
                View separator = new View(a);
                separator.setBackgroundColor((a.muted & 0xffffff) | 0x22000000);
                separator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, a.views.dp(1));
                size.setMarginStart(a.views.dp(14));
                size.setMarginEnd(a.views.dp(14));
                prayers.addView(separator, size);
            }
        }
    }

    private void destinations() {
        LinearLayout tools = a.views.column();
        tools.setPadding(0, a.views.dp(12), 0, 0);
        tools.setTag("phone.tools");
        a.body.addView(tools, new LinearLayout.LayoutParams(-1, -2));
        a.views
                .navigationRow(tools, "clock", a.t("phone_calendar"), "", this::openCalendar)
                .setTag("calendar.open");
        a.views
                .navigationRow(tools, "compass", a.t("phone_qibla"), "", () -> a.navigate("qibla"))
                .setTag("qibla.open");
        a.views
                .navigationRow(
                        tools, "mihrab", a.t("phone_widgets"), "", () -> a.navigate("widgets"))
                .setTag("widgets.open");
        LinearLayout more = a.views.column();
        more.setTag("phone.coming-soon");
        more.setPadding(0, a.views.dp(24), 0, 0);
        a.body.addView(more);
        more.addView(a.views.text(a.t("coming_soon"), 13, a.muted));
        for (String module : new String[] {"phone_quran", "phone_adhkar", "phone_tasbih"}) {
            LinearLayout locked =
                    a.views.navigationRow(
                            more,
                            "lock",
                            a.t(module),
                            "",
                            () -> a.info(a.t("coming_soon"), a.t(module)));
            ((LinearLayout.LayoutParams) locked.getLayoutParams()).bottomMargin = a.views.dp(8);
            locked.setContentDescription(a.t(module) + ", " + a.t("coming_soon"));
        }
    }

    tv.miqat.core.PrayerSelection nextPrayer(LinearLayout hero, long now, boolean live)
            throws Exception {
        PhoneSchedule.Day today = ui.settings.day(now, 0), tomorrow = ui.settings.day(now, 1);
        tv.miqat.core.PrayerSelection next =
                tv.miqat.companion.core.PhonePrayerRules.INSTANCE.nextPrayer(
                        today.times, tomorrow.times, now);
        double target = next == null ? 0 : next.getNextTime();
        TextView label =
                a.views.text(
                        a.t("phone_next")
                                + (target >= tomorrow.start ? " · " + a.t("phone_tomorrow") : "")
                                + " · "
                                + ui.location(),
                        12,
                        a.muted);
        label.setTag("phone.next.label");
        label.setPadding(0, 0, 0, a.views.dp(3));
        hero.addView(label);
        LinearLayout prayer = a.views.horizontal(hero);
        prayer.setPadding(0, a.views.dp(6), 0, a.views.dp(6));
        TextView name =
                a.views.text(
                        next == null
                                ? a.t("phone_unavailable")
                                : ui.data.prayerName(next.getNext(), a.ar),
                        28,
                        a.accent);
        name.setPadding(0, 0, 0, 0);
        name.setIncludeFontPadding(false);
        name.setTag("phone.next.name");
        name.setTypeface(a.heading);
        prayer.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (next != null) {
            TextView time = a.views.text(ui.time(target), 32, a.ink);
            time.setPadding(0, 0, 0, 0);
            time.setIncludeFontPadding(false);
            time.setTag("phone.next.time");
            time.setGravity(Gravity.END);
            time.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            time.setTypeface(a.heading);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, -2);
            size.setMarginStart(a.views.dp(12));
            prayer.addView(time, size);
        }
        if (next != null) {
            long minutes = Math.max(0, ((long) target - now + 59999) / 60000);
            TextView remaining =
                    a.views.text(
                            String.format(
                                    ui.locale(),
                                    a.t("phone_remaining"),
                                    ui.digits(
                                            String.format(
                                                    Locale.US,
                                                    "%02d:%02d",
                                                    minutes / 60,
                                                    minutes % 60))),
                            13,
                            a.accent);
            remaining.setPadding(0, a.views.dp(2), 0, 0);
            remaining.setGravity(Gravity.START);
            remaining.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            remaining.setTag("phone.next.remaining");
            hero.addView(remaining);
            if (live) {
                countdown = remaining;
                countdown.setTag("phone.countdown");
                nextTime = (long) target;
            }
        }
        return next;
    }
}

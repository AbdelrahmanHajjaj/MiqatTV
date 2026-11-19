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
        PhoneSchedule.Day tomorrow = ui.settings.day(now, 1);
        LinearLayout hero = ui.appearance.hero(a.body, ui.settings);
        hero.setPadding(a.views.dp(16), a.views.dp(12), a.views.dp(16), a.views.dp(12));
        hero.setTag("phone.hero");
        tv.miqat.core.PrayerSelection selection = nextPrayer(hero, now, true);
        int next = selection == null ? -1 : selection.getNext();
        boolean tomorrowNext = selection != null && selection.getNextTime() >= tomorrow.start;
        alertStatus(hero);
        PhoneSchedule.Day day = dateNavigation(now);
        renderTimes(day, next, tomorrowNext);
        if (tomorrow.times != null) {
            TextView fajr =
                    a.views.text(
                            a.t("phone_tomorrow")
                                    + " · "
                                    + ui.data.prayerName(0, a.ar)
                                    + " · "
                                    + ui.time(tomorrow.times[0]),
                            15,
                            a.muted);
            fajr.setTag("phone.tomorrow");
            fajr.setPadding(0, a.views.dp(4), 0, a.views.dp(16));
            a.body.addView(fajr);
        }
        destinations();
    }

    private void alertStatus(LinearLayout hero) {
        String state = PhoneAlarms.status(a, ui.settings);
        boolean repair = state.equals("phone_permission_missing");
        status = a.views.text(a.t(state), 12, a.muted);
        status.setTag("phone.status");
        status.setGravity(Gravity.CENTER);
        status.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        hero.addView(status);
        if (!ui.settings.locationReady || repair)
            a.views
                    .button(
                            hero,
                            a.t(
                                    !ui.settings.locationReady
                                            ? "phone_use_location"
                                            : repair ? "phone_repair" : "phone_audio"),
                            () -> {
                                if (ui.settings.locationReady)
                                    ui.open(repair ? "permissions" : "audio");
                                else ui.locationUi.useLocation();
                            })
                    .setTag("phone.setup");
        if (!ui.locationUi.locationMessage.isEmpty())
            hero.addView(a.views.text(a.t(ui.locationUi.locationMessage), 13, a.muted));
        playback =
                a.views.button(
                        hero,
                        a.t("phone_stop"),
                        () -> {
                            PhonePlayback.stop(a);
                            a.render();
                        });
        playback.setTag("phone.stop");
        playback.setVisibility(PhonePlayback.playing() ? View.VISIBLE : View.GONE);
        String audioError = PhoneStore.preferences(a).getString("audio-error", "");
        if (!audioError.isEmpty()) hero.addView(a.views.text(a.t(audioError), 13, a.accent));
    }

    private PhoneSchedule.Day dateNavigation(long now) {
        LinearLayout dateBar = a.views.horizontal(a.body);
        ((LinearLayout.LayoutParams) dateBar.getLayoutParams()).bottomMargin = a.views.dp(12);
        Button previous =
                a.views.iconButton(
                        dateBar,
                        "back",
                        a.t("phone_previous_day"),
                        () -> {
                            dayOffset--;
                            a.render();
                        });
        previous.setEnabled(dayOffset > -366);
        PhoneSchedule.Day day = ui.settings.day(now, dayOffset);
        SimpleDateFormat date = new SimpleDateFormat("EEEE, d MMMM yyyy", ui.locale());
        date.setTimeZone(ui.settings.zone());
        Button dateLabel =
                a.views.button(
                        dateBar,
                        ui.digits(date.format(new Date(day.start))),
                        () -> {
                            dayOffset = 0;
                            a.render();
                        });
        a.views.flex(dateLabel);
        dateLabel.setTag("phone.today");
        Button forward =
                a.views.iconButton(
                        dateBar,
                        "back",
                        a.t("phone_next_day"),
                        () -> {
                            dayOffset++;
                            a.render();
                        });
        forward.setRotation(180);
        forward.setEnabled(dayOffset < 366);
        dateLabel.append("\n" + ui.digits(PhoneTime.hijri(ui.settings, day.start, a.ar)));
        dateLabel.setTextSize(12);
        return day;
    }

    private void renderTimes(PhoneSchedule.Day day, int next, boolean tomorrowNext)
            throws Exception {
        if (day.times == null) a.body.addView(a.views.text(a.t("phone_unavailable"), 16, a.ink));
        else {
            int columns =
                    a.getResources().getDisplayMetrics().widthPixels
                                                    / a.getResources().getDisplayMetrics().density
                                            >= 360
                                    && a.getResources().getConfiguration().fontScale <= 1.4f
                            ? 3
                            : 2;
            LinearLayout pair = null;
            for (int i = 0; i < 6; i++) {
                if (i % columns == 0) {
                    pair = a.views.horizontal(a.body);
                    pair.setGravity(Gravity.TOP);
                }
                final int prayer = i;
                String name = ui.data.prayerName(i, a.ar), at = ui.time(day.times[i]);
                String mode =
                        i == 1
                                ? ""
                                : ui.value(
                                        "call." + PhoneStore.PRAYERS[i] + ".mode",
                                        ui.settings.value(
                                                "call." + PhoneStore.PRAYERS[i] + ".mode"));
                LinearLayout cell = a.views.column();
                cell.setPadding(a.views.dp(10), a.views.dp(8), a.views.dp(10), a.views.dp(8));
                cell.setMinimumHeight(a.views.dp(72));
                LinearLayout.LayoutParams cellSize = new LinearLayout.LayoutParams(0, -1, 1);
                cellSize.setMarginEnd(a.views.dp(i % columns < columns - 1 ? 4 : 0));
                cellSize.setMarginStart(a.views.dp(i % columns > 0 ? 4 : 0));
                cellSize.bottomMargin = a.views.dp(8);
                pair.addView(cell, cellSize);
                cell.setSelected(dayOffset == 0 && !tomorrowNext && i == next);
                GradientDrawable shape = a.views.background(a.surface, 20);
                if (cell.isSelected()) shape.setStroke(a.views.dp(2), a.accent);
                cell.setBackground(
                        new RippleDrawable(
                                ColorStateList.valueOf((a.accent & 0xffffff) | 0x22000000),
                                shape,
                                null));
                TextView label = a.views.text(name, 16, a.ink);
                label.setPadding(0, 0, 0, 0);
                cell.addView(label);
                TextView stamp = a.views.text(at, 20, a.accent);
                stamp.setPadding(0, a.views.dp(3), 0, 0);
                stamp.setTypeface(a.heading);
                cell.addView(stamp);
                cell.setTag("phone.prayer." + PhoneStore.PRAYERS[i]);
                cell.setFocusable(true);
                cell.setContentDescription(name + ", " + at + (mode.isEmpty() ? "" : ", " + mode));
                cell.setOnClickListener(
                        v -> ui.open(prayer == 1 ? "calculation" : PhoneStore.PRAYERS[prayer]));
            }
        }
    }

    private void destinations() {
        LinearLayout tools = a.views.horizontal(a.body);
        tools.setPadding(0, a.views.dp(8), 0, a.views.dp(8));
        Button qibla = a.views.button(tools, a.t("phone_qibla"), () -> a.navigate("qibla"));
        a.views.flex(qibla);
        qibla.setTag("qibla.open");
        Button widgets = a.views.button(tools, a.t("phone_widgets"), () -> a.navigate("widgets"));
        a.views.flex(widgets);
        ((LinearLayout.LayoutParams) qibla.getLayoutParams()).setMarginEnd(a.views.dp(8));
        ((LinearLayout.LayoutParams) widgets.getLayoutParams()).setMarginStart(a.views.dp(8));
        widgets.setTag("widgets.open");
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
        LinearLayout header = a.views.horizontal(hero);
        if (live) {
            Button location = a.views.button(header, ui.location(), () -> ui.open("calculation"));
            location.setTag("phone.location");
            location.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            location.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            location.setPadding(0, 0, 0, 0);
            location.setTextSize(14);
            a.views.flex(location);
        }
        TextView label =
                a.views.text(
                        a.t("phone_next")
                                + (target >= tomorrow.start
                                        ? " \u00b7 " + a.t("phone_tomorrow")
                                        : ""),
                        14,
                        a.muted);
        label.setTag("phone.next.label");
        label.setGravity(live ? Gravity.END : Gravity.START);
        label.setTextAlignment(
                live ? View.TEXT_ALIGNMENT_VIEW_END : View.TEXT_ALIGNMENT_VIEW_START);
        header.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout prayer = a.views.horizontal(hero);
        prayer.setPadding(0, a.views.dp(6), 0, a.views.dp(6));
        TextView name =
                a.views.text(
                        next == null
                                ? a.t("phone_unavailable")
                                : ui.data.prayerName(next.getNext(), a.ar),
                        28,
                        a.accent);
        name.setTag("phone.next.name");
        name.setTypeface(a.heading);
        prayer.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (next != null) {
            TextView time = a.views.text(ui.time(target), 32, a.ink);
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
                            18,
                            a.accent);
            remaining.setGravity(Gravity.CENTER);
            remaining.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
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

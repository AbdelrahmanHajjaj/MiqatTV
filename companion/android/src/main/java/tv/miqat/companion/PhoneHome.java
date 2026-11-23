package tv.miqat.companion;

import android.content.res.ColorStateList;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import java.util.*;

/** Daily prayer rendering and its foreground countdown; no preference or alarm ownership. */
final class PhoneHome {
    private final MainActivity a;
    private final PhoneUi ui;

    PhoneHome(MainActivity activity, PhoneUi ui) {
        a = activity;
        this.ui = ui;
    }

    private TextView countdown, status;
    private PlaybackWave playback;
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
                            countdown.setText(remaining(nextTime, now));
                        }
                        if (status != null && ui.settings != null) {
                            String state = PhoneAlarms.status(a, ui.settings);
                            status.setText(state.equals("phone_location_needed") ? "" : a.t(state));
                            status.setVisibility(state.equals("phone_alerts_ready") || state.equals("phone_location_needed") ? View.GONE : View.VISIBLE);
                        }
                    }
                    if (playback != null)
                        playback.sync();
                    clock.postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
                }
            };

    private String remaining(long target, long now) {
        long seconds = Math.max(0, (target - now + 999) / 1000);
        String duration = String.format(Locale.US, "%02d:%02d:%02d",
                seconds / 3600, seconds / 60 % 60, seconds % 60);
        return String.format(ui.locale(), a.t("phone_remaining"), ui.digits(duration));
    }

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
        verse();
        PhoneSchedule.Day day = ui.settings.day(now, 0);
        LinearLayout hero = ui.appearance.hero(a.body, ui.settings);
        hero.setPadding(a.views.dp(12), a.views.dp(4), a.views.dp(12), a.views.dp(12));
        hero.setTag("phone.hero");
        hero.setClipToPadding(false);
        cardMetadata(hero, day);
        PhoneSchedule.Day tomorrow = ui.settings.day(now, 1);
        tv.miqat.core.PrayerSelection selection = nextPrayer(hero, now, true);
        int next = selection == null ? -1 : selection.getNext();
        boolean tomorrowNext = selection != null && selection.getNextTime() >= tomorrow.start;
        eventLine(hero, now);
        playback=new PlaybackWave(a);
        LinearLayout.LayoutParams waveSize=new LinearLayout.LayoutParams(-1,a.views.dp(48));
        waveSize.setMargins(-a.views.dp(12),0,-a.views.dp(12),-a.views.dp(12));
        hero.addView(playback,waveSize);
        alertStatus();
        renderTimes(day, next, tomorrowNext);
        destinations();
    }

    private void verse() throws Exception {
        org.json.JSONArray lines = ui.data.source.getJSONArray("prayerVerse");
        StringBuilder sacred = new StringBuilder();
        for (int i = 0; i < lines.length(); i++) {
            if (i != 0) sacred.append(' ');
            sacred.append(lines.getString(i));
        }
        TextView text = a.views.text(sacred.toString(), 19, a.ink);
        text.setTag("phone.verse");
        text.setTypeface(a.getResources().getFont(R.font.amiri_bold));
        text.setTextLocale(new Locale("ar"));
        text.setTextDirection(View.TEXT_DIRECTION_RTL);
        text.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        text.setGravity(Gravity.CENTER);
        text.setIncludeFontPadding(false);
        text.setPadding(0, 0, 0, a.views.dp(12));
        a.body.addView(text);
    }

    private void eventLine(LinearLayout hero, long now) throws Exception {
        PhoneHijriEvents.Entry event = PhoneHijriEvents.upcoming(a, ui.settings, now, a.ar);
        if (event == null) return;
        String when = event.offset == 0 ? a.t("phone_today") : event.offset == 1
                ? a.t("phone_tomorrow")
                : String.format(ui.locale(), a.t("phone_event_in_days"), ui.digits(Integer.toString(event.offset)));
        Button entry = a.views.button(hero, event.label + " · " + when, () -> openCalendar(event.date));
        entry.setTag("phone.hijri.event");
        entry.setBackground(new RippleDrawable(
                ColorStateList.valueOf((a.accent & 0xffffff) | 0x22000000),
                a.views.background((a.accent & 0xffffff) | 0x10000000, 10), null));
        entry.setTextSize(12);
        entry.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        entry.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        entry.setPaddingRelative(a.views.dp(4), a.views.dp(4), a.views.dp(4), a.views.dp(4));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.topMargin = a.views.dp(10);
        entry.setLayoutParams(size);
    }

    private void cardMetadata(LinearLayout hero, PhoneSchedule.Day day) throws Exception {
        LinearLayout metadata = a.views.horizontal(hero);
        metadata.setTag("phone.location.row");
        metadata.setGravity(Gravity.CENTER_VERTICAL);
        Button date = a.views.button(metadata,
                ui.digits(PhoneTime.hijri(ui.settings, day.start, a.ar)), this::openCalendar);
        date.setTag("phone.today");
        date.setTextSize(12);
        date.setMaxLines(2);
        date.setContentDescription(a.t("phone_calendar") + ", " + date.getText());
        date.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        metaButton(date, "clock", a.muted);
        Button city = a.views.button(metadata,
                ui.locationUi.locator.running ? a.t("phone_location_working") : ui.location(),
                () -> {
                    if (PhoneLocation.allowed(a)) ui.locationUi.useLocation();
                    else ui.open("calculation");
                });
        city.setTag("phone.location");
        city.setTextSize(12);
        city.setMaxWidth(a.views.dp(120));
        city.setSingleLine(true);
        city.setEllipsize(android.text.TextUtils.TruncateAt.END);
        city.setEnabled(!ui.locationUi.locator.running);
        city.setContentDescription(a.t("phone_use_location") + ", " + ui.location());
        city.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        metaButton(city, "compass", a.accent);
        if (!ui.locationUi.locationMessage.isEmpty()
                && !ui.locationUi.locationMessage.equals("phone_location_updated")
                && !ui.locationUi.locationMessage.equals("phone_location_found")
                && !ui.locationUi.locationMessage.equals("phone_location_working")) {
            TextView message = a.views.text(a.t(ui.locationUi.locationMessage), 12, a.muted);
            message.setTag("phone.location.message");
            a.body.addView(message);
        }
    }

    private void metaButton(Button button, String glyph, int tint) {
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPaddingRelative(0, a.views.dp(4), a.views.dp(6), a.views.dp(4));
        button.setTextColor(tint);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        Drawable icon = Icons.get(a, glyph, tint);
        icon.setBounds(0, 0, a.views.dp(16), a.views.dp(16));
        button.setCompoundDrawablesRelative(icon, null, null, null);
        button.setCompoundDrawablePadding(a.views.dp(6));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf((a.accent & 0xffffff) | 0x22000000),
                a.views.background(android.graphics.Color.TRANSPARENT, 16), null));
    }

    private void alertStatus() {
        String state = PhoneAlarms.status(a, ui.settings);
        LinearLayout alerts = a.views.horizontal(a.body);
        alerts.setPadding(0, 0, 0, a.views.dp(8));
        status = a.views.text(state.equals("phone_location_needed") ? "" : a.t(state), 12, a.muted);
        status.setTag("phone.status");
        status.setVisibility(state.equals("phone_alerts_ready") || state.equals("phone_location_needed") ? View.GONE : View.VISIBLE);
        status.setPadding(0, 0, 0, 0);
        alerts.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        if (ui.settings.locationReady && state.equals("phone_permission_missing"))
            a.views
                    .iconButton(alerts, "gear", a.t("phone_repair"), () -> ui.open("permissions"))
                    .setTag("phone.setup");
        String audioError = PhoneStore.preferences(a).getString("audio-error", "");
        if (!audioError.isEmpty()) a.body.addView(a.views.text(a.t(audioError), 13, a.accent));
    }

    private void openCalendar() {
        openCalendar(ui.settings.day(System.currentTimeMillis(), 0).start);
    }

    private void openCalendar(long stamp) {
        PhoneCalendar.show(a, ui, stamp);
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
            row.setMinimumHeight(a.views.dp(52));
            row.setPadding(a.views.dp(12), a.views.dp(8), a.views.dp(12), a.views.dp(8));
            row.setTag("phone.prayer." + PhoneStore.PRAYERS[i]);
            row.setSelected(!tomorrowNext && i == next);
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
        LinearLayout tools = a.views.horizontal(a.body);
        tools.setPadding(0, a.views.dp(8), 0, 0);
        tools.setTag("phone.tools");
        for (String destination : new String[] {"qibla", "widgets"}) {
            Button action = a.views.button(tools,
                    a.t(destination.equals("qibla") ? "phone_qibla" : "phone_widgets_short"),
                    () -> a.navigate(destination));
            action.setTag(destination + ".open");
            action.setContentDescription(a.t(destination.equals("qibla") ? "phone_qibla" : "phone_widgets"));
            action.setTextSize(12);
            a.views.leadingIcon(action, destination.equals("qibla") ? "compass" : "mihrab");
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
            if (destination.equals("qibla")) size.setMarginEnd(a.views.dp(8));
            action.setLayoutParams(size);
        }
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
                                + (live ? "" : " · " + ui.location()),
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
            TextView remaining =
                    a.views.text(
                            remaining((long) target, now),
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

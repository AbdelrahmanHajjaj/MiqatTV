package tv.miqat.companion;

import android.widget.*;
import java.util.*;

/** A prayer's weekday overrides expand in place; defaults never copy between days. */
final class PhoneDayOverrides {
    private final MainActivity a;
    private final PhoneUi ui;

    PhoneDayOverrides(MainActivity a, PhoneUi ui) {
        this.a = a;
        this.ui = ui;
    }

    void render(String prayer) throws Exception {
        int index = Arrays.asList(PhoneStore.PRAYERS).indexOf(prayer);
        if (index < 0 || index == 1) throw new IllegalArgumentException();
        a.body.addView(a.views.heading(ui.data.prayerName(index, a.ar)));
        String[] names = new java.text.DateFormatSymbols(ui.locale()).getWeekdays();
        for (int n = 0; n < 7; n++) {
            int day = (n + 1) % 7;
            String base = "call." + prayer + ".", tag = "phone.day." + day;
            String sound = base + "day-" + day + "-sound", vibrate = base + "day-" + day + "-vibrate";
            boolean overridden = !ui.settings.value(sound).equals("inherit");
            LinearLayout card = a.views.card(a.body);
            card.setPadding(a.views.dp(12), a.views.dp(8), a.views.dp(12), a.views.dp(8));
            ((LinearLayout.LayoutParams)card.getLayoutParams()).bottomMargin = a.views.dp(8);
            a.views.toggle(card, names[day + 1], overridden, enabled -> {
                Map<String, String> changed = new LinkedHashMap<>();
                String mode = ui.settings.value(base + "mode");
                changed.put(sound, !enabled ? "inherit" : mode.equals("adhan")
                        ? "adhan" : mode.equals("notification") ? "notification" : "silent");
                if (enabled) changed.put(vibrate, Boolean.toString(ui.settings.vibrate(index)));
                ui.save(changed, false);
            }).setTag(tag);
            if (!overridden) continue;
            List<Choices.Option> choices = new ArrayList<>();
            for (String mode : new String[] {"adhan", "notification", "silent"})
                choices.add(new Choices.Option(mode, a.t("phone_" + mode)));
            a.views.segmentedChoices(card, tag + ".sound", choices, ui.settings.value(sound),
                    selected -> ui.save(sound, selected));
            a.views.toggle(card, a.t("phone_vibrate"), ui.settings.vibrateOnDay(index, day),
                    enabled -> ui.save(vibrate, Boolean.toString(enabled))).setTag(tag + ".vibrate");
        }
        if (!ui.settings.prayerEnabled(index)) {
            a.views.disableContent(a.body);
            a.body.setAlpha(.38f);
        }
    }
}

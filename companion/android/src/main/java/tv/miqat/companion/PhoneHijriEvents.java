package tv.miqat.companion;

import android.content.Context;
import android.icu.util.IslamicCalendar;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import tv.miqat.companion.core.HijriEvent;
import tv.miqat.companion.core.HijriEvents;

/** Bridges the canonical event catalog to the same corrected calendar used for prayer browsing. */
final class PhoneHijriEvents {
    private PhoneHijriEvents() {}

    static final class Entry {
        final String label;
        final int offset;
        final long date;
        Entry(String label, int offset, long date) {
            this.label = label;
            this.offset = offset;
            this.date = date;
        }
    }

    static String label(Context context, PhoneStore settings, long stamp, boolean ar) {
        try {
            JSONArray catalog = PhoneData.get(context).source.getJSONArray("hijriEvents");
            List<HijriEvent> dates = dates(catalog);
            IslamicCalendar date = PhoneCalendar.calendar(settings, stamp, ar);
            String id = HijriEvents.INSTANCE.find(dates,
                    date.get(android.icu.util.Calendar.MONTH) + 1,
                    date.get(android.icu.util.Calendar.DATE));
            if (id != null)
                for (int i = 0; i < catalog.length(); i++) {
                    JSONObject event = catalog.getJSONObject(i);
                    if (id.equals(event.getString("id"))) return event.getString(ar ? "ar" : "en");
                }
        } catch (Exception unavailable) {
            android.util.Log.w("MiqatCalendar", "Hijri event catalog unavailable", unavailable);
        }
        return "";
    }

    private static List<HijriEvent> dates(JSONArray catalog) throws Exception {
        List<HijriEvent> dates = new ArrayList<>();
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject event = catalog.getJSONObject(i);
            dates.add(new HijriEvent(event.getString("id"), event.getInt("month"), event.getInt("day")));
        }
        return dates;
    }

    static Entry upcoming(Context context, PhoneStore settings, long now, boolean ar) {
        try {
            int count = HijriEvents.UPCOMING_DAYS + 1;
            int[][] days = new int[count][2];
            long[] stamps = new long[count];
            for (int offset = 0; offset < count; offset++) {
                stamps[offset] = settings.day(now, offset).start;
                IslamicCalendar date = PhoneCalendar.calendar(settings, stamps[offset], ar);
                days[offset][0] = date.get(android.icu.util.Calendar.MONTH) + 1;
                days[offset][1] = date.get(android.icu.util.Calendar.DATE);
            }
            int offset = HijriEvents.INSTANCE.upcoming(
                    dates(PhoneData.get(context).source.getJSONArray("hijriEvents")), days);
            if (offset >= 0) return new Entry(label(context, settings, stamps[offset], ar), offset, stamps[offset]);
        } catch (Exception unavailable) {
            android.util.Log.w("MiqatCalendar", "Upcoming Hijri event unavailable", unavailable);
        }
        return null;
    }
}

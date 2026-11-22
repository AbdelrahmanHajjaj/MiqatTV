package tv.miqat.companion;

import com.batoulapps.adhan.*;
import com.batoulapps.adhan.data.DateComponents;
import java.util.*;
import tv.miqat.core.PrayerRules;

/** Explicit civil-zone astronomy adapter over the same pinned Adhan source as Android TV. */
final class PhoneSchedule {
    static final class Day {
        final double[] times;
        final long start;
        Day(double[] times,long start){this.times=times;this.start=start;}
    }
    static Day day(long now,TimeZone zone,int offset,double latitude,double longitude,PrayerMethod method,
        Madhab madhab,HighLatitudeRule high,int[] adjustments) {
        PrayerRules.INSTANCE.validateOffsets(adjustments);
        Calendar date=new GregorianCalendar(zone);date.setTimeInMillis(now);date.add(Calendar.DAY_OF_MONTH,offset);
        DateComponents civil=new DateComponents(date.get(Calendar.YEAR),date.get(Calendar.MONTH)+1,date.get(Calendar.DAY_OF_MONTH));
        date.set(Calendar.HOUR_OF_DAY,0);date.set(Calendar.MINUTE,0);date.set(Calendar.SECOND,0);date.set(Calendar.MILLISECOND,0);
        CalculationParameters params=method.getParameters();params.madhab=madhab;params.highLatitudeRule=high;
        params.adjustments=new PrayerAdjustments(adjustments[0],adjustments[1],adjustments[2],adjustments[3],adjustments[4],adjustments[5]);
        PrayerTimes result=new PrayerTimes(new Coordinates(latitude,longitude),civil,params);
        Date[] raw={result.fajr,result.sunrise,result.dhuhr,result.asr,result.maghrib,result.isha};
        double[] times=new double[6];
        for(int i=0;i<6;i++){if(raw[i]==null)return new Day(null,date.getTimeInMillis());times[i]=PrayerRules.INSTANCE.floorToMinute(raw[i].getTime());}
        return new Day(times,date.getTimeInMillis());
    }
}

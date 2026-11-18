package tv.miqat.companion;

import com.batoulapps.adhan.*;
import java.time.Instant;
import java.util.*;

public final class PhoneScheduleCheck {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static PhoneSchedule.Day day(String instant,String zone,int offset,double lat,double lng,PrayerMethod method,int[] adjustments){
        return PhoneSchedule.day(Instant.parse(instant).toEpochMilli(),TimeZone.getTimeZone(zone),offset,lat,lng,method,
            Madhab.SHAFI,HighLatitudeRule.MIDDLE_OF_THE_NIGHT,adjustments);
    }
    public static void main(String[] args) {
        TimeZone original=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            PhoneSchedule.Day cairo=day("2026-11-12T10:00:00Z","Africa/Cairo",0,30.0444,31.2357,PrayerMethod.EGYPTIAN,new int[6]);
            Calendar local=Calendar.getInstance(TimeZone.getTimeZone("Africa/Cairo"));int[] expected={288,377,700,879,1020,1100};
            for(int i=0;i<6;i++){local.setTimeInMillis((long)cairo.times[i]);check(local.get(Calendar.HOUR_OF_DAY)*60+local.get(Calendar.MINUTE)==expected[i],"Frozen Cairo vector "+i);}
            check(TimeZone.getDefault().getID().equals("Pacific/Honolulu"),"Calculation changed device zone");
            PhoneSchedule.Day tokyo=day("2026-11-12T23:30:00Z","Asia/Tokyo",0,35.6762,139.6503,PrayerMethod.EGYPTIAN,new int[6]);
            check(tokyo.start==Instant.parse("2026-11-12T15:00:00Z").toEpochMilli(),"Selected-zone civil day");
            PhoneSchedule.Day before=day("2026-03-08T12:00:00Z","America/New_York",0,40.7128,-74.006,PrayerMethod.NORTH_AMERICA,new int[6]);
            PhoneSchedule.Day after=day("2026-03-08T12:00:00Z","America/New_York",1,40.7128,-74.006,PrayerMethod.NORTH_AMERICA,new int[6]);
            check(after.start-before.start==23*3600000L,"DST civil-day transition");
            for(PrayerMethod method:PrayerMethod.values()) {
                int[] offsets={1,-1,30,-30,0,2};
                PhoneSchedule.Day base=day("2026-11-12T10:00:00Z","Africa/Cairo",0,30.0444,31.2357,method,new int[6]);
                PhoneSchedule.Day shifted=day("2026-11-12T10:00:00Z","Africa/Cairo",0,30.0444,31.2357,method,offsets);
                for(int i=0;i<6;i++){check(base.times[i]%60000==0,"Minute flooring");check(shifted.times[i]-base.times[i]==offsets[i]*60000.0,"Offset applied once");}
            }
            check(day("2026-06-21T12:00:00Z","UTC",0,89,0,PrayerMethod.EGYPTIAN,new int[6]).times==null,"Polar unavailable result must remain explicit");
        }finally{TimeZone.setDefault(original);}
        System.out.println("PASS: phone uses frozen Cairo vector, explicit civil zone/DST, every method/offset and unavailable polar result");
    }
}

package tv.miqat.companion.core

import kotlin.test.*

class PhonePrayerRulesTest {
    private val modes = intArrayOf(2, 0, 1, 2, 2, 2)
    private val none = IntArray(6)
    private val times = doubleArrayOf(600000.0, 900000.0, 1200000.0, 1800000.0, 2400000.0, 3000000.0)

    @Test fun locationQualityAndCityDistancesAreBounded() {
        assertTrue(PhonePrayerRules.usableLocation(30.0,31.0,3000.0,120000.0))
        assertFalse(PhonePrayerRules.usableLocation(30.0,31.0,10001.0,0.0))
        assertFalse(PhonePrayerRules.usableLocation(30.0,31.0,20.0,120001.0))
        assertFalse(PhonePrayerRules.usableLocation(30.0,31.0,20.0,-1.0))
        assertFalse(PhonePrayerRules.usableLocation(Double.NaN,31.0,20.0,0.0))
        assertEquals(0.0,PhonePrayerRules.distanceKm(30.0,31.0,30.0,31.0),0.000001)
        assertTrue(PhonePrayerRules.distanceKm(0.0,179.9,0.0,-179.9) in 22.0..23.0)
        assertEquals(20015.09,PhonePrayerRules.distanceKm(90.0,0.0,-90.0,0.0),0.1)
    }

    @Test fun weekdayAndQuietHoursKeepExplicitBoundaries() {
        fun mode(day:Int, minute:Int, start:Int=1320, end:Int=420, quietMode:Int=4) =
            PhonePrayerRules.alertMode(2,day,1 shl 5,minute,true,start,end,quietMode)
        assertEquals(0,mode(4,800)); assertEquals(2,mode(5,420))
        assertEquals(4,mode(5,1320)); assertEquals(4,mode(5,0))
        assertEquals(3,mode(5,100,0,0,3)); assertEquals(0,mode(5,100,0,0,0))
        assertEquals(2,mode(5,1000,60,120)); assertEquals(4,mode(5,60,60,120))
        assertEquals(2,mode(5,120,60,120))
        assertEquals(0,PhonePrayerRules.alertMode(0,5,127,100,false,0,0,4))
    }

    @Test fun nextPrayerSkipsSunriseAndUsesTomorrowWithoutInventingPolarTimes() {
        val tomorrow=times.map { it+86400000 }.toDoubleArray()
        assertEquals(2,PhonePrayerRules.nextPrayer(times,tomorrow,600000.0)!!.next)
        assertEquals(tomorrow[0],PhonePrayerRules.nextPrayer(times,tomorrow,3000000.0)!!.nextTime)
        assertNull(PhonePrayerRules.nextPrayer(times,null,3000000.0))
        assertNull(PhonePrayerRules.nextPrayer(null,null,0.0))
        assertEquals(tomorrow[0],PhonePrayerRules.nextPrayer(null,tomorrow,0.0)!!.nextTime)
    }

    @Test fun remindersStayIndependentAndSunriseNeverAlerts() {
        val before = intArrayOf(5, 0, 0, 0, 0, 0)
        val event = PhonePrayerRules.nextEvent(arrayOf(times), 0.0, modes, before, none, emptyArray())!!
        assertEquals("before", event.kind); assertEquals(300000.0, event.time)
        val call = PhonePrayerRules.nextEvent(arrayOf(times), 0.0, modes, before, none, arrayOf(event.id))!!
        assertEquals("call", call.kind); assertEquals(0, call.prayer)
        modes[0] = 0
        assertEquals(2, PhonePrayerRules.nextEvent(arrayOf(times), 0.0, modes, before, none, emptyArray())!!.prayer)
    }

    @Test fun claimsAndLateWindowPreventReplaysButAllowOnTimeRecovery() {
        val call = PhonePrayerRules.nextEvent(arrayOf(times), 600001.0, modes, none, none, emptyArray())!!
        assertEquals(0, call.prayer)
        assertTrue(PhonePrayerRules.due(call.time, 719999.0)); assertFalse(PhonePrayerRules.due(call.time, 720000.0))
        assertFalse(PhonePrayerRules.due(call.time, 599999.0))
        assertEquals(2, PhonePrayerRules.nextEvent(arrayOf(times), 600001.0, modes, none, none, arrayOf(call.id))!!.prayer)
        assertEquals(2, PhonePrayerRules.nextEvent(arrayOf(times), 720000.0, modes, none, none, emptyArray())!!.prayer)
    }

    @Test fun simultaneousEventsRemainDeliverableAndDayOrderDoesNotChooseTheWrongTime() {
        val collapsed = times.copyOf(); collapsed[2] = collapsed[0]
        val first = PhonePrayerRules.nextEvent(arrayOf(times.map { it + 86400000 }.toDoubleArray(), collapsed), 600000.0, modes, none, none, emptyArray())!!
        val second = PhonePrayerRules.nextEvent(arrayOf(collapsed), 600001.0, modes, none, none, arrayOf(first.id))!!
        assertEquals(0, first.prayer); assertEquals(2, second.prayer)
    }

    @Test fun afterRemindersAndUnavailableAstronomyAreExplicit() {
        val after = intArrayOf(5, 0, 0, 0, 0, 0)
        val call = PhonePrayerRules.nextEvent(arrayOf(times), 0.0, modes, none, after, emptyArray())!!
        val event = PhonePrayerRules.nextEvent(arrayOf(times), 600000.0, modes, none, after, arrayOf(call.id))!!
        assertEquals("after", event.kind); assertEquals(900000.0, event.time)
        assertNull(PhonePrayerRules.nextEvent(emptyArray(), 0.0, modes, none, none, emptyArray()))
    }

    @Test fun malformedSettingsAndTimesAreRejected() {
        assertFailsWith<IllegalArgumentException> { PhonePrayerRules.validate(Double.NaN, 0.0, modes, none, none, 100, 0) }
        assertFailsWith<IllegalArgumentException> { PhonePrayerRules.validate(0.0, 181.0, modes, none, none, 100, 0) }
        assertFailsWith<IllegalArgumentException> { PhonePrayerRules.validate(0.0, 0.0, intArrayOf(2, 1, 1, 1, 1, 1), none, none, 100, 0) }
        assertFailsWith<IllegalArgumentException> { PhonePrayerRules.validate(0.0, 0.0, modes, intArrayOf(61, 0, 0, 0, 0, 0), none, 100, 0) }
        assertFailsWith<IllegalArgumentException> { PhonePrayerRules.validate(0.0, 0.0, modes, none, none, 101, 0) }
        assertFailsWith<IllegalArgumentException> { PhonePrayerRules.validate(0.0, 0.0, modes, none, none, 100, 31) }
        assertFailsWith<IllegalArgumentException> { PhonePrayerRules.nextEvent(arrayOf(doubleArrayOf(Double.NaN)), 0.0, modes, none, none, emptyArray()) }
    }
}

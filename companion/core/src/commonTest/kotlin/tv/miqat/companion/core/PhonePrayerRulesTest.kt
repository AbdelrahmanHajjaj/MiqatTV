package tv.miqat.companion.core

import kotlin.test.*

class PhonePrayerRulesTest {
    private val modes = intArrayOf(2, 0, 1, 2, 2, 2)
    private val none = IntArray(6)
    private val times = doubleArrayOf(600000.0, 900000.0, 1200000.0, 1800000.0, 2400000.0, 3000000.0)

    @Test fun volumeUsesTheStreamMaximumAndAvailableSteps() {
        assertEquals(11,PhonePrayerRules.volumeIndex(70,15))
        assertEquals(5,PhonePrayerRules.volumeIndex(70,7))
        assertEquals(0,PhonePrayerRules.volumeIndex(0,15))
        assertEquals(1,PhonePrayerRules.volumeIndex(5,7))
        assertEquals(Int.MAX_VALUE,PhonePrayerRules.volumeIndex(100,Int.MAX_VALUE))
        assertFailsWith<IllegalArgumentException> { PhonePrayerRules.volumeIndex(-1,15) }
        assertFailsWith<IllegalArgumentException> { PhonePrayerRules.volumeIndex(101,15) }
        assertFailsWith<IllegalArgumentException> { PhonePrayerRules.volumeIndex(70,0) }
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

    @Test fun batchIncludesEveryFutureReminderWithoutReplayingOneAlreadyDue() {
        val before = intArrayOf(5, 0, 0, 0, 0, 0)
        val after = intArrayOf(10, 0, 0, 0, 0, 0)
        val events = PhonePrayerRules.futureDayEvents(times, times[0], modes, before, after)
        assertEquals(listOf("after", "call", "call", "call", "call"), events.map { it.kind })
        assertEquals(listOf(0, 2, 3, 4, 5), events.map { it.prayer })
        assertEquals(listOf(1200000.0, 1200000.0, 1800000.0, 2400000.0, 3000000.0), events.map { it.time })
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
    @Test fun weekdayOverrideCannotEnableDisabledPrayerAndCutoffFollowsRecordingLength() {
        assertEquals(0, PhonePrayerRules.effectiveMode(2, false, 2))
        assertEquals(0, PhonePrayerRules.effectiveMode(0, true, 2))
        assertEquals(4, PhonePrayerRules.effectiveMode(2, true, 4))
        assertEquals(2, PhonePrayerRules.effectiveMode(2, true, -1))
        assertEquals(150000, PhonePrayerRules.cutoffMillis(200000, 75, 0))
        assertEquals(75000, PhonePrayerRules.cutoffMillis(100000, 75, 0))
        assertEquals(30000, PhonePrayerRules.cutoffMillis(200000, 75, 30))
        assertEquals(200000, PhonePrayerRules.cutoffMillis(200000, 100, 0))
        assertFailsWith<IllegalArgumentException> { PhonePrayerRules.cutoffMillis(0, 100, 0) }
        assertFailsWith<IllegalArgumentException> { PhonePrayerRules.cutoffMillis(200000, 101, 0) }
        assertFailsWith<IllegalArgumentException> { PhonePrayerRules.cutoffMillis(200000, 74, 0) }
    }
}

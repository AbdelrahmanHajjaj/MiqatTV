package tv.miqat.companion.core

import kotlin.test.*

class PhonePrayerRulesTest {
    private val modes = intArrayOf(2, 0, 1, 2, 2, 2)
    private val none = IntArray(6)
    private val times = doubleArrayOf(600000.0, 900000.0, 1200000.0, 1800000.0, 2400000.0, 3000000.0)

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

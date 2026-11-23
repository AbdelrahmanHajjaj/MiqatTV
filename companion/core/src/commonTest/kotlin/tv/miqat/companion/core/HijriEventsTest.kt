package tv.miqat.companion.core

import kotlin.test.*

class HijriEventsTest {
    private val events = listOf(HijriEvent("year-start", 1, 1), HijriEvent("occasion", 12, 9))

    @Test fun matchesOnlyTheSuppliedHijriDateWithoutInferringUnlistedOccasions() {
        assertNull(HijriEvents.find(events, 12, 8))
        assertEquals("occasion", HijriEvents.find(events, 12, 9))
        assertNull(HijriEvents.find(events, 12, 10))
        assertNull(HijriEvents.find(events, 9, 27))
        assertNull(HijriEvents.find(emptyList(), 12, 9))
        assertEquals("year-start", HijriEvents.find(events, 1, 1))
    }

    @Test fun invalidDatesAreRejectedAtThePortableBoundary() {
        assertFailsWith<IllegalArgumentException> { HijriEvent("", 1, 1) }
        assertFailsWith<IllegalArgumentException> { HijriEvent("bad", 0, 1) }
        assertFailsWith<IllegalArgumentException> { HijriEvent("bad", 1, 31) }
        assertFailsWith<IllegalArgumentException> { HijriEvents.find(events, 13, 1) }
        assertFailsWith<IllegalArgumentException> { HijriEvents.find(events, 1, 0) }
        assertFailsWith<IllegalArgumentException> { HijriEvents.upcoming(events, Array(5) { intArrayOf(1, 1) }) }
        assertFailsWith<IllegalArgumentException> { HijriEvents.upcoming(events, arrayOf(intArrayOf(1))) }
    }

    @Test fun currentEventWinsAndUpcomingCanCrossTheHijriYear() {
        assertEquals(0, HijriEvents.upcoming(events, arrayOf(intArrayOf(12, 9), intArrayOf(1, 1))))
        assertEquals(2, HijriEvents.upcoming(events,
            arrayOf(intArrayOf(12, 29), intArrayOf(12, 30), intArrayOf(1, 1))))
        assertEquals(3, HijriEvents.upcoming(events,
            arrayOf(intArrayOf(12, 6), intArrayOf(12, 7), intArrayOf(12, 8), intArrayOf(12, 9))))
        assertEquals(-1, HijriEvents.upcoming(events, arrayOf(intArrayOf(12, 10))))
        assertEquals(-1, HijriEvents.upcoming(events, emptyArray()))
    }

    @Test fun rangesIncludeTheirLastDayAndRetainOverlappingObservances() {
        val days = listOf(HijriEvent("eid", 12, 10, 13), HijriEvent("tashriq", 12, 11, 13))
        assertEquals(listOf("eid"), HijriEvents.onDay(days, 12, 10).map { it.id })
        assertEquals(listOf("eid", "tashriq"), HijriEvents.onDay(days, 12, 13).map { it.id })
        assertNull(HijriEvents.find(days, 12, 14))
        assertEquals(0, HijriEvents.upcoming(days, arrayOf(intArrayOf(12, 12))))
        assertFailsWith<IllegalArgumentException> { HijriEvent("bad-range", 12, 10, 9) }
    }
}

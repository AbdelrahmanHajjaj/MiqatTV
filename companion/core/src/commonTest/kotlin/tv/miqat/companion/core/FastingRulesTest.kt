package tv.miqat.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FastingRulesTest {
    private val selected = FastingPreferences(true, true, true, true, true, 30, true)
    private val fajr = 4.8 * 60 * 60_000
    private val maghrib = 18.0 * 60 * 60_000

    @Test fun matchingSelectionsShareOnePairAtLocalFajrAndMaghrib() {
        for ((month, day, weekday) in listOf(
            Triple(9, 29, 1), Triple(1, 10, 4), Triple(12, 9, 3),
            Triple(2, 14, 1), Triple(3, 4, 1), Triple(3, 7, 4),
        )) {
            val events = events(month, day, weekday, selected)
            assertEquals(listOf(FastingRules.SUHOOR, FastingRules.IFTAR), events.map { it.kind })
            assertEquals(listOf(fajr - 30 * 60_000, maghrib), events.map { it.time })
            assertEquals(listOf(fajr, maghrib), events.map { it.prayerTime })
        }
    }

    @Test fun voluntarySchedulesRespectOptInAndExcludedDates() {
        assertTrue(events(2, 4, 1,
            selected.copy(mondayThursday = false)).isEmpty())
        assertTrue(events(2, 4, 2, selected).isEmpty())
        assertTrue(events(9, 15, 4,
            selected.copy(ramadan = false)).isEmpty())
        assertTrue(events(10, 1, 1, selected).isEmpty())
        for (day in 10..13)
            assertTrue(events(12, day, 4, selected).isEmpty())
        assertTrue(events(2, 4, 1,
            selected.copy(suhoorMinutes = 0, iftar = false)).isEmpty())
        assertEquals(listOf(FastingRules.IFTAR), events(2, 4, 1,
            selected.copy(suhoorMinutes = 0)).map { it.kind })
    }

    @Test fun dayBeforeWorksOnItsOwnAndFollowsTheUpcomingFastDate() {
        val eveOnly = selected.copy(suhoorMinutes = 0, iftar = false, eveMinutes = 1200)
        val evening = -4 * 60 * 60_000L
        val event = FastingRules.events(2, 14, 1, Double.NaN, Double.NaN, eveOnly, 0L, evening).single()
        assertEquals(FastingRules.EVE, event.kind)
        assertEquals(evening.toDouble(), event.time)
        assertEquals(0.0, event.prayerTime)
        assertTrue(FastingRules.events(10, 1, 1, fajr, maghrib, eveOnly, 0L, evening).isEmpty())
        assertTrue(FastingRules.events(2, 4, 2, fajr, maghrib, eveOnly, 0L, evening).isEmpty())
    }

    private fun events(month: Int, day: Int, weekday: Int, preferences: FastingPreferences) =
        FastingRules.events(month, day, weekday, fajr, maghrib, preferences, 0L, null)
}

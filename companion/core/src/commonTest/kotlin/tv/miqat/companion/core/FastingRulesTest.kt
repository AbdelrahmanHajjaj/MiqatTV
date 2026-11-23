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
            val events = FastingRules.events(month, day, weekday, fajr, maghrib, selected)
            assertEquals(listOf(FastingRules.SUHOOR, FastingRules.IFTAR), events.map { it.kind })
            assertEquals(listOf(fajr - 30 * 60_000, maghrib), events.map { it.time })
            assertEquals(listOf(fajr, maghrib), events.map { it.prayerTime })
        }
    }

    @Test fun voluntarySchedulesRespectOptInAndExcludedDates() {
        assertTrue(FastingRules.events(2, 4, 1, fajr, maghrib,
            selected.copy(mondayThursday = false)).isEmpty())
        assertTrue(FastingRules.events(2, 4, 2, fajr, maghrib, selected).isEmpty())
        assertTrue(FastingRules.events(9, 15, 4, fajr, maghrib,
            selected.copy(ramadan = false)).isEmpty())
        assertTrue(FastingRules.events(10, 1, 1, fajr, maghrib, selected).isEmpty())
        for (day in 10..13)
            assertTrue(FastingRules.events(12, day, 4, fajr, maghrib, selected).isEmpty())
        assertTrue(FastingRules.events(2, 4, 1, fajr, maghrib,
            selected.copy(suhoorMinutes = 0, iftar = false)).isEmpty())
        assertEquals(listOf(FastingRules.IFTAR), FastingRules.events(2, 4, 1, fajr, maghrib,
            selected.copy(suhoorMinutes = 0)).map { it.kind })
    }
}

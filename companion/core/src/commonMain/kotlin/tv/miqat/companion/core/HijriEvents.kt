package tv.miqat.companion.core

/** Recurring dates come from the canonical catalog; the shell supplies its corrected Hijri day. */
data class HijriEvent(val id: String, val month: Int, val day: Int) {
    init {
        require(id.isNotBlank() && month in 1..12 && day in 1..30)
    }
}

object HijriEvents {
    const val UPCOMING_DAYS = 3

    fun find(events: List<HijriEvent>, month: Int, day: Int): String? {
        require(month in 1..12 && day in 1..30)
        return events.firstOrNull { it.month == month && it.day == day }?.id
    }

    /** The shell supplies chronological corrected [month, day] pairs, beginning with today. */
    fun upcoming(events: List<HijriEvent>, dates: Array<IntArray>): Int {
        require(dates.size <= UPCOMING_DAYS + 1)
        dates.forEach { require(it.size == 2 && it[0] in 1..12 && it[1] in 1..30) }
        return dates.indexOfFirst { find(events, it[0], it[1]) != null }
    }
}

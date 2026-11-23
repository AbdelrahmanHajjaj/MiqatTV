package tv.miqat.companion.core

import tv.miqat.core.ReminderEvent

data class FastingPreferences(
    val ramadan: Boolean,
    val ashura: Boolean,
    val arafah: Boolean,
    val whiteDays: Boolean,
    val mondayThursday: Boolean,
    val suhoorMinutes: Int,
    val iftar: Boolean,
) {
    init {
        require(suhoorMinutes in 0..120)
    }

    val enabled: Boolean
        get() = (suhoorMinutes > 0 || iftar) && (ramadan || ashura || arafah || whiteDays || mondayThursday)
}

object FastingRules {
    const val SUHOOR = "suhoor"
    const val IFTAR = "iftar"

    /** Weekday uses Sunday=0. Hijri fields belong to the civil day of the fast. */
    fun events(
        month: Int, day: Int, weekday: Int, fajr: Double, maghrib: Double,
        preferences: FastingPreferences,
    ): List<ReminderEvent> {
        require(month in 1..12 && day in 1..30 && weekday in 0..6)
        require(fajr.isFinite() && maghrib.isFinite())
        if (!preferences.enabled || fajr >= maghrib) return emptyList()
        if (month == 10 && day == 1 || month == 12 && day in 10..13) return emptyList()
        val selected = when (month) {
            9 -> preferences.ramadan
            else -> preferences.ashura && month == 1 && day == 10 ||
                preferences.arafah && month == 12 && day == 9 ||
                preferences.whiteDays && day in 13..15 ||
                preferences.mondayThursday && (weekday == 1 || weekday == 4)
        }
        if (!selected) return emptyList()
        return buildList {
            if (preferences.suhoorMinutes > 0) {
                add(ReminderEvent("phone:suhoor:0:${fajr.toLong()}", SUHOOR, 0,
                    fajr - preferences.suhoorMinutes * 60_000.0, fajr))
            }
            if (preferences.iftar) {
                add(ReminderEvent("phone:iftar:4:${maghrib.toLong()}", IFTAR, 4, maghrib, maghrib))
            }
        }
    }
}

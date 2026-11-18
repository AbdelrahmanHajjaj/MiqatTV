package tv.miqat.companion.core

import tv.miqat.core.PrayerRules
import tv.miqat.core.ReminderEvent
import tv.miqat.core.PrayerSelection

/** Phone policy only; shells supply astronomy, civil days, clocks and durable claims. */
object PhonePrayerRules {
    const val LATE_WINDOW_MS = 120000.0

    fun validate(latitude: Double, longitude: Double, modes: IntArray, before: IntArray,
        after: IntArray, volume: Int, durationSeconds: Int) {
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
        require(modes.size == 6 && before.size == 6 && after.size == 6)
        require(modes.all { it in 0..2 } && modes[1] == 0 && before[1] == 0 && after[1] == 0)
        require(before.all { it in 0..60 } && after.all { it in 0..60 })
        require(volume in 0..100 && durationSeconds in 0..600 && durationSeconds % 30 == 0)
    }

    fun nextEvent(days: Array<DoubleArray>, now: Double, modes: IntArray, before: IntArray,
        after: IntArray, handled: Array<String>): ReminderEvent? {
        validate(0.0, 0.0, modes, before, after, 100, 0)
        require(days.size <= 3 && now.isFinite())
        var next: ReminderEvent? = null
        fun add(kind: String, prayer: Int, time: Double, prayerTime: Double) {
            if (time <= now - LATE_WINDOW_MS) return
            val id = "phone:$kind:$prayer:${prayerTime.toLong()}"
            if (id in handled || next?.let { time >= it.time } == true) return
            next = ReminderEvent(id, kind, prayer, time, prayerTime)
        }
        for (times in days) {
            require(times.size == 6 && times.all { it.isFinite() && it % 1.0 == 0.0 && it in -9007199254740991.0..9007199254740991.0 })
            for (prayer in times.indices) {
                if (!PrayerRules.isPrayer(prayer.toDouble()) || modes[prayer] == 0) continue
                val time = times[prayer]
                if (before[prayer] > 0) add("before", prayer, time - before[prayer] * 60000.0, time)
                add("call", prayer, time, time)
                if (after[prayer] > 0) add("after", prayer, time + after[prayer] * 60000.0, time)
            }
        }
        return next
    }

    fun nextPrayer(today: DoubleArray?, tomorrow: DoubleArray?, now: Double): PrayerSelection? {
        require(now.isFinite())
        val times = today ?: tomorrow ?: return null
        val next = PrayerRules.select(times, tomorrow?.get(0) ?: now, now)
        return next.takeIf { it.nextTime > now }
    }

    fun due(time: Double, now: Double): Boolean {
        require(time.isFinite() && now.isFinite())
        return now >= time && now - time < LATE_WINDOW_MS
    }
}

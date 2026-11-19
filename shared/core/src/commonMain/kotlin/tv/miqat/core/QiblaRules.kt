@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package tv.miqat.core

import kotlin.js.JsExport
import kotlin.math.*

/** Great-circle initial bearing, clockwise from true north. Sensors belong to shells. */
@JsExport
object QiblaRules {
    fun bearing(latitude: Double, longitude: Double): Double? {
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
        val radians = PI / 180.0
        val origin = latitude * radians
        val target = 21.4225 * radians
        val delta = (39.8262 - longitude) * radians
        val y = sin(delta) * cos(target)
        val x = cos(origin) * sin(target) - sin(origin) * cos(target) * cos(delta)
        // Bearing is undefined at the destination and its antipode.
        if (hypot(x, y) < 0.00001 || abs(latitude) == 90.0) return null
        return (atan2(y, x) / radians + 360.0) % 360.0
    }
}

@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package tv.miqat.core

import kotlin.js.JsExport
import kotlin.math.*

/** Coordinate/quality checks and city distance, without an OS location provider. */
@JsExport
object LocationRules {
    fun usableLocation(latitude: Double, longitude: Double, accuracyMetres: Double, ageMs: Double): Boolean =
        latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0 &&
        accuracyMetres.isFinite() && accuracyMetres in 0.0..10000.0 && ageMs.isFinite() && ageMs in 0.0..120000.0

    fun distanceKm(latitude: Double, longitude: Double, otherLatitude: Double, otherLongitude: Double): Double {
        require(usableLocation(latitude,longitude,0.0,0.0) && usableLocation(otherLatitude,otherLongitude,0.0,0.0))
        val radians = PI / 180.0
        val a = sin((otherLatitude-latitude)*radians/2).pow(2) +
            cos(latitude*radians)*cos(otherLatitude*radians)*sin((otherLongitude-longitude)*radians/2).pow(2)
        return 12742.0 * asin(sqrt(a.coerceIn(0.0,1.0)))
    }

}

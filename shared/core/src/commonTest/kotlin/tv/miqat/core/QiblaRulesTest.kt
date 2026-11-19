package tv.miqat.core

import kotlin.test.*

class QiblaRulesTest {
    @Test fun knownDirectionsAndUndefinedOrigins() {
        assertEquals(136.1, QiblaRules.bearing(30.0444,31.2357)!!,0.2)
        assertEquals(119.0, QiblaRules.bearing(51.5074,-0.1278)!!,0.2)
        assertEquals(58.5, QiblaRules.bearing(40.7128,-74.0060)!!,0.2)
        assertNull(QiblaRules.bearing(21.4225,39.8262))
        assertNull(QiblaRules.bearing(-21.4225,-140.1738))
        assertNull(QiblaRules.bearing(90.0,0.0))
        assertFailsWith<IllegalArgumentException>{QiblaRules.bearing(Double.NaN,0.0)}
        assertFailsWith<IllegalArgumentException>{QiblaRules.bearing(0.0,181.0)}
    }
}

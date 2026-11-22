package tv.miqat.core

import kotlin.test.*

class LocationRulesTest {
    @Test fun locationQualityAndCityDistancesAreBounded() {
        assertTrue(LocationRules.usableLocation(30.0,31.0,3000.0,120000.0))
        assertFalse(LocationRules.usableLocation(30.0,31.0,10001.0,0.0))
        assertFalse(LocationRules.usableLocation(30.0,31.0,20.0,120001.0))
        assertFalse(LocationRules.usableLocation(30.0,31.0,20.0,-1.0))
        assertFalse(LocationRules.usableLocation(Double.NaN,31.0,20.0,0.0))
        assertEquals(0.0,LocationRules.distanceKm(30.0,31.0,30.0,31.0),0.000001)
        assertTrue(LocationRules.distanceKm(0.0,179.9,0.0,-179.9) in 22.0..23.0)
        assertEquals(20015.09,LocationRules.distanceKm(90.0,0.0,-90.0,0.0),0.1)
    }

}

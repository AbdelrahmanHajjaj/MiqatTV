package tv.miqat.core

import kotlin.test.*

class DisplayTextTest {
    @Test fun mixedArabicAndWesternClocksKeepTheirTextAndSeparators() {
        val mixed = "الفجر 05:٣۰ · ۱۲ AM"
        assertEquals("الفجر ٠٥:٣٠ · ١٢ AM", DisplayText.digits(mixed, true))
        assertEquals("الفجر 05:30 · 12 AM", DisplayText.digits(mixed, false))
        assertEquals("", DisplayText.digits("", true))
    }
}

package tv.miqat.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordingNameTest {
    @Test fun trimsEdgesAndRejectsBlankLongOrMultilineNames() {
        assertEquals("أذان الفجر", RecordingName.validated("  أذان الفجر\n"))
        assertEquals("🎧".repeat(40), RecordingName.validated("🎧".repeat(40)))
        assertNull(RecordingName.validated("🎧".repeat(41)))
        assertNull(RecordingName.validated("  "))
        assertNull(RecordingName.validated("Line\nTwo"))
        assertNull(RecordingName.validated("Voice\u0085Name"))
    }
}

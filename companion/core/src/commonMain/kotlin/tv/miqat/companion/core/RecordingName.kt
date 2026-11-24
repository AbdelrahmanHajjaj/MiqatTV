package tv.miqat.companion.core

object RecordingName {
    // Match the Android library's trim and UTF-16 length rules.
    fun validated(value: String?): String? {
        val name = value?.trim { it <= ' ' } ?: return null
        return name.takeIf { it.length in 1..80 && it.none { c -> c < ' ' || c in '\u007f'..'\u009f' } }
    }
}

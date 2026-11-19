@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package tv.miqat.core

import kotlin.js.JsExport

/** UI digit conversion shared by native screens, previews and widgets. */
@JsExport
object DisplayText {
    fun digits(value: String, eastern: Boolean): String = buildString(value.length) {
        for (character in value) {
            val digit = when (character) {
                in '0'..'9' -> character - '0'
                in '٠'..'٩' -> character - '٠'
                in '۰'..'۹' -> character - '۰'
                else -> -1
            }
            append(if (digit < 0) character else (if (eastern) '٠' else '0') + digit)
        }
    }
}

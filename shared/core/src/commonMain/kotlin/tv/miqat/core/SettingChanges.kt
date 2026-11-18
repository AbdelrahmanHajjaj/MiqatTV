@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package tv.miqat.core

import kotlin.js.JsExport

/** Compare semantic settings against an editor baseline without touching storage. */
@JsExport
object SettingChanges {
    fun conflicts(
        ids: Array<String>, groups: Array<String>, current: Array<String>,
        expected: Array<String?>, changes: Array<String>,
    ): Array<String> {
        require(groups.size == ids.size && current.size == ids.size && expected.size == ids.size)
        require(ids.distinct().size == ids.size && changes.distinct().size == changes.size)
        require(changes.all { it in ids }) { "Unknown setting" }
        val changedGroups = mutableSetOf<String>()
        for (i in ids.indices) if (ids[i] in changes && groups[i].isNotEmpty()) changedGroups.add(groups[i])
        val result = mutableListOf<String>()
        for (i in ids.indices) {
            if (ids[i] !in changes && (groups[i].isEmpty() || groups[i] !in changedGroups)) continue
            require(expected[i] != null) { "Missing edit baseline: ${ids[i]}" }
            if (current[i] != expected[i]) result.add(ids[i])
        }
        return result.toTypedArray()
    }
}

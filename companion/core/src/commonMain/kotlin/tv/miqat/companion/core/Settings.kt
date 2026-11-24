package tv.miqat.companion.core

import tv.miqat.core.SettingChanges

/** Semantic values use catalog IDs; platform preference names never cross this boundary. */
class SettingSpec(
    val id: String,
    val kind: String,
    val group: String,
    val options: List<String>,
    val minimum: Int,
    val maximum: Int,
    val step: Int,
) {
    fun accepts(value: String): Boolean = when (kind) {
        "toggle" -> value == "true" || value == "false"
        "choice" -> value in options
        "number" -> value.toIntOrNull()?.let { it in minimum..maximum &&
            (id != "display.size" && id != "appearance.mihrab-opacity" || (it - minimum) % step == 0) } ?: false
        "list" -> {
            val items = if (value.isEmpty()) emptyList() else value.split(',')
            items.size <= maximum && items.distinct().size == items.size && items.all { it in options }
        }
        "text" -> value.length <= maximum
        else -> false
    }

    fun validate(value: String) {
        require(accepts(value)) { "Invalid value: $id" }
    }

    fun adjust(value: String, direction: Int): String {
        require(kind == "number" && direction in -1..1)
        validate(value)
        return (value.toInt() + direction * step).coerceIn(minimum, maximum).toString()
    }
}

class SettingsSnapshot(values: Map<String, String>) {
    val values: Map<String, String> = values.toMap()
}

class SaveResult(val status: String, val snapshot: SettingsSnapshot, val conflicts: List<String>)

/** The eventual authenticated LAN adapter implements this same small interface. */
interface TvSettings {
    val specs: List<SettingSpec>
    fun connect(): Boolean
    fun read(): SettingsSnapshot
    fun apply(expected: Map<String, String>, changes: Map<String, String>): SaveResult
}

/** Deterministic demo endpoint. Its online/failure flags model outcomes, not real pairing. */
class DemoTv(
    override val specs: List<SettingSpec>,
    initial: Map<String, String>,
    private val cityDefaults: Map<String, Map<String, String>>,
) : TvSettings {
    private val controls = specs.associateBy { it.id }
    private val saved = initial.toMutableMap()
    var online = true
    var failNextSave = false
    var readCount = 0
        private set
    var writeCount = 0
        private set

    init {
        require(controls.size == specs.size && initial.keys == controls.keys)
        specs.forEach { it.validate(initial.getValue(it.id)) }
        cityDefaults.forEach { (_, changes) -> changes.forEach { (id, value) -> controls[id]?.validate(value) } }
    }

    override fun connect(): Boolean = online

    override fun read(): SettingsSnapshot {
        check(online) { "TV unavailable" }
        readCount++
        return SettingsSnapshot(saved)
    }

    override fun apply(expected: Map<String, String>, changes: Map<String, String>): SaveResult {
        check(online) { "TV unavailable" }
        require(changes.isNotEmpty())
        changes.forEach { (id, value) -> requireNotNull(controls[id]) { "Unsupported setting: $id" }.validate(value) }
        val next = changes.toMutableMap()
        changes["prayer.location"]?.let { city ->
            cityDefaults[city]?.forEach { (id, value) -> if (id in controls && id !in next) next[id] = value }
        }
        val conflicts = SettingChanges.conflicts(specs.map { it.id }.toTypedArray(),
            specs.map { it.group }.toTypedArray(), specs.map { saved.getValue(it.id) }.toTypedArray(),
            specs.map { expected[it.id] }.toTypedArray(), next.keys.toTypedArray()).toList()
        if (conflicts.isNotEmpty()) return SaveResult("conflict", SettingsSnapshot(saved), conflicts)
        if (failNextSave) {
            failNextSave = false
            return SaveResult("failed", SettingsSnapshot(saved), emptyList())
        }
        next.forEach { (id, value) -> controls.getValue(id).validate(value) }
        saved.putAll(next)
        writeCount++
        return SaveResult("saved", SettingsSnapshot(saved), emptyList())
    }
}

/** Drafts contain explicit user changes only. Connecting and reading cannot produce a write. */
class SettingsDraft(val base: SettingsSnapshot) {
    private val edits = mutableMapOf<String, String>()
    val changes: Map<String, String> get() = edits.toMap()
    val values: Map<String, String> get() = base.values + edits

    fun set(spec: SettingSpec, value: String) {
        require(spec.id in base.values)
        spec.validate(value)
        if (base.values[spec.id] == value) edits.remove(spec.id) else edits[spec.id] = value
    }

    fun cancel() { edits.clear() }
}

object CompanionContract {
    val appearanceLayoutControls = listOf("appearance.countdown", "appearance.position", "appearance.compact-position",
        "appearance.prayers", "appearance.favourites")
    val screens = listOf("prayers", "phone-settings", "tvs", "pair", "overview", "settings", "editor", "appearance", "updates", "preferences", "lg-setup", "tv-setup", "onboarding", "qibla", "widgets")
    val actions = listOf("connect", "open-settings", "save-edit", "apply-appearance", "cancel-appearance", "scan-pairing-code",
        "preview-audio", "stop-audio", "choose-language", "simulate-pairing", "simulate-update")
    val connectionStates = listOf("connected", "disconnected")
    val saveStates = listOf("saved", "failed", "conflict", "uncertain", "busy")
}

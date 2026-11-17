package tv.miqat.companion.core

import kotlin.test.*

class SettingsTest {
    private fun spec(id: String, group: String = "") = SettingSpec(id, "choice", group, listOf("a", "b"), 0, 0, 1)
    private fun tv() = DemoTv(listOf(spec("one"), spec("two"), spec("theme", "appearance"), spec("layout", "appearance")),
        mapOf("one" to "a", "two" to "a", "theme" to "a", "layout" to "a"), emptyMap())

    @Test fun reconnectAndReadNeverWrite() {
        val tv = tv()
        repeat(3) { assertTrue(tv.connect()); tv.read() }
        assertEquals(0, tv.writeCount)
    }

    @Test fun unrelatedChangesSurviveAndSameSettingConflicts() {
        val tv = tv(); val base = tv.read()
        tv.apply(base.values, mapOf("one" to "b"))
        assertEquals("saved", tv.apply(base.values, mapOf("two" to "b")).status)
        val conflict = tv.apply(base.values, mapOf("one" to "a"))
        assertEquals("conflict", conflict.status)
        assertEquals("b", conflict.snapshot.values["one"])
        assertEquals("b", conflict.snapshot.values["two"])
    }

    @Test fun appearanceIsLocalUntilApplyAndGroupConflictIsAtomic() {
        val tv = tv(); val draft = SettingsDraft(tv.read())
        draft.set(tv.specs[2], "b")
        assertEquals("a", tv.read().values["theme"])
        draft.cancel(); assertTrue(draft.changes.isEmpty())
        draft.set(tv.specs[2], "b")
        tv.apply(draft.base.values, mapOf("layout" to "b"))
        assertEquals("conflict", tv.apply(draft.base.values, draft.changes).status)
        assertEquals("a", tv.read().values["theme"])
    }

    @Test fun failedAndDisconnectedSavesPreserveStateAndDraft() {
        val tv = tv(); val draft = SettingsDraft(tv.read()); draft.set(tv.specs[0], "b")
        tv.failNextSave = true
        assertEquals("failed", tv.apply(draft.base.values, draft.changes).status)
        assertEquals("a", tv.read().values["one"])
        assertEquals("b", draft.changes["one"])
        tv.online = false
        assertFalse(tv.connect())
        assertFailsWith<IllegalStateException> { tv.apply(draft.base.values, draft.changes) }
        tv.online = true
        assertEquals("saved", tv.apply(draft.base.values, draft.changes).status)
    }

    @Test fun malformedOrUnsupportedChangesNeverPartiallyWrite() {
        val tv = tv(); val base = tv.read()
        assertFailsWith<IllegalArgumentException> { tv.apply(base.values, mapOf("one" to "b", "two" to "bad")) }
        assertFailsWith<IllegalArgumentException> { tv.apply(base.values, mapOf("unknown" to "a")) }
        assertFailsWith<IllegalArgumentException> { tv.apply(emptyMap(), mapOf("one" to "b")) }
        assertEquals(base.values, tv.read().values)
    }

    @Test fun cityDefaultsAreConfirmedAndProtectedFromStaleOverwrite() {
        val city = SettingSpec("prayer.location", "choice", "", listOf("cairo", "dubai"), 0, 0, 1)
        val method = SettingSpec("prayer.method", "choice", "", listOf("egyptian", "dubai"), 0, 0, 1)
        val tv = DemoTv(listOf(city, method), mapOf(city.id to "cairo", method.id to "egyptian"),
            mapOf("dubai" to mapOf(method.id to "dubai")))
        val base = tv.read()
        assertEquals("dubai", tv.apply(base.values, mapOf(city.id to "dubai")).snapshot.values[method.id])
        assertEquals("conflict", tv.apply(base.values, mapOf(city.id to "cairo")).status)
    }

    @Test fun numberAndOrderedListBoundaries() {
        val size = SettingSpec("display.size", "number", "", emptyList(), 80, 150, 10)
        assertEquals("150", size.adjust("150", 1))
        assertFailsWith<IllegalArgumentException> { size.validate("95") }
        assertFailsWith<IllegalArgumentException> { size.validate("999999999999") }
        val list = SettingSpec("apps", "list", "", listOf("a", "b"), 0, 2, 1)
        list.validate(""); list.validate("b,a")
        assertFailsWith<IllegalArgumentException> { list.validate("a,a") }
        assertFailsWith<IllegalArgumentException> { list.validate("c") }
    }
}

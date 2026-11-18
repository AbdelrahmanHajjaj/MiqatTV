package tv.miqat.core

import kotlin.test.*

class SettingChangesTest {
    private val ids = arrayOf("city", "method", "theme", "layout")
    private val groups = arrayOf("", "", "appearance", "appearance")
    private val base = arrayOf<String?>("a", "a", "a", "a")

    @Test fun unrelatedChangesDoNotConflict() {
        assertContentEquals(emptyArray(), SettingChanges.conflicts(ids,groups,arrayOf("b","a","a","a"),base,arrayOf("method")))
    }
    @Test fun changedSettingAndWholeAppearanceGroupAreProtected() {
        val current = arrayOf("b","a","b","a")
        assertContentEquals(arrayOf("city"),SettingChanges.conflicts(ids,groups,current,base,arrayOf("city")))
        assertContentEquals(arrayOf("theme"),SettingChanges.conflicts(ids,groups,current,base,arrayOf("layout")))
    }
    @Test fun dependentFieldsCanBeProtectedTogether() {
        assertContentEquals(arrayOf("method"),SettingChanges.conflicts(ids,groups,arrayOf("a","b","a","a"),base,arrayOf("city","method")))
    }
    @Test fun malformedVectorsAndMissingProtectedBaselinesAreRejected() {
        assertFailsWith<IllegalArgumentException>{SettingChanges.conflicts(ids,emptyArray(),arrayOf("a","a","a","a"),base,arrayOf("city"))}
        assertFailsWith<IllegalArgumentException>{SettingChanges.conflicts(ids,groups,arrayOf("a","a","a","a"),base,arrayOf("unknown"))}
        assertFailsWith<IllegalArgumentException>{SettingChanges.conflicts(ids,groups,arrayOf("a","a","a","a"),arrayOf(null,"a","a","a"),arrayOf("city"))}
        assertFailsWith<IllegalArgumentException>{SettingChanges.conflicts(ids,groups,arrayOf("a","a","a","a"),base,arrayOf("city","city"))}
    }
    @Test fun noChangesNeedNoBaselineAndInputsAreNotMutated() {
        val current = arrayOf("a","a","a","a")
        assertTrue(SettingChanges.conflicts(ids,groups,current,arrayOfNulls(4),emptyArray()).isEmpty())
        assertContentEquals(arrayOf("a","a","a","a"),current)
    }
}

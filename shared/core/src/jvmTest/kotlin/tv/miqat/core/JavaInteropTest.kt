package tv.miqat.core

import kotlin.test.Test

class JavaInteropTest {
    @Test
    fun existingJavaCallersCanUseTheCore() = JavaInterop.check()
}

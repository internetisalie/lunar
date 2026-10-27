package net.internetisalie.lunar.coverage

import com.intellij.coverage.CoverageRunner
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** MAINT-44 TC-01: the runner lookup returns Lunar's registered `coverageRunner` extension instance. */
class LuaCoverageRunnerTest : BasePlatformTestCase() {
    fun testGetInstanceReturnsRegisteredExtension() {
        val registered = CoverageRunner.EP_NAME.extensionList.single { it is LuaCoverageRunner }

        val runner = LuaCoverageRunner.getInstance()

        assertSame(registered, runner)
        assertEquals("LuaCov", runner?.id)
    }
}

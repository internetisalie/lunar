package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-432: `resolveType` had no dumb-mode guard, so every call made while the IDE was indexing threw
 * `IndexNotReadyException` **through a catch block that logs it with `Logger.error`** — which in a
 * running IDE is an internal-error report, the red notification and the exception dialog. The user
 * was told the plugin had crashed because the indexes were not built yet.
 *
 * `resolveGlobal` has been guarded since BUG-395 (`LuaTypeManagerImpl:129`); `resolveType`, the other
 * door into the same machinery, was not. Nothing in the suite exercised dumb mode, which is how it
 * survived — these are the first tests here that do.
 *
 * **Why these tests can fail**: the platform's `TestLogger` converts `Logger.error` into a
 * `TestLoggerAssertionError`, so an unguarded `resolveType` fails the test *before* reaching any
 * assertion. Verified red on the pre-fix code with exactly
 * `TestLoggerAssertionError: Error resolving type …`.
 */
@RunWith(JUnit4::class)
class LuaTypeResolutionDumbModeTest : BasePlatformTestCase() {
    private fun seedClass() {
        myFixture.addFileToProject(
            "lib.lua",
            """
            ---@meta

            ---@class Widget
            local Widget = {}

            ---@return boolean
            function Widget:show() end
            """.trimIndent(),
        )
    }

    /** The reported defect: a call while dumb must degrade, not report a crash. */
    @Test
    fun testResolveTypeWhileDumbReturnsNullWithoutReportingAnError() {
        seedClass()
        val manager = LuaTypeManager.getInstance(project)
        val context = myFixture.configureByText("consumer.lua", "local x = 1\n")

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            runReadAction {
                assertNull(
                    "resolveType must degrade to null while indexing, as resolveGlobal already does",
                    manager.resolveType("Widget", context),
                )
            }
        }
    }

    /**
     * The half that matters more (bug-report scope item 2): a guard alone leaves the `Logger.error`
     * reachable by any index query that races the dumb check, because the catch block is shared.
     * `IndexNotReadyException` is control flow, exactly like `ProcessCanceledException`, and must be
     * rethrown without logging.
     *
     * `resolveModule` is deliberately included: the bug report claimed it "has the same catch shape".
     * It does not — it is `try/finally` with no catch — so this pins that it stays that way rather
     * than acquiring one.
     */
    @Test
    fun testResolveModuleWhileDumbDoesNotReportAnError() {
        seedClass()
        val manager = LuaTypeManager.getInstance(project)
        val context = myFixture.configureByText("consumer.lua", "local x = 1\n")

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            runReadAction { manager.resolveModule("lib", context) }
        }
    }

    /** Smart mode is untouched — the guard must not cost a resolution that used to work. */
    @Test
    fun testResolveTypeStillWorksWhenSmart() {
        seedClass()
        val manager = LuaTypeManager.getInstance(project)
        val context = myFixture.configureByText("consumer.lua", "local x = 1\n")

        runReadAction {
            assertNotNull("smart-mode resolution must be unchanged", manager.resolveType("Widget", context))
        }
    }
}

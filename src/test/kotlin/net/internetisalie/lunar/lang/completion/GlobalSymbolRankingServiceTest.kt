package net.internetisalie.lunar.lang.completion

import net.internetisalie.lunar.BaseDocumentTest
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.lang.psi.LuaFile
import org.junit.jupiter.api.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Tests for GlobalSymbolRankingService
 * Verifies proximity-based ranking, deduplication, and visibility filtering
 */
class GlobalSymbolRankingServiceTest : BaseDocumentTest() {

    @Test
    fun testGetProjectGlobalSymbols() {
        // Create test project with global functions
        myFixture.configureByText("module_a.lua", """
            function globalFunc() end
            function _privateFunc() end
        """.trimIndent())

        myFixture.configureByText("module_b.lua", """
            function anotherGlobal() end
        """.trimIndent())

        myFixture.configureByText("main.lua", """
            local x = 10
        """.trimIndent())

        val mainFile = myFixture.file as LuaFile
        val service = GlobalSymbolRankingService.getInstance(myFixture.project)

        // Test getting globals from main.lua (PSI reads require a read action)
        val globals = runReadAction {
            service.getProjectGlobalSymbols(
                mainFile,
                localSymbolNames = setOf("x"),
                importedSymbolNames = emptySet()
            )
        }

        // Should include globalFunc and anotherGlobal, but NOT _privateFunc (suppressed by default)
        val names = globals.map { it.name }
        assert(names.contains("globalFunc")) { "Should include globalFunc" }
        assert(names.contains("anotherGlobal")) { "Should include anotherGlobal" }
        assert(!names.contains("_privateFunc")) { "Should NOT include _privateFunc (suppressed)" }
        assert(!names.contains("x")) { "Should NOT include local symbol x" }
    }

    @Test
    fun testDeduplicationWithImportedSymbols() {
        myFixture.configureByText("utils.lua", """
            function helper() end
        """.trimIndent())

        myFixture.configureByText("main.lua", """
            local helper = require("utils")
        """.trimIndent())

        val mainFile = myFixture.file as LuaFile
        val service = GlobalSymbolRankingService.getInstance(myFixture.project)

        // Test deduplication: helper is in importedSymbolNames (PSI reads require a read action)
        val globals = runReadAction {
            service.getProjectGlobalSymbols(
                mainFile,
                localSymbolNames = emptySet(),
                importedSymbolNames = setOf("helper")
            )
        }

        val names = globals.map { it.name }
        assert(!names.contains("helper")) { "Should NOT include imported symbol helper" }
    }

    // MAINT-28 TC-25p (§2.5.5): the cached func-key snapshot is the identical List instance across
    // reads with no intervening PSI edit (no getAllKeys recomputation), and a fresh instance after
    // a PSI modification bumps MODIFICATION_COUNT.
    @Test
    fun `TC-25p func key snapshot is memoized until a PSI edit`() {
        myFixture.configureByText(
            "main.lua",
            """
            function globalOne() end
            local x = 10
            """.trimIndent(),
        )
        val service = GlobalSymbolRankingService.getInstance(myFixture.project)

        val first = runReadAction { service.funcKeySnapshotForTest() }
        val second = runReadAction { service.funcKeySnapshotForTest() }
        assertSame(first, second, "Key snapshot must be the identical List across reads with no PSI edit")

        // A new global bumps MODIFICATION_COUNT, invalidating the CachedValue.
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            myFixture.addFileToProject("more.lua", "function globalTwo() end\n")
        }

        val afterEdit = runReadAction { service.funcKeySnapshotForTest() }
        assertNotSame(second, afterEdit, "A PSI edit must invalidate the cache and yield a fresh List")
    }
}

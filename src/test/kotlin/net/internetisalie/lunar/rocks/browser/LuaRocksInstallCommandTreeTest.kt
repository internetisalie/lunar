package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.rocks.LuaRocksTreeLocator

/**
 * TC-ROCKS-16-04: [LuaRocksInstallCommand.resolveTargetTree] delegates to
 * [LuaRocksTreeLocator.treeRoot] (design §3.1), so the install target is always the canonical
 * project tree the rest of the plugin reads (or `null`, when the caller renders the no-tree hint).
 *
 * Asserts the delegation identity rather than an absolute `null` — the shared light-fixture temp
 * basePath can be contaminated with a `lua_modules` dir by a sibling test, so a bare `assertNull`
 * is order-dependent (isolated-tests-masks-full-suite). The null-when-absent behavior itself is a
 * property of `LuaRocksTreeLocator`, covered by its own tests.
 */
class LuaRocksInstallCommandTreeTest : BasePlatformTestCase() {

    fun `test resolveTargetTree delegates to LuaRocksTreeLocator`() {
        runReadAction {
            assertEquals(
                LuaRocksTreeLocator.treeRoot(project),
                LuaRocksInstallCommand.resolveTargetTree(project),
            )
        }
    }
}

package net.internetisalie.lunar.rocks.browser

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * TC-ROCKS-16-04: with no project rock tree (no `lua_modules` / `.luarocks`),
 * [LuaRocksInstallCommand.resolveTargetTree] returns `null` so the caller renders the no-tree hint
 * and disables Install (design §3.5).
 */
class LuaRocksInstallCommandTreeTest : BasePlatformTestCase() {

    fun `test resolveTargetTree is null when no rock tree exists`() {
        assertNull(LuaRocksInstallCommand.resolveTargetTree(project))
    }
}

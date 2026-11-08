package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

/**
 * TC-BUG-55: with no project rock tree, the browser offers to create one, and once it exists the
 * strip shows the new target tree.
 *
 * The light fixture shares one on-disk `basePath` across the suite, and sibling tests leave a
 * `lua_modules` there, so every test starts and ends with neither tree candidate present.
 */
class LuaRocksBrowserPanelInitTreeTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        removeTrees()
    }

    override fun tearDown() {
        try {
            removeTrees()
        } finally {
            super.tearDown()
        }
    }

    fun `test no tree offers the initialize link`() {
        withPanel(FakeInitializer(createsTree = false)) { panel ->
            assertEquals("No project rock tree", panel.treeStripText())
            assertTrue("the initialize link must be offered when no tree exists", panel.initializeLink.isVisible)
        }
    }

    fun `test the link creates the tree and the strip shows it`() {
        val initializer = FakeInitializer(createsTree = true)
        withPanel(initializer) { panel ->
            panel.initializeLink.doClick()

            assertEquals(1, initializer.calls)
            assertEquals("Target tree: ${base().resolve("lua_modules")}", panel.treeStripText())
            assertFalse("the link must hide once the tree exists", panel.initializeLink.isVisible)
        }
    }

    fun `test a failed initialize keeps the link offered`() {
        withPanel(FakeInitializer(createsTree = false)) { panel ->
            panel.initializeLink.doClick()

            assertEquals("No project rock tree", panel.treeStripText())
            assertTrue(panel.initializeLink.isVisible)
            assertTrue("the link must be usable again after a failure", panel.initializeLink.isEnabled)
        }
    }

    fun `test init runs as a bare luarocks init`() {
        assertEquals(listOf("init"), LuaRocksInstallCommand.buildInitArgs())
    }

    /** Stands in for `luarocks init`: creates `lua_modules` (or not) and reports the outcome. */
    private inner class FakeInitializer(
        private val createsTree: Boolean,
    ) : LuaRocksTreeInitializer {
        var calls = 0

        override fun initialize(onDone: (Boolean) -> Unit) {
            calls += 1
            if (createsTree) Files.createDirectories(base().resolve("lua_modules"))
            onDone(createsTree)
        }
    }

    private fun withPanel(
        initializer: LuaRocksTreeInitializer,
        assertions: (LuaRocksBrowserPanel) -> Unit,
    ) {
        val panel = LuaRocksBrowserPanel(project, initializer)
        try {
            assertions(panel)
        } finally {
            Disposer.dispose(panel)
        }
    }

    private fun base(): Path = Path.of(requireNotNull(project.basePath))

    private fun removeTrees() {
        listOf("lua_modules", ".luarocks").forEach { base().resolve(it).toFile().deleteRecursively() }
    }
}

package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * TC-BUG-56: opening the browser fills the Marketplace tab with the popular list, without the user
 * first typing into and clearing the search field; with no popular list it prompts for a search.
 */
class LuaRocksBrowserPanelPopularTest : BasePlatformTestCase() {
    fun `test opening the browser shows the popular list`() {
        val popular = listOf(PopularEntry("luafilesystem", "83,158"), PopularEntry("argparse", "62,626"))
        withPanel(SyncBrowserBackend(project, popular)) { panel ->
            assertEquals(listOf("luafilesystem", "argparse"), panel.marketplaceNames())
        }
    }

    fun `test with no popular list the marketplace prompts for a search`() {
        withPanel(SyncBrowserBackend(project)) { panel ->
            assertEmpty(panel.marketplaceNames())
            assertEquals("Search luarocks.org", panel.marketplaceEmptyText())
        }
    }

    private fun withPanel(
        backend: LuaRocksBrowserBackend,
        assertions: (LuaRocksBrowserPanel) -> Unit,
    ) {
        val panel = LuaRocksBrowserPanel(project, { it(false) }, backend)
        try {
            assertions(panel)
        } finally {
            Disposer.dispose(panel)
        }
    }
}

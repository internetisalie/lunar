package net.internetisalie.lunar.rocks.browser

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * TC-ROCKS-16-10: [PackageDetailPane.dependencyRows] maps `luarocks show` dependency strings to
 * clickable [DependencyRow]s whose [DependencyRow.packageName] is the leading token (design §4.2).
 *
 * Runs on the platform fixture so the pane (Swing + JBHtmlPane) constructs on the EDT.
 */
class PackageDetailPaneDependencyTest : BasePlatformTestCase() {

    fun `test dependencyRows expose the clickable package token`() {
        val meta = metadata(listOf("lua >= 5.1", "luassert >= 1.7"))
        val pane = PackageDetailPane(project, model())

        val rows = pane.dependencyRows(meta)

        assertEquals(listOf("lua >= 5.1", "luassert >= 1.7"), rows.map { it.raw })
        assertEquals(listOf("lua", "luassert"), rows.map { it.packageName })
    }

    fun `test dependencyRows is empty when no dependencies`() {
        val pane = PackageDetailPane(project, model())
        assertTrue(pane.dependencyRows(metadata(emptyList())).isEmpty())
    }

    private fun model() = LuaRocksBrowserModel(ProjectBackend(project), object : LuaRocksBrowserModel.Listener {
        override fun onState(state: BrowserState) = Unit
        override fun onRowChanged(index: Int) = Unit
    })

    private fun metadata(deps: List<String>) = LuaRockMetadata(
        name = "inspect",
        version = "3.1.3-0",
        summary = "A pretty printer",
        detailed = null,
        license = "MIT",
        homepage = "https://github.com/kikito/inspect.lua",
        issues = null,
        location = null,
        dependencies = deps,
        modules = emptyList(),
    )
}

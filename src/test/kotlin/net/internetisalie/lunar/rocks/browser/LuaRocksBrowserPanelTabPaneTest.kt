package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.UIUtil
import java.awt.Component

/**
 * TC-BUG-449: every tab of the browser owns a live [PackageDetailPane].
 *
 * A Swing component has exactly one parent, so installing one pane instance into two splitters
 * re-parents it out of the first. This asserts the property directly on the component tree — what a
 * screenshot cannot do cheaply, and what the whole suite missed between ROCKS-16-01 and BUG-449.
 *
 * **Assert containment, not `Splitter.secondComponent`.** That property is a stored field: the
 * splitter that *lost* the pane still returns it, so an assertion over it passes both before and
 * after the fix. Only the parent/child relation moves.
 *
 * **Each panel is disposed inside the test that built it.** `BasePlatformTestCase` reuses one light
 * project across the whole suite, and a live browser panel keeps project-scoped state warm;
 * deferring disposal to `testRootDisposable` left it alive long enough to perturb later tests
 * (measured: it reddened `LuaSourcePathModuleResolutionTest`).
 */
class LuaRocksBrowserPanelTabPaneTest : BasePlatformTestCase() {
    fun `test every tab actually contains its detail pane`() {
        withBrowserTabs { tabs ->
            assertEquals(2, tabs.tabCount)
            (0 until tabs.tabCount).forEach { index ->
                val title = tabs.getTitleAt(index)
                val splitter = tabs.getComponentAt(index) as? OnePixelSplitter
                val contained = splitter?.components?.filterIsInstance<PackageDetailPane>().orEmpty()
                assertEquals(
                    "tab '$title' does not contain a detail pane — another tab re-parented it away",
                    1,
                    contained.size,
                )
                assertSame(
                    "detail pane of tab '$title' is parented elsewhere",
                    splitter,
                    contained.first().parent,
                )
            }
        }
    }

    fun `test the two tabs do not share one detail pane instance`() {
        withBrowserTabs { tabs ->
            val panes = (0 until tabs.tabCount).map { detailPaneAt(tabs, it) }

            assertEquals("both tabs must hold a pane", 2, panes.count { it != null })
            assertNotSame(
                "the tabs share one PackageDetailPane; the second install re-parents it off the first",
                panes.first(),
                panes.last(),
            )
        }
    }

    /** Builds a browser panel, runs [assertions] over its tabs, and disposes it before returning. */
    private fun withBrowserTabs(assertions: (JBTabbedPane) -> Unit) {
        val panel = LuaRocksBrowserPanel(project)
        try {
            val tabs = UIUtil.findComponentOfType(panel, JBTabbedPane::class.java)
            assertions(requireNotNull(tabs) { "browser panel has no tabbed pane" })
        } finally {
            Disposer.dispose(panel)
        }
    }

    /** The pane a tab's splitter actually *contains* — not the field it merely still points at. */
    private fun detailPaneAt(
        tabs: JBTabbedPane,
        index: Int,
    ): Component? =
        (tabs.getComponentAt(index) as? OnePixelSplitter)
            ?.components
            ?.filterIsInstance<PackageDetailPane>()
            ?.firstOrNull()
}

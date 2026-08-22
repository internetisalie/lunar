package net.internetisalie.lunar.rocks.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.UIUtil
import net.internetisalie.lunar.rocks.deps.DependencyNode
import java.awt.Component

/**
 * TC-BUG-448-14: the dependency inspector's empty state is a platform [JBPanelWithEmptyText], not an
 * italic HTML string left-aligned at the top of a label.
 *
 * Platform empty text is centred, dimmed, non-italic and carries **no trailing period**, so the
 * copy is asserted against those conventions rather than pinned verbatim. That the panel is a
 * `JBPanelWithEmptyText` at all is the load-bearing half: the previous
 * `"<html><body><i>Select a dependency.</i></body></html>"` lived in the detail editor pane, so no
 * empty-state component existed to find.
 *
 * `CardLayout.show` flips child visibility without a peer, so which card is showing is assertable
 * headlessly — but **only a round trip proves the switching path exists.**
 * `CardLayout.addLayoutComponent` already leaves the first-added card visible and every later one
 * hidden, so asserting `empty.isVisible` on a freshly built panel observes construction order and
 * stays green with `show`'s null branch deleted entirely (verified: it did). The test therefore
 * drives the panel to the detail card first and back.
 */
class DependencyInspectorEmptyStateTest : BasePlatformTestCase() {
    fun `test the empty state is a platform empty-text panel`() {
        val inspector = DependencyInspectorPanel()

        val empty = UIUtil.findComponentOfType(inspector, JBPanelWithEmptyText::class.java)

        assertNotNull("the empty state must be a JBPanelWithEmptyText", empty)
        val text = requireNotNull(empty).emptyText.text
        assertTrue("empty text must say something; got '$text'", text.isNotBlank())
        assertFalse("empty text must not be HTML; got '$text'", text.contains("<"))
        assertFalse("platform empty text takes no trailing period; got '$text'", text.endsWith("."))
    }

    fun `test selecting then deselecting flips the visible card back to the empty state`() {
        val inspector = DependencyInspectorPanel()
        val empty = requireNotNull(UIUtil.findComponentOfType(inspector, JBPanelWithEmptyText::class.java))
        val detail = detailCardOf(inspector, empty)

        inspector.show(DependencyNode(packageName = "inspect", isTransitive = false))

        assertTrue("selecting a node must show the detail card", detail.isVisible)
        assertFalse("the empty card must hide while a node is selected", empty.isVisible)

        inspector.show(null)

        assertTrue("deselecting must bring the empty card back", empty.isVisible)
        assertFalse("the detail card must hide once nothing is selected", detail.isVisible)
    }

    /** The inspector's other card — the detail body, whichever component that happens to be. */
    private fun detailCardOf(
        inspector: DependencyInspectorPanel,
        empty: Component,
    ): Component {
        val others = inspector.components.filterNot { it === empty }
        assertEquals("the inspector must hold exactly one detail card beside the empty one", 1, others.size)
        return others.first()
    }
}

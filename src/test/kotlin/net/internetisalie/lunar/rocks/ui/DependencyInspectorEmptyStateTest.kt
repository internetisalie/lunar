package net.internetisalie.lunar.rocks.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.UIUtil

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
 * headlessly.
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

    fun `test the empty card is the one showing when nothing is selected`() {
        val inspector = DependencyInspectorPanel()
        inspector.show(null)

        val empty = requireNotNull(UIUtil.findComponentOfType(inspector, JBPanelWithEmptyText::class.java))

        assertTrue("the empty card must be the visible one", empty.isVisible)
        val others = inspector.components.filterNot { it === empty }
        assertEquals("the inspector must hold a second, hidden detail card", 1, others.size)
        assertFalse("the detail card must be hidden while nothing is selected", others.first().isVisible)
    }
}

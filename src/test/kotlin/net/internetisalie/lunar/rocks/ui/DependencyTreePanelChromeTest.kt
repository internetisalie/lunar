package net.internetisalie.lunar.rocks.ui

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JScrollBar

/**
 * TC-BUG-448-12 / TC-BUG-448-13: the LuaRocks Dependencies tool window carries a flat
 * [ActionToolbar] and a filter that announces itself, not four bordered boxes.
 *
 * The audit measured three icon-only `JButton`s plus a bare `JBTextField` with `columns = 16` and no
 * `emptyText`, which renders as a blank bordered box and reads as a fourth, empty button. Both are
 * structural properties of the component tree, which is what is asserted.
 *
 * **The L&F plants `JButton`s of its own** and they must be excluded or the assertion can never
 * pass: `BasicComboBoxUI` synthesizes a combo's arrow button and `BasicScrollBarUI` the two
 * scrollbar arrows. Only buttons the panel parents itself count.
 *
 * **The toolbar's own `getActions()` is empty here.** `ActionToolbarImpl` fills `myVisibleActions`
 * from an async update that never runs for a toolbar outside a window, so the actions are read off
 * `getActionGroup()` — the provider, not the last painted state.
 *
 * **What this cannot assert:** that the toolbar *paints* flat and borderless, or that the search
 * icon appears. Both are look-and-feel outcomes observable only in a rendered IDE; the unit gate
 * covers the component choice, and rendering stays a `verify-in-ide` matter.
 */
class DependencyTreePanelChromeTest : BasePlatformTestCase() {
    fun `test the panel parents no bordered push-buttons`() {
        val ours = ownButtonsIn(DependencyTreePanel(project))

        assertEquals(
            "tool-window actions must be an ActionToolbar, not JButtons",
            emptyList<String>(),
            ours.map { "JButton(text='${it.text}', tooltip='${it.toolTipText}')" },
        )
    }

    fun `test the three tree actions live on one action toolbar`() {
        val toolbars = descendantsOf(DependencyTreePanel(project)).filterIsInstance<ActionToolbar>()

        assertEquals("expected exactly one action toolbar", 1, toolbars.size)
        val toolbar = toolbars.first()
        assertEquals(
            listOf("Refresh", "Expand All", "Collapse All"),
            toolbar.actionGroup.getChildren(null).map { it.templatePresentation.text.orEmpty() },
        )
        assertNotNull(
            "targetComponent must be set or the actions disable unpredictably",
            toolbar.targetComponent,
        )
    }

    fun `test the filter field is a search field that says what it filters`() {
        val panel = DependencyTreePanel(project)

        val filter = UIUtil.findComponentOfType(panel, SearchTextField::class.java)

        assertNotNull("the filter must be a SearchTextField, not a bare text field", filter)
        val emptyText = requireNotNull(filter).textEditor.emptyText.text
        assertTrue(
            "a filter with blank emptyText renders as an empty bordered box; got '$emptyText'",
            emptyText.isNotBlank(),
        )
    }

    /** Buttons the panel parents itself — L&F-synthesized combo and scrollbar arrows excluded. */
    private fun ownButtonsIn(root: Container): List<JButton> =
        descendantsOf(root).filterIsInstance<JButton>().filterNot { button ->
            generateSequence(button.parent) { it.parent }
                .any { it is JComboBox<*> || it is JScrollBar }
        }

    /** Every component under [root], [root] excluded. */
    private fun descendantsOf(root: Container): List<Component> =
        root.components.flatMap { child ->
            listOf(child) + if (child is Container) descendantsOf(child) else emptyList()
        }
}

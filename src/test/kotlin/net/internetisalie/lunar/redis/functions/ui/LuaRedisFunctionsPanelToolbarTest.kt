package net.internetisalie.lunar.redis.functions.ui

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JScrollBar

/**
 * TC-BUG-448-05: the Redis Functions tool window drives its actions from a flat [ActionToolbar],
 * not from bordered Swing push-buttons.
 *
 * The audit measured three `JButton`s in this panel's header, which is what makes it read as a
 * foreign Swing app beside the native Problems window in the same screenshot. The property that
 * separates the two is structural — *which component classes are in the tree* — so that is what is
 * asserted, over the whole panel rather than over one field.
 *
 * **The L&F plants `JButton`s of its own** and they must be excluded or the assertion can never
 * pass: `BasicComboBoxUI` synthesizes the combo's arrow button and `BasicScrollBarUI` the two
 * scrollbar arrows. Only buttons we parent ourselves count.
 *
 * **The toolbar's own `getActions()` is empty here.** `ActionToolbarImpl` fills `myVisibleActions`
 * from an async update that never runs for a toolbar outside a window, so the actions are read off
 * `getActionGroup()` instead — the provider, not the last painted state.
 *
 * **What this cannot assert:** that the toolbar *paints* flat and borderless. That is a look-and-feel
 * outcome of `ActionButton`'s painter under the active theme, observable only in a rendered IDE. The
 * unit gate covers the component choice; rendering stays a `verify-in-ide` matter.
 */
class LuaRedisFunctionsPanelToolbarTest : BasePlatformTestCase() {
    fun `test the panel parents no bordered push-buttons`() {
        val ours = ownButtonsIn(LuaRedisFunctionsPanel(project))

        assertEquals(
            "tool-window actions must be an ActionToolbar, not JButtons",
            emptyList<String>(),
            ours.map { "JButton(text='${it.text}', tooltip='${it.toolTipText}')" },
        )
    }

    fun `test the three panel actions live on one action toolbar`() {
        val toolbars = descendantsOf(LuaRedisFunctionsPanel(project)).filterIsInstance<ActionToolbar>()

        assertEquals("expected exactly one action toolbar", 1, toolbars.size)
        val toolbar = toolbars.first()
        assertEquals(listOf("Refresh", "Deploy", "Delete"), toolbarActionNames(toolbar))
        assertNotNull(
            "targetComponent must be set or the actions disable unpredictably",
            toolbar.targetComponent,
        )
    }

    private fun toolbarActionNames(toolbar: ActionToolbar): List<String> =
        toolbar.actionGroup
            .getChildren(null)
            .map { it.templatePresentation.text.orEmpty() }

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

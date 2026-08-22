package net.internetisalie.lunar.definitions.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.UIUtil
import net.internetisalie.lunar.ui.RowGridAssertions
import javax.swing.JComponent

/**
 * BUG-448 #2: every catalog row is a label-less `row { cell(box); cell(status); cell(license);
 * cell(link) }`, which the Kotlin UI DSL lays out as `RowLayout.INDEPENDENT` — a sub-grid per row,
 * each sizing its four columns alone. Measured cost: 85px of column stagger over four rows, against
 * 0px on the native Plugins list. The fix is `.layout(RowLayout.PARENT_GRID)`; what a headless test
 * can see of it is that the cells are registered in one grid at one column.
 */
class LuaDefinitionLibrariesRowAlignmentTest : BasePlatformTestCase() {
    fun `test catalog rows share the page grid (BUG-448 #2)`() {
        val configurable = LuaDefinitionLibrariesConfigurable(project)
        try {
            val page = configurable.createComponent()
            assertColumnsShared(page, JBCheckBox::class.java, "library checkboxes")
            assertColumnsShared(page, HyperlinkLabel::class.java, "attribution links")
        } finally {
            configurable.disposeUIResources()
        }
    }

    private fun <T : JComponent> assertColumnsShared(
        page: JComponent,
        type: Class<T>,
        role: String,
    ) {
        val found = UIUtil.findComponentsOfType(page, type)
        assertTrue("Expected more than one row of $role to align", found.size > 1)
        assertTrue(
            "The $role must share one grid column — declare RowLayout.PARENT_GRID on the row",
            RowGridAssertions.shareOneColumn(found),
        )
    }
}

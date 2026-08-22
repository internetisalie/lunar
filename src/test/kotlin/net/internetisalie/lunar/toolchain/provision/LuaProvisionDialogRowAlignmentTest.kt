package net.internetisalie.lunar.toolchain.provision

import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import net.internetisalie.lunar.ui.RowGridAssertions
import javax.swing.JComponent

/**
 * BUG-448 #3: the optional-tool rows carry no label, so the Kotlin UI DSL defaulted them to
 * `RowLayout.INDEPENDENT` — a fresh sub-grid per row, each sizing its own columns, measured at 90px
 * of combo stagger. `.layout(RowLayout.PARENT_GRID)` registers those cells in the panel's root grid
 * instead. That grid identity is the structural cause the pixels follow from, and is the only half a
 * headless test can observe; the alignment itself is a `verify-in-ide` screenshot check.
 *
 * NOTE: the dialog is built, asserted and closed inside one EDT block, and the non-inline lambda
 * that requires lives in a private helper NOT named `test*` — see [LuaProvisionDialogRootDirTest]
 * and `LuaToolchainConfigurableTest` for why the JUnit3 scanner rejects `test…$lambda$N`.
 */
class LuaProvisionDialogRowAlignmentTest : ToolchainSettingsTestCase() {
    fun `test optional-tool rows share the panel grid (BUG-448 #3)`() {
        assertToolRowsShareOneGrid()
    }

    private fun assertToolRowsShareOneGrid() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val dialog = LuaProvisionDialog(project, initial = null)
            try {
                val rows = dialog.optionalToolRowsForTest()
                assertTrue("Expected more than one optional-tool row to align", rows.size > 1)
                assertTrue(
                    "Label-less checkbox cells must share the panel grid — declare RowLayout.PARENT_GRID",
                    RowGridAssertions.shareOneColumn(rows.map { it.first as JComponent }),
                )
                assertTrue(
                    "Version combos must share the panel grid — otherwise every row sizes its own column",
                    RowGridAssertions.shareOneColumn(rows.map { it.second as JComponent }),
                )
                assertSame(
                    "A row's checkbox and combo must live in one grid, not a per-row sub-grid",
                    RowGridAssertions.gridOf(rows.first().first),
                    RowGridAssertions.gridOf(rows.first().second),
                )
            } finally {
                dialog.close(0)
            }
        }
    }
}

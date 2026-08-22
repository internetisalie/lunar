package net.internetisalie.lunar.toolchain.ui

import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase

/**
 * BUG-448 #8: the inventory declared no column-width model, so `TableView` split the width evenly —
 * `Path` was elided to `/usr/loca…` and `Origin` to `Discover…` while `Kind` got the same width for
 * one short word. `ColumnInfo.getPreferredStringValue` is what `TableView.updateColumnSizes`
 * measures, so the assertion here is the ordering the content implies, not a copy of the constants.
 */
class LuaToolchainInventoryColumnWidthTest : ToolchainSettingsTestCase() {
    fun `test path column is wider than the short columns (BUG-448 #8)`() {
        val widths = columnWidths()
        assertEquals("Expected the six inventory columns", 6, widths.size)
        val (kind, name, path) = widths
        val version = widths[VERSION_COLUMN]
        assertTrue("Path must be the widest column, got path=$path kind=$kind", path > kind)
        assertTrue("Path must out-size Name, got path=$path name=$name", path > name)
        assertTrue("Version holds `5.4.7-1`, so it must be narrower than Path", version < path)
    }

    private fun columnWidths(): List<Int> {
        val collected = mutableListOf<Int>()
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val columnModel = LuaToolchainInventoryTable().tableForTest().columnModel
            repeat(columnModel.columnCount) { collected.add(columnModel.getColumn(it).preferredWidth) }
        }
        return collected
    }

    private companion object {
        const val VERSION_COLUMN = 3
    }
}

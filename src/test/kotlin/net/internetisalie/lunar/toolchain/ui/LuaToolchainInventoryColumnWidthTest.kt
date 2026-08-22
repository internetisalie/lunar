package net.internetisalie.lunar.toolchain.ui

import com.intellij.testFramework.EdtTestUtil
import com.intellij.util.ui.JBUI
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import net.internetisalie.lunar.ui.SwingLayoutProbe

/**
 * BUG-448 #8: the inventory declared no column-width model, so `TableView` split the width evenly —
 * `Path` was elided to `/usr/loca…` and `Origin` to `Discover…` while `Kind` got the same width for
 * one short word.
 *
 * The first fix for this shipped a regression, which is why this test measures two different things.
 * Sizing the columns to content also made the table's preferred width (857px, the sum of them) the
 * whole settings PAGE's preferred width. That is wider than the settings dialog's content area, so
 * the dialog scrolled the page horizontally and pushed `Origin` and `Health` off-screen — trading an
 * under-wide table for two unreachable columns. `preferredScrollableViewportSize` now decouples what
 * the table ASKS for from how it DIVIDES what it gets.
 *
 * So: [testPageDoesNotForceHorizontalScrolling] guards the regression, and
 * [testColumnsAreSizedToTheirContent] guards the original finding — on rendered widths, since
 * `TableColumn.preferredWidth` is only a request.
 */
class LuaToolchainInventoryColumnWidthTest : ToolchainSettingsTestCase() {
    fun testPageDoesNotForceHorizontalScrolling() {
        val pageWidth = pagePreferredWidth()
        assertTrue(
            "The Toolchain page asks for ${pageWidth}px, more than the ~${SETTINGS_CONTENT_WIDTH}px a " +
                "settings page gets — the dialog will scroll horizontally and hide the right-hand columns",
            pageWidth <= SETTINGS_CONTENT_WIDTH,
        )
    }

    fun testEveryColumnStaysVisibleAtBothDialogSizes() {
        listOf(DEFAULT_DIALOG_WIDTH, MAXIMISED_DIALOG_WIDTH).forEach { width ->
            val widths = renderedWidths(width)
            assertEquals("Expected the six inventory columns at ${width}px", 6, widths.size)
            assertTrue("A column was collapsed to nothing at ${width}px: $widths", widths.all { it > 0 })
            assertEquals(
                "Columns must divide the table exactly at ${width}px, not overflow it: $widths",
                width,
                widths.sum(),
            )
        }
    }

    /**
     * Asserts a MARGIN, not mere ordering. `JTable` spreads surplus by each column's remaining
     * headroom — equal when no `maxWidth` is set — so the absolute differences the model asks for
     * survive into the rendered widths while the ratios do not. Ordering alone would be satisfiable
     * by the one-pixel remainder an even split leaves on the leading columns.
     */
    fun testColumnsAreSizedToTheirContent() {
        val widths = renderedWidths(DEFAULT_DIALOG_WIDTH)
        val (kind, name, path) = widths
        assertTrue(
            "Path holds the longest value, so it must out-size Kind by a real margin: $widths",
            path - kind >= JBUI.scale(MIN_PATH_MARGIN),
        )
        assertTrue("Path must out-size Name: $widths", path > name)
        assertTrue("Version holds `5.4.7-1`, so it must be narrower than Path: $widths", widths[VERSION_COLUMN] < path)
    }

    private fun pagePreferredWidth(): Int {
        val collected = intArrayOf(0)
        readPageWidth(collected)
        return collected[0]
    }

    private fun readPageWidth(sink: IntArray) {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            sink[0] = LuaToolchainConfigurable().createPanel().preferredSize.width
        }
    }

    private fun renderedWidths(width: Int): List<Int> {
        val collected = mutableListOf<Int>()
        readRenderedWidths(width, collected)
        return collected
    }

    private fun readRenderedWidths(
        width: Int,
        sink: MutableList<Int>,
    ) {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val table = LuaToolchainInventoryTable().tableForTest()
            sink.addAll(SwingLayoutProbe.renderedColumnWidths(table, width))
        }
    }

    private companion object {
        const val VERSION_COLUMN = 3

        /**
         * Roughly what a settings page gets: the dialog's ~990px less the ~250px category tree and
         * the page insets. The point of the bound is that it is well under the 857px the unfixed
         * column model demanded, not its exact value.
         */
        const val SETTINGS_CONTENT_WIDTH = 680

        const val DEFAULT_DIALOG_WIDTH = 990
        const val MAXIMISED_DIALOG_WIDTH = 1700

        /** Under the model's own Path − Kind difference, far above a rounding remainder. */
        const val MIN_PATH_MARGIN = 100
    }
}

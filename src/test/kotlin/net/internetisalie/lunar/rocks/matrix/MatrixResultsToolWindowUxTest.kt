package net.internetisalie.lunar.rocks.matrix

import com.intellij.openapi.project.Project
import com.intellij.testFramework.EdtTestUtil
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.util.ui.JBUI
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import net.internetisalie.lunar.ui.SwingLayoutProbe
import javax.swing.JLabel
import javax.swing.table.TableColumnModel

/**
 * BUG-448 #21/#22/#23/#24 — the matrix tool window's four user-visible divergences: a dotted
 * internal id used as the window title, the enum constant `FAIL` shown as status text, the `Exit`
 * header abbreviating an exit code, and evenly split columns that gave a one-character value a
 * rockspec filename's width (measured 231/230/230/229px).
 *
 * #22 is covered at BOTH ends deliberately. Pinning only the wording helper leaves the line that
 * installs the renderer on the column model unprotected — delete it and the cell silently reverts to
 * `value.toString()`, which is `FAIL` again, with the whole suite still green.
 * [testStatusCellIsWiredToTheStatusColumn] asserts what the column model actually renders.
 */
class MatrixResultsToolWindowUxTest : ToolchainSettingsTestCase() {
    fun testStripeTitleIsADisplayNameNotTheDottedId() {
        val recorded = recordStripeTitle()
        assertNotNull("The factory must name its stripe; otherwise the platform falls back to the id", recorded)
        assertFalse("A dotted internal id must not reach the stripe: `$recorded`", recorded.orEmpty().contains('.'))
        assertFalse("The stripe title must differ from the id", recorded == MatrixResultsToolWindow.TOOL_WINDOW_ID)
    }

    fun testStatusWordingIsNotTheEnumConstant() {
        Status.entries.forEach { status ->
            val cell = matrixStatusCell(status)
            assertFalse("Status `${status.name}` reached the user verbatim", cell.text == status.name)
            assertEquals(
                "Status wording must be sentence case",
                cell.text.lowercase().replaceFirstChar { it.uppercase() },
                cell.text,
            )
        }
    }

    /** The wiring, not the wording: what does the STATUS column's renderer actually produce? */
    fun testStatusCellIsWiredToTheStatusColumn() {
        val rendered = renderFailedStatusCell()
        assertEquals(
            "The status column must render through MatrixStatusRenderer — a bare model value renders as `FAIL`",
            "Failed",
            rendered?.text,
        )
        assertNotNull("A rendered status must carry its severity icon", rendered?.icon)
    }

    fun testExitCodeColumnIsSpelledOut() {
        val columns = resultsColumnModel()
        val headers = (0 until columns.columnCount).map { columns.getColumn(it).headerValue }
        assertEquals("Exit code", headers[EXIT_COLUMN])
    }

    /**
     * Asserts the RENDERED widths, and asserts a MARGIN rather than mere ordering.
     *
     * Two things make the obvious assertion worthless here. `TableColumn.preferredWidth` is only a
     * request — `JTable` spreads surplus by each column's remaining headroom, equal when no
     * `maxWidth` is set, so the model's 320/220/110/80 renders as roughly 598/505/385/355: the
     * absolute differences survive, the ratios do not. And with every column equal, `1843 / 4` leaves
     * a remainder of 3 that lands on the leading columns, so a bare `widths[0] > widths[3]` passes on
     * a one-pixel rounding artifact even with no column model at all (measured — this test did
     * exactly that before the margin was added).
     *
     * The margin is what equal-surplus distribution preserves: the model's own 320 − 80.
     */
    fun testRockspecColumnRendersWiderThanTheExitCodeColumn() {
        val widths = renderedResultWidths()
        assertEquals("Expected four matrix columns", 4, widths.size)
        assertEquals("Columns must divide the table exactly, not overflow it: $widths", TOOL_WINDOW_WIDTH, widths.sum())
        val margin = widths[0] - widths[EXIT_COLUMN]
        assertTrue(
            "A rockspec filename must out-size an exit code by the width the model asks for, not by a " +
                "rounding remainder: got ${widths[0]} vs ${widths[EXIT_COLUMN]} (margin ${margin}px)",
            margin >= JBUI.scale(MIN_ROCKSPEC_MARGIN),
        )
    }

    private fun recordStripeTitle(): String? {
        val window = RecordingToolWindow(project)
        MatrixResultsToolWindow().init(window)
        return window.recordedStripeTitle
    }

    private fun resultsColumnModel(): TableColumnModel {
        val holder = arrayOfNulls<TableColumnModel>(1)
        readColumnModelOnEdt(holder)
        return holder[0] ?: error("results table not built")
    }

    private fun readColumnModelOnEdt(sink: Array<TableColumnModel?>) {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val panel = MatrixResultsToolWindow.MatrixResultsPanel.getInstance(project)
            sink[0] = panel.tableForTest().columnModel
        }
    }

    private fun renderedResultWidths(): List<Int> {
        val collected = mutableListOf<Int>()
        readRenderedWidths(collected)
        return collected
    }

    private fun readRenderedWidths(sink: MutableList<Int>) {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val panel = MatrixResultsToolWindow.MatrixResultsPanel.getInstance(project)
            sink.addAll(SwingLayoutProbe.renderedColumnWidths(panel.tableForTest(), TOOL_WINDOW_WIDTH))
        }
    }

    private fun renderFailedStatusCell(): JLabel? {
        val holder = arrayOfNulls<JLabel>(1)
        readFailedStatusCell(holder)
        return holder[0]
    }

    private fun readFailedStatusCell(sink: Array<JLabel?>) {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val panel = MatrixResultsToolWindow.MatrixResultsPanel.getInstance(project)
            try {
                panel.setResult(MatrixResult(listOf(failedRow())))
                val table = panel.tableForTest()
                val value = table.getValueAt(0, STATUS_COLUMN)
                val renderer = table.getCellRenderer(0, STATUS_COLUMN)
                sink[0] =
                    renderer.getTableCellRendererComponent(table, value, false, false, 0, STATUS_COLUMN) as? JLabel
            } finally {
                // The panel is a PROJECT service on the shared light project; leaving a row in it
                // would follow the suite around.
                panel.setResult(MatrixResult(emptyList()))
            }
        }
    }

    private fun failedRow(): MatrixRow =
        MatrixRow(
            env = LuaEnvironmentState(id = "E1", name = "E1", rootDir = "/p/E1", toolIds = mutableListOf()),
            rockspecLabel = "a-1.0-1.rockspec",
            status = Status.FAIL,
            exitCode = 1,
        )

    /**
     * The headless mock's own `setStripeTitle` is a no-op and its getter always returns "", so
     * asserting through it would pass against unfixed code. This records the call instead.
     */
    private class RecordingToolWindow(
        targetProject: Project,
    ) : ToolWindowHeadlessManagerImpl.MockToolWindow(targetProject) {
        var recordedStripeTitle: String? = null

        override fun setStripeTitle(title: String) {
            recordedStripeTitle = title
        }
    }

    private companion object {
        const val STATUS_COLUMN = 2
        const val EXIT_COLUMN = 3

        /** A bottom tool window at the audit's 1920px screen, less the tool window chrome. */
        const val TOOL_WINDOW_WIDTH = 1843

        /** Comfortably under the model's own 320 − 80, and far above a rounding remainder. */
        const val MIN_ROCKSPEC_MARGIN = 200
    }
}

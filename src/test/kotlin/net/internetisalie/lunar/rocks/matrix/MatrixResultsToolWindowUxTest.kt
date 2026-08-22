package net.internetisalie.lunar.rocks.matrix

import com.intellij.openapi.project.Project
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import javax.swing.table.TableColumnModel

/**
 * BUG-448 #21/#22/#23/#24 — the matrix tool window's four user-visible divergences: a dotted
 * internal id used as the window title, the enum constant `FAIL` shown as status text, the `Exit`
 * header abbreviating an exit code, and evenly split columns that gave a one-character value a
 * rockspec filename's width (measured 231/230/230/229px).
 */
class MatrixResultsToolWindowUxTest : BasePlatformTestCase() {
    fun `test stripe title is a display name not the dotted id (BUG-448 #21)`() {
        val recorded = recordStripeTitle()
        assertNotNull("The factory must name its stripe; otherwise the platform falls back to the id", recorded)
        assertFalse("A dotted internal id must not reach the stripe: `$recorded`", recorded.orEmpty().contains('.'))
        assertFalse("The stripe title must differ from the id", recorded == MatrixResultsToolWindow.TOOL_WINDOW_ID)
    }

    fun `test status is worded, not the enum constant (BUG-448 #22)`() {
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

    fun `test exit code column is spelled out (BUG-448 #23)`() {
        val columns = resultsColumnModel()
        val headers = (0 until columns.columnCount).map { columns.getColumn(it).headerValue }
        assertEquals("Exit code", headers[EXIT_COLUMN])
    }

    fun `test rockspec column is wider than the exit code column (BUG-448 #24)`() {
        val columns = resultsColumnModel()
        val rockspec = columns.getColumn(0).preferredWidth
        val exit = columns.getColumn(EXIT_COLUMN).preferredWidth
        assertTrue(
            "A rockspec filename needs more room than an exit code, got $rockspec vs $exit",
            rockspec > exit * 2,
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

    /**
     * The headless mock's own `setStripeTitle` is a no-op and `getStripeTitle` always returns "", so
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
        const val EXIT_COLUMN = 3
    }
}

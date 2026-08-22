package net.internetisalie.lunar.rocks.matrix

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import net.internetisalie.lunar.LuaBundle
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * Renders a matrix run as a per-env pass/fail table (ROCKS-15-04, design §2.6). The per-project
 * results panel lives in the [MatrixResultsPanel] project service (disposed with the project, so no
 * `Project` is retained past close); [RunMatrixAction] pushes results in via [MatrixResultsPanel.setResult].
 */
class MatrixResultsToolWindow : ToolWindowFactory {
    /**
     * BUG-448 #21: a `<toolWindow>` id doubles as its stripe title when nothing else is supplied, so
     * the dotted internal id surfaced as `Lunar.LuaMatrix` beside `LuaRocks Packages` and `Redis
     * Functions`. Naming the stripe here leaves the id — which is persisted layout state and a
     * lookup key in [RunMatrixAction] — untouched.
     */
    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = displayName()
    }

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        try {
            val panel = MatrixResultsPanel.getInstance(project)
            val content = toolWindow.contentManager.factory.createContent(panel, "Results", false)
            toolWindow.contentManager.addContent(content)
        } catch (throwable: Throwable) {
            LOG.warn("Failed to create Lua matrix tool window", throwable)
        }
    }

    companion object {
        const val TOOL_WINDOW_ID = "Lunar.LuaMatrix"

        /** What the user sees on the stripe and in View ▸ Tool Windows (BUG-448 #21). */
        fun displayName(): String = LuaBundle.message("toolwindow.matrix.displayName")

        private val LOG = Logger.getInstance(MatrixResultsToolWindow::class.java)

        /**
         * Builds the row cells `[rockspec, env, status, exit]` for a [MatrixResult] (test seam).
         *
         * The status cell holds the [Status] itself, not its `name`: BUG-448 #22 was the enum
         * constant `FAIL` reaching the user, and keeping the model value lets [MatrixStatusRenderer]
         * decide both the wording and the icon.
         */
        fun tableRows(result: MatrixResult): List<Array<Any>> =
            result.rows.map { arrayOf<Any>(it.rockspecLabel, it.env.name, it.status, it.exitCode ?: "") }
    }

    /**
     * Project-scoped JBTable-backed panel bound to the most recent matrix result. Registered as a
     * `@Service(PROJECT)` so the platform disposes it (and this Swing panel) with the project.
     */
    @Service(Service.Level.PROJECT)
    class MatrixResultsPanel : JPanel(BorderLayout()) {
        private val model = DefaultTableModel(COLUMN_HEADERS, 0)
        private val table = JBTable(model)

        init {
            applyColumnWidths()
            table.columnModel.getColumn(STATUS_COLUMN).cellRenderer = MatrixStatusRenderer
            add(JBScrollPane(table), BorderLayout.CENTER)
        }

        /**
         * BUG-448 #24: with no column-width model a `JBTable` splits evenly — measured at
         * 231/230/230/229px, so a one-character exit code got a rockspec filename's width. The
         * widths below size each column to what it actually holds.
         */
        private fun applyColumnWidths() {
            COLUMN_WIDTHS.forEachIndexed { index, width ->
                table.columnModel.getColumn(index).preferredWidth = JBUI.scale(width)
            }
        }

        internal fun tableForTest(): JBTable = table

        fun setResult(result: MatrixResult) {
            model.rowCount = 0
            tableRows(result).forEach { model.addRow(it) }
        }

        companion object {
            /** BUG-448 #23: `Exit` abbreviated what is an exit code. */
            private val COLUMN_HEADERS = arrayOf<Any>("Rockspec", "Environment", "Status", "Exit code")

            /** Unscaled preferred widths, in [COLUMN_HEADERS] order. */
            private val COLUMN_WIDTHS = listOf(320, 220, 110, 80)

            private const val STATUS_COLUMN = 2

            fun getInstance(project: Project): MatrixResultsPanel = project.getService(MatrixResultsPanel::class.java)
        }
    }
}

/** How a run [Status] is worded and iconified for the user (BUG-448 #22). */
internal data class MatrixStatusCell(
    val text: String,
    val icon: Icon,
)

/**
 * Maps a [Status] to sentence-case wording plus an icon.
 *
 * The enum constants are protocol-internal — `FAIL` is not a sentence and carried no severity cue.
 * Mirrors `healthCell` in `LuaToolchainInventoryTable`, which solves the same problem for tool health.
 */
internal fun matrixStatusCell(status: Status): MatrixStatusCell =
    when (status) {
        Status.PENDING -> MatrixStatusCell("Pending", AllIcons.General.Note)
        Status.RUNNING -> MatrixStatusCell("Running", AllIcons.Actions.Refresh)
        Status.PASS -> MatrixStatusCell("Passed", AllIcons.General.InspectionsOK)
        Status.FAIL -> MatrixStatusCell("Failed", AllIcons.General.Error)
    }

private object MatrixStatusRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        selected: Boolean,
        focused: Boolean,
        row: Int,
        column: Int,
    ): Component {
        val cell = (value as? Status)?.let(::matrixStatusCell)
        super.getTableCellRendererComponent(table, cell?.text ?: "", selected, focused, row, column)
        icon = cell?.icon
        return this
    }
}

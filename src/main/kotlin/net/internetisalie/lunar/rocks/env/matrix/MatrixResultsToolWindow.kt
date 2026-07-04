package net.internetisalie.lunar.rocks.env.matrix

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * Renders a matrix run as a per-env pass/fail table (ROCKS-15-04, design §2.6). A single shared
 * instance holds the last [MatrixResult]; [RunMatrixAction] pushes results in via [show].
 */
class MatrixResultsToolWindow : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        try {
            val panel = getOrCreatePanel(project)
            val content = toolWindow.contentManager.factory.createContent(panel, "Results", false)
            toolWindow.contentManager.addContent(content)
        } catch (throwable: Throwable) {
            LOG.warn("Failed to create Lua matrix tool window", throwable)
        }
    }

    companion object {
        const val TOOL_WINDOW_ID = "Lunar.LuaMatrix"
        private val LOG = Logger.getInstance(MatrixResultsToolWindow::class.java)
        private val PANELS = java.util.concurrent.ConcurrentHashMap<Project, MatrixResultsPanel>()

        internal fun getOrCreatePanel(project: Project): MatrixResultsPanel =
            PANELS.computeIfAbsent(project) { MatrixResultsPanel() }

        /** Builds the row cells `[label, status, exit]` for a [MatrixResult] (test seam). */
        fun tableRows(result: MatrixResult): List<Array<Any>> =
            result.rows.map { arrayOf<Any>(it.env.displayLabel(), it.status.name, it.exitCode ?: "") }
    }

    /** Simple JBTable-backed panel bound to the most recent matrix result. */
    internal class MatrixResultsPanel : JPanel(BorderLayout()) {
        private val model = DefaultTableModel(arrayOf<Any>("Environment", "Status", "Exit"), 0)

        init {
            add(JBScrollPane(JBTable(model)), BorderLayout.CENTER)
        }

        fun setResult(result: MatrixResult) {
            model.rowCount = 0
            tableRows(result).forEach { model.addRow(it) }
        }
    }
}

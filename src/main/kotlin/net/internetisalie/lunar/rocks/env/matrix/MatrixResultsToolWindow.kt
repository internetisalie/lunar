package net.internetisalie.lunar.rocks.env.matrix

import com.intellij.openapi.components.Service
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
 * Renders a matrix run as a per-env pass/fail table (ROCKS-15-04, design §2.6). The per-project
 * results panel lives in the [MatrixResultsPanel] project service (disposed with the project, so no
 * `Project` is retained past close); [RunMatrixAction] pushes results in via [MatrixResultsPanel.setResult].
 */
class MatrixResultsToolWindow : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
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
        private val LOG = Logger.getInstance(MatrixResultsToolWindow::class.java)

        /** Builds the row cells `[label, status, exit]` for a [MatrixResult] (test seam). */
        fun tableRows(result: MatrixResult): List<Array<Any>> =
            result.rows.map { arrayOf<Any>(it.env.displayLabel(), it.status.name, it.exitCode ?: "") }
    }

    /**
     * Project-scoped JBTable-backed panel bound to the most recent matrix result. Registered as a
     * `@Service(PROJECT)` so the platform disposes it (and this Swing panel) with the project.
     */
    @Service(Service.Level.PROJECT)
    class MatrixResultsPanel : JPanel(BorderLayout()) {
        private val model = DefaultTableModel(arrayOf<Any>("Environment", "Status", "Exit"), 0)

        init {
            add(JBScrollPane(JBTable(model)), BorderLayout.CENTER)
        }

        fun setResult(result: MatrixResult) {
            model.rowCount = 0
            tableRows(result).forEach { model.addRow(it) }
        }

        companion object {
            fun getInstance(project: Project): MatrixResultsPanel =
                project.getService(MatrixResultsPanel::class.java)
        }
    }
}

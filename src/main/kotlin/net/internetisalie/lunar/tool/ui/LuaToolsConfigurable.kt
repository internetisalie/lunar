package net.internetisalie.lunar.tool.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.NlsContexts.ConfigurableName
import net.internetisalie.lunar.tool.LuaTool
import net.internetisalie.lunar.tool.LuaToolManager
import net.internetisalie.lunar.tool.health.LuaToolHealthChecker
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

/**
 * Settings page (Settings > Tools > Lua Tools) that surfaces the tool inventory and its health
 * (TOOL-03, design §2.6). Lists every registered [LuaTool] with its type, path, version, validity
 * and last health-check reason, and offers Auto-Discover, Add, Remove, and Re-check actions.
 *
 * The inventory is a live, immediately-persisted model owned by [LuaToolManager]; this page mutates
 * it directly through the manager, so [isModified]/[apply] are intentionally no-ops.
 *
 * **Threading:** the Re-check action runs [LuaToolHealthChecker] on a pooled thread and refreshes
 * the table back on the EDT — the binary is never probed on the EDT.
 */
class LuaToolsConfigurable : Configurable {

    private val tableModel = ToolTableModel()
    private val table = JBTable(tableModel)
    private var rootPanel: JPanel? = null

    override fun getDisplayName(): @ConfigurableName String = "Lua Tools"

    override fun createComponent(): JComponent {
        reset()
        val decorated = ToolbarDecorator.createDecorator(table)
            .setAddAction { addToolViaChooser() }
            .setRemoveAction { removeSelectedTool() }
            .addExtraAction(
                object : com.intellij.openapi.actionSystem.AnAction(
                    "Auto-Discover",
                    "Scan PATH and common locations for Lua tools",
                    com.intellij.icons.AllIcons.Actions.Refresh,
                ) {
                    override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                        LuaToolManager.getInstance().autoDiscover()
                        reset()
                    }
                },
            )
            .addExtraAction(
                object : com.intellij.openapi.actionSystem.AnAction(
                    "Re-check Health",
                    "Re-run --version on every registered tool",
                    com.intellij.icons.AllIcons.Actions.ForceRefresh,
                ) {
                    override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                        recheckAll()
                    }
                },
            )
            .createPanel()

        val panel = JPanel(BorderLayout())
        panel.add(JBLabel("External Lua tools discovered and bound for this IDE:"), BorderLayout.NORTH)
        panel.add(decorated, BorderLayout.CENTER)
        rootPanel = panel
        return panel
    }

    override fun isModified(): Boolean = false

    override fun apply() {
        // Inventory mutations are applied immediately via LuaToolManager; nothing to flush here.
    }

    override fun reset() {
        tableModel.setTools(LuaToolManager.getInstance().getTools())
    }

    override fun disposeUIResources() {
        rootPanel = null
    }

    private fun addToolViaChooser() {
        val descriptor = FileChooserDescriptorFactory.singleFile()
            .withTitle("Select Lua Tool Binary")
        val chosen = FileChooser.chooseFile(descriptor, rootPanel, null, null) ?: return
        LuaToolManager.getInstance().registerTool(chosen.path)
        reset()
    }

    private fun removeSelectedTool() {
        val row = table.selectedRow.takeIf { it >= 0 } ?: return
        val tool = tableModel.toolAt(table.convertRowIndexToModel(row)) ?: return
        LuaToolManager.getInstance().unregisterTool(tool.id)
        reset()
    }

    private fun recheckAll() {
        val tools = LuaToolManager.getInstance().getTools()
        ApplicationManager.getApplication().executeOnPooledThread {
            tools.forEach { tool ->
                LuaToolHealthChecker.applyResult(tool, LuaToolHealthChecker.check(tool))
            }
            ApplicationManager.getApplication().invokeLater { reset() }
        }
    }

    /** Read-only table model over a snapshot of the tool inventory. */
    private class ToolTableModel : AbstractTableModel() {
        private val columns = arrayOf("Type", "Name", "Path", "Version", "Valid", "Status")
        private var tools: List<LuaTool> = emptyList()

        fun setTools(newTools: List<LuaTool>) {
            tools = newTools
            fireTableDataChanged()
        }

        fun toolAt(modelRow: Int): LuaTool? = tools.getOrNull(modelRow)

        override fun getRowCount(): Int = tools.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val tool = tools[rowIndex]
            return when (columnIndex) {
                0 -> tool.type.name
                1 -> tool.name
                2 -> tool.path
                3 -> tool.version.ifEmpty { "-" }
                4 -> if (tool.isValid) "yes" else "no"
                5 -> tool.lastCheckReason.ifEmpty { "(not checked)" }
                else -> ""
            }
        }
    }
}

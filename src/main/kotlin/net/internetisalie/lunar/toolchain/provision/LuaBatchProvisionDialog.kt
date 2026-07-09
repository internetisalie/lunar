package net.internetisalie.lunar.toolchain.provision

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.ListTableModel
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeedLoader
import javax.swing.JComponent

/**
 * Collects a base directory + an add/remove table of `(runtime kind, version)` rows for the
 * version-matrix flow (design §2.13). Request derivation is the pure [LuaBatchDerivation.toRequests]
 * (design §3.10). EDT-only; reads only the bundled feed.
 */
class LuaBatchProvisionDialog(private val targetProject: Project) : DialogWrapper(targetProject) {

    private val feed: LuaToolchainFeed = LuaToolchainFeedLoader.load()
    private val platform = LuaHostPlatform.current()

    private val baseDirField = TextFieldWithBrowseButton()
    private val tableModel = ListTableModel<LuaBatchRow>(kindColumn(), versionColumn())
    private val rowTable = JBTable(tableModel)

    init {
        title = "Provision Version Matrix"
        baseDirField.text = "${targetProject.guessProjectDir()?.path.orEmpty()}/.lua-matrix"
        baseDirField.addBrowseFolderListener(
            targetProject,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Matrix Base Directory")
                .withDescription("Directory under which one environment is provisioned per row"),
        )
        tableModel.addRow(defaultRow())
        init()
    }

    private fun defaultRow(): LuaBatchRow {
        val kindId = LuaToolCatalog.RUNTIME_KINDS.first()
        return LuaBatchRow(kindId, LuaToolCatalog.defaultVersion(feed, kindId, platform))
    }

    override fun createCenterPanel(): JComponent {
        val tablePanel = ToolbarDecorator.createDecorator(rowTable)
            .setAddAction { tableModel.addRow(defaultRow()) }
            .setRemoveAction { removeSelectedRow() }
            .createPanel()
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Base directory:", baseDirField)
            .addLabeledComponentFillVertically("Versions:", tablePanel)
            .panel
    }

    private fun removeSelectedRow() {
        val selected = rowTable.selectedRow
        if (selected >= 0) tableModel.removeRow(selected)
    }

    override fun doValidate(): ValidationInfo? {
        if (baseDirField.text.isBlank()) return ValidationInfo("Base directory is required", baseDirField)
        if (tableModel.items.isEmpty()) return ValidationInfo("Add at least one row", rowTable)
        return null
    }

    fun toRequests(): List<LuaProvisionRequest> =
        LuaBatchDerivation.toRequests(baseDirField.text.trim(), tableModel.items.toList())

    private fun kindColumn(): ColumnInfo<LuaBatchRow, String> =
        object : ColumnInfo<LuaBatchRow, String>("Runtime") {
            override fun valueOf(row: LuaBatchRow): String = row.kindId
            override fun isCellEditable(row: LuaBatchRow): Boolean = true
            override fun setValue(row: LuaBatchRow, value: String) {
                replaceRow(row, row.copy(kindId = value))
            }
        }

    private fun versionColumn(): ColumnInfo<LuaBatchRow, String> =
        object : ColumnInfo<LuaBatchRow, String>("Version") {
            override fun valueOf(row: LuaBatchRow): String = row.versionSpec
            override fun isCellEditable(row: LuaBatchRow): Boolean = true
            override fun setValue(row: LuaBatchRow, value: String) {
                replaceRow(row, row.copy(versionSpec = value))
            }
        }

    private fun replaceRow(oldRow: LuaBatchRow, newRow: LuaBatchRow) {
        val index = tableModel.items.indexOf(oldRow)
        if (index >= 0) tableModel.setItem(index, newRow)
    }
}

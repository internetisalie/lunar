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
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeedLoader
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Collects a base directory + an add/remove table of `(runtime kind, version)` rows for the
 * version-matrix flow (design §2.13). Request derivation is the pure [LuaBatchDerivation.toRequests]
 * (design §3.10). EDT-only; reads only the bundled feed.
 */
class LuaBatchProvisionDialog(
    private val targetProject: Project,
) : DialogWrapper(targetProject) {
    private val feed: LuaToolchainFeed = LuaToolchainFeedLoader.load()
    private val platform = LuaHostPlatform.current()

    private val baseDirField = TextFieldWithBrowseButton()
    private val tableModel = ListTableModel<LuaBatchRow>(kindColumn(), versionColumn())
    private val rowTable = JBTable(tableModel)

    /**
     * Built once. A Swing component has exactly one parent, so building a second panel over the same
     * fields would silently empty the live dialog (engineering contract §6, ONE PARENT PER COMPONENT).
     * Declared ahead of the `init` block because `init()` reaches this through [createCenterPanel].
     */
    private val centerPanel: JComponent by lazy { buildCenterPanel() }

    init {
        title = "Provision Version Matrix"
        // BUG-448 #6/#7: the dialog was narrower than its own title (rendered `Provision …on
        // Matrix`) and clipped the base directory to `/.lua-matrix`. The base-dir field is the
        // widest control, so sizing it in columns sets the dialog's width for both.
        baseDirField.textField.columns = PATH_FIELD_COLUMNS
        baseDirField.text = "${targetProject.guessProjectDir()?.path.orEmpty()}/.lua-matrix"
        baseDirField.addBrowseFolderListener(
            targetProject,
            FileChooserDescriptorFactory
                .createSingleFolderDescriptor()
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

    override fun createCenterPanel(): JComponent = centerPanel

    private fun buildCenterPanel(): JComponent {
        val tablePanel =
            ToolbarDecorator
                .createDecorator(rowTable)
                .setAddAction { tableModel.addRow(defaultRow()) }
                .setRemoveAction { removeSelectedRow() }
                .createPanel()
        return FormBuilder
            .createFormBuilder()
            .addLabeledComponent("Base directory:", baseDirField)
            .addLabeledComponentFillVertically("Versions:", tablePanel)
            .panel
            .apply { preferredSize = atLeastMinimum(preferredSize) }
    }

    /**
     * Raises [natural] to the minimum content size, once, when the panel is built.
     *
     * This is a fixed preferred size, not a live minimum — `setMinimumSize` would be that, and the
     * dialog does not need one. Taking the max of [natural] rather than assigning a constant is what
     * matters: a base directory wider than the minimum still sets the width, so BUG-448 #6 is not
     * bought by re-introducing BUG-448 #7.
     */
    private fun atLeastMinimum(natural: Dimension): Dimension =
        Dimension(
            maxOf(natural.width, JBUI.scale(MIN_WIDTH)),
            maxOf(natural.height, JBUI.scale(MIN_HEIGHT)),
        )

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

            override fun setValue(
                row: LuaBatchRow,
                value: String,
            ) {
                replaceRow(row, row.copy(kindId = value))
            }
        }

    private fun versionColumn(): ColumnInfo<LuaBatchRow, String> =
        object : ColumnInfo<LuaBatchRow, String>("Version") {
            override fun valueOf(row: LuaBatchRow): String = row.versionSpec

            override fun isCellEditable(row: LuaBatchRow): Boolean = true

            override fun setValue(
                row: LuaBatchRow,
                value: String,
            ) {
                replaceRow(row, row.copy(versionSpec = value))
            }
        }

    private fun replaceRow(
        oldRow: LuaBatchRow,
        newRow: LuaBatchRow,
    ) {
        val index = tableModel.items.indexOf(oldRow)
        if (index >= 0) tableModel.setItem(index, newRow)
    }

    /** Exposes the built center panel for width assertions (BUG-448 #6). */
    internal fun centerPanelForTest(): JComponent = centerPanel

    /** Exposes the base-directory field for width assertions (BUG-448 #7). */
    internal fun baseDirFieldForTest(): TextFieldWithBrowseButton = baseDirField

    private companion object {
        /** Wide enough for a project path plus the `/.lua-matrix` suffix (BUG-448 #7). */
        const val PATH_FIELD_COLUMNS: Int = 44

        /**
         * Unscaled minimum content size. FormBuilder's natural width came out narrower than the
         * dialog's own title, which the window then rendered as `Provision …on Matrix` (BUG-448 #6).
         */
        const val MIN_WIDTH: Int = 520
        const val MIN_HEIGHT: Int = 320
    }
}

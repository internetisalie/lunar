package net.internetisalie.lunar.toolchain.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.provision.LuaProvisionDialog
import net.internetisalie.lunar.toolchain.provision.LuaToolProvisioner
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import java.awt.Component
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

private val LOG = logger<LuaToolchainInventoryTable>()
private const val EMPTY_TEXT = "No tools registered — use Add, Auto-Discover or Provision"

/**
 * TOOLING-06 §2.2. The unified toolchain inventory: a live [TableView] over
 * [LuaToolchainRegistry.tools] with the Kind/Name/Path/Version/Origin/Health columns (§3.3)
 * and the Add / Auto-Discover / Provision… / Remove / Re-check toolbar (§3.2). All probing
 * runs on a pooled thread; [refresh] is EDT-only. The registry's topic subscription (owned by
 * the configurable) drives the auto-refresh — the per-action [refresh] here is belt-and-braces.
 */
class LuaToolchainInventoryTable {

    private val model = ListTableModel<LuaRegisteredTool>(
        KindColumn, NameColumn, PathColumn, VersionColumn, OriginColumn, HealthColumn
    )
    private val table = TableView(model)

    val component: JComponent

    init {
        table.setShowGrid(false)
        table.emptyText.text = EMPTY_TEXT
        component = ToolbarDecorator.createDecorator(table)
            .setAddAction { addTool() }
            .setRemoveAction { removeSelected() }
            .addExtraAction(discoverButton())
            .addExtraAction(provisionButton())
            .addExtraAction(recheckButton())
            .createPanel()
        refresh()
    }

    fun refresh() {
        model.items = registry().tools()
    }

    fun selectedTool(): LuaRegisteredTool? = table.selectedObject

    internal fun model(): ListTableModel<LuaRegisteredTool> = model

    private fun registry(): LuaToolchainRegistry = LuaToolchainRegistry.getInstance()

    private fun addTool() {
        val descriptor = FileChooserDescriptorFactory.singleFile()
            .withTitle("Select Lua Tool Binary")
        val chosen = FileChooser.chooseFile(descriptor, component, null, null) ?: return
        val path = chosen.path
        pooled {
            val registered = registry().registerTool(path)
            if (registered == null) {
                onEdt { Messages.showErrorDialog(component, "Not a recognized Lua tool: $path", "Add Tool") }
            }
        }
    }

    private fun removeSelected() {
        val tool = selectedTool() ?: return
        registry().unregisterTool(tool.id)
    }

    private fun discover() = pooled { registry().autoDiscover() }

    private fun recheck() = pooled { registry().tools().forEach { registry().refreshTool(it.id) } }

    private fun provision() {
        val candidates = openProjects()
        when {
            candidates.isEmpty() -> return
            candidates.size == 1 -> provisionInto(candidates.single())
            else -> showProjectChooser(candidates)
        }
    }

    private fun showProjectChooser(candidates: List<Project>) {
        val step = object : BaseListPopupStep<Project>("Choose Target Project", candidates) {
            override fun getTextFor(value: Project): String = value.name
            override fun onChosen(selectedValue: Project, finalChoice: Boolean): PopupStep<*>? {
                provisionInto(selectedValue)
                return FINAL_CHOICE
            }
        }
        JBPopupFactory.getInstance().createListPopup(step).showInFocusCenter()
    }

    private fun provisionInto(targetProject: Project) {
        val dialog = LuaProvisionDialog(targetProject, initial = null)
        if (dialog.showAndGet()) {
            LuaToolProvisioner.getInstance().provision(targetProject, dialog.toRequest())
        }
    }

    /** Returns all non-default, non-disposed open projects (BUG-372 project selection seam). */
    internal fun openProjects(): List<Project> =
        ProjectManager.getInstance().openProjects.filter { !it.isDefault && !it.isDisposed }

    private fun discoverButton(): AnAction =
        object : DumbAwareAction("Auto-Discover", null, AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) = discover()
        }

    private fun recheckButton(): AnAction =
        object : DumbAwareAction("Re-check", null, AllIcons.Actions.ForceRefresh) {
            override fun actionPerformed(e: AnActionEvent) = recheck()
        }

    private fun provisionButton(): AnAction =
        object : DumbAwareAction("Provision…", null, AllIcons.General.Add) {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) {
                val projects = openProjects()
                e.presentation.isEnabled = projects.isNotEmpty()
                e.presentation.description =
                    if (projects.isEmpty()) "No open project to provision into" else null
            }
            override fun actionPerformed(e: AnActionEvent) = provision()
        }

    private fun pooled(work: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                work()
            } catch (t: Throwable) {
                LOG.warn("Toolchain inventory action failed", t)
            }
            onEdt { refresh() }
        }
    }

    private fun onEdt(work: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(work, ModalityState.any())
    }
}

private fun kindDisplayName(tool: LuaRegisteredTool): String =
    LuaToolKindRegistry.findById(tool.kindId)?.displayName ?: tool.kindId

private object KindColumn : ColumnInfo<LuaRegisteredTool, String>("Kind") {
    override fun valueOf(item: LuaRegisteredTool): String = kindDisplayName(item)
}

private object NameColumn : ColumnInfo<LuaRegisteredTool, String>("Name") {
    override fun valueOf(item: LuaRegisteredTool): String =
        item.runtime?.let { "${it.product} ${it.version}" } ?: kindDisplayName(item)
}

private object PathColumn : ColumnInfo<LuaRegisteredTool, String>("Path") {
    override fun valueOf(item: LuaRegisteredTool): String = item.path
}

private object VersionColumn : ColumnInfo<LuaRegisteredTool, String>("Version") {
    override fun valueOf(item: LuaRegisteredTool): String = item.version ?: "-"
}

private object OriginColumn : ColumnInfo<LuaRegisteredTool, String>("Origin") {
    override fun valueOf(item: LuaRegisteredTool): String = when (item.origin) {
        Origin.DISCOVERED -> "Discovered"
        Origin.MANUAL -> "Manual"
        Origin.PROVISIONED -> "Provisioned"
    }
}

internal data class HealthCell(val text: String, val icon: Icon, val tooltip: String)

internal fun healthCell(health: LuaToolHealth): HealthCell {
    val (text, icon) = when {
        !health.fileExists -> "Missing" to AllIcons.General.Error
        !health.executable -> "Not executable" to AllIcons.General.Error
        health.probeOk == false -> "Probe failed" to AllIcons.General.Warning
        health.probeOk == null -> "Not checked" to AllIcons.General.Note
        else -> "OK" to AllIcons.General.InspectionsOK
    }
    return HealthCell(text, icon, health.reason ?: text)
}

private object HealthColumn : ColumnInfo<LuaRegisteredTool, LuaRegisteredTool>("Health") {
    override fun valueOf(item: LuaRegisteredTool): LuaRegisteredTool = item

    override fun getRenderer(item: LuaRegisteredTool?): TableCellRenderer = HealthRenderer
}

private object HealthRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        selected: Boolean,
        focused: Boolean,
        row: Int,
        column: Int
    ): Component {
        val tool = value as? LuaRegisteredTool
        val cell = tool?.let { healthCell(it.health) }
        super.getTableCellRendererComponent(table, cell?.text ?: "", selected, focused, row, column)
        icon = cell?.icon
        toolTipText = cell?.tooltip
        return this
    }
}

package net.internetisalie.lunar.toolchain.health

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import net.internetisalie.lunar.analysis.luacheck.LuaCheckInspection
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolKind
import net.internetisalie.lunar.toolchain.model.isUsable
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver
import net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable
import java.util.function.Function
import javax.swing.JComponent

private val LOG = logger<LuaToolEditorNotificationProvider>()

private const val LUACHECK_KIND_ID = "luacheck"

/**
 * Lua-file editor banner (design §2.4/§3.4). Renders at most one banner: a runtime banner when no
 * usable RUNTIME-kind tool resolves, else a broken-tool banner for the first *engaged* kind whose
 * *intended* tool is unusable. Collection is I/O- and process-free — it reads cached registry state
 * and the inspection profile only, so it is safe on a read-compatible thread.
 */
class LuaToolEditorNotificationProvider : EditorNotificationProvider, DumbAware {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        if (file.fileType != LuaFileType) return null
        return runCatching { computeBanner(project) }
            .onFailure { LOG.warn("Toolchain banner collection failed", it) }
            .getOrNull()
    }

    private fun computeBanner(project: Project): Function<in FileEditor, out JComponent?>? {
        runtimeBanner(project)?.let { return it }
        return brokenToolBanner(project)
    }

    private fun runtimeBanner(project: Project): Function<in FileEditor, out JComponent?>? {
        val monitor = LuaToolHealthMonitor.getInstance(project)
        if (LuaToolResolver.getInstance().resolveRuntime(project) != null) return null
        if (monitor.runtimeBannerDismissed) return null
        return Function { fileEditor ->
            warningPanel(project, fileEditor, "No usable Lua runtime for this project.").apply {
                createActionLabel("Dismiss") {
                    monitor.dismissRuntimeBanner()
                    EditorNotifications.getInstance(project).updateAllNotifications()
                }
            }
        }
    }

    private fun brokenToolBanner(project: Project): Function<in FileEditor, out JComponent?>? {
        for (kind in orderedKinds()) {
            if (!engaged(project, kind)) continue
            val intended = intendedTool(project, kind.id) ?: continue
            if (intended.isUsable) continue
            val reason = intended.health.reason ?: "unavailable"
            val message = "Lua tool '${kind.displayName}' is unavailable: $reason"
            return Function { fileEditor -> warningPanel(project, fileEditor, message) }
        }
        return null
    }

    private fun warningPanel(project: Project, fileEditor: FileEditor, message: String): EditorNotificationPanel =
        EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning).apply {
            text = message
            createActionLabel("Configure toolchain") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, LuaToolchainConfigurable::class.java)
            }
        }

    private fun orderedKinds(): List<LuaToolKind> {
        val (runtimeKinds, otherKinds) = LuaToolKindRegistry.all().partition { it.isRuntime }
        return runtimeKinds.sortedBy { it.id } + otherKinds.sortedBy { it.id }
    }

    private fun engaged(project: Project, kind: LuaToolKind): Boolean {
        if (kind.id == LUACHECK_KIND_ID && isLuaCheckInspectionEnabled(project)) return true
        return explicitlySelected(project, kind.id)
    }

    private fun explicitlySelected(project: Project, kindId: String): Boolean {
        val settings = LuaToolchainProjectSettings.getInstance(project)
        if (kindId in settings.getState().bindings.keys) return true
        if (kindId in LuaToolchainRegistry.getInstance().globalBindings().keys) return true
        val activeToolIds = settings.activeEnvironment()?.toolIds ?: return false
        return LuaToolchainRegistry.getInstance().tools()
            .any { it.id in activeToolIds && it.kindId == kindId }
    }

    private fun isLuaCheckInspectionEnabled(project: Project): Boolean {
        val displayKey = HighlightDisplayKey.find(LuaCheckInspection.SHORT_NAME) ?: return false
        return InspectionProjectProfileManager.getInstance(project).currentProfile.isToolEnabled(displayKey)
    }

    private fun intendedTool(project: Project, kindId: String): LuaRegisteredTool? {
        val settings = LuaToolchainProjectSettings.getInstance(project)
        val registry = LuaToolchainRegistry.getInstance()
        environmentIntended(settings, registry, kindId)?.let { return it }
        settings.getState().bindings[kindId]?.let { boundId ->
            registry.tool(boundId)?.let { return it }
        }
        registry.globalBindings()[kindId]?.let { boundId ->
            registry.tool(boundId)?.let { return it }
        }
        return null
    }

    private fun environmentIntended(
        settings: LuaToolchainProjectSettings,
        registry: LuaToolchainRegistry,
        kindId: String
    ): LuaRegisteredTool? {
        val toolIds = settings.activeEnvironment()?.toolIds ?: return null
        return toolIds.asSequence()
            .mapNotNull { registry.tool(it) }
            .firstOrNull { it.kindId == kindId }
    }
}

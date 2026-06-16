package net.internetisalie.lunar.tool.health

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.tool.LuaToolManager
import net.internetisalie.lunar.tool.LuaToolType
import net.internetisalie.lunar.tool.ui.LuaToolsConfigurable
import java.util.function.Function
import javax.swing.JComponent

/**
 * Shows an editor banner on Lua files when a tool that is bound (or effective) for the current
 * project is invalid (TOOL-03-04, design §2.6 and §3.4).
 *
 * The banner is non-intrusive — it appears only on `.lua` files — and links to the Tools
 * settings page so the user can fix the binding immediately.
 *
 * **Threading:** [collectNotificationData] is called on a read-lock-compatible thread (EDT or
 * background read action).  It only reads cached [net.internetisalie.lunar.tool.LuaTool] state
 * (no CLI calls), so no additional locking is needed.
 */
class LuaToolEditorNotificationProvider : EditorNotificationProvider, DumbAware {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (file.fileType !is LuaFileType) return null

        val manager = LuaToolManager.getInstance()
        val invalidTools = LuaToolType.entries.mapNotNull { type ->
            manager.getEffectiveTool(project, type)?.takeIf { !it.isValid }
        }

        if (invalidTools.isEmpty()) return null

        val first = invalidTools.first()
        val reason = first.lastCheckReason.ifEmpty { "unavailable" }
        val message = "Lua tool '${first.type}' is unavailable: $reason"

        return Function { fileEditor ->
            EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning).apply {
                text = message
                createActionLabel("Configure tools") {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, LuaToolsConfigurable::class.java)
                }
            }
        }
    }
}

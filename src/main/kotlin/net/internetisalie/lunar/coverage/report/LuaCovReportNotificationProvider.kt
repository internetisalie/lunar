package net.internetisalie.lunar.coverage.report

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageRunner
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.function.Function
import javax.swing.JComponent

class LuaCovReportNotificationProvider : EditorNotificationProvider, DumbAware {

    companion object {
        private val DISMISSED_KEY = Key.create<Boolean>("LuaCovReportNotificationDismissed")
    }

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        if (file.fileType != LuaCovReportFileType) return null
        if (file.getUserData(DISMISSED_KEY) == true) return null

        return Function { fileEditor ->
            EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
                text = "This is a LuaCov coverage report."
                createActionLabel("Load Coverage onto Project Files") {
                    val ioFile = VfsUtilCore.virtualToIoFile(file)
                    val runner = CoverageRunner.getInstanceById("LuaCov")
                    if (runner != null) {
                        val manager = CoverageDataManager.getInstance(project)
                        val suite = manager.addExternalCoverageSuite(ioFile, runner)
                        if (suite != null) {
                            val bundle = CoverageSuitesBundle(suite)
                            manager.chooseSuitesBundle(bundle)
                        }
                    }
                }
                createActionLabel("Dismiss") {
                    file.putUserData(DISMISSED_KEY, true)
                    EditorNotifications.getInstance(project).updateNotifications(file)
                }
            }
        }
    }
}

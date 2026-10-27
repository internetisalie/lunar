package net.internetisalie.lunar.coverage

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.lang.LuaIcons

/**
 * Action to import an external LuaCov report or stats file and load coverage overlays onto project files.
 */
class LuaCovReportImportAction :
    DumbAwareAction(
        "Import LuaCov Report...",
        "Load coverage data from a luacov.report.out or luacov.stats.out file",
        LuaIcons.COVERAGE,
    ) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = reportFileDescriptor()

        val selectedFile = FileChooser.chooseFile(descriptor, project, null) ?: return
        importReport(project, selectedFile)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    companion object {
        internal fun reportFileDescriptor(): FileChooserDescriptor =
            FileChooserDescriptorFactory
                .singleFile()
                .withFileFilter { file -> isReportFile(file) }
                .withTitle("Select LuaCov Report File")
                .withDescription("Select a luacov.report.out or luacov.stats.out file to load coverage data")

        /**
         * Imports [file] as a LuaCov report; returns false, after telling the user, if it is not one (BUG-540).
         * The descriptor's filter is not enough: the universal file chooser, on by default in 2026.2,
         * ignores it and offers every file.
         */
        internal fun importReport(
            project: Project,
            file: VirtualFile,
        ): Boolean {
            if (!isReportFile(file)) {
                Messages.showErrorDialog(project, "${file.name} is not a LuaCov report file.", "Import LuaCov Report")
                return false
            }
            val runner = LuaCoverageRunner.getInstance() ?: return false
            val manager = CoverageDataManager.getInstance(project)
            val suite = manager.addExternalCoverageSuite(VfsUtilCore.virtualToIoFile(file), runner) ?: return false
            manager.chooseSuitesBundle(CoverageSuitesBundle(suite))
            return true
        }

        private fun isReportFile(file: VirtualFile): Boolean = !file.isDirectory && file.name.endsWith(".out")
    }
}

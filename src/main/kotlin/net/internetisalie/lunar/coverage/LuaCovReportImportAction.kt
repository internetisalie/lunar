package net.internetisalie.lunar.coverage

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageRunner
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.lang.LuaIcons

/**
 * Action to import an external LuaCov report or stats file and load coverage overlays onto project files.
 */
class LuaCovReportImportAction : DumbAwareAction(
    "Import LuaCov Report...",
    "Load coverage data from a luacov.report.out or luacov.stats.out file",
    LuaIcons.COVERAGE
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = object : FileChooserDescriptor(true, false, false, false, false, false) {
            override fun isFileSelectable(file: VirtualFile?): Boolean {
                if (file == null || file.isDirectory) return false
                val name = file.name
                return name.endsWith(".out") || name == "luacov.report.out" || name == "luacov.stats.out"
            }
        }
        descriptor.title = "Select LuaCov Report File"
        descriptor.description = "Select a luacov.report.out or luacov.stats.out file to load coverage data"

        val selectedFile = FileChooser.chooseFile(descriptor, project, null) ?: return
        val ioFile = VfsUtilCore.virtualToIoFile(selectedFile)

        val runner = CoverageRunner.getInstanceById("LuaCov") ?: return
        val manager = CoverageDataManager.getInstance(project)

        val suite = manager.addExternalCoverageSuite(ioFile, runner) ?: return
        val bundle = CoverageSuitesBundle(suite)
        manager.chooseSuitesBundle(bundle)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

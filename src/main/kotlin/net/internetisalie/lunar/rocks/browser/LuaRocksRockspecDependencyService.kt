package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import net.internetisalie.lunar.rocks.LuaRocksTreeLocator

/**
 * Appends an installed rock to the discovered project rockspec's `dependencies` table
 * (ROCKS-16-13, risks DR-05). Uses the ROCKS-09 discovery machinery to locate the rockspec and the
 * pure [RockspecDependencyEditor] to compute the new text; the write runs under a
 * `WriteCommandAction` (engineering-contract §1).
 */
class LuaRocksRockspecDependencyService(private val project: Project) {

    /**
     * Adds `"<name> <constraint>"` to the first discovered project rockspec's `dependencies`.
     * Returns `true` when a rockspec was found and updated (or already contained the dependency),
     * `false` when no project rockspec exists.
     */
    fun addDependency(name: String, constraint: String?): Boolean {
        val rockspec = resolveRockspecFile() ?: return false
        applyTo(rockspec, name, constraint)
        return true
    }

    /** Rewrites [rockspec]'s `dependencies` to include the rock, under a write command (testable core). */
    fun applyTo(rockspec: VirtualFile, name: String, constraint: String?) {
        val entry = constraint?.takeIf { it.isNotBlank() }?.let { "$name $it" } ?: name
        val updated = RockspecDependencyEditor.addDependency(VfsUtil.loadText(rockspec), entry)
        WriteCommandAction.runWriteCommandAction(project) { VfsUtil.saveText(rockspec, updated) }
    }

    private fun resolveRockspecFile(): VirtualFile? {
        val path = runReadAction { LuaRocksTreeLocator.allProjectRockspecs(project).firstOrNull() } ?: return null
        return VfsUtil.findFile(path, true)
            ?: VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)
    }
}

package net.internetisalie.lunar.util

import com.intellij.ide.plugins.PluginManagerCore.getPlugin
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import net.internetisalie.lunar.LuaPlugin
import net.internetisalie.lunar.lang.LuaFileType

object LuaFileUtil {
    val pluginVirtualDirectory: VirtualFile?
        get() {
            val descriptor = getPlugin(PluginId.getId(LuaPlugin.ID)) ?: return null
            return VirtualFileManager.getInstance().findFileByNioPath(descriptor.pluginPath)
        }

    fun getPluginVirtualDirectoryChild(vararg args: String): VirtualFile? {
        var dir: VirtualFile? = pluginVirtualDirectory
        for (arg in args) {
            if (dir == null) break
            dir = dir.findChild(arg)
        }
        return dir
    }

    fun findLuaFilesInDir(dir: VirtualFile): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        VfsUtil.visitChildrenRecursively(dir, object : VirtualFileVisitor<VirtualFile>() {
            override fun visitFile(vf: VirtualFile): Boolean {
                if (vf.fileType == LuaFileType) {
                    result.add(vf)
                }
                return true
            }
        })
        return result
    }

    fun findPsiFiles(project: Project, virtualFiles : Collection<VirtualFile>) : List<PsiFile> {
        val psiManager = PsiManager.getInstance(project)
        return virtualFiles.mapNotNull { psiManager.findFile(it) }
    }
}

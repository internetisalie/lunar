package net.internetisalie.lunar.lang

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.lang.psi.LuaFile
import java.io.File

class LuaRequireReference(
    element: PsiElement,
    textRange: TextRange,
    private val moduleName: String
) : PsiReferenceBase<PsiElement>(element, textRange) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val patterns = PathConfiguration.getProjectSourcePathPatterns(project)
        for (pattern in patterns) {
            val path = pattern.interpolate(moduleName)
            val file = LocalFileSystem.getInstance().findFileByPath(path)
                ?: VfsUtil.findFileByIoFile(File(path), false)
            if (file != null) {
                val psiFile = PsiManager.getInstance(project).findFile(file)
                if (psiFile is LuaFile) return psiFile
            }
        }

        // If not found in source path patterns, look in the filename index (stubs/libraries)
        val fileName = moduleName.substringAfterLast('.') + ".lua"
        val expectedPathPart = moduleName.replace('.', '/') + ".lua"
        val expectedInitPathPart = moduleName.replace('.', '/') + "/init.lua"
        val projectScope = GlobalSearchScope.allScope(project)
        val virtualFiles = FilenameIndex.getVirtualFilesByName(fileName, projectScope) +
                           FilenameIndex.getVirtualFilesByName("init.lua", projectScope)
        
        for (virtualFile in virtualFiles) {
            val path = virtualFile.path
            if (path.endsWith(expectedPathPart) || path.endsWith(expectedInitPathPart) || !moduleName.contains('.')) {
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                if (psiFile is LuaFile) return psiFile
            }
        }

        return null
    }
}

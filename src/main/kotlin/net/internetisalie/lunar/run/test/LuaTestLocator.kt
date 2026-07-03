package net.internetisalie.lunar.run.test

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import java.io.File

object LuaTestLocator : SMTestLocator {
    const val PROTOCOL = "lua"

    override fun getLocation(
        protocol: String,
        path: String,
        project: Project,
        scope: GlobalSearchScope
    ): List<Location<PsiElement>> {
        if (protocol != PROTOCOL) return emptyList()

        val lastColon = path.lastIndexOf(':')
        if (lastColon == -1) return emptyList()

        val initialFilePath = path.substring(0, lastColon)
        val filePath = if (initialFilePath.startsWith("//")) {
            initialFilePath.substring(1)
        } else {
            initialFilePath
        }

        val lineStr = path.substring(lastColon + 1)
        val line = lineStr.toIntOrNull() ?: 1

        val targetVirtualFile = findVirtualFile(filePath, project) ?: return emptyList()

        return runReadActionBlocking {
            val targetPsiFile = PsiManager.getInstance(project).findFile(targetVirtualFile) ?: return@runReadActionBlocking emptyList()
            val document = PsiDocumentManager.getInstance(project).getDocument(targetPsiFile) ?: return@runReadActionBlocking emptyList()
            val lineIndex = (line - 1).coerceIn(0, document.lineCount - 1)
            val offset = document.getLineStartOffset(lineIndex)
            val targetElement = targetPsiFile.findElementAt(offset) ?: targetPsiFile
            listOf(PsiLocation(project, targetElement))
        }
    }

    private fun findVirtualFile(filePath: String, project: Project): com.intellij.openapi.vfs.VirtualFile? {
        val file = LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: VfsUtil.findFileByIoFile(File(filePath), false)
            ?: VirtualFileManager.getInstance().getFileSystem("temp")?.findFileByPath(filePath)
        if (file != null) return file

        if (!File(filePath).isAbsolute) {
            val projectDir = project.basePath
            if (projectDir != null) {
                val absolutePath = File(projectDir, filePath).absolutePath
                return LocalFileSystem.getInstance().findFileByPath(absolutePath)
                    ?: VfsUtil.findFileByIoFile(File(absolutePath), false)
                    ?: VirtualFileManager.getInstance().getFileSystem("temp")?.findFileByPath(absolutePath)
            }
        }
        return null
    }
}

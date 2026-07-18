package net.internetisalie.lunar.lang.path

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import net.internetisalie.lunar.lang.psi.LuaFile
import java.io.File

/**
 * MAINT-30-03 (§2.5/§3.6): the single canonical module→file resolver.
 *
 * Yields **all** candidate [LuaFile]s for [moduleName] in resolution order — source-path patterns
 * ([PathConfiguration.getProjectSourcePathPatterns]) first, then the [FilenameIndex] fallback
 * (`<name>.lua` + `init.lua`, filtered by the expected path tail). Lazy so each caller applies its
 * own terminal: `LuaRequireReference` takes `firstOrNull()`; `LuaTypeManagerImpl` keeps its
 * skip-untyped-and-try-next selection via `firstNotNullOfOrNull { getModuleType(it, …) }`.
 *
 * VFS lookups use `refresh = false` — resolution must not trigger a synchronous VFS refresh on the
 * read path (contract §1 threading; a freshly-written file is picked up on the next VFS event). This
 * reconciles the former refresh-flag divergence between the two resolvers (P1 #3 root).
 */
fun resolveModuleCandidates(project: Project, moduleName: String): Sequence<LuaFile> = sequence {
    val psiManager = PsiManager.getInstance(project)

    for (pattern in PathConfiguration.getProjectSourcePathPatterns(project)) {
        val virtualFile = findByPath(pattern.interpolate(moduleName)) ?: continue
        (psiManager.findFile(virtualFile) as? LuaFile)?.let { yield(it) }
    }

    val expectedPathPart = moduleName.replace('.', '/') + ".lua"
    val expectedInitPathPart = moduleName.replace('.', '/') + "/init.lua"
    val scope = GlobalSearchScope.allScope(project)
    val byName = FilenameIndex.getVirtualFilesByName(moduleName.substringAfterLast('.') + ".lua", scope) +
        FilenameIndex.getVirtualFilesByName("init.lua", scope)

    for (virtualFile in byName) {
        val path = virtualFile.path
        if (path.endsWith(expectedPathPart) || path.endsWith(expectedInitPathPart) || !moduleName.contains('.')) {
            (psiManager.findFile(virtualFile) as? LuaFile)?.let { yield(it) }
        }
    }
}

private fun findByPath(path: String): VirtualFile? =
    LocalFileSystem.getInstance().findFileByPath(path) ?: VfsUtil.findFileByIoFile(File(path), false)

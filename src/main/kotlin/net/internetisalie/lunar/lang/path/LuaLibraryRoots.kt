package net.internetisalie.lunar.lang.path

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * Every root of a library Lunar adds to the project: the target's runtime stubs, search-path
 * directories, the installed-rocks tree and definition libraries (BUG-62). Code under one of these
 * is loaded by Lua's own path rule, unlike project code, whose loading Lunar can only guess.
 */
object LuaLibraryRoots {
    private const val LUNAR_PACKAGE = "net.internetisalie.lunar."

    /** The deepest Lunar library root holding [file], or null for a file outside every library. */
    fun rootOf(
        project: Project,
        file: VirtualFile,
    ): VirtualFile? = roots(project).firstOrNull { VfsUtilCore.isAncestor(it, file, true) }

    /** Deepest first, so a nested root wins over the one that contains it. */
    private fun roots(project: Project): List<VirtualFile> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result.create(compute(project), ProjectRootManager.getInstance(project))
        }

    private fun compute(project: Project): List<VirtualFile> =
        AdditionalLibraryRootsProvider.EP_NAME.extensionList
            .filter { it.javaClass.name.startsWith(LUNAR_PACKAGE) }
            .flatMap { provider -> provider.getAdditionalProjectLibraries(project).flatMap { it.sourceRoots } }
            .distinct()
            .sortedByDescending { it.path.length }
}

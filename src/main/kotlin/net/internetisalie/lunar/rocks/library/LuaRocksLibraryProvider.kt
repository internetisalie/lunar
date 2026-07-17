package net.internetisalie.lunar.rocks.library

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.rocks.LuaRocksTreeLocator
import net.internetisalie.lunar.settings.LuaProjectSettings
import javax.swing.Icon

class LuaRocksLibraryProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val roots = installedRoots(project)
        if (roots.isEmpty()) return emptyList()
        return listOf(InstalledRocksLibrary(roots))
    }

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> =
        installedRoots(project)

    private fun installedRoots(project: Project): List<VirtualFile> {
        val tree = LuaRocksTreeLocator.treeRoot(project) ?: return emptyList()
        val version = LuaProjectSettings.getInstance(project).state.getTarget().getImplicitLanguageLevel().version

        val candidates = listOf(
            tree.resolve("share").resolve("lua").resolve(version),
            tree.resolve("lib").resolve("lua").resolve(version)
        )

        return candidates.mapNotNull { VfsUtil.findFile(it, false) }.filter { it.isDirectory }
    }

    class InstalledRocksLibrary(private val roots: List<VirtualFile>) :
        SyntheticLibrary(), ItemPresentation {
        override fun getSourceRoots(): Collection<VirtualFile> = roots
        override fun getPresentableText(): String = "Installed Rocks"
        override fun getLocationString(): String = "lua_modules"
        override fun getIcon(unused: Boolean): Icon = LuaIcons.FILE
        override fun hashCode(): Int = roots.hashCode()
        override fun equals(other: Any?): Boolean =
            other is InstalledRocksLibrary && other.roots == roots
    }
}

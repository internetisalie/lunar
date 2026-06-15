package net.internetisalie.lunar.lang.library

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.platform.target.RuntimeLibraryProvider
import net.internetisalie.lunar.settings.LuaProjectSettings
import javax.swing.Icon

class LuaLibraryProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val settings = LuaProjectSettings.getInstance(project).state
        val target = settings.getTarget()
        val runtimeProvider = RuntimeLibraryProvider(project)
        val libraryRoot = runtimeProvider.getLibraryRoot(target) ?: return emptyList()
        return listOf(LuaLibrary(libraryRoot))
    }

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> {
        val settings = LuaProjectSettings.getInstance(project).state
        val target = settings.getTarget()
        val runtimeProvider = RuntimeLibraryProvider(project)
        val libraryRoot = runtimeProvider.getLibraryRoot(target) ?: return emptyList()
        return listOf(libraryRoot)
    }

    class LuaLibrary(private val root: VirtualFile) : SyntheticLibrary(), ItemPresentation {
        override fun getSourceRoots(): Collection<VirtualFile> = listOf(root)

        override fun getIcon(unused: Boolean): Icon = LuaIcons.FILE

        override fun getPresentableText(): String = "Lua External API Stubs"

        override fun getLocationString(): String = "Lua Stubs"

        override fun hashCode(): Int = root.hashCode()

        override fun equals(other: Any?): Boolean =
            other is LuaLibrary && other.root == root
    }
}

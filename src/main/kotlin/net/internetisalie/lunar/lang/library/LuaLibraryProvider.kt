package net.internetisalie.lunar.lang.library

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.RuntimeLibraryProvider
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.settings.LuaProjectSettings
import javax.swing.Icon

class LuaLibraryProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val settings = LuaProjectSettings.getInstance(project).state
        val target = settings.getTarget()
        val runtimeProvider = RuntimeLibraryProvider(project)
        val libraryRoot = runtimeProvider.getLibraryRoot(target) ?: return emptyList()
        return listOf(LuaLibrary(libraryRoot, titleOf(target)))
    }

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> {
        val settings = LuaProjectSettings.getInstance(project).state
        val target = settings.getTarget()
        val runtimeProvider = RuntimeLibraryProvider(project)
        val libraryRoot = runtimeProvider.getLibraryRoot(target) ?: return emptyList()
        return listOf(libraryRoot)
    }

    companion object {
        /** The target as a user names it: `Lua 5.1` for standard Lua, `Redis 7+` otherwise (BUG-60). */
        internal fun titleOf(target: Target): String =
            if (target.platform == LuaPlatform.STANDARD) {
                target.getImplicitLanguageLevel().toString()
            } else {
                "${target.platform.label} ${target.version.label}"
            }
    }

    class LuaLibrary(
        private val root: VirtualFile,
        private val title: String,
    ) : SyntheticLibrary(),
        ItemPresentation {
        override fun getSourceRoots(): Collection<VirtualFile> = listOf(root)

        override fun getIcon(unused: Boolean): Icon = LuaIcons.FILE

        override fun getPresentableText(): String = title

        override fun getLocationString(): String = "standard library"

        override fun hashCode(): Int = root.hashCode()

        override fun equals(other: Any?): Boolean = other is LuaLibrary && other.root == root
    }
}

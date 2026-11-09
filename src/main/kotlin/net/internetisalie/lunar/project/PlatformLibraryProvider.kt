/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.internetisalie.lunar.project

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.stubs.StubIndex
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.indexing.PackageFile
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.platform.target.RuntimeLibraryProvider
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.nio.file.Paths
import javax.swing.Icon

class PlatformLibraryProvider : AdditionalLibraryRootsProvider() {
    /**
     * One library per search-path directory outside the project. The target's standard library is
     * [net.internetisalie.lunar.lang.library.LuaLibraryProvider]'s alone, so it is not listed twice (BUG-60).
     */
    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> =
        PlatformLibraryIndex.searchPathRoots(project).map { SearchPathLibrary(it) }

    /** A directory on the Lua search path, named by its full path so two `…/5.4` roots stay distinct. */
    class SearchPathLibrary(
        private val root: VirtualFile,
    ) : SyntheticLibrary(),
        ItemPresentation {
        override fun hashCode() = root.hashCode()

        override fun equals(other: Any?): Boolean = other is SearchPathLibrary && other.root == root

        override fun getSourceRoots() = listOf(root)

        override fun getLocationString() = "Lua search path"

        override fun getIcon(p0: Boolean): Icon = LuaIcons.FILE

        override fun getPresentableText(): String = FileUtil.getLocationRelativeToUserHome(root.presentableUrl)
    }
}

object PlatformLibraryIndex {
    fun getPlatformLibraryFolder(project: Project): VirtualFile? {
        val (_, folder) = getPlatformLibrary(project) ?: return null
        return folder
    }

    /** The existing search-path directories outside the project (BUG-60). */
    fun searchPathRoots(project: Project): List<VirtualFile> {
        val projectBasePath = project.basePath ?: return emptyList()
        return PathConfiguration
            .getStaticSourcePathPatterns(project)
            .map { it.leadingPath }
            .distinct()
            .map { Paths.get(it) }
            .filter { it.isAbsolute && !it.startsWith(projectBasePath) }
            .mapNotNull { VfsUtil.findFile(it, false) }
    }

    fun getPlatformLibrary(project: Project): Pair<LuaLanguageLevel, VirtualFile>? {
        val settings = LuaProjectSettings.getInstance(project).state
        val target = settings.getTarget()
        val level = target.getImplicitLanguageLevel()
        val runtimeProvider = RuntimeLibraryProvider(project)
        val libraryRoot = runtimeProvider.getLibraryRoot(target) ?: return null
        return Pair(level, libraryRoot)
    }

    fun getPackageFiles(project: Project): List<PackageFile> {
        val platformLibraryFolder = getPlatformLibraryFolder(project) ?: return emptyList()
        return platformLibraryFolder.children
            .filter { (it.extension ?: "") == "lua" }
            .map {
                if (it.name == "global.lua" || it.name == "builtin.lua") {
                    PackageFile("", true, it)
                } else {
                    PackageFile(it.name.substringBeforeLast('.', it.name), true, it)
                }
            }.toList()
    }

    /** Reloads platform libraries and forces a stub-index rebuild. Must run on the EDT with no read lock held. */
    fun reload() {
        WriteAction.run<RuntimeException> {
            val projects = ProjectManagerEx.getInstanceEx().openProjects
            for (project in projects) {
                ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(
                    EmptyRunnable.getInstance(),
                    RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED,
                )
            }
            StubIndex.getInstance().forceRebuild(Throwable("Lua language level changed."))
        }
    }
}

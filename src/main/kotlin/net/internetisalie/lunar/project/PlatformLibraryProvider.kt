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
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.stubs.StubIndex
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.indexing.PackageFile
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.platform.target.RuntimeLibraryProvider
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.util.LuaFileUtil
import java.nio.file.Paths
import javax.swing.Icon

class PlatformLibraryProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        return listOf(
            getExternalLibraries(project),
            getSupportLibraries(project),
        ).flatten()
    }

    fun getSupportLibraries(project: Project): Collection<SyntheticLibrary> {
        val (level, platformDirectoryVirtualFile) = PlatformLibraryIndex.getPlatformLibrary(project) ?: return emptyList()
        return listOf(PlatformLibrary(level, platformDirectoryVirtualFile))
    }

    fun getExternalLibraries(project: Project): Collection<SyntheticLibrary> {
        val projectBasePath = project.basePath ?: return emptyList()

        val virtualFiles = PathConfiguration.getProjectSourcePathPatterns(project)
            .map { it.leadingPath }
            .distinct()
            .map { it -> Paths.get(it) }
            .filter { it.isAbsolute }
            .filter { !it.startsWith(projectBasePath) }
            .mapNotNull { VfsUtil.findFile(it, true) }
            .toTypedArray()

        if (virtualFiles.isEmpty()) return emptyList()

        return listOf<SyntheticLibrary>(
            ExternalLibraries("Search Trees", *virtualFiles)
        )
    }

    class PlatformLibrary(private val level: LuaLanguageLevel,
                          private val root: VirtualFile
    ) : SyntheticLibrary(), ItemPresentation {
        private val roots = listOf(root)
        override fun hashCode() = root.hashCode()

        override fun equals(other: Any?): Boolean {
            return other is PlatformLibrary && other.root == root
        }

        override fun getSourceRoots() = roots

        override fun getLocationString() = "Lua Standard Library"

        override fun getIcon(p0: Boolean): Icon = LuaIcons.FILE

        override fun getPresentableText() = level.toString()
    }

    class ExternalLibraries(val name : String, vararg root: VirtualFile) : SyntheticLibrary(), ItemPresentation {
        private val roots =  listOf(*root)
        override fun hashCode() = roots.hashCode()
        override fun equals(other: Any?): Boolean {
            return other is ExternalLibraries && other.roots == roots
        }
        override fun getSourceRoots() = roots
        override fun getLocationString() = name
        override fun getIcon(p0: Boolean): Icon = LuaIcons.FILE
        override fun getPresentableText() = name
    }
}

object PlatformLibraryIndex {
    fun getPlatformLibraryFolder(project: Project): VirtualFile? {
        val (_, folder) =  getPlatformLibrary(project) ?: return null
        return folder
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
                if (it.name == "global.lua" || it.name == "builtin.lua")
                    PackageFile("", true, it)
                else
                    PackageFile(it.name.substringBeforeLast('.', it.name), true, it)
            }
            .toList()
    }

    fun reload() {
        WriteAction.run<RuntimeException> {
            val projects = ProjectManagerEx.getInstanceEx().openProjects
            for (project in projects) {
                ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED)
            }

            StubIndex.getInstance().forceRebuild(Throwable("Lua language level changed."))
        }
    }
}
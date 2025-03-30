package net.internetisalie.lunar.project

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.lang.indexing.PackageFile
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.util.LuaFileUtil
import java.io.File

data object PlatformLibraryIndex {
    private fun getPlatformLibraryFolder(): VirtualFile? {
        val level = LuaProjectSettings.instance.state.languageLevel
        val platformDirectory =
            LuaFileUtil.getPluginVirtualDirectoryChild("platform", "Lua${level.version}") ?: return null
        return VfsUtil.findFileByIoFile(File(platformDirectory.canonicalPath!!), true)
    }

    fun getPackageFiles(): List<PackageFile> {
        val platformLibraryFolder = getPlatformLibraryFolder() ?: return emptyList()
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
}
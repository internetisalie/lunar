package net.internetisalie.lunar.project

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.util.LuaFileUtil
import java.io.File

class PlatformLibraryIndex {
    private fun getPlatformLibraryFolder(): VirtualFile? {
        val level = LuaProjectSettings.instance.state.languageLevel
        val platformDirectory =
            LuaFileUtil.getPluginVirtualDirectoryChild("platform", "Lua${level.version}") ?: return null
        return VfsUtil.findFileByIoFile(File(platformDirectory.canonicalPath!!), true)
    }

    fun getGlobalNames(): Map<String, VirtualFile> {
        val platformLibraryFolder = getPlatformLibraryFolder() ?: return emptyMap()
        val globalFile = platformLibraryFolder.findChild("global.lua")!!
        val packageFile =  platformLibraryFolder.findChild("package.lua")!!
        return mapOf(
            "assert" to globalFile,
            "collectgarbage" to globalFile,
            "dofile" to globalFile,
            "error" to globalFile,
            "getmetatable" to globalFile,
            "ipairs" to globalFile,
            "load" to globalFile,
            "loadfile" to globalFile,
            "next" to globalFile,
            "pairs" to globalFile,
            "pcall" to globalFile,
            "print" to globalFile,
            "rawequal" to globalFile,
            "rawget" to globalFile,
            "rawset" to globalFile,
            "select" to globalFile,
            "setmetatable" to globalFile,
            "tonumber" to globalFile,
            "tostring" to globalFile,
            "type" to globalFile,
            "_VERSION" to globalFile,
            "xpcall" to globalFile,
            "require" to packageFile,
        )
    }

    fun getPackageNames(): Map<String, VirtualFile> {
        val platformLibraryFolder = getPlatformLibraryFolder() ?: return emptyMap()
        return mapOf(
            "coroutine" to platformLibraryFolder.findChild("coroutine.lua")!!,
            "debug" to platformLibraryFolder.findChild("debug.lua")!!,
            "io" to platformLibraryFolder.findChild("io.lua")!!,
            "math" to platformLibraryFolder.findChild("math.lua")!!,
            "os" to platformLibraryFolder.findChild("os.lua")!!,
            "package" to platformLibraryFolder.findChild("package.lua")!!,
            "string" to platformLibraryFolder.findChild("string.lua")!!,
            "table" to platformLibraryFolder.findChild("table.lua")!!,
            "utf8" to platformLibraryFolder.findChild("utf8.lua")!!,
        )
    }

    companion object {
        val instance = PlatformLibraryIndex()
    }
}
package net.internetisalie.lunar.lang

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * Gives Lua tooling files their own icon. The rockspec, `.luacheckrc` and `.busted` files are Lua
 * by file type and would otherwise all show the Lua icon; the TOML and JSON configs would show the
 * generic ones. Matches on the file name only, so it never reads content or PSI.
 */
class LuaFileIconProvider : FileIconProvider {
    override fun getIcon(
        file: VirtualFile,
        flags: Int,
        project: Project?,
    ): Icon? = iconFor(file.name)

    internal companion object {
        private val BY_NAME: Map<String, Icon> =
            mapOf(
                ".luacheckrc" to LuaIcons.LUACHECK_CONFIG,
                ".busted" to LuaIcons.BUSTED_CONFIG,
                "stylua.toml" to LuaIcons.STYLUA_CONFIG,
                ".stylua.toml" to LuaIcons.STYLUA_CONFIG,
                ".luarc.json" to LuaIcons.JSON_CONFIG,
                ".lunar-env.json" to LuaIcons.JSON_CONFIG,
            )

        fun iconFor(fileName: String): Icon? =
            BY_NAME[fileName] ?: LuaIcons.ROCKSPEC.takeIf { fileName.endsWith(".rockspec") }
    }
}

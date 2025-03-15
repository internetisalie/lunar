package net.internetisalie.lunar.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object LuaWorkFileType : LanguageFileType(LuaLanguage) {
    override fun getName(): String {
        return "Lua Workspace"
    }

    override fun getDescription(): String {
        return "Lua workspace file"
    }

    override fun getDefaultExtension(): String {
        return "luawork"
    }

    override fun getDisplayName(): String {
        return "Lua Workspace file"
    }

    override fun getIcon(): Icon {
        return LuaIcons.FILE
    }
}

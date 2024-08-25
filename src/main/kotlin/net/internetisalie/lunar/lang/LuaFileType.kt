package net.internetisalie.lunar.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object LuaFileType : LanguageFileType(LuaLanguage) {
    override fun getName(): String {
        return "Lua File"
    }

    override fun getDescription(): String {
        return "Lua language file"
    }

    override fun getDefaultExtension(): String {
        return "lua"
    }

    override fun getIcon(): Icon {
        return LuaIcons.FILE
    }
}

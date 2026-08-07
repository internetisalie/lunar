package net.internetisalie.lunar.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object LuaFileType : LanguageFileType(LuaLanguage) {
    override fun getName(): String = "Lua"

    override fun getDescription(): String = "Lua language file"

    override fun getDefaultExtension(): String = "lua"

    override fun getIcon(): Icon = LuaIcons.FILE
}

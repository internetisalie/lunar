package net.internetisalie.lunar.lang

import com.intellij.lang.Language

object  LuaLanguage : Language("Lua") {
    @JvmStatic
    val INSTANCE = LuaLanguage

    private fun readResolve(): Any = LuaLanguage
}

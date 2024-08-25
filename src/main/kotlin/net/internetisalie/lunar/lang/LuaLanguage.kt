package net.internetisalie.lunar.lang

import com.intellij.lang.Language

object  LuaLanguage : Language("Lua") {
    private fun readResolve(): Any = LuaLanguage
}

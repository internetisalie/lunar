package net.internetisalie.lunar.lang.psi

import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.lang.LuaLanguage

class LuaTokenType(debugName: String) : IElementType(debugName, LuaLanguage) {
    override fun toString(): String = "LuaTokenType." + super.toString()
}

package net.internetisalie.lunar.lang.psi

import com.intellij.psi.tree.IElementType
import net.internetisalie.lunar.lang.LuaLanguage

class LuaElementType(private val name: String) : IElementType(name, LuaLanguage) {
    override fun toString(): String {
        return name
    }
}

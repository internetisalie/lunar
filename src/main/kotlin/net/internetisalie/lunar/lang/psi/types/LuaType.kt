package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement

enum class LuaVisibility {
    PUBLIC,
    PROTECTED,
    PRIVATE
}

interface LuaType {
    val name: String

    fun resolveMember(name: String): LuaTypeMember?

    fun isAssignableTo(other: LuaType): Boolean
}

data class LuaTypeMember(
    val name: String,
    val type: LuaType,
    val visibility: LuaVisibility = LuaVisibility.PUBLIC,
    val description: String? = null,
    val sourceElement: PsiElement? = null
)

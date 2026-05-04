package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement

class LuaTypeReference(
    override val name: String,
    private val context: PsiElement
) : LuaType {
    private val resolved: LuaType by lazy {
        LuaTypeManager.getInstance(context.project).resolveType(name, context) ?: LuaPrimitiveType.UNKNOWN
    }

    override fun resolveMember(name: String): LuaTypeMember? = resolved.resolveMember(name)

    override fun isAssignableTo(other: LuaType): Boolean = resolved.isAssignableTo(other)

    override fun toString(): String = name
}

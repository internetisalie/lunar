package net.internetisalie.lunar.lang.insight.unwrap

import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaDoStatement
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaGlobalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaIfStatement
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaNumericForStatement
import net.internetisalie.lunar.lang.psi.LuaWhileStatement

/**
 * Maps an unwrappable block-construct kind to its applicable PSI type(s) and picker label (design §2.7).
 * `FUNCTION` deliberately matches only statement-context declarations, **not** the expression-form
 * `LuaFuncDef` (`local f = function() … end`): hoisting a body out of an expression position would
 * produce invalid Lua (risks DR-02 → exclude). EDITOR-06.
 */
enum class LuaConstruct(val unwrapDescription: String) {
    IF("Unwrap 'if'"),
    WHILE("Unwrap 'while'"),
    FOR("Unwrap 'for'"),
    DO("Unwrap 'do'"),
    FUNCTION("Unwrap 'function'"),
    ;

    fun matches(e: PsiElement): Boolean = when (this) {
        IF -> e is LuaIfStatement
        WHILE -> e is LuaWhileStatement
        FOR -> e is LuaNumericForStatement || e is LuaGenericForStatement
        DO -> e is LuaDoStatement
        FUNCTION -> e is LuaFuncDecl || e is LuaLocalFuncDecl || e is LuaGlobalFuncDecl
    }

    companion object {
        /** True if [e] is any unwrappable/removable block construct (excludes expression-form `LuaFuncDef`). */
        fun isConstruct(e: PsiElement): Boolean = entries.any { it.matches(e) }
    }
}

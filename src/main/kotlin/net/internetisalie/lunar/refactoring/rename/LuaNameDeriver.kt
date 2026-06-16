package net.internetisalie.lunar.refactoring.rename

import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaIndexExpr
import net.internetisalie.lunar.lang.psi.LuaNameRef

/**
 * Stateless name-derivation shared by [net.internetisalie.lunar.refactoring.rename.LuaNameSuggestionProvider]
 * (Rename popup) and [net.internetisalie.lunar.refactoring.LuaIntroduceVariableHandler] (Introduce
 * Variable). Implements INTENT-03 §3: extract a raw base name from the RHS expression and strip a
 * leading accessor/factory prefix when it is immediately followed by an uppercase letter.
 */
object LuaNameDeriver {

    private val prefixes = listOf("create", "build", "find", "load", "make", "get", "set", "new")

    /** Returns a single base-name candidate for [expr], or null if none can be derived. */
    fun baseName(expr: LuaExpr): String? {
        val raw = rawName(expr) ?: return null
        return stripPrefix(raw)
    }

    private fun rawName(expr: LuaExpr): String? = when (expr) {
        is LuaFuncCall -> calleeName(expr)
        is LuaNameRef -> expr.identifier.text
        else -> propertyName(expr)
    }

    private fun calleeName(call: LuaFuncCall): String? {
        val methodName = call.nameAndArgsList
            .mapNotNull { it.methodExpr?.nameRef?.identifier?.text }
            .lastOrNull()
        if (methodName != null) return methodName
        return PsiTreeUtil.findChildrenOfType(call.varOrExp, LuaNameRef::class.java)
            .lastOrNull()?.identifier?.text
    }

    private fun propertyName(expr: LuaExpr): String? {
        val index = PsiTreeUtil.findChildrenOfType(expr, LuaIndexExpr::class.java).lastOrNull()
        return index?.nameRef?.identifier?.text
    }

    private fun stripPrefix(name: String): String {
        for (prefix in prefixes) {
            if (name.length > prefix.length &&
                name.startsWith(prefix) &&
                name[prefix.length].isAsciiUpper()
            ) {
                return name[prefix.length].lowercaseChar() + name.substring(prefix.length + 1)
            }
        }
        return name
    }

    private fun Char.isAsciiUpper(): Boolean = this in 'A'..'Z'
}

package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import net.internetisalie.lunar.lang.psi.LuaVar

/**
 * Discovers implicit class fields from assignments `ClassName.field = …` and
 * `self.field = …` (inside a `ClassName` method), adding them as members without
 * overwriting explicit `@field`s (TYPE-02-05).
 *
 * RHS types use light syntactic inference only (no type-graph / `resolveType` call),
 * to avoid materialization-time reentrancy (see TYPE-02-DR-03).
 */
object LuaImplicitFields {

    private const val SELF = "self"

    fun collect(
        className: String,
        classDecls: Collection<LuaLocalVarDecl>,
        into: MutableMap<String, LuaTypeMember>,
    ) {
        val files = classDecls.mapNotNull { it.containingFile }.distinct()
        for (file in files) {
            val assignments = PsiTreeUtil.findChildrenOfType(file, LuaAssignmentStatement::class.java)
            for (assignment in assignments) {
                collectFromAssignment(className, assignment, into)
            }
        }
    }

    private fun collectFromAssignment(
        className: String,
        assignment: LuaAssignmentStatement,
        into: MutableMap<String, LuaTypeMember>,
    ) {
        val vars = assignment.varList.varList
        val exprs = assignment.exprList.exprList
        vars.forEachIndexed { index, luaVar ->
            val field = fieldNameFor(className, luaVar) ?: return@forEachIndexed
            if (into.containsKey(field)) return@forEachIndexed
            val rhs = exprs.getOrNull(index)
            into[field] = LuaTypeMember(field, lightInferType(rhs), sourceElement = luaVar)
        }
    }

    /** Field name iff [luaVar] is a single `base.field` access matching the class context. */
    private fun fieldNameFor(className: String, luaVar: LuaVar): String? {
        val field = singleFieldSuffixName(luaVar) ?: return null
        val base = luaVar.nameRef?.text ?: return null
        return when {
            base == className -> field
            base == SELF && isInClassMethod(className, luaVar) -> field
            else -> null
        }
    }

    /** The field name for `base.field`, or null for `base[i]`, `base.x.y`, or a bare name. */
    private fun singleFieldSuffixName(luaVar: LuaVar): String? {
        val suffix = luaVar.varSuffixList.singleOrNull() ?: return null
        if (suffix.nameAndArgsList.isNotEmpty()) return null
        return suffix.indexExpr.nameRef?.text
    }

    /** True if [luaVar] sits inside a method `function <className>:m()` / `.m()`. */
    private fun isInClassMethod(className: String, luaVar: LuaVar): Boolean {
        val funcDecl = PsiTreeUtil.getParentOfType(luaVar, LuaFuncDecl::class.java) ?: return false
        val funcName = funcDecl.funcName
        if (funcName.nameRef.text != className) return false
        return funcName.funcNameMethod != null || funcName.funcNamePropertyList.isNotEmpty()
    }

    /** Maps an RHS expression's syntactic KIND to a type without any graph/resolve call. */
    private fun lightInferType(rhs: LuaExpr?): LuaType = when (rhs) {
        null -> LuaPrimitiveType.ANY
        is LuaTableConstructor -> LuaPrimitiveType.TABLE
        is LuaTerminalExpr -> terminalType(rhs)
        is LuaFuncDef -> LuaPrimitiveType.FUNCTION
        else -> LuaPrimitiveType.ANY
    }

    private fun terminalType(terminal: LuaTerminalExpr): LuaType = when {
        terminal.number != null -> LuaPrimitiveType.NUMBER
        terminal.string != null -> LuaPrimitiveType.STRING
        terminal.text == "true" || terminal.text == "false" -> LuaPrimitiveType.BOOLEAN
        terminal.text == "nil" -> LuaPrimitiveType.NIL
        else -> LuaPrimitiveType.ANY
    }
}

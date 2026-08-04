package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import net.internetisalie.lunar.lang.psi.LuaVar

/**
 * BUG-398: the names a `---@class` is actually written through — the class name plus the local
 * variable each declaration binds it to.
 *
 * The two coincide in the idiomatic `---@class Player` / `local Player = {}`, and every member
 * lookup keyed on the class name alone quietly assumed they always would. LuaCATS libraries
 * routinely separate them — luassert declares `---@class luassert.internal` on `local internal = {}`
 * — and then `function internal.True()` and `internal.are = internal` are written against
 * `internal`, so a class-name-only match found nothing and the class materialized with **no members
 * at all**. Anything inheriting from it inherited nothing.
 *
 * Only the first bound name is taken: the class annotation sits on the declaration as a whole, so a
 * multi-name `local a, b = …` gives no way to say which name carries it, and guessing wider risks
 * pulling an unrelated variable's members in.
 */
internal fun classReceiverNames(className: String, decls: Collection<LuaLocalVarDecl>): Set<String> =
    buildSet {
        add(className)
        decls.forEach { decl -> declaredName(decl)?.let { add(it) } }
    }

/**
 * The first name a `local` declaration binds, read through the AST node.
 *
 * SYNTAX-18: a partially-parsed declaration can lack its `nameRef` while the generated getter is
 * declared `@NotNull`, which logs an error rather than returning null.
 */
internal fun declaredName(decl: LuaLocalVarDecl): String? =
    decl.attNameList.firstOrNull()
        ?.node?.findChildByType(LuaElementTypes.NAME_REF)?.psi?.text

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

    /**
     * [receivers] are the names the class is written through (see [classReceiverNames]); [files] are
     * the files to scan, which the caller derives from wherever the class is declared. Taking both
     * rather than the declarations themselves is what lets BUG-400's un-hosted `---@class` — declared
     * above a bare global assignment, with no `LuaLocalVarDecl` anywhere — use this too.
     */
    fun collect(
        receivers: Set<String>,
        files: Collection<PsiFile>,
        into: MutableMap<String, LuaTypeMember>,
    ) {
        for (file in files) {
            val assignments = PsiTreeUtil.findChildrenOfType(file, LuaAssignmentStatement::class.java)
            for (assignment in assignments) {
                collectFromAssignment(receivers, assignment, into)
            }
        }
    }

    private fun collectFromAssignment(
        receivers: Set<String>,
        assignment: LuaAssignmentStatement,
        into: MutableMap<String, LuaTypeMember>,
    ) {
        val vars = assignment.varList.varList
        val exprs = assignment.exprList.exprList
        vars.forEachIndexed { index, luaVar ->
            val field = fieldNameFor(receivers, luaVar) ?: return@forEachIndexed
            if (into.containsKey(field)) return@forEachIndexed
            val rhs = exprs.getOrNull(index)
            into[field] = LuaTypeMember(field, lightInferType(rhs), sourceElement = luaVar)
        }
    }

    /** Field name iff [luaVar] is a single `base.field` access matching the class context. */
    private fun fieldNameFor(receivers: Set<String>, luaVar: LuaVar): String? {
        val field = singleFieldSuffixName(luaVar) ?: return null
        val base = luaVar.nameRef?.text ?: return null
        return when {
            base in receivers -> field
            base == SELF && isInClassMethod(receivers, luaVar) -> field
            else -> null
        }
    }

    /** The field name for `base.field`, or null for `base[i]`, `base.x.y`, or a bare name. */
    private fun singleFieldSuffixName(luaVar: LuaVar): String? {
        val suffix = luaVar.varSuffixList.singleOrNull() ?: return null
        if (suffix.nameAndArgsList.isNotEmpty()) return null
        return suffix.indexExpr.nameRef?.text
    }

    /** True if [luaVar] sits inside a method `function <receiver>:m()` / `.m()`. */
    private fun isInClassMethod(receivers: Set<String>, luaVar: LuaVar): Boolean {
        val funcDecl = PsiTreeUtil.getParentOfType(luaVar, LuaFuncDecl::class.java) ?: return false
        val funcName = funcDecl.funcName
        if (funcName.nameRef.text !in receivers) return false
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

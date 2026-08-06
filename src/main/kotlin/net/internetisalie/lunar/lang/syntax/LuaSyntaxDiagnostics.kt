package net.internetisalie.lunar.lang.syntax

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaExprStatement
import net.internetisalie.lunar.lang.psi.LuaFuncCall

/**
 * Syntax errors Lunar reports **without** a `PsiErrorElement`.
 *
 * `lua.bnf:123` has `exprStatement ::= expr`, which is deliberately wider than Lua's
 * `stat ::= varlist '=' explist | functioncall | …`. The permissive rule exists so a half-typed line
 * still parses into a usable tree; the narrowing is then enforced here and surfaced by
 * [LuaStandaloneExpressionAnnotator]. That is the ordinary IntelliJ division of labour — parse
 * permissively, diagnose semantically.
 *
 * It also means "the parser produced no error elements" is **not** the same claim as "Lunar considers
 * this valid Lua", and BUG-409 is what happens when the two are conflated: the MAINT-35 parse oracle
 * defined acceptance as `parseErrors == 0`, so `from __future__ import braces` — which Lunar flags
 * with four errors — was scored as accepted, and the oracle invented a disagreement with PUC.
 *
 * The rule lives here, in one place, precisely because two callers must agree about it. Two code
 * paths independently deciding the same question is the shape MAINT-34 exists to clean up after.
 */
object LuaSyntaxDiagnostics {

    /**
     * True when [statement] is an expression statement Lua does not permit.
     *
     * Only a function call may stand alone. `funcCall ::= varOrExp nameAndArgs+` (`lua.bnf:297`) is
     * already exactly Lua's `functioncall`, so this is a type test rather than a re-derivation.
     */
    fun isInvalidStatement(statement: LuaExprStatement): Boolean = statement.expr !is LuaFuncCall

    /** Every statement in [file] that [isInvalidStatement] rejects. */
    fun invalidStatements(file: PsiFile): List<LuaExprStatement> =
        PsiTreeUtil.findChildrenOfType(file, LuaExprStatement::class.java).filter { isInvalidStatement(it) }
}

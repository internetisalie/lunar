package net.internetisalie.lunar.analysis.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import net.internetisalie.lunar.lang.psi.LuaBinOpExpr
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaVisitor
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypes
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * Flags operands of the Lua concatenation operator `..` whose inferred type cannot be
 * concatenated. Lua coerces only `string` and `number` operands; a `table`, `boolean`,
 * `nil`, or `function` operand is a runtime error (INSP-07).
 */
class LuaSuspiciousConcatenationInspection : LocalInspectionTool() {

    override fun getShortName(): String = "LuaSuspiciousConcatenation"

    override fun getGroupDisplayName(): String = "Lua"

    override fun getDisplayName(): String = "Suspicious concatenation"

    override fun isEnabledByDefault(): Boolean = true

    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val types = LuaTypesSnapshot.forFile(holder.file)
        return object : LuaVisitor() {
            override fun visitBinOpExpr(o: LuaBinOpExpr) {
                if (o.binOp.text != "..") return
                checkOperand(o.left, types, holder)
                o.right?.let { checkOperand(it, types, holder) }
            }
        }
    }

    private fun checkOperand(operand: LuaExpr, types: LuaTypes, holder: ProblemsHolder) {
        val graphType = resolveOperandType(operand, types)
        if (isConcatenable(graphType)) return
        holder.registerProblem(
            operand,
            "Suspicious concatenation: operand of type '${graphType.displayName()}' cannot be concatenated",
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        )
    }

    /**
     * Resolves the inferred graph type for a `LuaExpr` operand. The type engine records nodes on
     * `LuaNameRef` and other leaf elements (not on wrapping `LuaPrefixExpr`/`LuaVarOrExp`/`LuaVar`
     * nodes). This helper mirrors the engine's `unwrapExpression` by descending through single-child
     * wrappers until it finds an element with a non-Undefined inferred type.
     */
    private fun resolveOperandType(operand: PsiElement, types: LuaTypes, depth: Int = 0): LuaGraphType {
        if (depth > 10) return LuaGraphType.Undefined
        val direct = types.getValueType(operand)
        if (direct != LuaGraphType.Undefined) return direct
        val children = operand.children.filter { it !is PsiWhiteSpace && it !is PsiComment }
        if (children.size == 1) return resolveOperandType(children[0], types, depth + 1)
        return LuaGraphType.Undefined
    }

    private fun isConcatenable(type: LuaGraphType): Boolean = when (type) {
        LuaGraphType.String, LuaGraphType.Number -> true
        LuaGraphType.Any, LuaGraphType.Undefined -> true
        is LuaGraphType.Generic -> true
        LuaGraphType.Nil, LuaGraphType.Boolean,
        is LuaGraphType.Table, is LuaGraphType.Function,
        is LuaGraphType.Array -> false
        is LuaGraphType.Union -> type.types.any { isConcatenable(it) }
    }
}

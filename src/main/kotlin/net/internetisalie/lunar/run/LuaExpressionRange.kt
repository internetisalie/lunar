package net.internetisalie.lunar.run

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.XDebuggerUtil
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaStatement

/**
 * The debugger "expression under the cursor" range algorithm, extracted so both the MobDebug
 * [LuaDebuggerEvaluator] and the Redis-LDB `LuaLdbEvaluator` reuse a single implementation
 * (REDIS-02 design §2.6 — "reuse the exact algorithm in run/LuaDebuggerEvaluator"). Behavior is
 * identical to the pre-extraction MobDebug evaluator: the widest enclosing [LuaExpr] under the
 * caret is selected, and a non-expression offset yields `null`.
 */
object LuaExpressionRange {

    /** Widest enclosing [LuaExpr] text range at [offset] in [document], or `null` (design §2.6). */
    fun atOffset(project: Project, document: Document, offset: Int, sideEffectsAllowed: Boolean): TextRange? {
        val currentRange = Ref.create<TextRange?>(null)
        PsiDocumentManager.getInstance(project).commitAndRunReadAction {
            try {
                val virtualFile = PsiDocumentManager.getInstance(project)
                    .getPsiFile(document)
                    ?.virtualFile ?: return@commitAndRunReadAction
                val elementAtCursor: PsiElement = XDebuggerUtil.getInstance().findContextElement(
                    virtualFile,
                    offset,
                    project,
                    false,
                ) ?: return@commitAndRunReadAction
                val expression = findExpression(elementAtCursor, sideEffectsAllowed)
                if (expression != null) currentRange.set(expression.getSecond())
            } catch (_: IndexNotReadyException) {
            }
        }
        return currentRange.get()
    }

    private fun findExpression(element: PsiElement?, allowMethodCalls: Boolean): Pair<PsiElement?, TextRange?>? {
        var expression: LuaExpr? = PsiTreeUtil.getParentOfType(element, LuaExpr::class.java)

        while (expression != null && expression.parent is LuaExpr) {
            expression = expression.parent as LuaExpr
        }

        if (expression == null || expression is LuaStatement) return null

        return Pair.create<PsiElement, TextRange>(expression, expression.textRange)
    }
}

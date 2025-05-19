/*
 * Copyright 2011 Jon S Akhtar (Sylvanaar)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package net.internetisalie.lunar.run

import com.intellij.openapi.diagnostic.Logger
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
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaStatement

class LuaDebuggerEvaluator(private val myController: LuaDebuggerController) : XDebuggerEvaluator() {
    override fun evaluate(
        expression: String,
        callback: XEvaluationCallback,
        expressionPosition: XSourcePosition?
    ) {
        log.info("evaluating: '$expression'")
        myController.execute("return $expression")
            .then { xValue: XValue? ->
                if (xValue != null) callback.evaluated(xValue)
                else callback.invalidExpression("Evaluation returned no value")
            }
    }

    override fun evaluate(
        expression: XExpression,
        callback: XEvaluationCallback,
        expressionPosition: XSourcePosition?
    ) {
        if (expression.mode == EvaluationMode.EXPRESSION) {
            myController.execute("return " + expression.expression)
                .then { xValue: XValue? ->
                    if (xValue != null) callback.evaluated(xValue)
                    else callback.invalidExpression("Evaluation returned no value")
                }
        } else {
            myController.execute(expression.expression)
                .then { xValue: XValue? ->
                    if (xValue != null) callback.evaluated(xValue)
                    else callback.invalidExpression("Evaluation returned no value")
                }
        }
    }

    override fun getExpressionRangeAtOffset(
        project: Project,
        document: Document,
        offset: Int,
        sideEffectsAllowed: Boolean
    ): TextRange? {
        val currentRange = Ref.create<TextRange?>(null)
        PsiDocumentManager.getInstance(project).commitAndRunReadAction {
            try {
                val virtualFile = PsiDocumentManager.getInstance(project)
                    .getPsiFile(document)
                    ?.virtualFile ?: return@commitAndRunReadAction
                val elementAtCursor: PsiElement? = XDebuggerUtil.getInstance().findContextElement(
                    virtualFile,
                    offset,
                    project,
                    false
                )
                if (elementAtCursor == null) return@commitAndRunReadAction
                val pair: Pair<PsiElement?, TextRange?>? = findExpression(elementAtCursor, sideEffectsAllowed)
                if (pair != null) {
                    currentRange.set(pair.getSecond())
                }
            } catch (_: IndexNotReadyException) {
            }
        }
        return currentRange.get()
    }

    companion object {
        private val log = Logger.getInstance(LuaDebuggerEvaluator::class.java)

        private fun findExpression(element: PsiElement?, allowMethodCalls: Boolean): Pair<PsiElement?, TextRange?>? {
            var expression: LuaExpr? = PsiTreeUtil.getParentOfType(element, LuaExpr::class.java)

            while (expression != null && expression.parent is LuaExpr) {
                expression = expression.parent as LuaExpr
            }

            if (expression == null || expression is LuaStatement) return null

            return Pair.create<PsiElement, TextRange>(expression, expression.textRange)
        }
    }
}

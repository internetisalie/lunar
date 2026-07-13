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

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator

class LuaDebuggerEvaluator(private val myController: LuaDebuggerController) : XDebuggerEvaluator() {
    override fun evaluate(
        expression: String,
        callback: XEvaluationCallback,
        expressionPosition: XSourcePosition?
    ) {
        myController.launchEvaluate("return $expression", callback)
    }

    override fun evaluate(
        expression: XExpression,
        callback: XEvaluationCallback,
        expressionPosition: XSourcePosition?
    ) {
        if (expression.mode == EvaluationMode.EXPRESSION) {
            myController.launchEvaluate("return " + expression.expression, callback)
        } else {
            myController.launchEvaluate(expression.expression, callback)
        }
    }

    override fun getExpressionRangeAtOffset(
        project: Project,
        document: Document,
        offset: Int,
        sideEffectsAllowed: Boolean
    ): TextRange? = LuaExpressionRange.atOffset(project, document, offset, sideEffectsAllowed)
}

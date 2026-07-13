package net.internetisalie.lunar.redis.debug

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import net.internetisalie.lunar.run.LuaExpressionRange

/**
 * [XDebuggerEvaluator] for a paused LDB frame (design §2.6).
 *
 * Routes `evaluate` to `eval <expr>` through the [LuaLdbEvalHost] seam (the Phase-3 controller),
 * wrapping bare expressions in `return …` exactly as the MobDebug `run/LuaDebuggerEvaluator` does.
 * The "expression under the cursor" range is reused from the shared [LuaExpressionRange] (extracted
 * from `run/LuaDebuggerEvaluator` per design §2.6 — no duplicated algorithm).
 */
class LuaLdbEvaluator(private val evalHost: LuaLdbEvalHost) : XDebuggerEvaluator() {

    override fun evaluate(
        expression: String,
        callback: XEvaluationCallback,
        expressionPosition: XSourcePosition?,
    ) {
        evalHost.launchEvaluate("return $expression", callback)
    }

    override fun evaluate(
        expression: XExpression,
        callback: XEvaluationCallback,
        expressionPosition: XSourcePosition?,
    ) {
        if (expression.mode == EvaluationMode.EXPRESSION) {
            evalHost.launchEvaluate("return ${expression.expression}", callback)
        } else {
            evalHost.launchEvaluate(expression.expression, callback)
        }
    }

    override fun getExpressionRangeAtOffset(
        project: Project,
        document: Document,
        offset: Int,
        sideEffectsAllowed: Boolean,
    ): TextRange? = LuaExpressionRange.atOffset(project, document, offset, sideEffectsAllowed)
}

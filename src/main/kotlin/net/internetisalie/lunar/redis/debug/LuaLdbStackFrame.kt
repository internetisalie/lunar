package net.internetisalie.lunar.redis.debug

import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueGroup

/**
 * The single active LDB frame (design §2.12; LDB exposes one active frame).
 *
 * Holds only the platform-safe [XSourcePosition] and the parsed [LuaLdbLocal] list (no heavy
 * `Project`/`Editor`/`PsiFile`/`VirtualFile` refs — contract §4). [computeChildren] renders the
 * frame locals under a "Locals" group; [getEvaluator] hands out a [LuaLdbEvaluator] bound to the
 * Phase-3 controller via the [LuaLdbEvalHost] seam (design §2.6).
 */
class LuaLdbStackFrame(
    private val position: XSourcePosition?,
    private val evalHost: LuaLdbEvalHost,
    private val locals: List<LuaLdbLocal>,
) : XStackFrame() {

    override fun getSourcePosition(): XSourcePosition? = position

    override fun getEvaluator(): XDebuggerEvaluator = LuaLdbEvaluator(evalHost)

    override fun computeChildren(node: XCompositeNode) {
        val children = XValueChildrenList()
        if (locals.isNotEmpty()) children.addTopGroup(localsGroup())
        node.addChildren(children, true)
    }

    private fun localsGroup(): XValueGroup = object : XValueGroup("Locals") {
        override fun isAutoExpand(): Boolean = true

        override fun computeChildren(node: XCompositeNode) {
            val values = XValueChildrenList(locals.size)
            locals.forEach { local -> values.add(local.name, LuaLdbValue(local)) }
            node.addChildren(values, true)
        }
    }
}

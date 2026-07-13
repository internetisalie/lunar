package net.internetisalie.lunar.redis.debug

import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext

/**
 * The LDB suspend context raised on each pause (design §2.12).
 *
 * Builds a single-frame [LuaLdbExecutionStack] from the paused [position] and the parsed
 * [locals]; the frame's evaluator is bound to the Phase-3 controller via the [LuaLdbEvalHost] seam.
 * Holds only platform-safe / pure-data fields (contract §4). Mirrors `run/LuaSuspendContext`.
 */
class LuaLdbSuspendContext(
    position: XSourcePosition?,
    evalHost: LuaLdbEvalHost,
    locals: List<LuaLdbLocal>,
) : XSuspendContext() {

    private val executionStack = LuaLdbExecutionStack(LuaLdbStackFrame(position, evalHost, locals))

    override fun getActiveExecutionStack(): XExecutionStack = executionStack

    override fun getExecutionStacks(): Array<XExecutionStack?> = arrayOf(executionStack)
}

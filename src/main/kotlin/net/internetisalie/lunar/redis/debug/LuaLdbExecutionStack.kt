package net.internetisalie.lunar.redis.debug

import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import net.internetisalie.lunar.LuaBundle

/**
 * The LDB execution stack (design §2.12).
 *
 * LDB exposes a single active frame, so this stack holds exactly [topFrame] and yields it from
 * [computeStackFrames]. Mirrors `run/LuaExecutionStack` structurally.
 */
class LuaLdbExecutionStack(
    private val topFrame: LuaLdbStackFrame,
) : XExecutionStack(LuaBundle.message("debug.stack.thread.main")) {

    override fun getTopFrame(): XStackFrame = topFrame

    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
        container.addStackFrames(listOf(topFrame), true)
    }
}

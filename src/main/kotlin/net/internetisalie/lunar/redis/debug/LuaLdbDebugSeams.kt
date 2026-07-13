package net.internetisalie.lunar.redis.debug

import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator

/**
 * Narrow Phase-2 collaborator seams for the LDB XDebugger scaffolding (design §2.3, §2.4, §2.6).
 *
 * The design routes the structural classes ([LuaLdbEvaluator], [LuaLdbStackFrame],
 * [LuaLdbBreakpointHandler]) at the Phase-3 `LuaLdbController` / `LuaRedisDebugProcess`. Those live
 * session classes do not exist yet (Phase 3 owns transport + controller + lifecycle), so Phase 2
 * depends on these two minimal interfaces instead of the concrete controller. The Phase-3 controller
 * and debug process will implement them, keeping the structural classes free of any transport /
 * live-session logic while still delegating exactly as the design specifies.
 */

/** Evaluate seam: the `evaluate` bridge the LDB frame's [XDebuggerEvaluator] delegates to (design §2.6). */
interface LuaLdbEvalHost {
    /** Evaluate [expression] in the paused frame; the result flows back through [callback] (design §3.7). */
    fun launchEvaluate(expression: String, callback: XDebuggerEvaluator.XEvaluationCallback)
}

/** Breakpoint seam: register/unregister an LDB line breakpoint (design §2.4). */
interface LuaLdbBreakpointRegistrar {
    /** Register [breakpoint] with the running session (armed/paused) (design §2.4, §3.5). */
    fun addBreakpoint(breakpoint: XBreakpoint<*>)

    /** Unregister [breakpoint] from the running session (design §2.4). */
    fun removeBreakpoint(breakpoint: XBreakpoint<*>)
}

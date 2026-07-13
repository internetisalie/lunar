package net.internetisalie.lunar.redis.debug

import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint

/**
 * Registers/unregisters LDB line breakpoints of type [LuaLdbBreakpointType] (design §2.4).
 *
 * Mirrors `run/LuaLineBreakpointHandler`; delegates to a [LuaLdbBreakpointRegistrar] (the Phase-3
 * `LuaRedisDebugProcess`) so the handler carries no live-session logic.
 */
class LuaLdbBreakpointHandler(
    private val registrar: LuaLdbBreakpointRegistrar,
) : XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<*>>>(
    LuaLdbBreakpointType::class.java,
) {
    override fun registerBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>) {
        registrar.addBreakpoint(breakpoint)
    }

    override fun unregisterBreakpoint(
        breakpoint: XLineBreakpoint<XBreakpointProperties<*>>,
        temporary: Boolean,
    ) {
        registrar.removeBreakpoint(breakpoint)
    }
}

package net.internetisalie.lunar.run

import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint

class LuaLineBreakpointHandler(
    private val myDebugProcess: LuaDebugProcess
) : XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<*>>>(
    LuaLineBreakpointType::class.java,
) {
    override fun registerBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>) {
        myDebugProcess.addBreakPoint(breakpoint)
    }

    override fun unregisterBreakpoint(
        breakpoint: XLineBreakpoint<XBreakpointProperties<*>>,
        temporary: Boolean
    ) {
        myDebugProcess.removeBreakPoint(breakpoint)
    }
}

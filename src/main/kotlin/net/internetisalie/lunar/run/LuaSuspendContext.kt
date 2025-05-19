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

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext

class LuaSuspendContext: XSuspendContext {
    private val controller: LuaDebuggerController
    private val project : Project
    private val stack: LuaRemoteStack
    private val breakpoint : XBreakpoint<*>?
    private val position: XSourcePosition?
    private val executionStack: LuaExecutionStack

    constructor(project : Project, controller: LuaDebuggerController, breakpoint: XBreakpoint<*>?, stack: LuaRemoteStack) {
        this.project = project
        this.controller = controller
        this.breakpoint = breakpoint
        this.position = breakpoint?.sourcePosition
        this.stack = stack
        this.executionStack = initExecutionStack()
    }

    constructor(project: Project, controller: LuaDebuggerController, position: XSourcePosition?, stack: LuaRemoteStack) {
        this.project= project
        this.controller = controller
        this.breakpoint = null
        this.position = position
        this.stack = stack
        this.executionStack = initExecutionStack()
    }

    override fun getActiveExecutionStack(): XExecutionStack {
        return executionStack
    }

    private fun initExecutionStack(): LuaExecutionStack {
        val frame = LuaStackFrame(project, controller, position, 0)
        return LuaExecutionStack(project, controller, frame, stack)
    }

    override fun getExecutionStacks(): Array<XExecutionStack?> {
        return arrayOf(executionStack)
    }
}

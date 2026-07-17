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
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import net.internetisalie.lunar.LuaBundle

class LuaExecutionStack(
    private val project: Project,
    private val controller: LuaDebuggerController,
    private val topFrame: LuaStackFrame?,
    private val stack: LuaRemoteStack
) : XExecutionStack(LuaBundle.message("debug.stack.thread.main")) {

    override fun getTopFrame(): XStackFrame? {
        return topFrame
    }

    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
        val entries = stack.entries.drop(firstFrameIndex)
        val frames = entries.map {
            if (it.frame.file == "=[C]") LuaStackFrame(
                project,
                controller,
                null,
                it.frame.index
            )
            else LuaStackFrame(
                project,
                controller,
                LuaPosition.createLocalPosition(it.frame.virtualFile, it.frame.line),
                it.frame.name,
                it.frame.index,
                it,
            )
        }

        container.addStackFrames(frames, true)
    }
}

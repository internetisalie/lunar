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

import com.intellij.execution.ExecutionResult
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.internetisalie.lunar.util.LunarCoroutineScopeService

class LuaDebugProcess(
    session: XDebugSession,
    val executionResult: ExecutionResult,
) : XDebugProcess(session) {
    private val sessionScope =
        LunarCoroutineScopeService.getInstance(session.project).scope.childScope("LuaDebugSession")
    private val controller: LuaDebuggerController = LuaDebuggerController(session, sessionScope)
    private val lineBreakpointHandler: LuaLineBreakpointHandler? = LuaLineBreakpointHandler(this)
    private var myClosing = false
    private lateinit var myExecutionConsole: ConsoleView

    override fun getEditorsProvider(): XDebuggerEditorsProvider {
        return LuaDebuggerEditorsProvider()
    }

    override fun startStepOver(context: XSuspendContext?) {
        sessionScope.launch { controller.stepOver() }
    }

    override fun startStepInto(context: XSuspendContext?) {
        sessionScope.launch { controller.stepInto() }
    }

    override fun startStepOut(context: XSuspendContext?) {
        sessionScope.launch { controller.stepOut() }
    }

    override fun stop() {
        log.warn("stopping")
        myClosing = true
        controller.terminate()
        executionResult.processHandler?.destroyProcess()
    }

    override fun resume(context: XSuspendContext?) {
        sessionScope.launch { controller.resume() }
    }

    override fun runToPosition(position: XSourcePosition, context: XSuspendContext?) {
        throw AbstractMethodError()
    }

    override fun doGetProcessHandler(): ProcessHandler? {
        return executionResult.processHandler
    }

    override fun createConsole(): ExecutionConsole {
        myExecutionConsole = executionResult.executionConsole as ConsoleView
        controller.setConsole(myExecutionConsole)
        return myExecutionConsole
    }

    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>?> {
        return arrayOf(lineBreakpointHandler)
    }

    override fun sessionInitialized() {
        super.sessionInitialized()

        val processHandler = executionResult.processHandler
        processHandler?.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                log.info("processTerminated event received (exitCode=${event.exitCode})")
                myClosing = true
                controller.terminated()
            }
        })

        sessionScope.launch {
            try {
                withBackgroundProgress(session.project, "Connecting to debugger") {
                    log.info("connecting")
                    controller.connect()
                    log.info("connected")
                    session.rebuildViews()
                    drainInstalledBreakpoints()
                    controller.resume()
                    log.info("connection running")
                }
            } catch (e: Exception) {
                log.error("Failed to connect to debugger", e)
                executionResult.processHandler?.destroyProcess()
                if (!myClosing) {
                    withContext(Dispatchers.EDT) {
                        Messages.showErrorDialog(
                            "Unable to establish connection with debugger:\n${e.message}",
                            "Connecting to Debugger",
                        )
                    }
                }
            }
        }
    }

    private val installedBreaks: MutableList<XBreakpoint<*>> = ArrayList()

    override fun getEvaluator(): XDebuggerEvaluator? {
        return session.currentStackFrame?.evaluator
    }

    /** Drain breakpoints registered before the connection was ready; called inside the connect coroutine. */
    private suspend fun drainInstalledBreakpoints() {
        val pending: List<XBreakpoint<*>>
        synchronized(installedBreaks) {
            pending = installedBreaks.toList()
            installedBreaks.clear()
        }
        for (b in pending) controller.addBreakPoint(b)
    }

    fun addBreakPoint(pos: XBreakpoint<*>) {
        log.info("add breakpoint $pos")
        if (controller.isReady) {
            sessionScope.launch { controller.addBreakPoint(pos) }
        } else {
            synchronized(installedBreaks) { installedBreaks.add(pos) }
        }
    }

    fun removeBreakPoint(pos: XBreakpoint<*>) {
        log.info("remove breakpoint $pos")
        sessionScope.launch { controller.removeBreakPoint(pos) }
    }

    companion object {
        private val log = Logger.getInstance(LuaDebugProcess::class.java)
    }
}

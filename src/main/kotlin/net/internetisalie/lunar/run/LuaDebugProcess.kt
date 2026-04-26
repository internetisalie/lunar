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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.ui.Messages
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XSuspendContext
import javax.swing.SwingUtilities

class LuaDebugProcess(
    session: XDebugSession,
    val executionResult: ExecutionResult,
) : XDebugProcess(session) {
    private val controller: LuaDebuggerController = LuaDebuggerController(session)
    private val lineBreakpointHandler: LuaLineBreakpointHandler? = LuaLineBreakpointHandler(this)
    private var myClosing = false
    private lateinit var myExecutionConsole: ConsoleView

    override fun getEditorsProvider(): XDebuggerEditorsProvider {
        return LuaDebuggerEditorsProvider()
    }

    override fun startStepOver(context: XSuspendContext?) {
        controller.stepOver()
    }

    override fun startStepInto(context: XSuspendContext?) {
        controller.stepInto()
    }

    override fun startStepOut(context: XSuspendContext?) {
        controller.stepOut()
    }

    override fun stop() {
        log.warn("stopping")
        myClosing = true
        executionResult.processHandler?.destroyProcess()
    }

    override fun resume(context: XSuspendContext?) {
        controller.resume()
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

        ProgressManager.getInstance().run(object : Backgroundable(null, "Connecting to debugger", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.setText("Connecting to debugger...")
                log.info("connecting")
                try {
                    controller.waitForConnect()

                    log.info("connected")
                    indicator.setText("... Debugger connected")

                    session.rebuildViews()

                    registerBreakpoints()

                    controller.resume()

                    log.info("connection running")
                } catch (e: Exception) {
                    log.error("Failed to connect to debugger", e)

                    if (executionResult.processHandler != null)
                        executionResult.processHandler.destroyProcess()

                    if (!myClosing) SwingUtilities.invokeLater {
                        Messages.showErrorDialog(
                            (StringBuilder()).append("Unable to establish connection with debugger:\n")
                                .append(e.message).toString(), "Connecting to Debugger"
                        )
                    }
                }
            }
        })
    }


    var installedBreaks: MutableList<XBreakpoint<*>> = ArrayList()

    override fun getEvaluator(): XDebuggerEvaluator? {
        return session.currentStackFrame?.evaluator
    }

    @Synchronized
    private fun registerBreakpoints() {
        log.info("registering pending breakpoints")

        for (b in installedBreaks) {
            while (!controller.isReady) {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    e.printStackTrace() //To change body of catch statement use File | Settings | File Templates.
                    return
                }
            }

            controller.addBreakPoint(b)
        }

        installedBreaks.clear()
    }

    @Synchronized
    fun addBreakPoint(pos: XBreakpoint<*>) {
        log.info("add breakpoint $pos")
        if (controller.isReady) controller.addBreakPoint(pos)
        else installedBreaks.add(pos)
    }

    @Synchronized
    fun removeBreakPoint(pos: XBreakpoint<*>) {
        log.info("remove breakpoint $pos")
        // if (controller.isReady())
        controller.removeBreakPoint(pos)
    }

    companion object {
        private val log = Logger.getInstance(LuaDebugProcess::class.java)
    }
}

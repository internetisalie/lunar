package net.internetisalie.lunar.redis.debug

import com.intellij.execution.ExecutionResult
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.MessageType
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.launch
import net.internetisalie.lunar.redis.run.LuaRedisRunConfiguration
import net.internetisalie.lunar.run.LuaDebuggerEditorsProvider
import net.internetisalie.lunar.util.LunarCoroutineScopeService

/**
 * The XDebugger bridge for a Redis LDB debug session (design §2.2).
 *
 * Maps step/resume/stop onto [LuaLdbController]; disables Step Out (LDB has no step-out verb); owns a
 * session `childScope` (from [LunarCoroutineScopeService]) so every action dispatches off the EDT. The
 * controller implements the Phase-2 [LuaLdbBreakpointRegistrar] seam, so breakpoint registration
 * delegates straight through. Reuses the MobDebug [LuaDebuggerEditorsProvider] for expression editors.
 */
class LuaRedisDebugProcess(
    session: XDebugSession,
    private val executionResult: ExecutionResult,
    config: LuaRedisRunConfiguration,
) : XDebugProcess(session) {

    private val sessionScope =
        LunarCoroutineScopeService.getInstance(session.project).scope.childScope("RedisLdbSession")
    private val controller = LuaLdbController(session, sessionScope, config)
    private val breakpointHandler = LuaLdbBreakpointHandler(controller)
    private var stepOutReported = false

    override fun getEditorsProvider(): XDebuggerEditorsProvider = LuaDebuggerEditorsProvider()

    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>?> = arrayOf(breakpointHandler)

    override fun getEvaluator(): XDebuggerEvaluator? = session.currentStackFrame?.evaluator

    override fun startStepInto(context: XSuspendContext?) {
        sessionScope.launch { controller.step() }
    }

    override fun startStepOver(context: XSuspendContext?) {
        sessionScope.launch { controller.next() }
    }

    override fun startStepOut(context: XSuspendContext?) {
        if (!stepOutReported) {
            stepOutReported = true
            session.reportMessage(STEP_OUT_UNSUPPORTED, MessageType.INFO)
        }
    }

    override fun resume(context: XSuspendContext?) {
        sessionScope.launch { controller.continueRun() }
    }

    override fun stop() {
        sessionScope.launch { controller.abort() }
    }

    override fun runToPosition(position: XSourcePosition, context: XSuspendContext?) {
        throw UnsupportedOperationException("Run to position is not supported by the Redis Lua debugger")
    }

    override fun doGetProcessHandler(): ProcessHandler? = executionResult.processHandler

    /**
     * Returns the run console and mounts the mid-pause "Redis" tab once the debugger UI is ready
     * (design §2.7). The tab is a [LuaLdbRedisConsoleTab] (reusing the REDIS-01 reply-tree console),
     * added via the session's [com.intellij.execution.ui.RunnerLayoutUi] — a programmatic path, so no
     * plugin.xml EP is needed. The tab disposes with the process.
     */
    override fun createConsole(): ExecutionConsole {
        session.runWhenUiReady { ui -> mountRedisTab(ui) }
        return executionResult.executionConsole
    }

    private fun mountRedisTab(ui: RunnerLayoutUi) {
        val tab = LuaLdbRedisConsoleTab(session.project, controller, sessionScope)
        val content = ui.createContent(REDIS_TAB_ID, tab.component, REDIS_TAB_TITLE, null, null)
        content.isCloseable = false
        content.setDisposer(tab)
        ui.addContent(content)
    }

    override fun sessionInitialized() {
        super.sessionInitialized()
        sessionScope.launch {
            try {
                controller.connect()
            } catch (failure: Throwable) {
                log.info("Redis debug connect failed: ${failure.message}")
            }
        }
    }

    companion object {
        const val STEP_OUT_UNSUPPORTED = "Step Out is not supported by the Redis Lua debugger"

        private const val REDIS_TAB_ID = "RedisLdbConsole"
        private const val REDIS_TAB_TITLE = "Redis"

        private val log = logger<LuaRedisDebugProcess>()
    }
}

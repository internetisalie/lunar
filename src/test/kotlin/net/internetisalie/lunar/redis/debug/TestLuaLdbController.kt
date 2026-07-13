package net.internetisalie.lunar.redis.debug

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.internetisalie.lunar.redis.resp.RespException
import net.internetisalie.lunar.redis.resp.RespValue
import net.internetisalie.lunar.redis.run.LuaRedisRunConfiguration
import net.internetisalie.lunar.redis.run.LuaRedisRunConfigurationType
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Fake-transport coverage of [LuaLdbController] (design §3.5, §3.6).
 *
 * Drives the controller with a scripted [LdbIo] (no live socket, contract §5): a session progresses
 * HANDSHAKE→ARMED→PAUSED→RUNNING→TERMINATED, and the error paths call `reportError`/`errorOccurred`.
 * The [XDebugSession] is a `java.lang.reflect.Proxy` recording only the callbacks under test — robust to
 * unrelated platform signature changes. A light platform fixture supplies the [Project] and a real
 * script file (so `readScriptBody` resolves).
 */
class TestLuaLdbController : BasePlatformTestCase() {

    /** A scripted LDB transport: each call pops the next reply, or raises a queued failure. */
    private class FakeTransport(
        private val enterReply: RespValue,
        private val evalReply: RespValue,
        private val sendReplies: ArrayDeque<Any>,
    ) : LdbIo {
        val sentCommands = mutableListOf<LdbCommand>()

        override suspend fun enterDebug(mode: LuaRedisDebugMode): RespValue = enterReply

        override suspend fun eval(scriptBody: String, keys: List<String>, argv: List<String>): RespValue = evalReply

        override suspend fun send(command: LdbCommand): RespValue {
            sentCommands.add(command)
            val next = sendReplies.removeFirstOrNull() ?: return RespValue.Simple("OK")
            if (next is Throwable) throw next
            return next as RespValue
        }
    }

    private class SessionRecorder {
        var breakpointReached = 0
        var positionReached = 0
        val errors = mutableListOf<String>()
        val messages = mutableListOf<String>()
    }

    private fun fakeSession(project: Project, recorder: SessionRecorder): XDebugSession {
        val handler = InvocationHandler { _, method: Method, args ->
            when (method.name) {
                "getProject" -> project
                "breakpointReached" -> { recorder.breakpointReached++; null }
                "positionReached" -> { recorder.positionReached++; null }
                "reportError" -> { recorder.errors.add(args?.get(0) as? String ?: ""); null }
                "reportMessage" -> { recorder.messages.add(args?.get(0) as? String ?: ""); null }
                "getCurrentStackFrame" -> null
                "setPauseActionSupported" -> null
                else -> defaultFor(method)
            }
        }
        return Proxy.newProxyInstance(
            XDebugSession::class.java.classLoader,
            arrayOf(XDebugSession::class.java),
            handler,
        ) as XDebugSession
    }

    private fun defaultFor(method: Method): Any? = when (method.returnType) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        else -> null
    }

    private fun newConfig(): LuaRedisRunConfiguration {
        val type = LuaRedisRunConfigurationType.getInstance()
        val factory = type.configurationFactories[0]
        val config = factory.createTemplateConfiguration(project) as LuaRedisRunConfiguration
        config.scriptPath = scriptFile().absolutePath
        return config
    }

    /**
     * A real on-disk `.lua` file refreshed into the local VFS — the controller resolves the script via
     * a `file://` URL (as production does, `LuaRedisRunProfileState.scriptFileUrl`), so a `temp://`
     * fixture file would not resolve.
     */
    private fun scriptFile(): File {
        val file = File.createTempFile("debug_script", ".lua")
        file.writeText("local x = 1\nreturn x\n")
        file.deleteOnExit()
        LocalFileSystem.getInstance().refreshAndFindFileByPath(file.absolutePath)
        return file
    }

    /** A fake conditional line breakpoint at [line] (0-based) carrying [condition] (or none if null). */
    private fun fakeBreakpoint(line: Int, condition: String?): XBreakpoint<*> {
        val position = Proxy.newProxyInstance(
            XSourcePosition::class.java.classLoader,
            arrayOf(XSourcePosition::class.java),
        ) { _, method: Method, _ -> if (method.name == "getLine") line else defaultFor(method) } as XSourcePosition
        val expression = condition?.let {
            Proxy.newProxyInstance(
                XExpression::class.java.classLoader,
                arrayOf(XExpression::class.java),
            ) { _, method: Method, _ -> if (method.name == "getExpression") it else defaultFor(method) } as XExpression
        }
        return Proxy.newProxyInstance(
            XBreakpoint::class.java.classLoader,
            arrayOf(XBreakpoint::class.java),
        ) { _, method: Method, _ ->
            when (method.name) {
                "getSourcePosition" -> position
                "getConditionExpression" -> expression
                else -> defaultFor(method)
            }
        } as XBreakpoint<*>
    }

    /** A single-scalar eval reply block (`<value> <text>`) for the condition gate. */
    private fun evalScalarBlock(text: String): RespValue = RespValue.Array(
        listOf(RespValue.Simple("<value> $text")),
    )

    private fun stopBlock(line: Int, reason: String): RespValue = RespValue.Array(
        listOf(
            RespValue.Simple("* Stopped at $line, stop reason = $reason"),
            RespValue.Simple("$line   local x = 1"),
        ),
    )

    private fun sessionEndedBlock(): RespValue = RespValue.Array(
        listOf(RespValue.Simple("* Lua debugging session ended")),
    )

    private fun printBlock(): RespValue = RespValue.Array(
        listOf(RespValue.Simple("<value> x = 1")),
    )

    private fun controllerWith(transport: LdbIo, recorder: SessionRecorder): Pair<LuaLdbController, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob())
        val session = fakeSession(project, recorder)
        val controller = LuaLdbController.forTest(session, scope, newConfig()) { transport }
        return controller to scope
    }

    /** The happy path: HANDSHAKE→ARMED→PAUSED (first stop), then step→pause, then continue→TERMINATED. */
    fun testSessionLifecycleReachesTerminated() {
        val recorder = SessionRecorder()
        val replies = ArrayDeque<Any>(
            listOf(
                printBlock(),          // readLocals on first pause
                stopBlock(2, "step"),  // reply to Step
                printBlock(),          // readLocals on step pause
                sessionEndedBlock(),   // reply to Continue → session end
            ),
        )
        val transport = FakeTransport(RespValue.Simple("OK"), stopBlock(1, "breakpoint"), replies)
        val (controller, scope) = controllerWith(transport, recorder)
        try {
            runBlocking {
                controller.connect()
                assertTrue("session should be armed after connect", controller.isArmed)
                controller.step()
                controller.continueRun()
            }
            assertEquals("two pauses expected (first stop + step)", 2, recorder.positionReached)
            assertFalse("session should be torn down after continue→end", controller.isArmed)
            assertTrue(transport.sentCommands.any { it is LdbCommand.Step })
            assertTrue(transport.sentCommands.any { it is LdbCommand.Continue })
        } finally {
            scope.cancel()
        }
    }

    /** TC-LDB-COND-1 (false): a conditional breakpoint whose condition is `false` auto-resumes, no pause. */
    fun testConditionalBreakpointFalseResumes() {
        val recorder = SessionRecorder()
        val replies = ArrayDeque<Any>(
            listOf(
                RespValue.Simple("OK"),        // reply to Break(1) during drain
                evalScalarBlock("false"),      // reply to Eval(condition) → not holds
                sessionEndedBlock(),           // reply to the auto Continue → session end
            ),
        )
        val transport = FakeTransport(RespValue.Simple("OK"), stopBlock(1, "breakpoint"), replies)
        val (controller, scope) = controllerWith(transport, recorder)
        try {
            runBlocking {
                controller.addBreakpoint(fakeBreakpoint(0, "x > 5"))
                controller.connect()
            }
            assertEquals("false condition must not surface a pause", 0, recorder.breakpointReached)
            assertEquals("false condition must not surface a position pause", 0, recorder.positionReached)
            assertTrue("controller must auto-issue Continue", transport.sentCommands.any { it is LdbCommand.Continue })
            assertFalse("session ends after the auto-resume", controller.isArmed)
        } finally {
            scope.cancel()
        }
    }

    /** TC-LDB-COND-1 (true): a conditional breakpoint whose condition is truthy raises `breakpointReached`. */
    fun testConditionalBreakpointTruePauses() {
        val recorder = SessionRecorder()
        val replies = ArrayDeque<Any>(
            listOf(
                RespValue.Simple("OK"),  // reply to Break(1) during drain
                evalScalarBlock("6"),    // reply to Eval(condition) → holds
                printBlock(),            // reply to Print (readLocals) on pause
            ),
        )
        val transport = FakeTransport(RespValue.Simple("OK"), stopBlock(1, "breakpoint"), replies)
        val (controller, scope) = controllerWith(transport, recorder)
        try {
            runBlocking {
                controller.addBreakpoint(fakeBreakpoint(0, "x > 5"))
                controller.connect()
            }
            assertEquals("truthy condition must surface a breakpoint pause", 1, recorder.breakpointReached)
            assertFalse("truthy condition must not auto-Continue", transport.sentCommands.any { it is LdbCommand.Continue })
        } finally {
            scope.cancel()
        }
    }

    /** TC-LDB-ERR-1: an in-band eval error routes to `callback.errorOccurred`, never `evaluated`. */
    fun testEvaluateEvalFailureCallsErrorOccurred() {
        val recorder = SessionRecorder()
        val evalError = RespValue.Error("ERR", "Error running script: attempt to call a nil value")
        val replies = ArrayDeque<Any>(
            listOf(
                printBlock(),  // readLocals on first pause
                evalError,     // reply to the eval command
            ),
        )
        val transport = FakeTransport(RespValue.Simple("OK"), stopBlock(1, "breakpoint"), replies)
        val (controller, scope) = controllerWith(transport, recorder)
        val callback = RecordingCallback()
        try {
            runBlocking {
                controller.connect()
                controller.evaluateForTest("return x", callback)
            }
            assertNull("evaluated must not be called", callback.value)
            assertNotNull("errorOccurred must be called", callback.error)
            assertTrue(callback.error.orEmpty().contains("attempt to call a nil value"))
        } finally {
            scope.cancel()
        }
    }

    /** TC-LDB-ERR-2: a mid-session transport IO failure calls `session.reportError` and stops cleanly. */
    fun testTransportIoFailureCallsReportError() {
        val recorder = SessionRecorder()
        val replies = ArrayDeque<Any>(
            listOf(
                printBlock(),                       // readLocals on first pause
                RespException.Io(java.io.IOException("connection reset")), // reply to Step → connection loss
            ),
        )
        val transport = FakeTransport(RespValue.Simple("OK"), stopBlock(1, "breakpoint"), replies)
        val (controller, scope) = controllerWith(transport, recorder)
        try {
            runBlocking {
                controller.connect()
                controller.step()
            }
            assertTrue("reportError must be called on connection loss", recorder.errors.isNotEmpty())
            assertFalse("session must not remain armed after IO failure", controller.isArmed)
        } finally {
            scope.cancel()
        }
    }

    /** TC-LDB-STEPOUT-1: the Step-Out affordance carries the exact unsupported tooltip (LDB has no step-out). */
    fun testStepOutTooltipText() {
        assertEquals(
            "Step Out is not supported by the Redis Lua debugger",
            LuaRedisDebugProcess.STEP_OUT_UNSUPPORTED,
        )
    }

    private class RecordingCallback : XDebuggerEvaluator.XEvaluationCallback {
        var value: XValue? = null
        var error: String? = null

        override fun evaluated(result: XValue) {
            value = result
        }

        override fun errorOccurred(errorMessage: String) {
            error = errorMessage
        }
    }
}

package net.internetisalie.lunar.run

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import javax.swing.Icon
import javax.swing.event.HyperlinkListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Coverage of [LuaDebuggerEvaluator.getExpressionRangeAtOffset]: the widest enclosing [LuaExpr]
 * under the cursor is selected (full index/binary chain, not a leaf), and a non-expression
 * offset yields `null` (MAINT-13-04). Uses a hand-rolled [XDebugSession] fake — no mocking
 * framework is on the test classpath — since the evaluator never touches the controller here.
 */
class TestLuaDebuggerEvaluator : BaseDocumentTest() {

    /** Returns the text of the selected expression range at [marker], or null if none. */
    private fun rangeAt(text: String, marker: String): String? {
        val psiFile = myFixture.configureByText(LuaFileType, text)
        val project = myFixture.project
        val offset = text.indexOf(marker)
        // getExpressionRangeAtOffset never touches the controller/scope, so a throwaway scope suffices.
        val evaluator = LuaDebuggerEvaluator(LuaDebuggerController(fakeSession(project), CoroutineScope(SupervisorJob())))
        return runInEdtAndGet {
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
            val range = evaluator.getExpressionRangeAtOffset(project, document, offset, false)
            range?.let { document.getText(it) }
        }
    }

    /** TC 14: caret in the leaf `c` selects the whole `a.b.c` index chain. */
    @Test
    fun testSelectsWholeIndexChain() {
        assertEquals("a.b.c", rangeAt("x = a.b.c", "c"))
    }

    /** TC 15: caret on `1` selects the whole `1 + 2` binary expression. */
    @Test
    fun testSelectsWholeBinaryExpr() {
        assertEquals("1 + 2", rangeAt("x = 1 + 2", "1"))
    }

    /** TC 16: caret on the `local` keyword is not inside an expression → null. */
    @Test
    fun testKeywordOffsetReturnsNull() {
        assertNull(rangeAt("local y = 1", "local"))
    }

    private fun fakeSession(project: Project): XDebugSession = object : XDebugSession {
        override fun getProject(): Project = project
        override fun getRunProfile(): RunProfile? = null
        override fun setPauseActionSupported(isSupported: Boolean) {}

        override fun getDebugProcess(): XDebugProcess = TODO("not used")
        override fun isSuspended(): Boolean = TODO("not used")
        override fun getCurrentStackFrame(): XStackFrame? = TODO("not used")
        override fun getSuspendContext(): XSuspendContext? = TODO("not used")
        override fun getCurrentPosition(): XSourcePosition? = TODO("not used")
        override fun getTopFramePosition(): XSourcePosition? = TODO("not used")
        override fun stepOver(ignoreBreakpoints: Boolean) = TODO("not used")
        override fun stepInto() = TODO("not used")
        override fun stepOut() = TODO("not used")
        override fun forceStepInto() = TODO("not used")
        override fun runToPosition(position: XSourcePosition, ignoreBreakpoints: Boolean) = TODO("not used")
        override fun pause() = TODO("not used")
        override fun resume() = TODO("not used")
        override fun showExecutionPoint() = TODO("not used")
        override fun setCurrentStackFrame(
            executionStack: XExecutionStack,
            frame: XStackFrame,
            isTopFrame: Boolean,
        ) = TODO("not used")

        override fun updateBreakpointPresentation(
            breakpoint: XLineBreakpoint<*>,
            icon: Icon?,
            errorMessage: String?,
        ) = TODO("not used")

        override fun setBreakpointVerified(breakpoint: XLineBreakpoint<*>) = TODO("not used")
        override fun setBreakpointInvalid(breakpoint: XLineBreakpoint<*>, errorMessage: String?) = TODO("not used")
        override fun breakpointReached(
            breakpoint: XBreakpoint<*>,
            evaluatedLogExpression: String?,
            suspendContext: XSuspendContext,
        ): Boolean = TODO("not used")

        override fun positionReached(suspendContext: XSuspendContext) = TODO("not used")
        override fun sessionResumed() = TODO("not used")
        override fun stop() = TODO("not used")
        override fun setBreakpointMuted(muted: Boolean) = TODO("not used")
        override fun areBreakpointsMuted(): Boolean = TODO("not used")
        override fun addSessionListener(listener: XDebugSessionListener, parentDisposable: Disposable) =
            TODO("not used")

        override fun addSessionListener(listener: XDebugSessionListener) = TODO("not used")
        override fun removeSessionListener(listener: XDebugSessionListener) = TODO("not used")
        override fun reportMessage(message: String, type: MessageType, listener: HyperlinkListener?) =
            TODO("not used")

        override fun getSessionName(): String = TODO("not used")

        @Deprecated("Do not use.")
        override fun getRunContentDescriptor(): RunContentDescriptor = TODO("not used")

        override fun rebuildViews() = TODO("not used")
        override fun <V : XSmartStepIntoVariant> smartStepInto(handler: XSmartStepIntoHandler<V>, variant: V) =
            TODO("not used")

        @Deprecated("Deprecated in Java")
        override fun updateExecutionPosition() = TODO("not used")

        override fun initBreakpoints() = TODO("not used")
        override fun getConsoleView(): ConsoleView = TODO("not used")
        override fun getUI(): RunnerLayoutUi? = TODO("not used")
        override fun isMixedMode(): Boolean = TODO("not used")
        override fun getExecutionEnvironment() = TODO("not used")
        override fun isStopped(): Boolean = TODO("not used")
        override fun isPaused(): Boolean = TODO("not used")
    }
}

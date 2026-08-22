package net.internetisalie.lunar.run

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XNavigatable
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestLuaDebugVariable : BaseDocumentTest() {
    @Test
    fun testDebugVariableCreation() {
        val value = LuaDebugValue("number", "42", null)
        val variable = LuaDebugVariable("myVar", value, true)

        assertNotNull(variable)
        assertEquals("myVar", variable.name)
    }

    @Test
    fun testDebugVariableWithProject() {
        val value = LuaDebugValue("number", "42", null)
        val variable = LuaDebugVariable("x", value, true, myFixture.project)

        assertNotNull(variable)
        assertEquals("x", variable.name)
    }

    @Test
    fun testComputeSourcePositionNullProjectFallsBackToSuper() {
        val value = LuaDebugValue("number", "42", null)
        val variable = LuaDebugVariable("x", value, true)

        val recorded = mutableListOf<XSourcePosition?>()
        val navigatable = XNavigatable { position -> recorded.add(position) }

        variable.computeSourcePosition(navigatable)

        // super (XValue.computeSourcePosition) records exactly one call with a null position.
        assertEquals(1, recorded.size)
        assertNull(recorded.single())
    }

    @Test
    fun testDebugVariableWithLocalFlag() {
        val value = LuaDebugValue("string", "\"hello\"", null)
        val localVar = LuaDebugVariable("local_var", value, true)
        val globalVar = LuaDebugVariable("global_var", value, false)

        assertNotNull(localVar)
        assertNotNull(globalVar)
        assertEquals("local_var", localVar.name)
        assertEquals("global_var", globalVar.name)
    }

    @Test
    fun testDebugVariableWithTable() {
        myFixture.configureByText(LuaFileType, "{a = 1, b = 2}")
        runReadAction {
            val element = PsiTreeUtil.findChildOfType(myFixture.file, LuaTableConstructor::class.java)
            assertNotNull(element)
            val luaValue = LuaValue(element)
            val tableDebugValue = LuaDebugValue(luaValue, null, null)

            val variable = LuaDebugVariable("tableVar", tableDebugValue, true)

            assertNotNull(variable)
            assertEquals("tableVar", variable.name)
        }
    }

    @Test
    fun testDebugVariableMultipleVariables() {
        val var1 = LuaDebugValue("number", "1", null)
        val var2 = LuaDebugValue("string", "\"text\"", null)
        val var3 = LuaDebugValue("boolean", "true", null)

        val debug1 = LuaDebugVariable("x", var1, true)
        val debug2 = LuaDebugVariable("y", var2, true)
        val debug3 = LuaDebugVariable("z", var3, true)

        assertNotNull(debug1)
        assertNotNull(debug2)
        assertNotNull(debug3)
        assertEquals("x", debug1.name)
        assertEquals("y", debug2.name)
        assertEquals("z", debug3.name)
    }

    /**
     * BUG-414: `computeSourcePosition` is a platform callback invoked off the EDT with no guaranteed
     * read lock, and it walks PSI. Driven through [LuaDebugVariable.navigateFrom], the seam that
     * stands in for the live debug session this test cannot install.
     *
     * The control assertion is not decoration. Under [BaseDocumentTest] the test thread already
     * permits read access, so a same-thread version of this test would pass identically with and
     * without the read action — a green asserting nothing. The control proves the walking thread
     * holds no read lock at the moment the walk is made.
     */
    @Test
    fun testComputeSourcePositionWalksPsiUnderAReadAction() {
        myFixture.configureByText(LuaFileType, "local target = 1\nprint(target)\n")

        val targetProject = myFixture.project
        val pausedAt: XSourcePosition? =
            ApplicationManager.getApplication().runReadAction<XSourcePosition?> {
                XDebuggerUtil.getInstance().createPosition(myFixture.file.virtualFile, 1)
            }
        assertNotNull(pausedAt)

        val variable = LuaDebugVariable("target", LuaDebugValue("number", "1", null), true, targetProject)
        val recorded = mutableListOf<XSourcePosition?>()
        val navigatable = XNavigatable { position -> recorded.add(position) }

        val readAccessOnWalker = AtomicBoolean(true)
        val failure = AtomicReference<Throwable?>(null)

        val walker =
            Thread {
                readAccessOnWalker.set(ApplicationManager.getApplication().isReadAccessAllowed)
                runCatching { variable.navigateFrom(targetProject, pausedAt, navigatable) }
                    .onFailure { thrown -> failure.set(thrown) }
            }

        walker.start()
        walker.join(WALKER_TIMEOUT_MS)

        assertFalse(readAccessOnWalker.get(), "control: the walking thread must hold no read lock")
        assertNull(failure.get(), "the PSI walk threw off the EDT: ${failure.get()}")
        assertEquals(1, recorded.size)
        assertEquals(0, recorded.single()?.line, "navigates to `local target` on line 0")
    }

    private companion object {
        const val WALKER_TIMEOUT_MS = 10_000L
    }
}

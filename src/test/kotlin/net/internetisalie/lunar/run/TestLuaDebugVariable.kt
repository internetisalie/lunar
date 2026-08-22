package net.internetisalie.lunar.run

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.frame.XValueChildrenList
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon
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

    /**
     * BUG-447: nothing overrode `getEvaluationExpression`, so the platform's `addToWatches` — which
     * silently discards a null expression — had nothing to add. These assert the expression each
     * variable contributes; the Add to Watches surface itself is the VNC gate, not a unit test.
     */
    @Test
    fun testWatchExpressionForATopLevelLocal() {
        val variable = LuaDebugVariable("count", LuaDebugValue("number", "1", null), true)

        assertEquals("count", variable.evaluationExpression)
    }

    @Test
    fun testWatchExpressionForAStringKeyedChild() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val root = tableVariable("cfg", "do local _={name=\"lunar\"};return _;end")
            val child = childrenOf(root).single { it.name == "name" }

            assertEquals("cfg[\"name\"]", child.evaluationExpression)
        }
    }

    /** The case a naive restoration still gets wrong: `isIndex` was hardcoded `false` at the only site that sets it. */
    @Test
    fun testWatchExpressionForANumericKeyedChild() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val root = tableVariable("items", "do local _={10, 20};return _;end")
            val child = childrenOf(root).single { it.name == "[1]" }

            assertEquals("items[1]", child.evaluationExpression)
        }
    }

    @Test
    fun testWatchExpressionRecursesThroughNestedTables() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val root = tableVariable("a", "do local _={b={c=1}};return _;end")
            val nested = childrenOf(root).single { it.name == "b" }
            val leaf = childrenOf(nested).single { it.name == "c" }

            assertEquals("a[\"b\"][\"c\"]", leaf.evaluationExpression)
        }
    }

    /** A key with no Lua literal form yields no expression — better no watch than one that evaluates elsewhere. */
    @Test
    fun testWatchExpressionIsAbsentForAKeyWithNoLiteralForm() {
        val table = LuaTable()
        table.named[LuaValue(kind = LuaValueKind.Function)] = LuaValue.newNumber(1.0)
        val root = LuaDebugVariable("t", LuaDebugValue(LuaValue.newTable(table), null, null), true)

        val child = childrenOf(root).single()

        assertEquals("[function]", child.name)
        assertNull(child.evaluationExpression)
    }

    /** A quote inside a key must not break out of the generated string literal. */
    @Test
    fun testWatchExpressionEscapesAQuotedKey() {
        val table = LuaTable()
        table.named[LuaValue.newString("a\"b")] = LuaValue.newNumber(1.0)
        val root = LuaDebugVariable("t", LuaDebugValue(LuaValue.newTable(table), null, null), true)

        val child = childrenOf(root).single()

        assertEquals("t[\"a\\\"b\"]", child.evaluationExpression)
    }

    private fun tableVariable(
        name: String,
        chunk: String,
    ): LuaDebugVariable {
        val table = LuaDebugValueParser.parseChunk(myFixture.project, chunk)
        return LuaDebugVariable(name, LuaDebugValue(LuaValue.newTable(table), null, null), true)
    }

    private fun childrenOf(variable: LuaDebugVariable): List<LuaDebugVariable> {
        val node = CapturingNode()
        variable.computeChildren(node)
        val captured = node.captured ?: return emptyList()
        return (0 until captured.size()).map { captured.getValue(it) as LuaDebugVariable }
    }

    private class CapturingNode : XCompositeNode {
        var captured: XValueChildrenList? = null

        override fun addChildren(
            children: XValueChildrenList,
            last: Boolean,
        ) {
            captured = children
        }

        override fun tooManyChildren(remaining: Int) {}

        override fun setAlreadySorted(alreadySorted: Boolean) {}

        override fun setErrorMessage(errorMessage: String) {}

        override fun setErrorMessage(
            errorMessage: String,
            link: XDebuggerTreeNodeHyperlink?,
        ) {}

        override fun setMessage(
            message: String,
            icon: Icon?,
            attributes: SimpleTextAttributes,
            link: XDebuggerTreeNodeHyperlink?,
        ) {}
    }

    private companion object {
        const val WALKER_TIMEOUT_MS = 10_000L
    }
}

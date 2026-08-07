package net.internetisalie.lunar.lang.types

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph

/**
 * BUG-417. A type error spanning the whole file has no user-actionable location — and the platform
 * hides lower-severity infos under an ERROR range, so ONE file-wide type error silently buried every
 * other inspection's results for the file. Measured on the zerobrane corpus member: `filetree.lua`
 * carried five errors anchored at range 0–43946 (the entire file, via `fromLuaType`'s synthetic
 * nodes anchored at `graph.firstNodeElement()`), and all 126 of its undeclared-variable warnings
 * vanished; corpus-wide, `LuaUndeclaredVariable` read 843 with the type inspection enabled versus
 * 1 954 without it.
 *
 * The graph must therefore (a) re-anchor a failure to the value element when the use element is
 * file-wide, and (b) refuse the error entirely when both are — which is what makes an inspection's
 * results independent of which other inspections ran.
 */
class LuaTypeErrorAnchoringTest : BasePlatformTestCase() {
    fun testFileWideUseReanchorsToTheValueElement() {
        val file = myFixture.configureByText("t.lua", "local narrow = 1\n")
        val narrow = PsiTreeUtil.findChildOfType(file, LuaNameRef::class.java)!!

        val graph = LuaTypeGraph()
        val variable = graph.variable(file)
        graph.addEdge(graph.value(narrow, LuaGraphType.Table()), variable)
        graph.addEdge(variable, graph.use(file, LuaGraphType.Number))
        graph.checkTypes()

        assertEquals("the mismatch must still be reported", 1, graph.errors.size)
        assertEquals("…anchored at the narrow value element, not the file", narrow, graph.errors.single().element)
    }

    fun testFullySyntheticFailureIsDroppedNotFileWide() {
        val file = myFixture.configureByText("t.lua", "local narrow = 1\n")

        val graph = LuaTypeGraph()
        val variable = graph.variable(file)
        graph.addEdge(graph.value(file, LuaGraphType.Table()), variable)
        graph.addEdge(variable, graph.use(file, LuaGraphType.Number))
        graph.checkTypes()

        assertTrue(
            "a failure with no real anchor must be dropped, got: ${graph.errors.map { it.message }}",
            graph.errors.isEmpty(),
        )
    }

    /** An ordinary narrow-anchored error is untouched — the net must not swallow real diagnostics. */
    fun testNarrowlyAnchoredErrorsAreUnaffected() {
        val file = myFixture.configureByText("t.lua", "local narrow = 1\n")
        val narrow = PsiTreeUtil.findChildOfType(file, LuaNameRef::class.java)!!

        val graph = LuaTypeGraph()
        val variable = graph.variable(narrow)
        graph.addEdge(graph.value(narrow, LuaGraphType.Table()), variable)
        graph.addEdge(variable, graph.use(narrow, LuaGraphType.Number))
        graph.checkTypes()

        assertEquals(1, graph.errors.size)
        assertEquals(narrow, graph.errors.single().element)
    }
}

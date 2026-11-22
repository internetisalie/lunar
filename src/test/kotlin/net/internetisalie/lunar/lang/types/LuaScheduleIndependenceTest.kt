package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-114: a file's findings and its final closure are a function of the program, not of the order
 * the checker visits its variables in. Each fixture is built twice, the second time with the fixed
 * point visiting variables last-created first, and the sorted `(severity, anchor, message)` rows and
 * the closure relation are compared.
 *
 * `method` and `nilsum` differ between the two schedules when findings are made while the pairs are
 * checked: the first reports three extra `fun(n) | fun(n)` hypotheses under the reversed schedule,
 * the second loses four findings.
 */
@RunWith(JUnit4::class)
class LuaScheduleIndependenceTest : BasePlatformTestCase() {
    private class Built(
        val rows: List<String>,
        val closure: String,
    )

    private fun build(
        file: PsiFile,
        reversed: Boolean,
    ): Built {
        LuaTypeGraph.scheduleReversed = reversed
        try {
            val snapshot = LuaTypesVisitor.buildSnapshot(file) as LuaTypesSnapshot
            val rows =
                snapshot.getErrors().map {
                    val range = it.element.textRange
                    "${it.severity}|${range.startOffset}-${range.endOffset}|${it.message}"
                }
            return Built(rows.sorted(), snapshot.closureDigest())
        } finally {
            LuaTypeGraph.scheduleReversed = false
        }
    }

    private fun assertIndependent(
        name: String,
        text: String,
    ) {
        val file = myFixture.configureByText("$name.lua", text)
        LuaTypesSnapshot.forFile(file)
        val creationOrder = build(file, reversed = false)
        val reversedOrder = build(file, reversed = true)
        assertEquals("$name: findings under the reversed schedule", creationOrder.rows, reversedOrder.rows)
        assertEquals("$name: closure under the reversed schedule", creationOrder.closure, reversedOrder.closure)
    }

    @Test
    fun testMethodCallsOnAClassAreReportedWhateverTheSchedule() =
        assertIndependent(
            "method",
            "---@class P61\nlocal P = {}\n---@param n number\nfunction P:set(n) self.n = n end\n" +
                "local p = P\np:set(1)\nlocal q = p\nq:set('s')\n",
        )

    @Test
    fun testNilAndDeclaredDemandFindingsDoNotDependOnTheSchedule() =
        assertIndependent(
            "nilsum",
            "---@param n number\nlocal function count(n) end\nlocal d = \"s\"\nlocal e = wx.thing\ne = d\ncount(e)\n" +
                "local x = nil\ncount(x)\nlocal y = nil\nif c then y = 1 end\ncount(y)\nlocal s = nil .. 'x'\n",
        )

    @Test
    fun testHigherOrderFunctionsKeepOneClosure() =
        assertIndependent(
            "hof",
            "---@param f fun(a: number): number\n---@return fun(a: number): number\n" +
                "local function wrap(f) return function(a) return f(a) end end\n" +
                "local k = wrap(function(a) return a * 2 end)\nlocal m = wrap(k)\nprint(m(1))\n",
        )

    @Test
    fun testCallbackAndChainFixturesKeepOneClosure() {
        assertIndependent(
            "callback2",
            "---@param cb fun(x: string): number\nlocal function f(cb) return cb('a') end\n" +
                "local r = f(function(x) return #x end)\nprint(r + 1)\n",
        )
        assertIndependent(
            "chain",
            "local function id(v) return v end\nlocal t = {}\nt.a = id(t)\nt.b = id(t.a)\n" +
                "local u = id(t.b)\nprint(u.a, u.b)\n",
        )
    }

    @Test
    fun testAFileHandleMethodCallKeepsOneClosure() =
        assertIndependent(
            "seek",
            "local zh = io.open('f', 'rb')\nif not zh then return end\nlocal a, b = zh:seek('set', 3)\n",
        )
}

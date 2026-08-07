package net.internetisalie.lunar.lang.syntax

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * BUG-409. The syntax errors Lunar reports **without** a `PsiErrorElement`.
 *
 * Each case asserts both numbers, because the whole defect was that they differ: the MAINT-35 oracle
 * read only the first and concluded Lunar had accepted input it in fact rejects with visible errors.
 */
class LuaSyntaxDiagnosticsTest : BasePlatformTestCase() {
    private fun counts(source: String): Pair<Int, Int> {
        myFixture.configureByText("t.lua", source)
        return Pair(
            PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java).size,
            LuaSyntaxDiagnostics.invalidStatements(myFixture.file).size,
        )
    }

    /** The recorded witness: `luacheck/spec/samples/python_code.lua` is literally this line. */
    fun testBug409WitnessIsRejected() {
        assertEquals(Pair(0, 4), counts("from __future__ import braces\n"))
    }

    /** Not only bare names — `exprStatement ::= expr` admits any expression. */
    fun testAnyNonCallExpressionIsRejected() {
        assertEquals("a lone number", Pair(0, 1), counts("9\n"))
        assertEquals("a bare name sequence", Pair(0, 4), counts("a b c d\n"))
        assertEquals("a string", Pair(0, 1), counts("\"hello\"\n"))
        assertEquals("an index chain that is not a call", Pair(0, 1), counts("a.b.c\n"))
    }

    /** Only a function call may stand alone, and every call shape must be allowed. */
    fun testCallsAreAccepted() {
        assertEquals("plain call", Pair(0, 0), counts("print(1)\n"))
        assertEquals("method call", Pair(0, 0), counts("obj:method()\n"))
        assertEquals("string-argument call", Pair(0, 0), counts("require \"m\"\n"))
        assertEquals("table-argument call", Pair(0, 0), counts("f{1, 2}\n"))
        assertEquals("chained call", Pair(0, 0), counts("a.b.c(1)\n"))
    }

    /** Ordinary Lua must report nothing, or the oracle would gain false rejects instead. */
    fun testValidLuaIsClean() {
        assertEquals(Pair(0, 0), counts("local x = 1\nprint(x)\n"))
        assertEquals(Pair(0, 0), counts("local t = {}\nfunction t.f() return 1 end\nt.f()\n"))
        assertEquals(Pair(0, 0), counts("for i = 1, 10 do print(i) end\n"))
    }
}

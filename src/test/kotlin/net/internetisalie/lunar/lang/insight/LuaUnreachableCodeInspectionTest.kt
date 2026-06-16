package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.inspections.LuaUnreachableCodeInspection
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Real-flow tests for INSP-04. Note: in Lua a `return`/`break` must be the *last* statement of its
 * block, so `return x` immediately followed by more code is a *parse error*, not unreachable code.
 * Genuine, syntactically-valid unreachable code therefore arises when control abrupts inside a nested
 * block — `do return end` / `if … then return else return end` / a `goto` that skips a statement — and
 * code follows at the enclosing level. These inputs exercise exactly those shapes.
 */
@RunWith(JUnit4::class)
class LuaUnreachableCodeInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaUnreachableCodeInspection())
    }

    private fun unreachable(text: String): List<HighlightInfo> {
        myFixture.configureByText("test.lua", text)
        return myFixture.doHighlighting().filter { it.description == "Unreachable code" }
    }

    private fun highlightedText(text: String, info: HighlightInfo): String =
        text.substring(info.startOffset, info.endOffset)

    @Test
    fun testCodeAfterReturnFlagged() {
        val text = """
            function test()
                do return 1 end
                print("unreachable")
            end
        """.trimIndent()
        val warnings = unreachable(text)
        assertEquals("Expected one warning, found: ${warnings.map { highlightedText(text, it) }}", 1, warnings.size)
        assertEquals("print(\"unreachable\")", highlightedText(text, warnings.single()))
    }

    @Test
    fun testFileLevelUnreachableFlagged() {
        // checkFile path (owner = LuaFile): top-level dead code after a do-return.
        val text = """
            do return end
            print("unreachable")
        """.trimIndent()
        val warnings = unreachable(text)
        assertEquals("Expected one file-level warning, found: ${warnings.map { highlightedText(text, it) }}", 1, warnings.size)
        assertEquals("print(\"unreachable\")", highlightedText(text, warnings.single()))
    }

    @Test
    fun testReachableAfterBreakNotFlagged() {
        val warnings = unreachable(
            """
            function test()
                while true do break end
                print("reachable")
            end
            """.trimIndent(),
        )
        assertTrue("Expected no warnings, found: $warnings", warnings.isEmpty())
    }

    @Test
    fun testDeadBranchAfterIfElseFlagged() {
        val text = """
            function test(x)
                if x then return 1 else return 2 end
                print("unreachable")
            end
        """.trimIndent()
        val warnings = unreachable(text)
        assertEquals(1, warnings.size)
        assertEquals("print(\"unreachable\")", highlightedText(text, warnings.single()))
    }

    @Test
    fun testSingleHeadOfDeadRun() {
        val text = """
            function test()
                do return end
                print("a")
                print("b")
                print("c")
            end
        """.trimIndent()
        val warnings = unreachable(text)
        assertEquals("Only the head of the dead run should be flagged, found: ${warnings.map { highlightedText(text, it) }}", 1, warnings.size)
        assertEquals("print(\"a\")", highlightedText(text, warnings.single()))
    }

    @Test
    fun testGotoLabelKeepsTargetReachable() {
        val text = """
            goto target
            print("skipped")
            ::target::
            print("reached")
        """.trimIndent()
        val warnings = unreachable(text)
        assertEquals("Only the statement between goto and label is dead, found: ${warnings.map { highlightedText(text, it) }}", 1, warnings.size)
        assertEquals("print(\"skipped\")", highlightedText(text, warnings.single()))
    }

    @Test
    fun testRemoveUnreachableQuickFix() {
        myFixture.configureByText(
            "test.lua",
            """
            function test()
                do return 1 end
                print(<caret>"unreachable")
            end
            """.trimIndent(),
        )
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Remove unreachable code")
        myFixture.launchAction(intention)
        val result = myFixture.file.text
        assertFalse("Unreachable statement should be deleted: $result", result.contains("unreachable"))
        assertTrue(
            "Reachable code and structure should survive: $result",
            result.contains("return 1") && result.contains("function test()") && result.trimEnd().endsWith("end"),
        )
    }

    @Test
    fun testErrorCallNotTreatedAsTerminator() {
        // v1 scope: error() is an ordinary call node in the shipped CFG (no visitFuncCall override),
        // so code after it is reported reachable. Flagging it is INSP-04-C1 / DR-1 future work.
        val warnings = unreachable(
            """
            function test()
                error("boom")
                print("after error")
            end
            """.trimIndent(),
        )
        assertTrue("error() must not abrupt flow in v1, found: $warnings", warnings.isEmpty())
    }

    @Test
    fun testNestedDeadFunctionFlaggedOnceAtOwner() {
        val text = """
            local function outer()
                do return end
                local function inner()
                    print("inner dead too")
                end
            end
        """.trimIndent()
        val warnings = unreachable(text)
        assertEquals("Dead nested function flagged once at its head, found: ${warnings.map { highlightedText(text, it) }}", 1, warnings.size)
        assertTrue(
            "Head should be the inner-function declaration statement, was: '${highlightedText(text, warnings.single())}'",
            highlightedText(text, warnings.single()).startsWith("local function inner()"),
        )
    }

    @Test
    fun testDeadCompoundLoopHighlightsWholeStatement() {
        val text = """
            local function f()
                do return end
                for i = 1, 10 do
                    print(i)
                end
            end
        """.trimIndent()
        val warnings = unreachable(text)
        assertEquals(1, warnings.size)
        val expected = "for i = 1, 10 do\n        print(i)\n    end"
        assertEquals(
            "The whole for-statement should be greyed, was: '${highlightedText(text, warnings.single())}'",
            expected,
            highlightedText(text, warnings.single()),
        )
    }
}

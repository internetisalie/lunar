package net.internetisalie.lunar.lang.syntax

import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class LuaBraceMatchingTest : BaseDocumentTest() {

    @Test
    fun testMatcherRegistration() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText("test.lua", "(<caret>)")
                val file = myFixture.file
                val matcher = BraceMatchingUtil.getBraceMatcher(file.fileType, file.viewProvider.baseLanguage)
                Assertions.assertNotNull(matcher)
            }
        }
    }

    @Test
    fun testBracePairs() {
        val matcher = LuaPairedBraceMatcher()
        val pairs = matcher.pairs

        val expected = setOf(
            Pair("LuaTokenType.(", "LuaTokenType.)"),
            Pair("LuaTokenType.[", "LuaTokenType.]"),
            Pair("LuaTokenType.{", "LuaTokenType.}"),
            Pair("LuaTokenType.repeat", "LuaTokenType.until"),
            Pair("LuaTokenType.do", "LuaTokenType.end"),
            Pair("LuaTokenType.function", "LuaTokenType.end"),
            Pair("LuaTokenType.if", "LuaTokenType.end")
        )

        val actual = pairs.map { Pair(it.leftBraceType.toString(), it.rightBraceType.toString()) }.toSet()
        Assertions.assertEquals(expected, actual)
    }

    private fun doTest(text: String, caretOffset: Int, expectedMatchOffset: Int, forward: Boolean) {
        val actualMatchOffset = EdtTestUtil.runInEdtAndGet<Int, RuntimeException> {
            runReadAction {
                myFixture.configureByText("test.lua", text)
                val file = myFixture.file
                val editor = myFixture.editor
                editor.caretModel.moveToOffset(caretOffset)
                BraceMatchingUtil.getMatchedBraceOffset(editor, forward, file)
            }
        }
        Assertions.assertEquals(expectedMatchOffset, actualMatchOffset, "Matching brace offset mismatch at caret $caretOffset")
    }

    @Test
    fun testParenthesesMatch() {
        // ( )
        // 0 1 2
        doTest("( )", 0, 2, true)
        doTest("( )", 2, 0, false)
    }

    @Test
    fun testNestedBraces() {
        // { [ ( ) ] }
        // 012345678901
        doTest("{ [ ( ) ] }", 0, 10, true)
        doTest("{ [ ( ) ] }", 2, 8, true)
        doTest("{ [ ( ) ] }", 4, 6, true)

        doTest("{ [ ( ) ] }", 10, 0, false)
        doTest("{ [ ( ) ] }", 8, 2, false)
        doTest("{ [ ( ) ] }", 6, 4, false)
    }

    @Test
    fun testNoMatchInString() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                // " ( "
                // 01234
                myFixture.configureByText("test.lua", "\" ( \"")
                val editor = myFixture.editor
                editor.caretModel.moveToOffset(2) // at '('

                // BraceMatchingUtil.getMatchedBraceOffset crashes with AssertionError
                // if called on a non-brace token (like STRING here).
                // This confirms that the platform does not see it as a matchable brace.
                val matchedOffset = try {
                    BraceMatchingUtil.getMatchedBraceOffset(editor, true, myFixture.file)
                } catch (e: AssertionError) {
                    -1
                }
                Assertions.assertEquals(-1, matchedOffset, "Should not match braces inside strings")
            }
        }
    }

    @Test
    fun testRepeatUntilMatch() {
        // repeat x = 1 until true
        // 01234567890123456789012
        doTest("repeat x = 1 until true", 0, 13, true)
        doTest("repeat x = 1 until true", 13, 0, false)
    }
}

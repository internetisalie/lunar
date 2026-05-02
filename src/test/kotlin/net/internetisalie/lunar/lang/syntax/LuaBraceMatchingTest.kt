package net.internetisalie.lunar.lang.syntax

import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class LuaBraceMatchingTest : BaseDocumentTest() {

    @Test
    fun testMatcherRegistration() {
        myFixture.configureByText("test.lua", "(<caret>)")
        val file = myFixture.file
        val matcher = BraceMatchingUtil.getBraceMatcher(file.fileType, file.viewProvider.baseLanguage)
        println("Matcher class: ${matcher?.javaClass?.name}")
        Assertions.assertTrue(matcher is LuaPairedBraceMatcher)
    }

    @Test
    fun testBracePairs() {
        val matcher = LuaPairedBraceMatcher()
        val pairs = matcher.pairs

        val expected = setOf(
            Pair("LPAREN", "RPAREN"),
            Pair("LBRACK", "RBRACK"),
            Pair("LCURLY", "RCURLY")
        )

        val actual = pairs.map { Pair(it.leftBraceType.toString(), it.rightBraceType.toString()) }.toSet()
        Assertions.assertEquals(expected, actual)
    }

    private fun doTest(text: String, caretOffset: Int, expectedMatchOffset: Int, forward: Boolean) {
        myFixture.configureByText("test.lua", text)
        val file = myFixture.file
        val editor = myFixture.editor
        editor.caretModel.moveToOffset(caretOffset)
        val actualMatchOffset = BraceMatchingUtil.getMatchedBraceOffset(editor, forward, file)
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
        // " ( "
        // 01234
        myFixture.configureByText("test.lua", "\" ( \"")
        val offset = BraceMatchingUtil.getMatchedBraceOffset(myFixture.editor, true, myFixture.file)
        Assertions.assertEquals(-1, offset, "Should not match braces inside strings")
    }

    @Test
    fun testRepeatUntilMatch() {
        // repeat ... until
        // 012345678901234567
        myFixture.configureByText("test.lua", "repeat x = 1 until true")
        val editor = myFixture.editor

        // Test jumping forward from repeat
        editor.caretModel.moveToOffset(0)
        val untilOffset = BraceMatchingUtil.getMatchedBraceOffset(editor, true, myFixture.file)
        Assertions.assertEquals(13, untilOffset, "repeat should match until")

        // Test jumping backward from until
        editor.caretModel.moveToOffset(13)
        val repeatOffset = BraceMatchingUtil.getMatchedBraceOffset(editor, false, myFixture.file)
        Assertions.assertEquals(0, repeatOffset, "until should match repeat")
    }

    @Test
    fun testIfThenEndMatch() {
        // if true then print(1) end
        // 012345678901234567890123456
        myFixture.configureByText("test.lua", "if true then print(1) end")
        val editor = myFixture.editor

        editor.caretModel.moveToOffset(0) // at 'if'
        val endOffset = BraceMatchingUtil.getMatchedBraceOffset(editor, true, myFixture.file)
        Assertions.assertEquals(22, endOffset, "if should match end")
    }
}


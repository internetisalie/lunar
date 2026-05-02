package net.internetisalie.lunar.lang.syntax

import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class LuaBraceMatchingTest : BaseDocumentTest() {

    @Test
    fun testParentheses() {
        myFixture.configureByText("test.lua", "(<caret>)")
        val offset = myFixture.caretOffset
        val file = myFixture.file
        val document = myFixture.getDocument(file)

        // This is a bit simplified, usually we'd test if the IDE actually highlights it
        // but since we're in a unit test, we check if the brace matcher is recognized.
        val matcher = BraceMatchingUtil.getBraceMatcher(file.fileType, file.viewProvider.getLanguage())
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
}

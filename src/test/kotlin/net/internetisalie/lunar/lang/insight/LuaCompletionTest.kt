package net.internetisalie.lunar.lang.insight

import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LuaCompletionTest : BaseDocumentTest() {

    private fun doTest(text: String, vararg expected: String) {
        configureByText(text)
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings
        assertNotNull(strings, "Completion lookup should not be null")
        for (s in expected) {
            assertTrue(strings.contains(s), "Completion should contain '$s'. Found: $strings")
        }
    }

    private fun doNotContainTest(text: String, vararg unexpected: String) {
        configureByText(text)
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings ?: return
        for (s in unexpected) {
            assertTrue(!strings.contains(s), "Completion should NOT contain '$s'. Found: $strings")
        }
    }

    @Test
    fun `test basic keywords at statement start`() {
        doTest("<caret>", "if", "while", "function", "local", "for", "repeat", "return", "do")
    }

    @Test
    fun `test then after if`() {
        doTest("if true <caret>", "then")
    }

    @Test
    fun `test no then inside if block`() {
        doNotContainTest("if true then <caret>", "then")
    }

    @Test
    fun `test else after then block`() {
        doTest("if true then print(1) <caret>", "else", "elseif", "end")
    }

    @Test
    fun `test in after for name list`() {
        doTest("for k, v <caret>", "in")
    }

    @Test
    fun `test do after for identifier equals`() {
        doTest("for i = 1, 10 <caret>", "do")
    }

    @Test
    fun `test no do after for k v in`() {
        doNotContainTest("for k, v <caret>", "do")
    }
}

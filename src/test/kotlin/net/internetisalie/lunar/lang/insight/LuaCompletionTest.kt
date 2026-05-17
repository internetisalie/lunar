package net.internetisalie.lunar.lang.insight

import net.internetisalie.lunar.IndexedDocumentTest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LuaCompletionTest : IndexedDocumentTest() {

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

    // COMP-02: Symbol Completion Tests

    @Test
    fun `test local variable completion`() {
        doTest("local test_var = 10\n<caret>", "test_var")
    }

    @Test
    fun `test parameter completion`() {
        doTest("function test_func(param1)\n  <caret>\nend", "param1")
    }

    @Test
    fun `test function name completion`() {
        doTest("local function helper()\nend\n<caret>", "helper")
    }

    @Test
    fun `test for loop variable completion`() {
        doTest("for i = 1, 10 do\n  <caret>\nend", "i")
    }

    @Test
    fun `test generic for loop variable completion`() {
        doTest("for k, v in pairs(t) do\n  <caret>\nend", "k", "v")
    }

    @Test
    fun `test shadowing - inner scope takes precedence`() {
        configureByText("local x = 1\nif true then\n  local x = 2\n  <caret>\nend")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings ?: return
        assertTrue(strings.contains("x"), "Completion should contain 'x'")
    }

    @Test
    fun `test completion after identifier prefix`() {
        configureByText("local test_var = 10\ntes<caret>")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings ?: return
        assertTrue(strings.contains("test_var"), "Completion should contain 'test_var'")
    }

    @Test
    fun `test no completion after dot (member completion reserved for COMP-04)`() {
        configureByText("local t = {}\nt.<caret>")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings
        assertNotNull(strings, "Completion lookup should not be null")
        assertTrue(strings.isEmpty() || !strings.contains("field"), "Dot completion should not suggest variables")
    }

    @Test
    fun `test local variable in function body`() {
        configureByText("function test()\n  local x = 1\n  <caret>\nend")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings ?: return
        assertTrue(strings.contains("x"), "Completion should contain 'x'")
    }

    @Test
    fun `test parameter in function body`() {
        configureByText("function test(param)\n  <caret>\nend")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings ?: return
        assertTrue(strings.contains("param"), "Completion should contain 'param'")
    }

    @Test
    fun `test no keywords when typing symbol prefix`() {
        configureByText("local test_var = 10\ntes<caret>")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings ?: return
        // Should contain the symbol
        assertTrue(strings.contains("test_var"), "Completion should contain 'test_var'")
        // Should NOT contain keywords when there's a prefix
        assertTrue(!strings.contains("if"), "Completion should NOT contain 'if' when typing a prefix")
        assertTrue(!strings.contains("while"), "Completion should NOT contain 'while' when typing a prefix")
    }

    @Test
    fun `test global variable completion`() {
        configureByText("global_var = 20\n\ndo\n    <caret>\nend")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings ?: return
        assertTrue(strings.contains("global_var"), "Completion should contain 'global_var'")
    }
}

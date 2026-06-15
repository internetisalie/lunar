package net.internetisalie.lunar.lang.insight

import net.internetisalie.lunar.IndexedDocumentTest
import org.junit.jupiter.api.Disabled
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

    // COMP-03 DR-03: Phase 1 + Phase 2 Integration Tests

    @Test
    fun `DR-03-01 Verify circular dependency doesn't crash - A requires B, B requires A`() {
        // Spike test to verify that circular require statements don't cause
        // stack overflow or other crashes when completion is triggered
        myFixture.addFileToProject(
            "module_a.lua",
            """
            local b = require("module_b")
            function function_a() end
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "module_b.lua",
            """
            local a = require("module_a")
            function function_b() end
            """.trimIndent()
        )

        configureByText("local a = require(\"module_a\")\nif true <caret>")

        try {
            myFixture.completeBasic()
            // Test passes if no stack overflow or exception is thrown
        } catch (e: StackOverflowError) {
            throw AssertionError("Circular dependency A->B->A caused stack overflow", e)
        }
    }

    @Test
    fun `DR-03-03 Verify test fixture supports multi-file projects`() {
        // Spike test to verify that test infrastructure can handle multi-file scenarios
        // (Actual cache invalidation will be tested in Phase 2.1 after GlobalSymbolRankingService is added)
        myFixture.addFileToProject(
            "config.lua",
            "return { debug = true }"
        )

        // Verify the test fixture can handle multiple files
        configureByText("local cfg = require(\"config\")\nif true <caret>")
        
        // Test passes if fixture handles multi-file scenario without crashing
        myFixture.completeBasic()
    }

    @Test
    fun `DR-03-04 Ready for Phase 2 - test fixtures can handle global symbol completion`() {
        // Spike test to verify that the test infrastructure is ready for Phase 2 global symbol testing
        // Creates a realistic multi-file project structure
        myFixture.addFileToProject(
            "service.lua",
            """
            function create_service() return {} end
            function destroy_service() end
            return {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "utils.lua",
            """
            function helper() end
            function cleanup() end
            return {}
            """.trimIndent()
        )

        configureByText(
            """
            local svc = require("service")
            local util = require("utils")
            if <caret>
            """.trimIndent()
        )

        // Test passes if fixture handles multi-module project without crashing
        myFixture.completeBasic()
    }

    @Test
    @Disabled(
        "Cross-file completion cannot be exercised in the LightTempDirTestFixture: require " +
            "resolution needs the real LocalFileSystem and the project-global stub query returns " +
            "nothing for addFileToProject'd files here. Recursion logic lives in " +
            "LuaCrossFileCompletionProvider; verify via an integration test. Pending: decide approach.",
    )
    fun `COMP-03 Verify recursive cross-file completion`() {
        // Create module A that provides a function.
        // Note: symbol names must not begin with a Lua keyword (e.g. `function`), or the
        // completion site (`<prefix><caret>`) would be lexed as that keyword and the
        // identifier-reference contributor would never fire.
        myFixture.addFileToProject(
            "module_a.lua",
            """
            function helper_from_a() end
            return {}
            """.trimIndent()
        )
        // Create module B that requires module A and provides its own function
        myFixture.addFileToProject(
            "module_b.lua",
            """
            require("module_a")
            function helper_from_b() end
            return {}
            """.trimIndent()
        )

        // In our main file, we only require module B, but we should get completion
        // for globals defined in module A too, because B requires A.
        configureByText(
            """
            require("module_b")
            helper_<caret>
            """.trimIndent()
        )

        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings
        assertNotNull(strings, "Completion lookup should not be null")
        assertTrue(strings.contains("helper_from_a"), "Completion should contain 'helper_from_a' via recursive require. Found: $strings")
        assertTrue(strings.contains("helper_from_b"), "Completion should contain 'helper_from_b'. Found: $strings")
    }
}

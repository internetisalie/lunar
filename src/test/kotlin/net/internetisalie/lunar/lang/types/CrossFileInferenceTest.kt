package net.internetisalie.lunar.lang.types

import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for cross-file type resolution and module inference.
 *
 * This test suite validates type resolution across multiple files:
 * - Module require() and type inference from module return types
 * - Multi-file type checking with external dependencies
 * - Inlay hints for inferred types across files
 * - Early return handling in modules
 *
 * **Component**: Type Inference Engine - Phase 6 (Cross-File Resolution)
 * **Features**:
 * - Module/file indexing and symbol lookup
 * - Require statement resolution to module exports
 * - Cross-file type binding
 */
@RunWith(JUnit4::class)
class CrossFileInferenceTest : IndexedBasePlatformTestCase() {

    // =========================================================================
    // Module require resolution
    // =========================================================================

    @Test
    fun testRequireResolution() {
        myFixture.addFileToProject(
            "mylib.lua",
            """
            ---@type string
            return "hello"
            """.trimIndent()
        )

        val file = myFixture.configureByText(
            "test.lua",
            """
            local mylib = require("mylib")
            ---@type string
            local check = mylib
            """.trimIndent()
        )

        myFixture.configureByFiles("test.lua", "mylib.lua")

        val snapshot = LuaTypesVisitor.getTypes(myFixture.file)
        assertNotNull(snapshot)
        val errors = snapshot.getErrors()
        if (errors.isNotEmpty()) {
            println("Errors in testRequireResolution: ${errors.map { it.message }}")
        }
        assertTrue("Should have no errors for valid require usage: ${errors.map { it.message }}", errors.isEmpty())
    }

    // =========================================================================
    // Inlay hints for inferred types
    // =========================================================================

    @Test
    fun testInlayHintsExist() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local s = "hello"
            local n = 100
            local function f(x, y)
                return x + y
            end
            """.trimIndent()
        )

        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull(snapshot)
        // verify inferred types
        val sVar = PsiTreeUtil.findChildrenOfType(file, LuaLocalVarDecl::class.java).first { it.text.contains("local s") }.attNameList.first().nameRef
        assertEquals("string", snapshot.getValueType(sVar).displayName())
    }

    // =========================================================================
    // Multi-return early returns in modules
    // =========================================================================

    @Test
    fun testEarlyReturnInFile() {
        myFixture.addFileToProject(
            "early.lua",
            """
            if true then
                ---@type number
                return 1
            end
            ---@type string
            return "hi"
            """.trimIndent()
        )

        val file = myFixture.configureByText(
            "test_early.lua",
            """
            local mod = require("early")
            ---@type boolean
            local check = mod -- Error: number | string not assignable to boolean
            """.trimIndent()
        )

        myFixture.configureByFiles("test_early.lua", "early.lua")

        val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
        val errors = snapshot.getErrors()

        assertFalse("Should have errors for invalid assignment from early return module", errors.isEmpty())
        assertTrue("Error should mention number | string and boolean, but got: ${errors.map { it.message }}",
            errors.any { it.message.contains("number | string") && it.message.contains("boolean") })
    }
}

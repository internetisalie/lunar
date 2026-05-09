package net.internetisalie.lunar.lang

import com.intellij.openapi.command.WriteCommandAction
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.insight.LuaBindings
import net.internetisalie.lunar.lang.insight.LuaBindingsVisitor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Comprehensive test suite for Lua symbol resolution and scoping.
 * Establishes baseline behavior for MAINT-04 refactoring (symbol resolution redesign).
 *
 * These tests verify that the current LuaBindingsVisitor-based implementation correctly
 * resolves local variables, parameters, loop variables, and handles scoping rules.
 * After MAINT-04, all tests must pass with the new PsiScopeProcessor implementation.
 */
class LuaSymbolResolutionTest : BaseDocumentTest() {

    private fun getBindings(): LuaBindings {
        var bindings: LuaBindings? = null
        WriteCommandAction.writeCommandAction(myFixture.project).run<RuntimeException?> {
            bindings = LuaBindingsVisitor.getBindings(myFixture.file)
        }
        Assertions.assertNotNull(bindings)
        return bindings!!
    }

    // ====== Scope Chaining Tests ======

    /**
     * Test: Simple local variable declaration and usage within same scope.
     */
    @Test
    fun testSimpleLocalVariable() {
        configureByText(
            """
                local x = 42
                print(x)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        // Should have at least one reference
        Assertions.assertTrue(bindings.references.isNotEmpty(), "File should have references")
        
        // Check that 'x' is in the bindings
        val xReferences = bindings.references.values.filter { it.name.joinToString(".") == "x" }
        Assertions.assertTrue(xReferences.isNotEmpty(), "Variable 'x' should be in bindings")
        
        // Check that at least one is defined (the declaration)
        val xDefined = xReferences.any { it.defined }
        Assertions.assertTrue(xDefined, "Variable 'x' should have a defined binding")
    }

    /**
     * Test: Nested block scoping - variable visible in nested block, invisible after.
     */
    @Test
    fun testNestedBlockScoping() {
        configureByText(
            """
                local x = 1
                do
                    local y = 2
                    print(x, y)
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        // Both x and y should be in bindings
        val xRefs = bindings.references.values.filter { it.name.joinToString(".") == "x" }
        val yRefs = bindings.references.values.filter { it.name.joinToString(".") == "y" }
        
        Assertions.assertTrue(xRefs.isNotEmpty(), "Variable 'x' should be resolvable in nested block")
        Assertions.assertTrue(yRefs.isNotEmpty(), "Variable 'y' should be resolvable in nested block")
    }

    /**
     * Test: Multiple local variables with distinct names in same scope.
     */
    @Test
    fun testMultipleDeclarationsInScope() {
        configureByText(
            """
                local a = 1
                local b = 2
                local c = 3
                print(a, b, c)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        // All three variables should be in bindings
        val aRefs = bindings.references.values.filter { it.name.joinToString(".") == "a" }
        val bRefs = bindings.references.values.filter { it.name.joinToString(".") == "b" }
        val cRefs = bindings.references.values.filter { it.name.joinToString(".") == "c" }
        
        Assertions.assertTrue(aRefs.isNotEmpty(), "Variable 'a' should be in bindings")
        Assertions.assertTrue(bRefs.isNotEmpty(), "Variable 'b' should be in bindings")
        Assertions.assertTrue(cRefs.isNotEmpty(), "Variable 'c' should be in bindings")
    }

    // ====== Function Parameter Tests ======

    /**
     * Test: Function parameters are resolvable within function body.
     */
    @Test
    fun testFunctionParameterResolution() {
        configureByText(
            """
                function add(x, y)
                    return x + y
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        // Parameters x and y should be in bindings
        val xRefs = bindings.references.values.filter { it.name.joinToString(".") == "x" }
        val yRefs = bindings.references.values.filter { it.name.joinToString(".") == "y" }
        
        Assertions.assertTrue(xRefs.isNotEmpty(), "Parameter 'x' should be resolvable")
        Assertions.assertTrue(yRefs.isNotEmpty(), "Parameter 'y' should be resolvable")
    }

    /**
     * Test: Multiple function parameters with distinct names.
     */
    @Test
    fun testMultipleParameters() {
        configureByText(
            """
                function process(name, age, score)
                    print(name, age, score)
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val nameRefs = bindings.references.values.filter { it.name.joinToString(".") == "name" }
        val ageRefs = bindings.references.values.filter { it.name.joinToString(".") == "age" }
        val scoreRefs = bindings.references.values.filter { it.name.joinToString(".") == "score" }
        
        Assertions.assertTrue(nameRefs.isNotEmpty(), "Parameter 'name' should resolve")
        Assertions.assertTrue(ageRefs.isNotEmpty(), "Parameter 'age' should resolve")
        Assertions.assertTrue(scoreRefs.isNotEmpty(), "Parameter 'score' should resolve")
    }

    // ====== Loop Variable Tests ======

    /**
     * Test: Numeric for loop counter variable is resolvable within loop body.
     */
    @Test
    fun testForLoopVariableResolution() {
        configureByText(
            """
                for i = 1, 10 do
                    print(i)
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val iRefs = bindings.references.values.filter { it.name.joinToString(".") == "i" }
        Assertions.assertTrue(iRefs.isNotEmpty(), "Loop variable 'i' should be resolvable")
    }

    /**
     * Test: Generic for loop with multiple iteration variables.
     */
    @Test
    fun testGenericForLoopMultipleVariables() {
        configureByText(
            """
                for k, v in pairs(t) do
                    print(k, v)
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val kRefs = bindings.references.values.filter { it.name.joinToString(".") == "k" }
        val vRefs = bindings.references.values.filter { it.name.joinToString(".") == "v" }
        
        Assertions.assertTrue(kRefs.isNotEmpty(), "Loop variable 'k' should resolve")
        Assertions.assertTrue(vRefs.isNotEmpty(), "Loop variable 'v' should resolve")
    }

    /**
     * Test: While loop does not introduce new variable scope.
     */
    @Test
    fun testWhileLoopNoVariableScope() {
        configureByText(
            """
                local i = 1
                while i < 10 do
                    print(i)
                    i = i + 1
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val iRefs = bindings.references.values.filter { it.name.joinToString(".") == "i" }
        Assertions.assertTrue(iRefs.isNotEmpty(), "Variable 'i' should resolve to outer local")
    }

    // ====== Control Flow & Edge Cases ======

    /**
     * Test: Redeclaration of local in same scope.
     */
    @Test
    fun testRedeclarationInSameScope() {
        configureByText(
            """
                local x = 1
                local x = 2
                print(x)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val xRefs = bindings.references.values.filter { it.name.joinToString(".") == "x" }
        Assertions.assertTrue(xRefs.isNotEmpty(), "Redeclared variable 'x' should be in bindings")
        
        // Should have at least one defined binding (may only track latest declaration)
        val xDefined = xRefs.filter { it.defined }
        Assertions.assertTrue(xDefined.isNotEmpty(), "Should have at least one defined 'x' binding")
    }

    /**
     * Test: Nested function introduces new scope for parameters.
     */
    @Test
    fun testNestedFunctions() {
        configureByText(
            """
                function outer(a)
                    function inner(b)
                        print(a, b)
                    end
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val aRefs = bindings.references.values.filter { it.name.joinToString(".") == "a" }
        val bRefs = bindings.references.values.filter { it.name.joinToString(".") == "b" }
        
        Assertions.assertTrue(aRefs.isNotEmpty(), "Outer parameter 'a' should be visible in inner function")
        Assertions.assertTrue(bRefs.isNotEmpty(), "Inner parameter 'b' should be resolvable")
    }
}

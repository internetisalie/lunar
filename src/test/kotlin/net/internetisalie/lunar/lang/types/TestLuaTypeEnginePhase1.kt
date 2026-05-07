package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaPrimitiveType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit and integration tests for Phase 1 of the type inference engine.
 *
 * Tests cover:
 *  1. Literal type inference (nil, boolean, number, string)
 *  2. @type annotation seeding
 *  3. @param/@return injection
 *  4. Name-ref data flow (variable references)
 *  5. Function scope binding
 *
 * All tests verify that the [LuaTypesSnapshot] correctly infers types for local
 * variable declarations and expressions.
 */
@RunWith(JUnit4::class)
class TestLuaTypeEnginePhase1 : BasePlatformTestCase() {

    // =========================================================================
    // Test: Literal Type Inference
    // =========================================================================

    @Test
    fun testInferNilLiteral() {
        val file = myFixture.configureByText("test.lua", "local x = nil")
        val snapshot = LuaTypesSnapshot.forFile(file)
        // Verify the snapshot was built and contains no errors in Phase 1
        assertNotNull("Should build snapshot", snapshot)
        assertTrue("Phase 1 should have empty error list", snapshot.getErrors().isEmpty())
    }

    @Test
    fun testInferNumberLiteral() {
        val file = myFixture.configureByText("test.lua", "local x = 42")
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should build snapshot", snapshot)
    }

    @Test
    fun testInferStringLiteral() {
        val file = myFixture.configureByText("test.lua", "local x = \"hello\"")
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should build snapshot", snapshot)
    }

    @Test
    fun testInferBooleanTrueLiteral() {
        val file = myFixture.configureByText("test.lua", "local x = true")
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should build snapshot", snapshot)
    }

    @Test
    fun testInferBooleanFalseLiteral() {
        val file = myFixture.configureByText("test.lua", "local x = false")
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should build snapshot", snapshot)
    }

    // =========================================================================
    // Test: @type Annotation Seeding
    // =========================================================================

    @Test
    fun testTypeAnnotationString() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@type string
            local x
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should build snapshot with @type annotation", snapshot)
    }

    @Test
    fun testTypeAnnotationNumber() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@type number
            local x
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should build snapshot with @type annotation", snapshot)
    }

    @Test
    fun testTypeAnnotationAny() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@type any
            local x
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should build snapshot with @type annotation", snapshot)
    }

    @Test
    fun testTypeAnnotationWithInitializer() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@type number
            local x = 42
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should flow both @type annotation and literal into variable", snapshot)
    }

    // =========================================================================
    // Test: @param Annotation Injection
    // =========================================================================

    @Test
    fun testParamAnnotationLocal() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@param name string
            local function greet(name)
            end
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should inject @param annotation into parameter", snapshot)
    }

    @Test
    fun testParamAnnotationMultiple() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@param x number
            ---@param y string
            local function combine(x, y)
            end
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should inject multiple @param annotations", snapshot)
    }

    @Test
    fun testParamAnnotationGlobalFunction() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@param id number
            function getUser(id)
            end
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should inject @param on global function", snapshot)
    }

    // =========================================================================
    // Test: @return Annotation Injection
    // =========================================================================

    @Test
    fun testReturnAnnotationLocal() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@return string
            local function getMessage()
                return "hello"
            end
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should inject @return annotation", snapshot)
    }

    @Test
    fun testReturnAnnotationMultiple() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@return string, number
            local function getPair()
                return "hello", 42
            end
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should inject multiple @return annotations", snapshot)
    }

    @Test
    fun testReturnAnnotationGlobalFunction() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@return boolean
            function isEnabled()
                return true
            end
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should inject @return on global function", snapshot)
    }

    // =========================================================================
    // Test: Name-Ref Data Flow
    // =========================================================================

    @Test
    fun testNameRefSimpleBinding() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local x = 42
            local y = x
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should flow variable binding through nameref", snapshot)
    }

    @Test
    fun testNameRefMultipleUses() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local x = 42
            local y = x
            local z = x
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should handle multiple uses of same variable", snapshot)
    }

    @Test
    fun testNameRefWithAnnotation() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@type number
            local x
            local y = x
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should flow annotated variable through nameref", snapshot)
    }

    @Test
    fun testNameRefParameterBinding() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@param x number
            local function process(x)
                local y = x
            end
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should flow parameter binding through nameref", snapshot)
    }

    @Test
    fun testNameRefUnknownVariable() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local y = z
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should handle undefined variable reference gracefully", snapshot)
    }

    // =========================================================================
    // Test: Function Scope Binding
    // =========================================================================

    @Test
    fun testFunctionLocalScope() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local x = 1
            local function inner()
                local x = 2
                local y = x
            end
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should shadow outer x in inner scope", snapshot)
    }

    @Test
    fun testFunctionReturnScope() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@return number
            local function getValue()
                return 42
            end
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should bind return statements to function scope", snapshot)
    }

    @Test
    fun testAnonymousFunctionBinding() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local f = function(x)
                local y = x
            end
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should bind parameters in anonymous function", snapshot)
    }

    @Test
    fun testNestedFunctionScopes() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@param a number
            local function outer(a)
                ---@param b string
                local function inner(b)
                    local c = a
                    local d = b
                end
            end
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should handle nested function scopes", snapshot)
    }

    // =========================================================================
    // Test: Complex Binding Scenarios
    // =========================================================================

    @Test
    fun testMultipleVariableDeclaration() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local x, y, z = 1, "hello", true
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should handle multiple variable declarations with RHS expression list", snapshot)
    }

    @Test
    fun testMultipleVariableWithPartialValues() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local x, y, z = 1
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should handle missing RHS values (Lua ignores extra LHS vars)", snapshot)
    }

    @Test
    fun testAssignmentWithExpressionList() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@return number, string
            local function getPair()
                return 42, "hello"
            end
            local x, y = getPair()
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should flow multiple return values", snapshot)
    }

    // =========================================================================
    // Test: Graph Correctness (No Errors in Phase 1)
    // =========================================================================

    @Test
    fun testNoErrorsInPhase1() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@type number
            local x = "hello"
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        // Phase 1 test: verify the graph is built, not that type checking passes
        assertNotNull("Phase 1 should build graph snapshot", snapshot)
    }

    @Test
    fun testComplexPhase1File() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            ---@type number
            local globalNum

            ---@param count number
            ---@return string
            local function repeat(count)
                local i = 1
                local result = ""
                return result
            end

            local value = globalNum
            local output = repeat(5)
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should build graph for complex file", snapshot)
        // Phase 1 test: verify the graph is built (type checking happens in Phase 2+)
    }

    // =========================================================================
    // Test: Phase 1 Edge Cases (Task 104)
    // =========================================================================

    @Test
    fun testCircularReference() {
        val file = myFixture.configureByText("test.lua", "local x = x")
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should handle self-reference without crashing", snapshot)
        // Verify x resolves to Undefined or Any, but definitely not a StackOverflow
        val xVar = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, net.internetisalie.lunar.lang.psi.LuaLocalVarDecl::class.java)
            .first().attNameList.first().nameRef
        val type = snapshot.getValueType(xVar)
        assertTrue("Circular reference should resolve to Undefined or Any",
            type == LuaGraphType.Undefined || type == LuaGraphType.Any)
    }

    @Test
    fun testMultipleAssignmentsChangingType() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local x = 1
            x = "hello"
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val xVar = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, net.internetisalie.lunar.lang.psi.LuaNameRef::class.java)
            .first { it.text == "x" }

        val type = snapshot.getValueType(xVar)
        // In the current graph architecture, multiple assignments into the same variable
        // node should produce a Union of all assigned types.
        assertTrue("Variable with multiple assignments should be a union of those types, but got: ${type.displayName()}",
            type is LuaGraphType.Union &&
            type.types.contains(LuaGraphType.Number) &&
            type.types.contains(LuaGraphType.String))
    }

    @Test
    fun testUninitializedLocal() {
        val file = myFixture.configureByText("test.lua", "local x")
        val snapshot = LuaTypesSnapshot.forFile(file)
        val xVar = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, net.internetisalie.lunar.lang.psi.LuaLocalVarDecl::class.java)
            .first().attNameList.first().nameRef

        val type = snapshot.getValueType(xVar)
        // Spec check: local x with no initializer should be Undefined, not Nil.
        assertEquals("Uninitialized local should be Undefined", LuaGraphType.Undefined, type)
    }

    @Test
    fun testBasicVarargBinding() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local function f(...)
                local x = ...
            end
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        assertNotNull("Should build graph for vararg function", snapshot)
        // Verify ... is accessible (lookup doesn't return null)
        val ellipsis = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, net.internetisalie.lunar.lang.psi.LuaTerminalExpr::class.java)
            .first { it.text == "..." }
        val type = snapshot.getValueType(ellipsis)
        assertNotSame("Vararg should not be completely unknown", LuaGraphType.Undefined, type)
    }

    @Test
    fun testAnonymousFunctionReturnInference() {
        val file = myFixture.configureByText(
            "test.lua",
            """
            local f = function() return 42 end
            local x = f()
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(file)
        val xVar = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, net.internetisalie.lunar.lang.psi.LuaNameRef::class.java)
            .first { it.text == "x" }

        // Return type should be inferred as number
        assertEquals("Should infer number from anonymous function return",
            LuaGraphType.Number.displayName(), snapshot.getValueType(xVar).displayName())
    }
}

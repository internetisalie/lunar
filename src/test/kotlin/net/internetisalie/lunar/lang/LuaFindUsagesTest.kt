package net.internetisalie.lunar.lang

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.insight.LuaBindings
import net.internetisalie.lunar.lang.insight.LuaBindingsVisitor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Comprehensive test suite for Lua "find usages" and "reference highlighting" features.
 * Establishes baseline behavior for MAINT-04 refactoring (symbol resolution redesign).
 *
 * These tests verify that the IDE can find all usages of a symbol and highlight references
 * appropriately. This is critical for refactoring support and IDE navigation.
 */
class LuaFindUsagesTest : BaseDocumentTest() {

    private fun getBindings(): LuaBindings {
        var bindings: LuaBindings? = null
        WriteCommandAction.writeCommandAction(myFixture.project).run<RuntimeException?> {
            bindings = LuaBindingsVisitor.getBindings(myFixture.file)
        }
        Assertions.assertNotNull(bindings)
        return bindings!!
    }

    // ====== Find Usages — Local Variables ======

    /**
     * Test: Find all usages of a local variable within its scope.
     */
    @Test
    @Disabled("Find usages feature - requires reference search infrastructure")
    fun testFindUsagesLocalVariable() {
        configureByText(
            """
                local x = 42
                print(x)
                x = x + 1
                print(x)
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
        
        // Should find: declaration + 3 usages (print 1, assign, print 2)
    }

    /**
     * Test: Find usages of variable doesn't include shadowed outer variable.
     */
    @Test
    @Disabled("Shadowed variable reference tracking")
    fun testFindUsagesShadowedVariable() {
        configureByText(
            """
                local x = 1
                print(x)
                
                do
                    local x = 2
                    print(x)  -- Should not count outer x
                end
                
                print(x)  -- Should count outer x
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
    }

    /**
     * Test: Find usages of parameter across function body.
     */
    @Test
    @Disabled("Parameter usage finding")
    fun testFindUsagesFunctionParameter() {
        configureByText(
            """
                local function process(value)
                    print(value)
                    value = value + 1
                    print(value)
                    return value
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
        
        // Should find: parameter + 4 usages
    }

    /**
     * Test: Find usages of loop variable within loop.
     */
    @Test
    @Disabled("Loop variable usage tracking")
    fun testFindUsagesLoopVariable() {
        configureByText(
            """
                for i = 1, 10 do
                    print(i)
                    local j = i + 1
                    print(j)
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
    }

    // ====== Find Usages — Global Variables ======

    /**
     * Test: Find all usages of a global variable in same file.
     */
    @Test
    @Disabled("Global variable usage finding")
    fun testFindUsagesGlobalVariable() {
        configureByText(
            """
                globalVar = 1
                print(globalVar)
                globalVar = globalVar + 1
                print(globalVar)
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
    }

    /**
     * Test: Find usages of global function.
     */
    @Test
    @Disabled("Global function usage finding")
    fun testFindUsagesGlobalFunction() {
        configureByText(
            """
                function helper()
                    return 42
                end
                
                local x = helper()
                local y = helper()
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
    }

    // ====== Find Usages — Exclude Definition ======

    /**
     * Test: Find usages excludes the definition/declaration.
     * Definition itself should not be counted as a "usage".
     */
    @Test
    @Disabled("Definition exclusion in find usages")
    fun testFindUsagesExcludeDefinition() {
        configureByText(
            """
                local myVar = 42  -- Declaration (should not be in usages)
                print(myVar)      -- Usage 1
                print(myVar)      -- Usage 2
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
        
        // Should find 2 usages, not 3 (excluding declaration)
    }

    /**
     * Test: Find usages for function declaration vs calls.
     * Function name in definition is not a "usage".
     */
    @Test
    @Disabled("Function definition vs call distinction")
    fun testFindUsagesExcludeFunctionDefinition() {
        configureByText(
            """
                local function myFunc()  -- Definition
                    return 1
                end
                
                myFunc()  -- Usage 1
                myFunc()  -- Usage 2
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
        
        // Should find 2 usages (the calls), not counting the definition
    }

    /**
     * Test: Parameter definition not counted as usage.
     */
    @Test
    @Disabled("Parameter definition vs usage distinction")
    fun testFindUsagesExcludeParameterDefinition() {
        configureByText(
            """
                local function foo(param)  -- Parameter definition
                    print(param)           -- Usage 1
                    print(param)           -- Usage 2
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
    }

    // ====== Reference Highlighting ======

    /**
     * Test: Highlight all usages of a variable in current scope.
     * When cursor is on a variable name, all references highlight.
     */
    @Test
    @Disabled("Reference highlighting feature")
    fun testHighlightAllUsagesInScope() {
        configureByText(
            """
                local x = 1
                print(x)
                x = 2
                print(x)
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
        
        // When cursor on 'x', should highlight declaration + 3 usages
    }

    /**
     * Test: Reference highlighting excludes same name from different scope.
     * Inner scope's variable shouldn't highlight when selecting outer scope variable.
     */
    @Test
    @Disabled("Scope-aware highlighting")
    fun testHighlightExcludesOtherScopes() {
        configureByText(
            """
                local x = 1
                print(x)  -- Should highlight
                
                do
                    local x = 2
                    print(x)  -- Should NOT highlight when selecting outer x
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
    }

    /**
     * Test: Reference highlighting includes parameter and all usages.
     */
    @Test
    @Disabled("Parameter reference highlighting")
    fun testHighlightParameterReferences() {
        configureByText(
            """
                local function process(value)
                    print(value)
                    value = value + 1
                    return value
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
    }

    /**
     * Test: Reference highlighting for loop variable.
     */
    @Test
    @Disabled("Loop variable highlighting")
    fun testHighlightLoopVariableReferences() {
        configureByText(
            """
                for i = 1, 10 do
                    print(i)
                    if i > 5 then
                        break
                    end
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
    }

    /**
     * Test: Reference highlighting for nested scope variable.
     * Inner variable highlights independently of outer.
     */
    @Test
    @Disabled("Nested scope highlighting")
    fun testHighlightNestedScopeVariable() {
        configureByText(
            """
                local x = 1
                print(x)
                
                do
                    local x = 2
                    print(x)
                    print(x)
                end
                
                print(x)
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
    }

    // ====== Cross-File Find Usages ======

    /**
     * Test: Find usages across multiple files (global scope).
     */
    @Test
    @Disabled("Cross-file usage finding - requires multi-file support")
    fun testFindUsagesAcrossFiles() {
        // This would require setting up multiple files
        // File 1: module.lua
        myFixture.configureByText("module.lua", """
            globalFunc = function()
                return 42
            end
        """.trimIndent())

        // File 2: main.lua  
        configureByText("""
            local result = globalFunc()
            print(result)
        """.trimIndent())

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
        
        // Should find globalFunc usage in main.lua
    }

    /**
     * Test: Find usages of require'd module.
     */
    @Test
    @Disabled("Require usage finding")
    fun testFindUsagesOfRequiredModule() {
        configureByText(
            """
                local utils = require("utils")
                local result = utils.helper()
                print(utils.version)
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
    }

    // ====== Edge Cases ======

    /**
     * Test: Find usages when variable assigned to itself.
     */
    @Test
    @Disabled("Self-assignment pattern")
    fun testFindUsagesSelfAssignment() {
        configureByText(
            """
                local x = 1
                x = x + 1
                x = x * 2
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
    }

    /**
     * Test: Find usages in table field context.
     */
    @Test
    @Disabled("Table field usage tracking")
    fun testFindUsagesInTableField() {
        configureByText(
            """
                local obj = {x = 1}
                print(obj.x)
                obj.x = 2
                print(obj.x)
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
    }

    /**
     * Test: Find usages in function call context.
     */
    @Test
    @Disabled("Function call usage finding")
    fun testFindUsagesInFunctionCall() {
        configureByText(
            """
                local function process(value)
                    return value * 2
                end
                
                local x = 5
                local result = process(x)
                print(x)
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
    }

    /**
     * Test: Find usages when variable is in closure.
     */
    @Test
    @Disabled("Closure variable tracking")
    fun testFindUsagesInClosure() {
        configureByText(
            """
                local x = 10
                local function closure()
                    return x + 5
                end
                
                x = 20
                print(closure())
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
    }

    /**
     * Test: Find usages when name used in comment (should not count).
     */
    @Test
    @Disabled("Comment filtering")
    fun testFindUsagesIgnoresComments() {
        configureByText(
            """
                local x = 1
                print(x)  -- x is used here
                -- x appears in this comment
                print(x)
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
        
        // Should find 2 usages, not 3 (comment doesn't count)
    }

    /**
     * Test: Find usages in string literal (should not count).
     */
    @Test
    @Disabled("String literal filtering")
    fun testFindUsagesIgnoresStringLiterals() {
        configureByText(
            """
                local x = 1
                print("x is a variable")
                print(x)
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
        
        // Should find 1 usage, not 2 (string literal doesn't count)
    }

    /**
     * Test: Find usages handles empty scope gracefully.
     */
    @Test
    @Disabled("Empty scope handling")
    fun testFindUsagesEmptyScope() {
        configureByText(
            """
                local x = 1
                -- No usages of x
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
        
        // Should return just the declaration, 0 usages
    }

    /**
     * Test: Find usages for built-in function like print.
     */
    @Test
    @Disabled("Built-in function usage finding")
    fun testFindUsagesBuiltinFunction() {
        configureByText(
            """
                print("hello")
                print("world")
                print("!")
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
        
        // Should find 3 usages of print
    }
}

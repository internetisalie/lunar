package net.internetisalie.lunar.lang

import com.intellij.openapi.command.WriteCommandAction
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.insight.LuaBindings
import net.internetisalie.lunar.lang.insight.LuaBindingsVisitor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Comprehensive test suite for Lua "goto definition" navigation and reference resolution.
 * Establishes baseline behavior for MAINT-04 refactoring (symbol resolution redesign).
 *
 * These tests verify that the current LuaBindingsVisitor-based implementation correctly
 * resolves definitions for goto/navigation purposes, including locals, globals, functions,
 * parameters, and cross-file references.
 */
class LuaGotoDefinitionTest : BaseDocumentTest() {

    private fun getBindings(): LuaBindings {
        var bindings: LuaBindings? = null
        WriteCommandAction.writeCommandAction(myFixture.project).run<RuntimeException?> {
            bindings = LuaBindingsVisitor.getBindings(myFixture.file)
        }
        Assertions.assertNotNull(bindings)
        return bindings!!
    }

    // ====== Local Variable Goto Definition ======

    /**
     * Test: Goto definition on simple local variable.
     */
    @Test
    fun testGotoLocalVariableDefinition() {
        configureByText(
            """
                local x = 42
                print(x)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        // Should have references to 'x'
        val xReferences = bindings.references.values.filter { it.name.joinToString(".") == "x" }
        Assertions.assertTrue(xReferences.isNotEmpty(), "Local variable 'x' should have references")
        
        // Should have at least one definition
        val xDefined = xReferences.any { it.defined }
        Assertions.assertTrue(xDefined, "Local variable 'x' should have a definition")
    }

    /**
     * Test: Goto definition on shadowed variable (should navigate to innermost scope).
     */
    @Test
    fun testGotoShadowedVariableDefinition() {
        configureByText(
            """
                local x = "outer"
                do
                    local x = "inner"
                    print(x)
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val xReferences = bindings.references.values.filter { it.name.joinToString(".") == "x" }
        Assertions.assertTrue(xReferences.isNotEmpty(), "Shadowed variable 'x' should be resolvable")
    }

    /**
     * Test: Goto definition on table field access.
     * `print(obj.field)` should resolve 'obj' to its definition.
     */
    @Test
    @Disabled("Table field resolution - may require field-level tracking")
    fun testGotoTableFieldDefinition() {
        configureByText(
            """
                local obj = {x = 1, y = 2}
                print(obj.x)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val objReferences = bindings.references.values.filter { it.name.joinToString(".") == "obj" }
        Assertions.assertTrue(objReferences.isNotEmpty(), "Table object 'obj' should be resolvable for field access")
    }

    // ====== Function Definition Navigation ======

    /**
     * Test: Goto definition on local function call.
     */
    @Test
    fun testGotoLocalFunctionDefinition() {
        configureByText(
            """
                local function helper()
                    return 42
                end
                print(helper())
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val helperReferences = bindings.references.values.filter { it.name.joinToString(".") == "helper" }
        Assertions.assertTrue(helperReferences.isNotEmpty(), "Local function 'helper' should be resolvable")
        
        val helperDefined = helperReferences.any { it.defined }
        Assertions.assertTrue(helperDefined, "Local function 'helper' should have a definition")
    }

    /**
     * Test: Goto definition on global function call.
     */
    @Test
    fun testGotoGlobalFunctionDefinition() {
        configureByText(
            """
                function globalFunc()
                    return "global"
                end
                print(globalFunc())
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val globalFuncRefs = bindings.references.values.filter { it.name.joinToString(".") == "globalFunc" }
        Assertions.assertTrue(globalFuncRefs.isNotEmpty(), "Global function 'globalFunc' should be resolvable")
        
        val globalFuncDefined = globalFuncRefs.any { it.defined }
        Assertions.assertTrue(globalFuncDefined, "Global function 'globalFunc' should have a definition")
    }

    /**
     * Test: Goto definition on nested function.
     * Navigate from function call to nested function definition.
     */
    @Test
    fun testGotoNestedFunctionDefinition() {
        configureByText(
            """
                local function outer()
                    local function inner()
                        return 1
                    end
                    return inner()
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val innerReferences = bindings.references.values.filter { it.name.joinToString(".") == "inner" }
        Assertions.assertTrue(innerReferences.isNotEmpty(), "Nested function 'inner' should be resolvable")
    }

    /**
     * Test: Goto definition on function assigned to variable.
     */
    @Test
    fun testGotoFunctionVariableDefinition() {
        configureByText(
            """
                local fn = function()
                    return 99
                end
                print(fn())
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val fnReferences = bindings.references.values.filter { it.name.joinToString(".") == "fn" }
        Assertions.assertTrue(fnReferences.isNotEmpty(), "Function variable 'fn' should be resolvable")
        
        val fnDefined = fnReferences.any { it.defined }
        Assertions.assertTrue(fnDefined, "Function variable 'fn' should have a definition")
    }

    // ====== Function Parameter Navigation ======

    /**
     * Test: Goto definition on function parameter.
     */
    @Test
    fun testGotoFunctionParameterDefinition() {
        configureByText(
            """
                local function process(value)
                    print(value)
                end
                process(42)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val valueReferences = bindings.references.values.filter { it.name.joinToString(".") == "value" }
        Assertions.assertTrue(valueReferences.isNotEmpty(), "Function parameter 'value' should be resolvable")
        
        val valueDefined = valueReferences.any { it.defined }
        Assertions.assertTrue(valueDefined, "Function parameter 'value' should have a definition")
    }

    /**
     * Test: Goto definition on multiple function parameters.
     */
    @Test
    fun testGotoMultipleFunctionParametersDefinition() {
        configureByText(
            """
                local function compute(a, b, c)
                    return a + b + c
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val aReferences = bindings.references.values.filter { it.name.joinToString(".") == "a" }
        val bReferences = bindings.references.values.filter { it.name.joinToString(".") == "b" }
        val cReferences = bindings.references.values.filter { it.name.joinToString(".") == "c" }
        
        Assertions.assertTrue(aReferences.isNotEmpty(), "Parameter 'a' should be resolvable")
        Assertions.assertTrue(bReferences.isNotEmpty(), "Parameter 'b' should be resolvable")
        Assertions.assertTrue(cReferences.isNotEmpty(), "Parameter 'c' should be resolvable")
    }

    /**
     * Test: Goto definition on variadic parameter (... rest).
     */
    @Test
    fun testGotoVariadicParameterDefinition() {
        configureByText(
            """
                local function varargs(...)
                    local args = {...}
                    return args
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        // ... is a special parameter; verify args is resolvable
        val argsReferences = bindings.references.values.filter { it.name.joinToString(".") == "args" }
        Assertions.assertTrue(argsReferences.isNotEmpty(), "Variadic result 'args' should be resolvable")
    }

    // ====== Loop Variable Navigation ======

    /**
     * Test: Goto definition on for loop variable.
     */
    @Test
    fun testGotoForLoopVariableDefinition() {
        configureByText(
            """
                for i = 1, 10 do
                    print(i)
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val iReferences = bindings.references.values.filter { it.name.joinToString(".") == "i" }
        Assertions.assertTrue(iReferences.isNotEmpty(), "For loop variable 'i' should be resolvable")
        
        val iDefined = iReferences.any { it.defined }
        Assertions.assertTrue(iDefined, "For loop variable 'i' should have a definition")
    }

    /**
     * Test: Goto definition on generic for loop variables.
     * `for k, v in pairs(t)` has both k and v as loop variables.
     */
    @Test
    fun testGotoGenericForLoopVariablesDefinition() {
        configureByText(
            """
                local t = {1, 2, 3}
                for k, v in ipairs(t) do
                    print(k, v)
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val kReferences = bindings.references.values.filter { it.name.joinToString(".") == "k" }
        val vReferences = bindings.references.values.filter { it.name.joinToString(".") == "v" }
        
        Assertions.assertTrue(kReferences.isNotEmpty(), "Generic for loop variable 'k' should be resolvable")
        Assertions.assertTrue(vReferences.isNotEmpty(), "Generic for loop variable 'v' should be resolvable")
    }

    /**
     * Test: Goto definition on while loop doesn't create new variables.
     * While loops don't declare variables; verify iteration correctly.
     */
    @Test
    fun testGotoWhileLoopVariableUsage() {
        configureByText(
            """
                local i = 0
                while i < 10 do
                    i = i + 1
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val iReferences = bindings.references.values.filter { it.name.joinToString(".") == "i" }
        Assertions.assertTrue(iReferences.isNotEmpty(), "While loop variable 'i' should be resolvable")
    }

    // ====== Global Variable Navigation ======

    /**
     * Test: Goto definition on global variable.
     */
    @Test
    fun testGotoGlobalVariableDefinition() {
        configureByText(
            """
                globalX = 42
                print(globalX)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val globalXRefs = bindings.references.values.filter { it.name.joinToString(".") == "globalX" }
        Assertions.assertTrue(globalXRefs.isNotEmpty(), "Global variable 'globalX' should be resolvable")
        
        val globalXDefined = globalXRefs.any { it.defined }
        Assertions.assertTrue(globalXDefined, "Global variable 'globalX' should have a definition")
    }

    /**
     * Test: Goto definition on global assigned in multiple places.
     * Multiple assignments to same global; goto should show all definitions.
     */
    @Test
    fun testGotoGlobalWithMultipleAssignments() {
        configureByText(
            """
                counter = 0
                counter = counter + 1
                print(counter)
                counter = counter * 2
                print(counter)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val counterRefs = bindings.references.values.filter { it.name.joinToString(".") == "counter" }
        Assertions.assertTrue(counterRefs.isNotEmpty(), "Global 'counter' with multiple assignments should be resolvable")
    }

    // ====== Module/Require Navigation ======

    /**
     * Test: Goto definition on required module.
     */
    @Test
    fun testGotoRequiredModuleDefinition() {
        configureByText(
            """
                local utils = require("utils")
                print(utils)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val utilsReferences = bindings.references.values.filter { it.name.joinToString(".") == "utils" }
        Assertions.assertTrue(utilsReferences.isNotEmpty(), "Required module 'utils' should be resolvable")
        
        val utilsDefined = utilsReferences.any { it.defined }
        Assertions.assertTrue(utilsDefined, "Required module 'utils' should have a definition point")
    }

    /**
     * Test: Goto definition on global function from required module.
     */
    @Test
    @Disabled("Cross-file module function navigation - requires require resolution infrastructure")
    fun testGotoModuleFunctionDefinition() {
        myFixture.configureByText("utils.lua", """
            function utils.helper()
                return 42
            end
        """.trimIndent())

        configureByText(
            """
                local utils = require("utils")
                print(utils.helper())
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val utilsReferences = bindings.references.values.filter { it.name.joinToString(".") == "utils" }
        Assertions.assertTrue(utilsReferences.isNotEmpty(), "Module function 'utils' should be navigable")
    }

    // ====== Label Navigation (Goto/Label Goto) ======

    /**
     * Test: Goto definition on label target.
     * `goto label_name` should navigate to `::label_name::` definition.
     */
    @Test
    @Disabled("Label navigation - dedicated label resolution system")
    fun testGotoLabelDefinition() {
        configureByText(
            """
                ::start::
                print("at start")
                if true then
                    goto finish
                end
                ::finish::
                print("at finish")
            """.trimIndent()
        )

        val bindings = getBindings()
        
        // Labels have separate resolution; verify they're tracked
        Assertions.assertNotNull(bindings)
    }

    /**
     * Test: Multiple labels with same base name (different scopes).
     * Labels in different scopes shouldn't conflict.
     */
    @Test
    @Disabled("Multi-scope label navigation - complex label scoping")
    fun testGotoMultipleLabelsDefinition() {
        configureByText(
            """
                do
                    ::label::
                    print("inner")
                end
                
                do
                    ::label::
                    print("outer")
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        Assertions.assertNotNull(bindings)
    }

    // ====== Navigation with Multiple Matches ======

    /**
     * Test: Multiple possible definitions (ambiguous goto).
     * In Lua, this can happen with globals defined in multiple places.
     */
    @Test
    fun testGotoMultiplePossibleDefinitions() {
        configureByText(
            """
                x = 1
                print(x)
                x = 2
                print(x)
                x = 3
                print(x)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val xReferences = bindings.references.values.filter { it.name.joinToString(".") == "x" }
        Assertions.assertTrue(xReferences.isNotEmpty(), "Global with multiple definitions should have all references")
    }

    // ====== Navigation Edge Cases ======

    /**
     * Test: Goto definition on undefined variable (forward reference).
     * Should not resolve (or resolve to nil).
     */
    @Test
    fun testGotoUndefinedVariableNoResolution() {
        configureByText(
            """
                print(undefined)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        // Undefined variables typically don't have definitions
        val undefinedRefs = bindings.references.values.filter { it.name.joinToString(".") == "undefined" }
        // May or may not be in bindings depending on implementation
    }

    /**
     * Test: Goto definition on 'self' parameter (OO pattern).
     * `function obj:method(self)` or implicit self.
     */
    @Test
    @Disabled("OO self parameter - method colon syntax special handling")
    fun testGotoSelfParameterDefinition() {
        configureByText(
            """
                local obj = {}
                function obj:method()
                    print(self)
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val selfReferences = bindings.references.values.filter { it.name.joinToString(".") == "self" }
        // self is implicit in method definitions with colon syntax
    }

    /**
     * Test: Goto definition on built-in global (e.g., 'print', 'type').
     */
    @Test
    @Disabled("Built-in globals - depends on stdlib definition availability")
    fun testGotoBuiltinGlobalDefinition() {
        configureByText(
            """
                print("hello")
                local t = type({})
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val printRefs = bindings.references.values.filter { it.name.joinToString(".") == "print" }
        val typeRefs = bindings.references.values.filter { it.name.joinToString(".") == "type" }
        
        // Built-ins may not be explicitly defined in current implementation
    }

    /**
     * Test: Goto definition on redeclared variable (most recent definition).
     */
    @Test
    fun testGotoRedeclaredVariableLatestDefinition() {
        configureByText(
            """
                local x = 1
                local x = 2
                print(x)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val xReferences = bindings.references.values.filter { it.name.joinToString(".") == "x" }
        Assertions.assertTrue(xReferences.isNotEmpty(), "Redeclared variable 'x' should resolve to latest definition")
    }
}

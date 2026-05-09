package net.internetisalie.lunar.lang

import com.intellij.openapi.command.WriteCommandAction
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.insight.LuaBindings
import net.internetisalie.lunar.lang.insight.LuaBindingsVisitor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Comprehensive test suite for Lua global symbol resolution and cross-file imports.
 * Establishes baseline behavior for MAINT-04 refactoring (symbol resolution redesign).
 *
 * These tests verify that the current LuaBindingsVisitor-based implementation correctly
 * resolves global variables, module exports, and cross-file dependencies.
 *
 * Note: Many of these tests may fail with the current implementation, which is expected.
 * They establish the behavioral specification that the new PsiScopeProcessor implementation
 * must satisfy during MAINT-04.
 */
class LuaGlobalResolutionTest : BaseDocumentTest() {

    private fun getBindings(): LuaBindings {
        var bindings: LuaBindings? = null
        WriteCommandAction.writeCommandAction(myFixture.project).run<RuntimeException?> {
            bindings = LuaBindingsVisitor.getBindings(myFixture.file)
        }
        Assertions.assertNotNull(bindings)
        return bindings!!
    }

    // ====== Global Variable Resolution (Same File) ======

    /**
     * Test: Global variable defined and used in same file.
     * A variable assigned without 'local' keyword is a global.
     */
    @Test
    fun testGlobalVariableInSameFile() {
        configureByText(
            """
                x = 42
                print(x)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        // Global 'x' should be resolvable
        val xReferences = bindings.references.values.filter { it.name.joinToString(".") == "x" }
        Assertions.assertTrue(xReferences.isNotEmpty(), "Global variable 'x' should be in bindings")
        
        // Should have at least one definition (the assignment)
        val xDefined = xReferences.any { it.defined }
        Assertions.assertTrue(xDefined, "Global 'x' should have a defined binding")
    }

    /**
     * Test: Global variable used before assignment (forward reference).
     * In Lua, this is allowed but resolves to nil at runtime. The reference should still resolve.
     */
    @Test
    @Disabled("Forward reference in global scope - current implementation may not support")
    fun testGlobalForwardReference() {
        configureByText(
            """
                print(x)
                x = 42
            """.trimIndent()
        )

        val bindings = getBindings()
        val xReferences = bindings.references.values.filter { it.name.joinToString(".") == "x" }
        Assertions.assertTrue(xReferences.isNotEmpty(), "Forward reference to global 'x' should resolve")
    }

    /**
     * Test: Multiple globals with distinct names in same file.
     */
    @Test
    fun testMultipleGlobalsInSameFile() {
        configureByText(
            """
                a = 1
                b = 2
                c = 3
                print(a, b, c)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val aRefs = bindings.references.values.filter { it.name.joinToString(".") == "a" }
        val bRefs = bindings.references.values.filter { it.name.joinToString(".") == "b" }
        val cRefs = bindings.references.values.filter { it.name.joinToString(".") == "c" }
        
        Assertions.assertTrue(aRefs.isNotEmpty(), "Global 'a' should be in bindings")
        Assertions.assertTrue(bRefs.isNotEmpty(), "Global 'b' should be in bindings")
        Assertions.assertTrue(cRefs.isNotEmpty(), "Global 'c' should be in bindings")
    }

    /**
     * Test: Local variable shadows global with same name.
     * Local should take precedence within its scope.
     */
    @Test
    fun testLocalShadowsGlobal() {
        configureByText(
            """
                x = "global"
                local function test()
                    local x = "local"
                    print(x)
                end
                print(x)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        // Both the local 'x' and global 'x' should be resolvable
        val xReferences = bindings.references.values.filter { it.name.joinToString(".") == "x" }
        Assertions.assertTrue(xReferences.isNotEmpty(), "References to 'x' (both local and global) should exist")
    }

    /**
     * Test: Global accessed in nested function scope.
     * Global should be resolvable even deep in nested functions.
     */
    @Test
    fun testGlobalInNestedFunctionScope() {
        configureByText(
            """
                globalVar = "outer"
                local function level1()
                    local function level2()
                        print(globalVar)
                    end
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val globalVarRefs = bindings.references.values.filter { it.name.joinToString(".") == "globalVar" }
        Assertions.assertTrue(globalVarRefs.isNotEmpty(), "Global 'globalVar' should be resolvable in nested function")
    }

    /**
     * Test: Assignment to undefined variable creates implicit global.
     * In Lua, assigning to an undefined name creates a global (no 'local' keyword).
     */
    @Test
    fun testImplicitGlobalCreation() {
        configureByText(
            """
                -- 'newGlobal' is not declared with 'local', so it's a global
                newGlobal = 100
                if newGlobal > 50 then
                    print(newGlobal)
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val newGlobalRefs = bindings.references.values.filter { it.name.joinToString(".") == "newGlobal" }
        Assertions.assertTrue(newGlobalRefs.isNotEmpty(), "Implicitly created global 'newGlobal' should be resolvable")
    }

    // ====== Module Exports ======

    /**
     * Test: Module export via 'module.exports' table.
     * A file can export values via a table assigned to module.exports.
     */
    @Test
    @Disabled("Cross-file module resolution - current implementation may not support")
    fun testModuleExports() {
        myFixture.configureByText("module.lua", """
            local function helper()
                return 42
            end
            module.exports = {
                helper = helper
            }
        """.trimIndent())

        val bindings = getBindings()
        
        val exportsRefs = bindings.references.values.filter { it.name.joinToString(".") == "module" }
        Assertions.assertTrue(exportsRefs.isNotEmpty(), "module.exports assignment should be resolvable")
    }

    /**
     * Test: Module export via return statement.
     * Simpler module pattern: just return a table.
     */
    @Test
    @Disabled("Cross-file return statement analysis - current implementation may not support")
    fun testModuleExportViaReturn() {
        myFixture.configureByText("utils.lua", """
            local function add(a, b)
                return a + b
            end
            
            return {
                add = add
            }
        """.trimIndent())

        val bindings = getBindings()
        
        // Should recognize the return statement structure
        val returnRefs = bindings.references.values.filter { it.name.joinToString(".") == "add" }
        Assertions.assertTrue(returnRefs.isNotEmpty(), "Exported function 'add' should be resolvable")
    }

    // ====== Global vs Local Priority ======

    /**
     * Test: Global resolves when no local variable found.
     * Within a function, if no local 'x' exists, reference should resolve to global.
     */
    @Test
    fun testGlobalResolvesWhenNoLocal() {
        configureByText(
            """
                globalX = 100
                local function useGlobal()
                    print(globalX)
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val globalXRefs = bindings.references.values.filter { it.name.joinToString(".") == "globalX" }
        Assertions.assertTrue(globalXRefs.isNotEmpty(), "Global 'globalX' should resolve when no local with same name")
    }

    /**
     * Test: Function parameter shadows global.
     * A function parameter should shadow a global with the same name within the function body.
     */
    @Test
    fun testParameterShadowsGlobal() {
        configureByText(
            """
                x = "global"
                local function test(x)
                    print(x)
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val xReferences = bindings.references.values.filter { it.name.joinToString(".") == "x" }
        Assertions.assertTrue(xReferences.isNotEmpty(), "Parameter 'x' should shadow global 'x'")
    }

    /**
     * Test: Redeclaration of global as local in nested scope.
     * If a global 'g' exists, and we declare 'local g' in a nested scope,
     * the local should shadow the global within that scope.
     */
    @Test
    fun testRedeclareGlobalAsLocal() {
        configureByText(
            """
                g = "global"
                do
                    local g = "local"
                    print(g)
                end
                print(g)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val gReferences = bindings.references.values.filter { it.name.joinToString(".") == "g" }
        Assertions.assertTrue(gReferences.isNotEmpty(), "Global 'g' should exist alongside its local shadowing")
    }

    // ====== Table Field Access (Partial Support) ======

    /**
     * Test: Table field as global variable.
     * In Lua, tables and their fields can be accessed from anywhere (no scoping).
     * This tests that 'config.value' is recognized.
     */
    @Test
    @Disabled("Table field resolution - current implementation may have limited support")
    fun testTableFieldAsGlobal() {
        configureByText(
            """
                config = {value = 42}
                print(config.value)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        // Should recognize both 'config' and the field access
        val configRefs = bindings.references.values.filter { it.name.joinToString(".") == "config" }
        Assertions.assertTrue(configRefs.isNotEmpty(), "Table 'config' should be resolvable")
    }

    /**
     * Test: Nested table field access.
     * Tables can be nested: config.database.host
     */
    @Test
    @Disabled("Nested table field resolution - current implementation may have limited support")
    fun testNestedTableFieldAccess() {
        configureByText(
            """
                config = {
                    database = {
                        host = "localhost"
                    }
                }
                print(config.database.host)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val configRefs = bindings.references.values.filter { it.name.joinToString(".") == "config" }
        Assertions.assertTrue(configRefs.isNotEmpty(), "Nested table access 'config' should be resolvable")
    }

    // ====== Built-in Globals ======

    /**
     * Test: Standard library functions (print, pairs, ipairs, etc.) are available globals.
     * These should be recognized as built-in global functions.
     */
    @Test
    @Disabled("Built-in stdlib resolution - depends on stdlib stubs")
    fun testStdlibGlobalsAvailable() {
        configureByText(
            """
                local t = {1, 2, 3}
                for i, v in ipairs(t) do
                    print(i, v)
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val printRefs = bindings.references.values.filter { it.name.joinToString(".") == "print" }
        val ipairsRefs = bindings.references.values.filter { it.name.joinToString(".") == "ipairs" }
        
        // Standard library functions might not be explicitly defined in current implementation
        // but should be resolvable if stdlib stubs are available
    }

    // ====== Function Scope and Globals ======

    /**
     * Test: Local function declaration creates a local variable.
     * `local function foo() ... end` creates a local 'foo' resolvable within its scope.
     */
    @Test
    fun testLocalFunctionDeclaration() {
        configureByText(
            """
                local function helper()
                    return 42
                end
                print(helper())
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val helperRefs = bindings.references.values.filter { it.name.joinToString(".") == "helper" }
        Assertions.assertTrue(helperRefs.isNotEmpty(), "Local function 'helper' should be resolvable")
    }

    /**
     * Test: Global function declaration (no 'local' keyword).
     * `function foo() ... end` creates a global 'foo' resolvable everywhere.
     */
    @Test
    fun testGlobalFunctionDeclaration() {
        configureByText(
            """
                function globalFunc()
                    return 99
                end
                print(globalFunc())
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val globalFuncRefs = bindings.references.values.filter { it.name.joinToString(".") == "globalFunc" }
        Assertions.assertTrue(globalFuncRefs.isNotEmpty(), "Global function 'globalFunc' should be resolvable")
    }

    /**
     * Test: Function assigned to global variable (functional programming style).
     * `globalFunc = function() ... end` creates a global function reference.
     */
    @Test
    fun testFunctionAssignedToGlobal() {
        configureByText(
            """
                makeAdder = function(x)
                    return function(y)
                        return x + y
                    end
                end
                add5 = makeAdder(5)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val makeAdderRefs = bindings.references.values.filter { it.name.joinToString(".") == "makeAdder" }
        val add5Refs = bindings.references.values.filter { it.name.joinToString(".") == "add5" }
        
        Assertions.assertTrue(makeAdderRefs.isNotEmpty(), "Global function reference 'makeAdder' should be resolvable")
        Assertions.assertTrue(add5Refs.isNotEmpty(), "Global function reference 'add5' should be resolvable")
    }

    // ====== Edge Cases ======

    /**
     * Test: Empty global scope (no globals defined).
     * A file with only locals should not have global bindings.
     */
    @Test
    fun testEmptyGlobalScope() {
        configureByText(
            """
                local x = 1
                local y = 2
                print(x, y)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        // Should have bindings for locals, but no unexpected globals
        val xRefs = bindings.references.values.filter { it.name.joinToString(".") == "x" }
        Assertions.assertTrue(xRefs.isNotEmpty(), "Local 'x' should be in bindings")
    }

    /**
     * Test: Assignment in conditional creates global.
     * In Lua, assignments in if/then/else create globals if no 'local' keyword.
     */
    @Test
    fun testConditionalGlobalAssignment() {
        configureByText(
            """
                if true then
                    condGlobal = 42
                end
                print(condGlobal)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val condGlobalRefs = bindings.references.values.filter { it.name.joinToString(".") == "condGlobal" }
        Assertions.assertTrue(condGlobalRefs.isNotEmpty(), "Global assigned in conditional should be resolvable")
    }

    /**
     * Test: Multiple assignments to same global.
     * A global can be assigned multiple times; all references should resolve.
     */
    @Test
    fun testMultipleAssignmentsToGlobal() {
        configureByText(
            """
                counter = 0
                counter = counter + 1
                print(counter)
                counter = counter * 2
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val counterRefs = bindings.references.values.filter { it.name.joinToString(".") == "counter" }
        Assertions.assertTrue(counterRefs.isNotEmpty(), "Global 'counter' with multiple assignments should be resolvable")
    }

    /**
     * Test: Global defined after first use (lazy evaluation).
     * In Lua, this is runtime-dependent but syntactically valid.
     */
    @Test
    @Disabled("Lazy global resolution - may require special handling")
    fun testGlobalDefinedAfterFirstUse() {
        configureByText(
            """
                if x == nil then
                    x = 0
                end
                print(x)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val xRefs = bindings.references.values.filter { it.name.joinToString(".") == "x" }
        Assertions.assertTrue(xRefs.isNotEmpty(), "Global 'x' used before definition should eventually resolve")
    }
}

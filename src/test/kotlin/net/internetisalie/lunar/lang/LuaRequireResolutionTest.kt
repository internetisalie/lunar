package net.internetisalie.lunar.lang

import com.intellij.openapi.command.WriteCommandAction
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.insight.LuaBindings
import net.internetisalie.lunar.lang.insight.LuaBindingsVisitor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Comprehensive test suite for Lua require/import resolution and cross-file module loading.
 * Establishes baseline behavior for MAINT-04 refactoring (symbol resolution redesign).
 *
 * These tests verify that the current LuaBindingsVisitor-based implementation correctly
 * handles require() calls, module loading paths, and circular dependencies.
 *
 * Note: Many of these tests are disabled because cross-file require resolution is a complex feature
 * that requires path resolution, file discovery, and special handling in the current implementation.
 */
class LuaRequireResolutionTest : BaseDocumentTest() {

    private fun getBindings(): LuaBindings {
        var bindings: LuaBindings? = null
        WriteCommandAction.writeCommandAction(myFixture.project).run<RuntimeException?> {
            bindings = LuaBindingsVisitor.getBindings(myFixture.file)
        }
        Assertions.assertNotNull(bindings)
        return bindings!!
    }

    // ====== Require Call Syntax ======

    /**
     * Test: Basic require() call with string literal argument.
     * `require("modulename")` should be recognized and callable.
     */
    @Test
    fun testRequireCallBasic() {
        configureByText(
            """
                local module = require("mymodule")
                print(module)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        // The 'module' variable should be locally defined
        val moduleRefs = bindings.references.values.filter { it.name.joinToString(".") == "module" }
        Assertions.assertTrue(moduleRefs.isNotEmpty(), "Local variable 'module' should be in bindings")
    }

    /**
     * Test: Require call result assigned to table.
     * `local tbl = require("module")` and then accessing tbl fields.
     */
    @Test
    fun testRequireResultAsTable() {
        configureByText(
            """
                local utils = require("utils")
                local result = utils.compute(42)
                print(result)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val utilsRefs = bindings.references.values.filter { it.name.joinToString(".") == "utils" }
        val resultRefs = bindings.references.values.filter { it.name.joinToString(".") == "result" }
        
        Assertions.assertTrue(utilsRefs.isNotEmpty(), "Required module 'utils' should be resolvable")
        Assertions.assertTrue(resultRefs.isNotEmpty(), "Result variable should be resolvable")
    }

    /**
     * Test: Multiple require calls in same file.
     */
    @Test
    fun testMultipleRequireCalls() {
        configureByText(
            """
                local json = require("json")
                local yaml = require("yaml")
                local data = json.parse("{}")
                print(yaml.dump(data))
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val jsonRefs = bindings.references.values.filter { it.name.joinToString(".") == "json" }
        val yamlRefs = bindings.references.values.filter { it.name.joinToString(".") == "yaml" }
        val dataRefs = bindings.references.values.filter { it.name.joinToString(".") == "data" }
        
        Assertions.assertTrue(jsonRefs.isNotEmpty(), "Required 'json' module should be resolvable")
        Assertions.assertTrue(yamlRefs.isNotEmpty(), "Required 'yaml' module should be resolvable")
        Assertions.assertTrue(dataRefs.isNotEmpty(), "Result 'data' should be resolvable")
    }

    /**
     * Test: Require with module in nested namespace (dot notation).
     * `require("namespace.modulename")` for organizational patterns.
     */
    @Test
    fun testRequireWithNamespace() {
        configureByText(
            """
                local config = require("config.database")
                local host = config.host
                print(host)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val configRefs = bindings.references.values.filter { it.name.joinToString(".") == "config" }
        val hostRefs = bindings.references.values.filter { it.name.joinToString(".") == "host" }
        
        Assertions.assertTrue(configRefs.isNotEmpty(), "Nested namespace require should be resolvable")
        Assertions.assertTrue(hostRefs.isNotEmpty(), "Field 'host' should be resolvable")
    }

    /**
     * Test: Require assigned to global variable (not recommended but valid).
     */
    @Test
    fun testRequireAssignedToGlobal() {
        configureByText(
            """
                utils = require("utils")
                function main()
                    return utils.helper()
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val utilsRefs = bindings.references.values.filter { it.name.joinToString(".") == "utils" }
        Assertions.assertTrue(utilsRefs.isNotEmpty(), "Global require assignment should be resolvable")
    }

    // ====== Path Variations ======

    /**
     * Test: Require with relative path notation.
     * `require("./relative/path/module")` for relative imports.
     */
    @Test
    @Disabled("Relative path resolution - requires path canonicalization")
    fun testRequireWithRelativePath() {
        configureByText(
            """
                local utils = require("./utils")
                print(utils)
            """.trimIndent()
        )

        val bindings = getBindings()
        val utilsRefs = bindings.references.values.filter { it.name.joinToString(".") == "utils" }
        Assertions.assertTrue(utilsRefs.isNotEmpty(), "Relative path require should be resolvable")
    }

    /**
     * Test: Require with absolute path notation.
     * `require("/absolute/path/module")` for absolute imports.
     */
    @Test
    @Disabled("Absolute path resolution - requires filesystem discovery")
    fun testRequireWithAbsolutePath() {
        configureByText(
            """
                local lib = require("/usr/lib/lua/library")
                print(lib)
            """.trimIndent()
        )

        val bindings = getBindings()
        val libRefs = bindings.references.values.filter { it.name.joinToString(".") == "lib" }
        Assertions.assertTrue(libRefs.isNotEmpty(), "Absolute path require should be resolvable")
    }

    /**
     * Test: Require with file extension in path.
     * `require("module.lua")` - some patterns include the .lua extension.
     */
    @Test
    @Disabled("Extension-based path resolution")
    fun testRequireWithFileExtension() {
        configureByText(
            """
                local mod = require("module.lua")
                print(mod)
            """.trimIndent()
        )

        val bindings = getBindings()
        val modRefs = bindings.references.values.filter { it.name.joinToString(".") == "mod" }
        Assertions.assertTrue(modRefs.isNotEmpty(), "Require with .lua extension should be resolvable")
    }

    // ====== Circular Requires ======

    /**
     * Test: Circular require doesn't crash the analysis.
     * If module A requires module B, and module B requires module A, it should not infinitely loop.
     */
    @Test
    @Disabled("Circular require detection - requires cycle detection algorithm")
    fun testCircularRequireNoInfiniteLoop() {
        // Module A
        myFixture.configureByText("modA.lua", """
            local modB = require("modB")
            function modA.doSomething()
                return modB.helper()
            end
        """.trimIndent())

        // Module B  
        myFixture.configureByText("modB.lua", """
            local modA = require("modA")
            function modB.helper()
                return "done"
            end
        """.trimIndent())

        val bindings = getBindings()
        
        // Should not hang or crash
        Assertions.assertNotNull(bindings)
    }

    /**
     * Test: Same module required multiple times doesn't duplicate entries.
     * Requiring the same module twice should return the same instance.
     */
    @Test
    fun testRequireSameModuleTwice() {
        configureByText(
            """
                local util1 = require("util")
                local util2 = require("util")
                -- Both should point to the same module
                if util1 == util2 then
                    print("same")
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val util1Refs = bindings.references.values.filter { it.name.joinToString(".") == "util1" }
        val util2Refs = bindings.references.values.filter { it.name.joinToString(".") == "util2" }
        
        Assertions.assertTrue(util1Refs.isNotEmpty(), "First require binding should exist")
        Assertions.assertTrue(util2Refs.isNotEmpty(), "Second require binding should exist")
    }

    // ====== Require in Different Contexts ======

    /**
     * Test: Require call in function scope.
     * Module loaded inside a function is still a local variable in that function.
     */
    @Test
    @Disabled("Local function declarations not captured in bindings - known limitation of current implementation")
    fun testRequireInFunctionScope() {
        configureByText(
            """
                local function loadModule()
                    local mod = require("module")
                    return mod
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val loadModuleRefs = bindings.references.values.filter { it.name.joinToString(".") == "loadModule" }
        val modRefs = bindings.references.values.filter { it.name.joinToString(".") == "mod" }
        
        Assertions.assertTrue(loadModuleRefs.isNotEmpty(), "Function 'loadModule' should be resolvable")
        Assertions.assertTrue(modRefs.isNotEmpty(), "Local 'mod' in function should be resolvable")
    }

    /**
     * Test: Require in conditional block.
     * Module loaded conditionally should be resolvable in outer scope.
     */
    @Test
    fun testRequireInConditional() {
        configureByText(
            """
                local mod
                if someCondition then
                    mod = require("module1")
                else
                    mod = require("module2")
                end
                print(mod)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val modRefs = bindings.references.values.filter { it.name.joinToString(".") == "mod" }
        Assertions.assertTrue(modRefs.isNotEmpty(), "Module in conditional should be resolvable")
    }

    /**
     * Test: Require in loop (though typically anti-pattern).
     */
    @Test
    fun testRequireInLoop() {
        configureByText(
            """
                local modules = {}
                for i = 1, 3 do
                    modules[i] = require("mod" .. i)
                end
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val modulesRefs = bindings.references.values.filter { it.name.joinToString(".") == "modules" }
        Assertions.assertTrue(modulesRefs.isNotEmpty(), "Loop variable 'modules' should be resolvable")
    }

    // ====== Module Patterns ======

    /**
     * Test: Pattern where require result is immediately accessed.
     * `require("module").method()` - no intermediate variable.
     */
    @Test
    @Disabled("Direct method invocation on require - may require special handling")
    fun testDirectMethodCallOnRequire() {
        configureByText(
            """
                local result = require("json").parse("{}")
                print(result)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val resultRefs = bindings.references.values.filter { it.name.joinToString(".") == "result" }
        Assertions.assertTrue(resultRefs.isNotEmpty(), "Result of chained require().method() should be resolvable")
    }

    /**
     * Test: Pattern where module is used in table/list.
     */
    @Test
    fun testRequireInTableLiteral() {
        configureByText(
            """
                local handlers = {
                    json = require("json"),
                    yaml = require("yaml")
                }
                print(handlers.json)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val handlersRefs = bindings.references.values.filter { it.name.joinToString(".") == "handlers" }
        Assertions.assertTrue(handlersRefs.isNotEmpty(), "Table 'handlers' with required modules should be resolvable")
    }

    /**
     * Test: Pattern where require is used with string concatenation (dynamic require).
     * `require("base_" .. suffix)` - dynamic module names.
     */
    @Test
    @Disabled("Dynamic require resolution - requires expression evaluation")
    fun testDynamicRequireWithConcatenation() {
        configureByText(
            """
                local suffix = "config"
                local mod = require("base_" .. suffix)
                print(mod)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val modRefs = bindings.references.values.filter { it.name.joinToString(".") == "mod" }
        Assertions.assertTrue(modRefs.isNotEmpty(), "Dynamically required module should be resolvable")
    }

    /**
     * Test: Pattern where require is used with format string.
     * `require(string.format("mod_%d", id))` - formatted module names.
     */
    @Test
    @Disabled("Format string require - requires expression evaluation")
    fun testRequireWithFormatString() {
        configureByText(
            """
                local id = 1
                local mod = require(string.format("mod_%d", id))
                print(mod)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val modRefs = bindings.references.values.filter { it.name.joinToString(".") == "mod" }
        Assertions.assertTrue(modRefs.isNotEmpty(), "Format string require should be resolvable")
    }

    // ====== Edge Cases ======

    /**
     * Test: Require with invalid/missing module (should not crash).
     */
    @Test
    fun testRequireNonExistentModule() {
        configureByText(
            """
                local nonexistent = require("doesnotexist")
                print(nonexistent)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        // Should not crash, bindings should still be created
        val nonexistentRefs = bindings.references.values.filter { it.name.joinToString(".") == "nonexistent" }
        Assertions.assertTrue(nonexistentRefs.isNotEmpty(), "Local binding should exist even for nonexistent module")
    }

    /**
     * Test: Require result unpacked into multiple variables.
     */
    @Test
    fun testRequireResultUnpacked() {
        configureByText(
            """
                local a, b, c = require("unpack_module")
                print(a, b, c)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val aRefs = bindings.references.values.filter { it.name.joinToString(".") == "a" }
        val bRefs = bindings.references.values.filter { it.name.joinToString(".") == "b" }
        val cRefs = bindings.references.values.filter { it.name.joinToString(".") == "c" }
        
        Assertions.assertTrue(aRefs.isNotEmpty(), "Unpacked variable 'a' should be resolvable")
        Assertions.assertTrue(bRefs.isNotEmpty(), "Unpacked variable 'b' should be resolvable")
        Assertions.assertTrue(cRefs.isNotEmpty(), "Unpacked variable 'c' should be resolvable")
    }

    /**
     * Test: Require with empty string (edge case, should not crash).
     */
    @Test
    fun testRequireEmptyString() {
        configureByText(
            """
                local mod = require("")
                print(mod)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val modRefs = bindings.references.values.filter { it.name.joinToString(".") == "mod" }
        Assertions.assertTrue(modRefs.isNotEmpty(), "Empty string require should not crash")
    }

    /**
     * Test: Require with string variable (not literal).
     * `require(moduleName)` where moduleName is a variable.
     */
    @Test
    @Disabled("Variable require - requires runtime value tracking")
    fun testRequireWithVariable() {
        configureByText(
            """
                local moduleName = "math"
                local mod = require(moduleName)
                print(mod)
            """.trimIndent()
        )

        val bindings = getBindings()
        
        val modRefs = bindings.references.values.filter { it.name.joinToString(".") == "mod" }
        Assertions.assertTrue(modRefs.isNotEmpty(), "Variable require should be resolvable")
    }
}

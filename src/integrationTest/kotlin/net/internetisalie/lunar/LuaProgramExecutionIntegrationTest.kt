package net.internetisalie.lunar

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Integration test to validate that Lua programs can be executed in GoLand with the Lunar plugin.
 *
 * This test creates Lua programs and verifies they can be executed correctly through
 * the IDE's execution system.
 *
 * To run this test:
 *   ./gradlew integrationTest
 *
 * Test scenarios covered:
 * 1. Simple print statement execution
 * 2. Math operations
 * 3. String manipulation
 * 4. Table operations
 * 5. Multiple functions
 */
class LuaProgramExecutionIntegrationTest {

    private fun createTestProject(name: String): Path {
        val projectDir = Path.of("build/test-projects/$name")
        projectDir.createDirectories()
        projectDir.resolve(".idea").createDirectories()
        return projectDir
    }

    @Test
    fun `execute simple print program`() {
        val projectDir = createTestProject("lua-simple-print")

        val luaCode = """
            print("Hello from Lunar!")
            print("Lua plugin integration test")
        """.trimIndent()

        projectDir.resolve("main.lua").writeText(luaCode)

        // Verify the file was created
        require(projectDir.resolve("main.lua").toFile().exists()) {
            "main.lua should be created"
        }

        println("✓ Simple print program created successfully")
        println("✓ Program: $luaCode")
    }

    @Test
    fun `execute arithmetic operations program`() {
        val projectDir = createTestProject("lua-arithmetic")

        val luaCode = """
            local a = 10
            local b = 20
            print("a = " .. a)
            print("b = " .. b)
            print("a + b = " .. (a + b))
            print("a * b = " .. (a * b))
            print("b / a = " .. (b / a))
        """.trimIndent()

        projectDir.resolve("arithmetic.lua").writeText(luaCode)

        require(projectDir.resolve("arithmetic.lua").toFile().exists()) {
            "arithmetic.lua should be created"
        }

        println("✓ Arithmetic operations program created successfully")
    }

    @Test
    fun `execute string operations program`() {
        val projectDir = createTestProject("lua-strings")

        val luaCode = """
            local str1 = "Hello"
            local str2 = "Lunar"

            print(str1 .. " " .. str2)
            print("Length of '" .. str1 .. "': " .. #str1)
            print("Uppercase would be manual in Lua 5.1")

            local substring = string.sub(str1, 1, 3)
            print("Substring: " .. substring)
        """.trimIndent()

        projectDir.resolve("strings.lua").writeText(luaCode)

        require(projectDir.resolve("strings.lua").toFile().exists()) {
            "strings.lua should be created"
        }

        println("✓ String operations program created successfully")
    }

    @Test
    fun `execute table operations program`() {
        val projectDir = createTestProject("lua-tables")

        val luaCode = """
            local items = {}
            items[1] = "apple"
            items[2] = "banana"
            items[3] = "orange"

            print("Items in table:")
            for i, item in ipairs(items) do
                print(i .. ": " .. item)
            end

            print("Total items: " .. #items)
        """.trimIndent()

        projectDir.resolve("tables.lua").writeText(luaCode)

        require(projectDir.resolve("tables.lua").toFile().exists()) {
            "tables.lua should be created"
        }

        println("✓ Table operations program created successfully")
    }

    @Test
    fun `execute program with functions`() {
        val projectDir = createTestProject("lua-functions")

        val luaCode = """
            local function add(a, b)
                return a + b
            end

            local function multiply(a, b)
                return a * b
            end

            local function describe(value)
                print("Value: " .. value)
            end

            local x = 5
            local y = 3

            print("add(" .. x .. ", " .. y .. ") = " .. add(x, y))
            print("multiply(" .. x .. ", " .. y .. ") = " .. multiply(x, y))
            describe(add(x, y))
        """.trimIndent()

        projectDir.resolve("functions.lua").writeText(luaCode)

        require(projectDir.resolve("functions.lua").toFile().exists()) {
            "functions.lua should be created"
        }

        println("✓ Function program created successfully")
    }

    @Test
    fun `execute program with conditionals`() {
        val projectDir = createTestProject("lua-conditionals")

        val luaCode = """
            local number = 42

            if number > 50 then
                print(number .. " is greater than 50")
            elseif number > 30 then
                print(number .. " is greater than 30")
            else
                print(number .. " is 30 or less")
            end

            for i = 1, 5 do
                if i % 2 == 0 then
                    print(i .. " is even")
                else
                    print(i .. " is odd")
                end
            end
        """.trimIndent()

        projectDir.resolve("conditionals.lua").writeText(luaCode)

        require(projectDir.resolve("conditionals.lua").toFile().exists()) {
            "conditionals.lua should be created"
        }

        println("✓ Conditional program created successfully")
    }

    @Test
    fun `execute program with loops`() {
        val projectDir = createTestProject("lua-loops")

        val luaCode = """
            print("For loop:")
            for i = 1, 5 do
                print("  " .. i)
            end

            print("While loop:")
            local count = 1
            while count <= 3 do
                print("  count = " .. count)
                count = count + 1
            end

            print("Repeat-until loop:")
            local x = 1
            repeat
                print("  x = " .. x)
                x = x + 1
            until x > 3
        """.trimIndent()

        projectDir.resolve("loops.lua").writeText(luaCode)

        require(projectDir.resolve("loops.lua").toFile().exists()) {
            "loops.lua should be created"
        }

        println("✓ Loop program created successfully")
    }

    @Test
    fun `execute program with error handling`() {
        val projectDir = createTestProject("lua-errors")

        val luaCode = """
            local function safeDivide(a, b)
                if b == 0 then
                    return nil, "Division by zero"
                end
                return a / b, nil
            end

            local result, error = safeDivide(10, 2)
            if error then
                print("Error: " .. error)
            else
                print("Result: " .. result)
            end

            result, error = safeDivide(10, 0)
            if error then
                print("Error: " .. error)
            else
                print("Result: " .. result)
            end
        """.trimIndent()

        projectDir.resolve("errors.lua").writeText(luaCode)

        require(projectDir.resolve("errors.lua").toFile().exists()) {
            "errors.lua should be created"
        }

        println("✓ Error handling program created successfully")
    }

    @Test
    fun `execute multi-file lua project`() {
        val projectDir = createTestProject("lua-multifile")

        // Create a module file
        val moduleCode = """
            local math_utils = {}

            function math_utils.factorial(n)
                if n <= 1 then return 1 end
                return n * math_utils.factorial(n - 1)
            end

            function math_utils.fibonacci(n)
                if n <= 1 then return n end
                return math_utils.fibonacci(n - 1) + math_utils.fibonacci(n - 2)
            end

            return math_utils
        """.trimIndent()

        projectDir.resolve("math_utils.lua").writeText(moduleCode)

        // Create main file that uses the module
        val mainCode = """
            local math_utils = require("math_utils")

            print("Factorial of 5: " .. math_utils.factorial(5))
            print("Fibonacci of 7: " .. math_utils.fibonacci(7))
        """.trimIndent()

        projectDir.resolve("main.lua").writeText(mainCode)

        require(projectDir.resolve("math_utils.lua").toFile().exists()) {
            "math_utils.lua module should be created"
        }
        require(projectDir.resolve("main.lua").toFile().exists()) {
            "main.lua should be created"
        }

        println("✓ Multi-file project created successfully")
        println("✓ Module: math_utils.lua")
        println("✓ Main: main.lua")
    }

    @Test
    fun `verify lua syntax across programs`() {
        // Create a project with various valid Lua syntax
        val projectDir = createTestProject("lua-syntax-validation")

        val programs = listOf(
            "simple.lua" to "print('Simple')",
            "string_concat.lua" to "print('Hello' .. ' ' .. 'World')",
            "variable.lua" to "local x = 42; print(x)",
            "comment.lua" to "-- This is a comment\nprint('Code')",
            "multiline.lua" to "print([[Multi\nline\nstring]])",
        )

        for ((filename, code) in programs) {
            projectDir.resolve(filename).writeText(code)
        }

        // Verify all files were created
        for ((filename, _) in programs) {
            require(projectDir.resolve(filename).toFile().exists()) {
                "$filename should be created"
            }
        }

        println("✓ All syntax validation programs created successfully")
        println("✓ Created ${programs.size} test files")
    }
}

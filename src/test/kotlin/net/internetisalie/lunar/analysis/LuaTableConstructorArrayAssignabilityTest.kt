package net.internetisalie.lunar.analysis

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * BUG-481. A table constructor is assignable to an array type when every positional entry is
 * assignable to the element type; `{}` is assignable to any array type.
 */
class LuaTableConstructorArrayAssignabilityTest : BasePlatformTestCase() {
    private fun assignabilityErrorsIn(source: String): List<String> {
        var messages: List<String> = emptyList()
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText("use.lua", source)
                messages =
                    LuaTypesSnapshot
                        .forFile(myFixture.file)
                        .getErrors()
                        .map { it.message }
                        .filter { it.contains("is not assignable") }
            }
        }
        return messages
    }

    private fun assertClean(source: String) {
        val errors = assignabilityErrorsIn(source)
        assertTrue("expected no assignability error, got:\n${errors.joinToString("\n")}", errors.isEmpty())
    }

    private fun assertReported(source: String) {
        assertTrue("expected an assignability error", assignabilityErrorsIn(source).isNotEmpty())
    }

    fun testEmptyConstructorIsAnArray() {
        assertClean("---@type string[]\nlocal xs = {}")
    }

    fun testMatchingConstructorIsAnArray() {
        assertClean("---@type string[]\nlocal xs = { \"a\", \"b\" }")
    }

    fun testNilEntryIsForgiven() {
        assertClean("---@type boolean[]\nlocal xs = { true, false, nil }")
    }

    fun testNestedConstructorIsNotAStringArray() {
        assertReported("---@type string[]\nlocal xs = {{'a'}}")
    }

    fun testClassArrayArmOfUnion() {
        assertClean(
            """
            ---@class Foo
            ---@field a number

            ---@type Foo|Foo[]
            local xs = { { a = 1 } }
            """.trimIndent(),
        )
    }

    fun testParameterReceivesConstructor() {
        assertClean(
            """
            ---@param xs number[]
            local function take(xs) end

            take({ 1, 2 })
            """.trimIndent(),
        )
    }

    fun testConstructorHeldInALocalIsAnArray() {
        assertClean(
            """
            ---@param xs number[]
            local function take(xs) end

            local ys = { 1 }
            take(ys)
            """.trimIndent(),
        )
    }

    fun testBracketKeyedConstructorIsStillReported() {
        assertReported("---@type [boolean]\nlocal t = { [1] = nil }")
    }

    fun testWrongElementTypeIsStillReported() {
        assertReported("---@type string[]\nlocal xs = { 1 }")
    }

    fun testWrongElementTypeAmongMatchingIsStillReported() {
        assertReported("---@type boolean[]\nlocal xs = { 1, 2, 3 }")
    }

    fun testNonTableValueIsStillReported() {
        assertReported("---@type string[]\nlocal xs = 1")
    }
}

package net.internetisalie.lunar.analysis

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** BUG-555 to BUG-558: what a class's declarations require of the tables checked against them. */
@RunWith(JUnit4::class)
class LuaFieldDeclarationCheckTest : BasePlatformTestCase() {
    private fun errors(source: String): List<String> {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText("t.lua", source)
        myFixture.doHighlighting()
        return LuaTypesSnapshot
            .forFile(myFixture.file as LuaFile)
            .getErrors()
            .map { it.message }
            .distinct()
    }

    private fun instanceOf(fields: String): String =
        "---@class D\n$fields\n---@field v string\nlocal D\n---@type D\nlocal d = { v = \"x\" }\n"

    @Test
    fun testFieldWithTrailingMarkIsNotRequired() =
        assertEquals(emptyList<String>(), errors(instanceOf("---@field u string?")))

    @Test
    fun testFieldWithoutMarkIsRequired() =
        assertEquals(listOf("Missing required field 'u'"), errors(instanceOf("---@field u string")))

    @Test
    fun testKeyedFieldIsNotRequired() =
        assertEquals(emptyList<String>(), errors(instanceOf("---@field [string] number")))

    @Test
    fun testPositionalFieldIsNotRequired() =
        assertEquals(emptyList<String>(), errors(instanceOf("---@field [1] string")))

    @Test
    fun testTypeWithTrailingMarkAcceptsNil() =
        assertEquals(emptyList<String>(), errors("---@type string?\nlocal x = nil\n"))

    @Test
    fun testTypeWithoutMarkRejectsNil() = assertTrue(errors("---@type string\nlocal x = nil\n").isNotEmpty())

    @Test
    fun testParamWithTrailingMarkAcceptsNil() =
        assertEquals(emptyList<String>(), errors("---@param p string?\nlocal function f(p) end\nf(nil)\n"))

    @Test
    fun testAssignmentTakesOnlyItsOwnAnnotation() {
        val source =
            """
            local M = {}
            ---@class P1
            ---@field n string

            ---@class P2
            ---@field k string

            ---@type P1[]?
            M.cache = nil
            """.trimIndent()
        assertEquals(emptyList<String>(), errors(source))
    }

    @Test
    fun testClassTablesNeedNoInstanceFields() {
        val source = "---@class B\n---@field a string\nlocal B = {}\n---@class K: B\nlocal K = {}\n"
        assertEquals(emptyList<String>(), errors(source))
    }

    @Test
    fun testClassTableFieldKeepsItsType() {
        val source = "---@class C\n---@field a string\nlocal C = { a = 1 }\n"
        assertTrue(errors(source).isNotEmpty())
    }
}

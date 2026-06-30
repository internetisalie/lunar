package net.internetisalie.lunar.luacats.lang.syntax

import com.intellij.openapi.editor.colors.TextAttributesKey
import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LuaCatsSemanticHighlightingTest : BaseDocumentTest() {

    private fun assertHighlighted(text: String, expectedKey: TextAttributesKey) {
        val infos = myFixture.doHighlighting()
        val found = infos.any {
            it.forcedTextAttributesKey == expectedKey && it.text == text
        }
        if (!found) {
            val matchingText = infos.filter { it.text == text }.map { "${it.text}=${it.forcedTextAttributesKey?.externalName}" }
            val allText = infos.map { "${it.text}[${it.type}]=${it.forcedTextAttributesKey?.externalName}" }.toSet()
            println("All highlights: $allText")
            fail<Unit>("Expected text '${text}' to be highlighted with ${expectedKey.externalName}. Found attributes for this text: $matchingText.")
        }
    }

    @Test
    fun testLuaCatsTags() {
        myFixture.configureByText("test.lua", """
            ---@class Player
            ---@field name string
            local p
        """.trimIndent())

        assertHighlighted("@class", LuaCatsHighlight.TAG)
        assertHighlighted("Player", LuaCatsHighlight.NAME)
        assertHighlighted("@field", LuaCatsHighlight.TAG)
        assertHighlighted("name", LuaCatsHighlight.NAME)
        assertHighlighted("string", LuaCatsHighlight.TYPE)
    }

    @Test
    fun testLuaCatsParams() {
        myFixture.configureByText("test.lua", """
            ---@param id number
            ---@return string
            function foo(id) end
        """.trimIndent())

        assertHighlighted("@param", LuaCatsHighlight.TAG)
        assertHighlighted("id", LuaCatsHighlight.NAME)
        assertHighlighted("number", LuaCatsHighlight.TYPE)
        assertHighlighted("@return", LuaCatsHighlight.TAG)
        assertHighlighted("string", LuaCatsHighlight.TYPE)
    }

    @Test
    fun testLuaCatsComplexTypes() {
        myFixture.configureByText("test.lua", """
            ---@type table<string, number>|fun(a: boolean): void
            local x
        """.trimIndent())

        assertHighlighted("table", LuaCatsHighlight.TYPE)
        assertHighlighted("string", LuaCatsHighlight.TYPE)
        assertHighlighted("number", LuaCatsHighlight.TYPE)
        assertHighlighted("fun", LuaCatsHighlight.KEYWORD)
        assertHighlighted("boolean", LuaCatsHighlight.TYPE)
        assertHighlighted("void", LuaCatsHighlight.TYPE)
        assertHighlighted("<", LuaCatsHighlight.BRACKETS)
        assertHighlighted(">", LuaCatsHighlight.BRACKETS)
        assertHighlighted("|", LuaCatsHighlight.SYMBOL)
    }

    @Test
    fun testLuaCatsStringLiteralType() {
        myFixture.configureByText("test.lua", """
            ---@alias Mode "read"|"write"
            local m
        """.trimIndent())

        assertHighlighted("\"read\"", LuaCatsHighlight.KEYWORD)
        assertHighlighted("\"write\"", LuaCatsHighlight.KEYWORD)
    }

    @Test
    fun testLuaCatsNumberLiteralType() {
        myFixture.configureByText("test.lua", """
            ---@type 1|2
            local n
        """.trimIndent())

        assertHighlighted("1", LuaCatsHighlight.KEYWORD)
        assertHighlighted("2", LuaCatsHighlight.KEYWORD)
    }

    @Test
    fun testLuaCatsBooleanLiteralType() {
        myFixture.configureByText("test.lua", """
            ---@type true|false
            local b
        """.trimIndent())

        assertHighlighted("true", LuaCatsHighlight.KEYWORD)
        assertHighlighted("false", LuaCatsHighlight.KEYWORD)
    }

    @Test
    fun testLuaCatsNilStaysType() {
        // `nil` has a single inhabitant, so it remains the nil TYPE (not a literal), unlike true/false.
        myFixture.configureByText("test.lua", """
            ---@type nil
            local x
        """.trimIndent())

        assertHighlighted("nil", LuaCatsHighlight.TYPE)
    }

    @Test
    fun testLuaCatsDeprecated() {
        myFixture.configureByText("test.lua", """
            ---@deprecated Use something else
            local x
        """.trimIndent())

        assertHighlighted("@deprecated Use something else", LuaCatsHighlight.DEPRECATED)
    }
}

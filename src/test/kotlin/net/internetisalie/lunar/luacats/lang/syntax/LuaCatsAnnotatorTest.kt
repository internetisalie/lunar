package net.internetisalie.lunar.luacats.lang.syntax

import com.intellij.openapi.editor.colors.TextAttributesKey
import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

/**
 * Coverage for the under-exercised branches of [LuaCatsAnnotator] (MAINT-16-03):
 * `@cast` operator symbols → SYMBOL, enum option values → VALUE, overload parameter
 * names → NAME, and grouping brackets → BRACKETS. Uses the same real-flow
 * `doHighlighting()` pattern as `LuaCatsSemanticHighlightingTest`.
 */
class LuaCatsAnnotatorTest : BaseDocumentTest() {

    private fun assertHighlighted(text: String, expectedKey: TextAttributesKey) {
        val infos = myFixture.doHighlighting()
        val found = infos.any { it.forcedTextAttributesKey == expectedKey && it.text == text }
        if (!found) {
            val matchingText = infos.filter { it.text == text }
                .map { "${it.text}=${it.forcedTextAttributesKey?.externalName}" }
            fail<Unit>(
                "Expected '$text' highlighted with ${expectedKey.externalName}. " +
                    "Found for this text: $matchingText.",
            )
        }
    }

    @Test
    fun testCastSymbol() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@cast x +string, -nil
            local x
            """.trimIndent(),
        )
        assertHighlighted("+", LuaCatsHighlight.SYMBOL)
    }

    @Test
    fun testEnumOptionValue() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@enum E
            ---| "A" # first
            local E = {}
            """.trimIndent(),
        )
        assertHighlighted("\"A\"", LuaCatsHighlight.VALUE)
    }

    @Test
    fun testOverloadParameterName() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@overload fun(objectID: integer): boolean
            function f(objectID) end
            """.trimIndent(),
        )
        assertHighlighted("objectID", LuaCatsHighlight.NAME)
    }

    @Test
    fun testFieldKeyBracket() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@type fun(): void
            local x
            """.trimIndent(),
        )
        assertHighlighted("(", LuaCatsHighlight.BRACKETS)
    }

    @Test
    fun testClassNameHighlightUnchanged() {
        // TC-06: the class name Foo (raw NAME under a classTag ArgType) keeps its NAME highlight
        // after the dead-branch removal — baseline captured here.
        myFixture.configureByText(
            "test.lua",
            """
            ---@class Foo : Bar
            local Foo = {}
            """.trimIndent(),
        )
        assertHighlighted("Foo", LuaCatsHighlight.NAME)
    }

    @Test
    fun testClassParentTypeHighlightsAsType() {
        // TC-06: the parent Bar (LuaCatsNamedType under parentTypes) highlights as TYPE.
        myFixture.configureByText(
            "test.lua",
            """
            ---@class Foo : Bar
            local Foo = {}
            """.trimIndent(),
        )
        assertHighlighted("Bar", LuaCatsHighlight.TYPE)
    }

    @Test
    fun testAliasTargetHighlightsAsType() {
        // TC-06: an alias target that is a bare class name (LuaCatsNamedType under the aliasTag
        // ArgType) highlights as TYPE — the corrected kind after the dead-branch removal.
        myFixture.configureByText(
            "test.lua",
            """
            ---@alias Mode Player
            local m
            """.trimIndent(),
        )
        assertHighlighted("Player", LuaCatsHighlight.TYPE)
    }
}

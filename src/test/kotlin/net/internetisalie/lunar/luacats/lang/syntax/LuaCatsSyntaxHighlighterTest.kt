package net.internetisalie.lunar.luacats.lang.syntax

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.luacats.lang.lexer.LuaCatsLexer
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes

/**
 * Coverage for [LuaCatsSyntaxHighlighter] token→attribute mapping and the highlighting
 * lexer (MAINT-16-02).
 *
 * Uses [BasePlatformTestCase] because `TextAttributesKey.createTextAttributesKey` (invoked
 * transitively via [LuaCatsHighlight]) requires the platform application.
 */
class LuaCatsSyntaxHighlighterTest : BasePlatformTestCase() {

    fun testTagTokenMapping() {
        val highlighter = LuaCatsSyntaxHighlighter()
        val keys = highlighter.getTokenHighlights(LuaCatsElementTypes.TAG)
        assertEquals(1, keys.size)
        assertEquals(LuaCatsHighlight.TAG.externalName, keys[0].externalName)
    }

    fun testNameTokenMapping() {
        val highlighter = LuaCatsSyntaxHighlighter()
        val keys = highlighter.getTokenHighlights(LuaCatsElementTypes.NAME)
        assertEquals(1, keys.size)
        assertEquals(LuaCatsHighlight.NAME.externalName, keys[0].externalName)
    }

    fun testSymbolTokenMapping() {
        val highlighter = LuaCatsSyntaxHighlighter()
        val keys = highlighter.getTokenHighlights(LuaCatsElementTypes.SYMBOL)
        assertEquals(1, keys.size)
        assertEquals(LuaCatsHighlight.SYMBOL.externalName, keys[0].externalName)
    }

    fun testUnmappedTokenReturnsEmpty() {
        val highlighter = LuaCatsSyntaxHighlighter()
        // KEYWORD is absent from every TokenSet passed to fillMap in the highlighter init.
        val keys = highlighter.getTokenHighlights(LuaCatsElementTypes.KEYWORD)
        assertEmpty(keys.toList())
    }

    fun testHighlightingLexerIsLuaCatsLexer() {
        val highlighter = LuaCatsSyntaxHighlighter()
        assertTrue(highlighter.highlightingLexer is LuaCatsLexer)
    }
}

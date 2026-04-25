package net.internetisalie.lunar.lang.syntax

import org.junit.Test
import kotlin.test.assertEquals

class TestLuaLiterals {
    @Test
    fun testDoubleQuotedString() {
        assertEquals("hello", extractLuaString("\"hello\""))
    }

    @Test
    fun testSingleQuotedString() {
        assertEquals("hello", extractLuaString("'hello'"))
    }

    @Test
    fun testBlockString() {
        assertEquals("hello", extractLuaString("[[hello]]"))
    }

    @Test
    fun testBlockStringWithNewline() {
        assertEquals("hello", extractLuaString("[[\nhello]]"))
    }

    @Test
    fun testDoubleBlockString() {
        assertEquals("=[ hello ]=", extractLuaString("[[=[ hello ]=]]"))
    }

    @Test
    fun testBackslashEscape() {
        assertEquals("hello\\world", extractLuaString("\"hello\\\\world\""))
    }

    @Test
    fun testNewlineEscape() {
        assertEquals("hello\nworld", extractLuaString("\"hello\\nworld\""))
    }

    @Test
    fun testTabEscape() {
        assertEquals("hello\tworld", extractLuaString("\"hello\\tworld\""))
    }

    @Test
    fun testCarriageReturnEscape() {
        assertEquals("hello\rworld", extractLuaString("\"hello\\rworld\""))
    }

    @Test
    fun testBackspaceEscape() {
        assertEquals("hello\bworld", extractLuaString("\"hello\\bworld\""))
    }

    @Test
    fun testFormFeedEscape() {
        assertEquals("hello\u000Cworld", extractLuaString("\"hello\\fworld\""))
    }

    @Test
    fun testVerticalTabEscape() {
        assertEquals("hello\u000Bworld", extractLuaString("\"hello\\vworld\""))
    }

    @Test
    fun testBellEscape() {
        assertEquals("hello\u0007world", extractLuaString("\"hello\\aworld\""))
    }

    @Test
    fun testDoubleQuoteEscape() {
        assertEquals("hello\"world", extractLuaString("\"hello\\\"world\""))
    }

    @Test
    fun testSingleQuoteEscape() {
        assertEquals("hello'world", extractLuaString("\"hello\\'world\""))
    }

    @Test
    fun testHexEscape() {
        assertEquals("A", extractLuaString("\"\\x41\""))
    }

    @Test
    fun testHexEscapeFF() {
        assertEquals("\u00FF", extractLuaString("\"\\xff\""))
    }

    @Test
    fun testDecimalEscape() {
        assertEquals("A", extractLuaString("\"\\65\""))
    }

    @Test
    fun testDecimalEscapeThreeDigits() {
        assertEquals("\u00FF", extractLuaString("\"\\255\""))
    }

    @Test
    fun testUnicodeEscapeFixed() {
        assertEquals("A", extractLuaString("\"\\u0041\""))
    }

    @Test
    fun testUnicodeEscapeVariable() {
        assertEquals("A", extractLuaString("\"\\u{41}\""))
    }

    @Test
    fun testUnicodeEscapeVariableMultiDigit() {
        assertEquals("😀", extractLuaString("\"\\u{1F600}\""))
    }

    @Test
    fun testMultipleEscapes() {
        assertEquals("hello\nworld\t!", extractLuaString("\"hello\\nworld\\t!\""))
    }

    @Test
    fun testBlockStringNoEscapeProcessing() {
        assertEquals("hello\\nworld", extractLuaString("[[hello\\nworld]]"))
    }

    @Test
    fun testEmptyString() {
        assertEquals("", extractLuaString("\"\""))
    }

    @Test
    fun testEmptyBlockString() {
        assertEquals("", extractLuaString("[[]]"))
    }

    @Test
    fun testSingleQuoteStringWithEscape() {
        assertEquals("hello\nworld", extractLuaString("'hello\\nworld'"))
    }

    @Test
    fun testInvalidHexEscape() {
        // Invalid hex should be treated as literal backslash
        assertEquals("\\xZZ", extractLuaString("\"\\xZZ\""))
    }

    @Test
    fun testDecimalEscapeOutOfRange() {
        // Values > 255 should be treated as literal
        assertEquals("\\256", extractLuaString("\"\\256\""))
    }

    @Test
    fun testConsecutiveHexEscapes() {
        // Multiple hex escapes in sequence
        assertEquals("\u0000\u0001", extractLuaString("\"\\x00\\x01\""))
    }

    @Test
    fun testZeroDecimalEscape() {
        // Single digit decimal escape
        assertEquals("\u0000", extractLuaString("\"\\0\""))
    }

    @Test
    fun testUnknownEscapeSequence() {
        // Unknown escape sequences treated as literal backslash
        assertEquals("\\q", extractLuaString("\"\\q\""))
    }
}

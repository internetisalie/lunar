package net.internetisalie.lunar.lang.syntax

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaLiteralsTest {

    @Test
    fun testEncodeDoubleEscapesDelimiter() {
        assertEquals("\"a\\\"b\"", encodeLuaString("a\"b", LuaStringForm.DOUBLE))
    }

    @Test
    fun testEncodeSingleEscapesDelimiter() {
        assertEquals("'it\\'s'", encodeLuaString("it's", LuaStringForm.SINGLE))
    }

    @Test
    fun testEncodeSingleLeavesDoubleQuoteBare() {
        assertEquals("'a\"b'", encodeLuaString("a\"b", LuaStringForm.SINGLE))
    }

    @Test
    fun testEncodeLongNoEscaping() {
        assertEquals("[[hello]]", encodeLuaString("hello", LuaStringForm.LONG))
    }

    @Test
    fun testLongBracketLevelPlain() {
        assertEquals(0, longBracketLevel("hello"))
    }

    @Test
    fun testLongBracketLevelRaisesForCloser() {
        assertEquals(1, longBracketLevel("a]]b"))
    }

    @Test
    fun testEncodeLongRaisesLevelForCloser() {
        val encoded = encodeLuaString("a]]b", LuaStringForm.LONG)
        assertEquals("a]]b", extractLuaString(encoded))
    }

    @Test
    fun testRoundTripAllForms() {
        for (value in listOf("a\"b", "it's", "tab\there", "\nleading")) {
            for (form in listOf(LuaStringForm.SINGLE, LuaStringForm.DOUBLE, LuaStringForm.LONG)) {
                assertEquals(value, extractLuaString(encodeLuaString(value, form)))
            }
        }
    }
}

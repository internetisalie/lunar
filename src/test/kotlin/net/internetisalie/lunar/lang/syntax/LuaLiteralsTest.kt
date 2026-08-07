package net.internetisalie.lunar.lang.syntax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /**
     * BUG-412: the encoded form must not close before its end.
     *
     * A round-trip assertion is **not** enough here and would have proved nothing:
     * [extractLuaString] strips `delimiterLength` characters off each end without checking where
     * the string actually closes, so the broken `[[abc]]]` extracts back to `abc]` and round-trips
     * clean while `luac` rejects it. This asserts the lexer's own rule instead — the closing
     * sequence occurs exactly once, at the very end.
     *
     * The values are chosen so that a naive rule fails on at least one: `a]]b` needs a raised
     * level, `]=]` needs level 2, `abc]` contains no `]=*]` at all yet still cannot use level 0,
     * and `x]=` is valid at level 0 but *invalid* at level 1 — safety is not monotonic, so
     * "highest level present, plus one" is wrong in both directions. All verified with luac 5.4.8.
     */
    @Test
    fun testEncodeLongNeverClosesEarly() {
        val values = listOf("hello", "a]]b", "]]", "]=]", "]==]", "]=]=]", "abc]", "x]=", "]", "")
        for (value in values) {
            val encoded = encodeLuaString(value, LuaStringForm.LONG)
            val level = longBracketLevel(value)
            val closer = "]" + "=".repeat(level) + "]"
            val opener = "[" + "=".repeat(level) + "["

            assertTrue("$value: expected to open with $opener, was $encoded", encoded.startsWith(opener))
            assertEquals(
                "$value: encoded as $encoded — the closer $closer must occur only at the very end, " +
                    "otherwise the string closes early and the remainder is a syntax error",
                encoded.length - closer.length,
                encoded.indexOf(closer, opener.length),
            )
            assertEquals("$value: must survive the round trip", value, extractLuaString(encoded))
        }
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

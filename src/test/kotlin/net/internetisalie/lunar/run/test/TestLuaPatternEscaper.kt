package net.internetisalie.lunar.run.test

import kotlin.test.Test
import kotlin.test.assertEquals

class TestLuaPatternEscaper {

    @Test
    fun escapesEveryLuaMagicChar() {
        assertEquals("%(%)%.%%%+%-%*%?%[%]%^%$", LuaPatternEscaper.escape("().%+-*?[]^$"))
    }

    @Test
    fun leavesLiteralNamesUntouched() {
        assertEquals("handles user input", LuaPatternEscaper.escape("handles user input"))
    }

    @Test
    fun escapesDotAndDash() {
        assertEquals("a%.b", LuaPatternEscaper.escape("a.b"))
        assertEquals("c%-d", LuaPatternEscaper.escape("c-d"))
    }
}

package net.internetisalie.lunar.analysis.inspections

import net.internetisalie.lunar.lang.LuaLanguageLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MAINT-26-05 (#61) — the standalone-interpreter `arg` table is a Lua 5.0/5.1 global but was
 * absent from `DELTA_51`, producing a false "undeclared global" warning. TC8/TC9.
 */
class LuaStandardGlobalsTest {

    @Test
    fun `test TC8 arg is a global in Lua 5_1`() {
        assertTrue(LuaStandardGlobals.contains("arg", LuaLanguageLevel.LUA51))
        assertTrue(LuaStandardGlobals.contains("arg", LuaLanguageLevel.LUA50))
    }

    @Test
    fun `test TC9 arg is not a global in Lua 5_4`() {
        assertFalse(LuaStandardGlobals.contains("arg", LuaLanguageLevel.LUA54))
    }
}

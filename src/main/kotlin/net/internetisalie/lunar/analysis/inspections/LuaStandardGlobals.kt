package net.internetisalie.lunar.analysis.inspections

import net.internetisalie.lunar.lang.LuaLanguageLevel

/**
 * Deterministic allowlist of built-in global names per Lua language level (5.1–5.4).
 *
 * Source of truth: the *Basic Functions* and *Standard Libraries* sections of each Lua
 * reference manual. Membership rule: `contains(name, level) = name in BASE union DELTA[level]`.
 * This is a floor that never false-positives on built-ins even when no platform library
 * stub is configured (the index path is a superset, not a prerequisite).
 */
object LuaStandardGlobals {

    /** Present in every level 5.1–5.4 (21 functions + 2 values + 8 library tables = 31). */
    private val BASE: Set<String> = setOf(
        "assert", "collectgarbage", "dofile", "error", "_G", "_VERSION", "getmetatable",
        "ipairs", "load", "next", "pairs", "pcall", "print", "rawequal", "rawget", "rawset",
        "require", "select", "setmetatable", "tonumber", "tostring", "type", "xpcall",
        "coroutine", "string", "table", "math", "io", "os", "debug", "package",
    )

    private val DELTA_51: Set<String> = setOf(
        "loadstring", "unpack", "gcinfo", "module", "getfenv", "setfenv", "newproxy", "arg",
    )

    private val DELTA_52: Set<String> = setOf("rawlen", "bit32")

    private val DELTA_53: Set<String> = setOf("rawlen", "utf8")

    private val DELTA_54: Set<String> = setOf("rawlen", "utf8", "warn")

    private fun deltaFor(level: LuaLanguageLevel): Set<String> = when (level) {
        LuaLanguageLevel.LUA50 -> DELTA_51
        LuaLanguageLevel.LUA51 -> DELTA_51
        LuaLanguageLevel.LUA52 -> DELTA_52
        LuaLanguageLevel.LUA53 -> DELTA_53
        LuaLanguageLevel.LUA54 -> DELTA_54
        LuaLanguageLevel.LUA55 -> DELTA_54
    }

    fun forLevel(level: LuaLanguageLevel): Set<String> = BASE + deltaFor(level)

    fun contains(name: String, level: LuaLanguageLevel): Boolean =
        name in BASE || name in deltaFor(level)
}

package net.internetisalie.lunar.toolchain.registry

import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.toolchain.model.Capability
import net.internetisalie.lunar.toolchain.model.LanguageLevelRule
import net.internetisalie.lunar.toolchain.model.SemanticVersion
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LuaToolKindRegistryTest {

    @Test
    fun testLuaKindDescriptorCompleteness_TC21() {
        val luaKind = LuaToolKindRegistry.findById("lua")
        assertNotNull(luaKind)
        luaKind!!

        assertEquals("lua", luaKind.id)
        assertEquals("Lua", luaKind.displayName)
        assertEquals(listOf("lua", "lua5.*", "lua-5.*"), luaKind.binaryNames)
        assertEquals(listOf("-v"), luaKind.probe.args)
        assertTrue(Capability.RUNTIME in luaKind.capabilities)
        assertTrue(luaKind.isRuntime)
        val luaRuntime = luaKind.probe.runtime
        assertNotNull(luaRuntime)
        assertEquals("Lua", luaRuntime!!.productToken)
        assertEquals(LuaPlatform.STANDARD, luaRuntime.platform)

        val rule = luaRuntime.languageLevel
        assertTrue(rule is LanguageLevelRule.ByVersionPrefix)
        val byPrefix = rule as LanguageLevelRule.ByVersionPrefix
        assertEquals(LuaLanguageLevel.LUA50, byPrefix.fallback)
        assertEquals(
            listOf(
                "5.1" to LuaLanguageLevel.LUA51,
                "5.2" to LuaLanguageLevel.LUA52,
                "5.3" to LuaLanguageLevel.LUA53,
                "5.4" to LuaLanguageLevel.LUA54,
                "5.5" to LuaLanguageLevel.LUA55
            ),
            byPrefix.prefixes
        )
    }

    @Test
    fun testRegistryCompleteness_TC22() {
        val allKinds = LuaToolKindRegistry.all()
        assertEquals(8, allKinds.size)

        val expectedIds = listOf(
            "lua", "luajit", "tarantool", "luarocks", "luacheck", "stylua", "luacov", "busted"
        )
        assertEquals(expectedIds, allKinds.map { it.id })

        for (id in expectedIds) {
            val kind = LuaToolKindRegistry.findById(id)
            assertNotNull(kind)
            assertEquals(id, kind!!.id)
        }

        assertNull(LuaToolKindRegistry.findById("nope"))
    }

    @Test
    fun testAllKindsVerbatimProperties() {
        // Detailed check of all 8 kinds to ensure they match design exactly
        val luajit = LuaToolKindRegistry.findById("luajit")!!
        assertEquals("LuaJIT", luajit.displayName)
        assertEquals(listOf("luajit", "luajit-2.*", "luajit2.*"), luajit.binaryNames)
        assertEquals(setOf(Capability.RUNTIME), luajit.capabilities)
        assertEquals(listOf("-v"), luajit.probe.args)
        val luajitRuntime = luajit.probe.runtime
        assertNotNull(luajitRuntime)
        assertEquals(LuaPlatform.LUAJIT, luajitRuntime!!.platform)
        assertEquals("LuaJIT", luajitRuntime.productToken)
        assertEquals(LanguageLevelRule.Fixed(LuaLanguageLevel.LUA51), luajitRuntime.languageLevel)

        val tarantool = LuaToolKindRegistry.findById("tarantool")!!
        assertEquals("Tarantool", tarantool.displayName)
        assertEquals(listOf("tarantool"), tarantool.binaryNames)
        assertEquals(setOf(Capability.RUNTIME), tarantool.capabilities)
        assertEquals(listOf("-v"), tarantool.probe.args)
        val tarantoolRuntime = tarantool.probe.runtime
        assertNotNull(tarantoolRuntime)
        assertEquals(LuaPlatform.TARANTOOL, tarantoolRuntime!!.platform)
        assertEquals("Tarantool", tarantoolRuntime.productToken)
        assertEquals(LanguageLevelRule.Fixed(LuaLanguageLevel.LUA51), tarantoolRuntime.languageLevel)

        val luarocks = LuaToolKindRegistry.findById("luarocks")!!
        assertEquals("LuaRocks", luarocks.displayName)
        assertEquals(listOf("luarocks"), luarocks.binaryNames)
        assertEquals(setOf(Capability.PACKAGE_MANAGER), luarocks.capabilities)
        assertEquals(listOf("--version"), luarocks.probe.args)
        assertEquals(SemanticVersion(3, 0, 0), luarocks.minVersion)

        val luacheck = LuaToolKindRegistry.findById("luacheck")!!
        assertEquals("luacheck", luacheck.displayName)
        assertEquals(listOf("luacheck"), luacheck.binaryNames)
        assertEquals(setOf(Capability.LINTER), luacheck.capabilities)
        assertEquals(listOf("--version"), luacheck.probe.args)

        val stylua = LuaToolKindRegistry.findById("stylua")!!
        assertEquals("StyLua", stylua.displayName)
        assertEquals(listOf("stylua"), stylua.binaryNames)
        assertEquals(setOf(Capability.FORMATTER), stylua.capabilities)
        assertEquals(listOf("--version"), stylua.probe.args)

        val luacov = LuaToolKindRegistry.findById("luacov")!!
        assertEquals("LuaCov", luacov.displayName)
        assertEquals(listOf("luacov"), luacov.binaryNames)
        assertEquals(setOf(Capability.COVERAGE), luacov.capabilities)
        assertEquals(listOf("--help"), luacov.probe.args)

        val busted = LuaToolKindRegistry.findById("busted")!!
        assertEquals("Busted", busted.displayName)
        assertEquals(listOf("busted"), busted.binaryNames)
        assertEquals(setOf(Capability.TEST_RUNNER), busted.capabilities)
        assertEquals(listOf("--version"), busted.probe.args)
    }

    @Test
    fun testRegexMatchingVerbatimSamples() {
        val lua = LuaToolKindRegistry.findById("lua")!!
        val luaMatch1 = lua.probe.versionRegex.find("Lua 5.4.6  Copyright (C) 1994-2023 Lua.org, PUC-Rio")
        assertNotNull(luaMatch1)
        assertEquals("5.4.6", luaMatch1!!.groupValues[1])

        val luaMatch2 = lua.probe.versionRegex.find("Lua 5.1.5  Copyright (C) 1994-2012 Lua.org, PUC-Rio")
        assertNotNull(luaMatch2)
        assertEquals("5.1.5", luaMatch2!!.groupValues[1])

        val luajit = LuaToolKindRegistry.findById("luajit")!!
        val luajitMatch = luajit.probe.versionRegex.find("LuaJIT 2.1.1700008891 -- Copyright (C) 2005-2023 Mike Pall.")
        assertNotNull(luajitMatch)
        assertEquals("2.1.1700008891", luajitMatch!!.groupValues[1])

        val tarantool = LuaToolKindRegistry.findById("tarantool")!!
        val tarantoolMatch = tarantool.probe.versionRegex.find("Tarantool 2.11.0-entrypoint")
        assertNotNull(tarantoolMatch)
        assertEquals("2.11.0-entrypoint", tarantoolMatch!!.groupValues[1])

        val luarocks = LuaToolKindRegistry.findById("luarocks")!!
        val luarocksMatch = luarocks.probe.versionRegex.find("/usr/local/bin/luarocks 3.11.0\nLuaRocks main command-line interface")
        assertNotNull(luarocksMatch)
        assertEquals("3.11.0", luarocksMatch!!.groupValues[1])

        val luaVersionRegex = luarocks.probe.luaVersionRegex
        assertNotNull(luaVersionRegex)
        val luarocksLuaMatch = luaVersionRegex!!.find("for Lua 5.4")
        assertNotNull(luarocksLuaMatch)
        assertEquals("5.4", luarocksLuaMatch!!.groupValues[1])

        val luacheck = LuaToolKindRegistry.findById("luacheck")!!
        val luacheckMatch = luacheck.probe.versionRegex.find("Luacheck: 0.26.0")
        assertNotNull(luacheckMatch)
        assertEquals("0.26.0", luacheckMatch!!.groupValues[1])

        val stylua = LuaToolKindRegistry.findById("stylua")!!
        val styluaMatch = stylua.probe.versionRegex.find("stylua 0.20.0")
        assertNotNull(styluaMatch)
        assertEquals("0.20.0", styluaMatch!!.groupValues[1])

        val luacov = LuaToolKindRegistry.findById("luacov")!!
        val luacovMatch = luacov.probe.versionRegex.find("LuaCov 0.15.0 - coverage analyzer for Lua")
        assertNotNull(luacovMatch)
        assertEquals("0.15.0", luacovMatch!!.groupValues[1])

        val busted = LuaToolKindRegistry.findById("busted")!!
        val bustedMatch1 = busted.probe.versionRegex.find("2.2.0")
        assertNotNull(bustedMatch1)
        assertEquals("2.2.0", bustedMatch1!!.groupValues[1])

        val bustedMatch2 = busted.probe.versionRegex.find("busted 2.2.0")
        assertNotNull(bustedMatch2)
        assertEquals("2.2.0", bustedMatch2!!.groupValues[1])
    }

    @Test
    fun testBustedHardenedRegexNegative() {
        val busted = LuaToolKindRegistry.findById("busted")!!
        // Busted versionRegex is: (?:busted\s+)?(\d[\w.\-]*)
        // Error prose like "busted: error: no spec files found" or "Error: something" should NOT match.
        val matchProse1 = busted.probe.versionRegex.find("busted: error: no spec files found")
        assertNull(matchProse1)

        val matchProse2 = busted.probe.versionRegex.find("Error: something")
        assertNull(matchProse2)

        val matchProse3 = busted.probe.versionRegex.find("busted 2.2.0-beta1")
        assertNotNull(matchProse3)
        assertEquals("2.2.0-beta1", matchProse3!!.groupValues[1])
    }

    @Test
    fun testInferKind_TC14() {
        // luarocks.bat -> luarocks
        assertEquals("luarocks", LuaToolKindRegistry.inferKind("luarocks.bat")?.id)
        assertEquals("luarocks", LuaToolKindRegistry.inferKind("LUAROCKS.BAT")?.id)

        // LUA5.4.EXE -> lua
        assertEquals("lua", LuaToolKindRegistry.inferKind("LUA5.4.EXE")?.id)
        assertEquals("lua", LuaToolKindRegistry.inferKind("lua5.4")?.id)
        assertEquals("lua", LuaToolKindRegistry.inferKind("lua-5.3.exe")?.id)

        // stylua -> stylua
        assertEquals("stylua", LuaToolKindRegistry.inferKind("stylua")?.id)
        assertEquals("stylua", LuaToolKindRegistry.inferKind("StyLua.exe")?.id)

        // foo -> null
        assertNull(LuaToolKindRegistry.inferKind("foo"))
        assertNull(LuaToolKindRegistry.inferKind("foo.exe"))
    }
}

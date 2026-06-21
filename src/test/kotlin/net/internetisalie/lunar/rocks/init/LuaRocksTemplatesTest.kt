package net.internetisalie.lunar.rocks.init

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure unit tests for [LuaRocksTemplates] — no IDE fixture required.
 *
 * These tests verify that the generated file bodies match the spec (design §4).
 */
class LuaRocksTemplatesTest {

    // ------------------------------------------------------------------ rockspec

    @Test
    fun `rockspec library contains package name and src module path`() {
        val spec = LuaRocksTemplates.rockspec("my-lib", RockType.LIBRARY)
        assertContains(spec, """package = "my-lib"""")
        assertContains(spec, """version = "scm-1"""")
        assertContains(spec, """["my-lib"] = "src/my-lib.lua"""")
        assertFalse(spec.contains("src/main.lua"), "Library should not reference main.lua")
    }

    @Test
    fun `rockspec application contains bin install section`() {
        val spec = LuaRocksTemplates.rockspec("my-app", RockType.APPLICATION)
        assertContains(spec, """package = "my-app"""")
        assertContains(spec, "src/main.lua")
        assertContains(spec, "install")
        assertContains(spec, "bin")
    }

    @Test
    fun `rockspec format is 3_0`() {
        val spec = LuaRocksTemplates.rockspec("lib", RockType.LIBRARY)
        assertContains(spec, """rockspec_format = "3.0"""")
    }

    // ------------------------------------------------------------------ setupLua

    @Test
    fun `setupLua contains package path manipulation`() {
        val setup = LuaRocksTemplates.setupLua()
        assertContains(setup, "lua_modules/share/lua/")
        assertContains(setup, "package.path")
        assertContains(setup, "package.cpath")
    }

    // ------------------------------------------------------------------ mainModule

    @Test
    fun `mainModule library returns a table`() {
        val mod = LuaRocksTemplates.mainModule("my-lib", RockType.LIBRARY)
        assertContains(mod, "local my-lib = {}")
        assertContains(mod, "return my-lib")
    }

    @Test
    fun `mainModule application calls main`() {
        val mod = LuaRocksTemplates.mainModule("my-app", RockType.APPLICATION)
        assertContains(mod, "local function main")
        assertContains(mod, "hello from my-app")
        assertContains(mod, "main(...)")
    }

    // ------------------------------------------------------------------ makefile

    @Test
    fun `makefile contains standard targets`() {
        val mk = LuaRocksTemplates.makefile("my-lib")
        assertContains(mk, ".PHONY: build test install clean")
        assertContains(mk, "luarocks make")
        assertContains(mk, "busted")
        assertContains(mk, "my-lib-scm-1.rockspec")
        assertContains(mk, "rm -rf lua_modules .luarocks")
    }

    // ------------------------------------------------------------------ bustedSpec

    @Test
    fun `bustedSpec contains describe and require`() {
        val spec = LuaRocksTemplates.bustedSpec("my-lib")
        assertContains(spec, """describe("my-lib"""")
        assertContains(spec, """require("my-lib")""")
    }

    // ------------------------------------------------------------------ gitignore

    @Test
    fun `gitignore contains lua_modules and luarocks`() {
        val gi = LuaRocksTemplates.gitignore()
        assertContains(gi, "/lua_modules/")
        assertContains(gi, "/.luarocks/")
        assertContains(gi, "luacov.stats.out")
    }
}

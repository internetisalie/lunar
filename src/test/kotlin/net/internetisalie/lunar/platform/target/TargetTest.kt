package net.internetisalie.lunar.platform.target

import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TargetTest {
    @Test
    fun testStandardLua51LanguageLevel() {
        val target = Target(LuaPlatform.STANDARD, VersionEntry("5.1", "lua-5.1"))
        assertEquals(LuaLanguageLevel.LUA51, target.getImplicitLanguageLevel())
    }

    @Test
    fun testStandardLua52LanguageLevel() {
        val target = Target(LuaPlatform.STANDARD, VersionEntry("5.2", "lua-5.2"))
        assertEquals(LuaLanguageLevel.LUA52, target.getImplicitLanguageLevel())
    }

    @Test
    fun testStandardLua53LanguageLevel() {
        val target = Target(LuaPlatform.STANDARD, VersionEntry("5.3", "lua-5.3"))
        assertEquals(LuaLanguageLevel.LUA53, target.getImplicitLanguageLevel())
    }

    @Test
    fun testStandardLua54LanguageLevel() {
        val target = Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4"))
        assertEquals(LuaLanguageLevel.LUA54, target.getImplicitLanguageLevel())
    }

    @Test
    fun testStandardLua55FallsBackToLua54() {
        val target = Target(LuaPlatform.STANDARD, VersionEntry("5.5", "lua-5.5"))
        assertEquals(LuaLanguageLevel.LUA54, target.getImplicitLanguageLevel())
    }

    @Test
    fun testLuaJITAllVersionsAreLua51() {
        val luajit20 = Target(LuaPlatform.LUAJIT, VersionEntry("2.0", "luajit-2.0"))
        assertEquals(LuaLanguageLevel.LUA51, luajit20.getImplicitLanguageLevel())

        val luajit21 = Target(LuaPlatform.LUAJIT, VersionEntry("2.1", "luajit-2.1"))
        assertEquals(LuaLanguageLevel.LUA51, luajit21.getImplicitLanguageLevel())
    }

    @Test
    fun testRedisAllVersionsAreLua51() {
        val redis5 = Target(LuaPlatform.REDIS, VersionEntry("5", "redis-5"))
        assertEquals(LuaLanguageLevel.LUA51, redis5.getImplicitLanguageLevel())

        val redis7 = Target(LuaPlatform.REDIS, VersionEntry("7+", "redis-7"))
        assertEquals(LuaLanguageLevel.LUA51, redis7.getImplicitLanguageLevel())
    }

    @Test
    fun testTarantoolIsLua51() {
        val target = Target(LuaPlatform.TARANTOOL, VersionEntry("2.10", "tarantool-2.10"))
        assertEquals(LuaLanguageLevel.LUA51, target.getImplicitLanguageLevel())
    }

    @Test
    fun testNGXIsLua51() {
        val target = Target(LuaPlatform.NGX, VersionEntry("latest", "ngx-latest"))
        assertEquals(LuaLanguageLevel.LUA51, target.getImplicitLanguageLevel())
    }


    @Test
    fun testPandocIsLua54() {
        val target = Target(LuaPlatform.PANDOC, VersionEntry("latest", "pandoc-latest"))
        assertEquals(LuaLanguageLevel.LUA54, target.getImplicitLanguageLevel())
    }

    @Test
    fun testGetLibraryRootPath() {
        val standard51 = Target(LuaPlatform.STANDARD, VersionEntry("5.1", "lua-5.1"))
        assertEquals("runtime/standard/lua-5.1", standard51.getLibraryRootPath())

        val redis7 = Target(LuaPlatform.REDIS, VersionEntry("7+", "redis-7"))
        assertEquals("runtime/redis/redis-7", redis7.getLibraryRootPath())

        val luajit21 = Target(LuaPlatform.LUAJIT, VersionEntry("2.1", "luajit-2.1"))
        assertEquals("runtime/luajit/luajit-2.1", luajit21.getLibraryRootPath())
    }

    @Test
    fun testGetLuacheckStdMappingTable() {
        // STANDARD
        assertEquals("lua51", Target(LuaPlatform.STANDARD, VersionEntry("5.1", "lua-5.1", luacheckStd = "lua51")).getLuacheckStd())
        assertEquals("lua52", Target(LuaPlatform.STANDARD, VersionEntry("5.2", "lua-5.2", luacheckStd = "lua52")).getLuacheckStd())
        assertEquals("lua53", Target(LuaPlatform.STANDARD, VersionEntry("5.3", "lua-5.3", luacheckStd = "lua53")).getLuacheckStd())
        assertEquals("lua54", Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4", luacheckStd = "lua54")).getLuacheckStd())
        assertEquals("lua54", Target(LuaPlatform.STANDARD, VersionEntry("5.5", "lua-5.5", luacheckStd = "lua54")).getLuacheckStd())

        // LUAJIT
        assertEquals("luajit", Target(LuaPlatform.LUAJIT, VersionEntry("2.0", "luajit-2.0", luacheckStd = "luajit")).getLuacheckStd())
        assertEquals("luajit", Target(LuaPlatform.LUAJIT, VersionEntry("2.1", "luajit-2.1", luacheckStd = "luajit")).getLuacheckStd())

        // REDIS
        assertEquals("redis5", Target(LuaPlatform.REDIS, VersionEntry("5", "redis-5", luacheckStd = "redis5")).getLuacheckStd())
        assertEquals("redis6", Target(LuaPlatform.REDIS, VersionEntry("6", "redis-6", luacheckStd = "redis6")).getLuacheckStd())
        assertEquals("redis7", Target(LuaPlatform.REDIS, VersionEntry("7+", "redis-7", luacheckStd = "redis7")).getLuacheckStd())

        // OMITTED (null)
        assertEquals(null, Target(LuaPlatform.TARANTOOL, VersionEntry("2.10", "tarantool-2.10", luacheckStd = null)).getLuacheckStd())
        assertEquals(null, Target(LuaPlatform.NGX, VersionEntry("latest", "ngx-latest", luacheckStd = null)).getLuacheckStd())
        assertEquals(null, Target(LuaPlatform.PANDOC, VersionEntry("latest", "pandoc-latest", luacheckStd = null)).getLuacheckStd())
    }

    @Test
    fun testDefault() {
        val target = Target.default()
        assertEquals(LuaPlatform.STANDARD, target.platform)
        assertEquals("5.1", target.version.label)  // First version in registry
        assertEquals(LuaLanguageLevel.LUA51, target.getImplicitLanguageLevel())
    }
}

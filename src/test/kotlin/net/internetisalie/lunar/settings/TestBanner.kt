package net.internetisalie.lunar.settings

import net.internetisalie.lunar.platform.Banner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.text.contains
import kotlin.text.substringBefore
import kotlin.text.trim

class TestBanner {
    private fun getOutputText(input : String) : String {
        var result = input
        result = result.trim(' ', '\n', '\t')
        if (result.contains('\n')) {
            result = result.substringBefore('\n')
        }
        return result
    }

    @Test
    fun testLua54() {
        val output = getOutputText("""
            
        Lua 5.4.6  Copyright (C) 1994-2023 Lua.org, PUC-Rio

        """.trimIndent())


        val banner = Banner.create(output)
        assertNotNull(banner)
        assertEquals("Lua", banner.product)
        assertEquals("5.4.6", banner.version)
        assertTrue { "PUC-Rio" in banner.full }
    }

    @Test
    fun testLua53() {
        val output = getOutputText("""
            
        Lua 5.3.6  Copyright (C) 1994-2020 Lua.org, PUC-Rio

        """.trimIndent())


        val banner = Banner.create(output)
        assertNotNull(banner)
        assertEquals("Lua", banner.product)
        assertEquals("5.3.6", banner.version)
        assertTrue { "PUC-Rio" in banner.full }
    }

    @Test
    fun testLua52() {
        val output = getOutputText("""
            
        Lua 5.2.4  Copyright (C) 1994-2015 Lua.org, PUC-Rio

        """.trimIndent())


        val banner = Banner.create(output)
        assertNotNull(banner)
        assertEquals("Lua", banner.product)
        assertEquals("5.2.4", banner.version)
        assertTrue { "PUC-Rio" in banner.full }
    }

    @Test
    fun testLua51() {
        val output = getOutputText("""
            
        Lua 5.1.5  Copyright (C) 1994-2012 Lua.org, PUC-Rio

        """.trimIndent())

        val banner = Banner.create(output)
        assertNotNull(banner)
        assertEquals("Lua", banner.product)
        assertEquals("5.1.5", banner.version)
        assertTrue { "PUC-Rio" in banner.full }
    }

    @Test
    fun testLua50() {
        val output = getOutputText("""
            
        Lua 5.0.3  Copyright (C) 1994-2006 Tecgraf, PUC-Rio

        """.trimIndent())

        val banner = Banner.create(output)
        assertNotNull(banner)
        assertEquals("Lua", banner.product)
        assertEquals("5.0.3", banner.version)
        assertTrue { "PUC-Rio" in banner.full }
    }

    @Test
    fun testTarantool260() {
        val output = getOutputText("""
            
        Tarantool 2.6.0-0-g47aa4e01e
        Target: Linux-x86_64-RelWithDebInfo
        Build options: cmake . -DCMAKE_INSTALL_PREFIX=/usr -DENABLE_BACKTRACE=ON
        Compiler: /usr/bin/cc /usr/bin/g++

        """.trimIndent())

        val banner = Banner.create(output)
        assertNotNull(banner)
        assertEquals("Tarantool", banner.product)
        assertEquals("2.6.0-0-g47aa4e01e", banner.version)
    }

    @Test
    fun testLuaJIT21() {
        val output = getOutputText("""

        LuaJIT 2.1.1744318430 -- Copyright (C) 2005-2025 Mike Pall. https://luajit.org/

        """.trimIndent())

        val banner = Banner.create(output)
        assertNotNull(banner)
        assertEquals("LuaJIT", banner.product)
        assertEquals("2.1.1744318430", banner.version)
    }

}
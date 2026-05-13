package net.internetisalie.lunar.platform.target

import net.internetisalie.lunar.platform.LuaPlatform
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformVersionRegistryTest {
    @Test
    fun testGetVersionsForStandardPlatform() {
        val versions = PlatformVersionRegistry.getVersions(LuaPlatform.STANDARD)
        assertEquals(5, versions.size)
        assertEquals("5.1", versions[0].label)
        assertEquals("5.2", versions[1].label)
        assertEquals("5.3", versions[2].label)
        assertEquals("5.4", versions[3].label)
        assertEquals("5.5", versions[4].label)
    }

    @Test
    fun testGetVersionsForLuaJIT() {
        val versions = PlatformVersionRegistry.getVersions(LuaPlatform.LUAJIT)
        assertEquals(2, versions.size)
        assertEquals("2.0", versions[0].label)
        assertEquals("2.1", versions[1].label)
    }

    @Test
    fun testGetVersionsForRedis() {
        val versions = PlatformVersionRegistry.getVersions(LuaPlatform.REDIS)
        assertEquals(3, versions.size)
        assertEquals("5", versions[0].label)
        assertEquals("6", versions[1].label)
        assertEquals("7+", versions[2].label)
    }

    @Test
    fun testGetVersionsForUnsupportedPlatform() {
        // If we add a test platform that's not registered, getVersions should return empty
        val versions = PlatformVersionRegistry.getVersions(LuaPlatform.STANDARD)
        assertTrue(versions.isNotEmpty())
    }

    @Test
    fun testDefaultVersionForStandard() {
        val defaultVersion = PlatformVersionRegistry.defaultVersion(LuaPlatform.STANDARD)
        assertNotNull(defaultVersion)
        assertEquals("5.1", defaultVersion.label)
    }

    @Test
    fun testDefaultVersionForRedis() {
        val defaultVersion = PlatformVersionRegistry.defaultVersion(LuaPlatform.REDIS)
        assertNotNull(defaultVersion)
        assertEquals("5", defaultVersion.label)
    }

    @Test
    fun testFindVersion() {
        val version = PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, "5.3")
        assertNotNull(version)
        assertEquals("5.3", version.label)
        assertEquals("lua-5.3", version.pathSegment)
    }

    @Test
    fun testFindVersionNotFound() {
        val version = PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, "5.6")
        assertNull(version)
    }

    @Test
    fun testFindVersionRedis7() {
        val version = PlatformVersionRegistry.findVersion(LuaPlatform.REDIS, "7+")
        assertNotNull(version)
        assertEquals("7+", version.label)
        assertEquals("redis-7", version.pathSegment)
        assertEquals("redis7", version.luacheckStd)
    }

    @Test
    fun testPlatforms() {
        val platforms = PlatformVersionRegistry.platforms()
        assertTrue(platforms.contains(LuaPlatform.STANDARD))
        assertTrue(platforms.contains(LuaPlatform.LUAJIT))
        assertTrue(platforms.contains(LuaPlatform.REDIS))
        assertTrue(platforms.contains(LuaPlatform.TARANTOOL))
        assertTrue(platforms.contains(LuaPlatform.NGX))
        assertTrue(platforms.contains(LuaPlatform.PANDOC))
    }

    @Test
    fun testIsSupported() {
        assertTrue(PlatformVersionRegistry.isSupported(LuaPlatform.STANDARD))
        assertTrue(PlatformVersionRegistry.isSupported(LuaPlatform.REDIS))
        assertTrue(PlatformVersionRegistry.isSupported(LuaPlatform.LUAJIT))
    }

    @Test
    fun testLuacheckStdMapping() {
        // Standard platforms should have luacheck stds
        val lua51 = PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, "5.1")
        assertNotNull(lua51)
        assertEquals("lua51", lua51.luacheckStd)

        val lua55 = PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, "5.5")
        assertNotNull(lua55)
        assertEquals("lua54", lua55.luacheckStd)  // Fallback to lua54
    }

    @Test
    fun testPathSegments() {
        val redis5 = PlatformVersionRegistry.findVersion(LuaPlatform.REDIS, "5")
        assertNotNull(redis5)
        assertEquals("redis-5", redis5.pathSegment)

        val redis7 = PlatformVersionRegistry.findVersion(LuaPlatform.REDIS, "7+")
        assertNotNull(redis7)
        assertEquals("redis-7", redis7.pathSegment)

        val luajit21 = PlatformVersionRegistry.findVersion(LuaPlatform.LUAJIT, "2.1")
        assertNotNull(luajit21)
        assertEquals("luajit-2.1", luajit21.pathSegment)
    }
}

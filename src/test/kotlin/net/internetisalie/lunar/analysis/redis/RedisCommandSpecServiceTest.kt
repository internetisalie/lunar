package net.internetisalie.lunar.analysis.redis

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.platform.target.VersionEntry

/**
 * REDIS-04 Phase 2 — command-spec service (AC-2): TC-SPEC-1, TC-SPEC-2.
 */
class RedisCommandSpecServiceTest : BasePlatformTestCase() {

    private val service get() = RedisCommandSpecService.getInstance()

    /** TC-SPEC-1: bundled redis-7 spec loads with GET (arity 2); result is cached. */
    fun testSpecForRedis7LoadsKnownCommandAndCaches() {
        val target = Target(LuaPlatform.REDIS, VersionEntry("7+", "redis-7"))

        val first = service.specFor(target)
        val second = service.specFor(target)

        assertFalse("redis-7 spec should be non-empty", first.commands.isEmpty())
        val get = first.lookup("GET")
        assertNotNull("GET must be present in the redis-7 spec", get)
        assertEquals("GET arity", 2, get?.arity)
        assertEquals("GET since", "1.0.0", get?.since)
        assertTrue("case-insensitive lookup", first.lookup("get") === get)
        assertSame("second call returns the cached instance", first, second)
    }

    /** TC-SPEC-1 (cont.): arity/flags convention — SET is variadic (negative), write. */
    fun testSetIsVariadicWriteCommand() {
        val spec = service.specFor(Target(LuaPlatform.REDIS, VersionEntry("7+", "redis-7")))
        val set = spec.lookup("SET")
        assertNotNull(set)
        assertEquals("SET arity (negative = >= |n|)", -3, set?.arity)
        assertTrue("SET carries the write flag", set?.flags?.contains("write") == true)
    }

    /** TC-SPEC-1 (cont.): per-version filtering — SINTERCARD (since 7.0.0) only in 7. */
    fun testVersionFilteringExcludesNewerCommands() {
        val redis7 = service.specFor(Target(LuaPlatform.REDIS, VersionEntry("7+", "redis-7")))
        val redis6 = service.specFor(Target(LuaPlatform.REDIS, VersionEntry("6", "redis-6")))

        assertNotNull("SINTERCARD present under redis-7", redis7.lookup("SINTERCARD"))
        assertNull("SINTERCARD absent under redis-6", redis6.lookup("SINTERCARD"))
    }

    /** TC-SPEC-2: a target with no bundled spec yields EMPTY (no exception). */
    fun testSpecForUnbundledTargetIsEmpty() {
        val tarantool = Target(LuaPlatform.TARANTOOL, VersionEntry("2.10", "tarantool-2.10"))
        val standard = Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4"))

        assertSame(RedisCommandSpec.EMPTY, service.specFor(tarantool))
        assertSame(RedisCommandSpec.EMPTY, service.specFor(standard))
        assertTrue(service.specFor(tarantool).names().isEmpty())
    }
}

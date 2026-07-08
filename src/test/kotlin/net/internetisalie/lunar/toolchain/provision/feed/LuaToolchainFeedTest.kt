package net.internetisalie.lunar.toolchain.provision.feed

import net.internetisalie.lunar.toolchain.provision.LuaArch
import net.internetisalie.lunar.toolchain.provision.LuaHostPlatform
import net.internetisalie.lunar.toolchain.provision.LuaOs
import net.internetisalie.lunar.toolchain.provision.LuaProvisionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Loads the REAL bundled feed resource (no network, no fixture) and exercises the model,
 * alias closure, TC 3 resolution cases, and the §3.11 comparator ordering (design §2.5, §3.2,
 * §3.11, §4.1; requirement TOOLING-04-02, TC 3).
 */
class LuaToolchainFeedTest {
    private val linux = LuaHostPlatform(LuaOs.LINUX, LuaArch.X86_64)
    private val windows = LuaHostPlatform(LuaOs.WINDOWS, LuaArch.X86_64)
    private val hexPin = Regex("[0-9a-f]{64}")

    private fun feed(): LuaToolchainFeed = LuaToolchainFeedLoader.load()

    @Test
    fun loadsRealResourceWithExpectedKinds() {
        val feed = feed()
        assertEquals(1, feed.feedVersion)
        val expected = setOf("lua", "luarocks", "stylua", "lua-language-server", "luacheck", "busted", "luacov")
        assertEquals(expected, feed.kinds.keys)
    }

    @Test
    fun shipsTheFullPucAndLuaRocksVersionSets() {
        val lua = feed().kinds.getValue("lua").versions.map { it.version }.toSet()
        val expectedLua = buildSet {
            add("5.1")
            (1..5).forEach { add("5.1.$it") }
            (0..4).forEach { add("5.2.$it") }
            (0..6).forEach { add("5.3.$it") }
            (0..8).forEach { add("5.4.$it") }
            add("5.5.0")
        }
        assertEquals(expectedLua, lua)

        val luarocks = feed().kinds.getValue("luarocks").versions.map { it.version }.toSet()
        assertTrue("luarocks 3.0.0 present", "3.0.0" in luarocks)
        assertTrue("luarocks 3.13.0 present", "3.13.0" in luarocks)
    }

    @Test
    fun everySha256IsRealHexOrTodoSentinel() {
        feed().kinds.values.forEach { kind ->
            kind.versions.forEach { version ->
                version.source?.let { assertPinValid(it.sha256) }
                version.assets.forEach { assertPinValid(it.sha256) }
            }
        }
    }

    private fun assertPinValid(sha256: String) {
        val valid = sha256 == "TODO-PIN" || hexPin.matches(sha256)
        assertTrue("sha256 must be a 64-hex lowercase string or exactly 'TODO-PIN', was '$sha256'", valid)
    }

    @Test
    fun everyAliasChainClosesOnAShippedEntryWithoutCycles() {
        feed().kinds.forEach { (kindId, kind) ->
            val shipped = kind.versions.map { it.version }.toSet()
            kind.aliases.forEach { (key, _) ->
                assertChainCloses(kindId, kind, key, shipped)
            }
        }
    }

    private fun assertChainCloses(kindId: String, kind: LuaFeedKind, start: String, shipped: Set<String>) {
        var current = start
        val maxHops = kind.aliases.size + 1
        var hops = 0
        while (current !in shipped) {
            current = requireNotNull(kind.aliases[current]) {
                "$kindId alias chain from '$start' dead-ends at '$current' (not shipped, not an alias key)"
            }
            assertTrue("$kindId alias chain from '$start' cycles", ++hops <= maxHops)
        }
    }

    @Test
    fun stopsAtShippedEntryForFivePointOneZero() {
        // 5.1.0 -> alias -> 5.1 (a shipped entry) -> STOP; must NOT chase 5.1 -> 5.1.5.
        val resolved = LuaToolchainFeedLoader.resolveVersion(feed(), "lua", "5.1.0", linux)
        assertEquals("5.1", resolved.version)
    }

    @Test
    fun resolvesTc3CasesOnLinux() {
        // TC 3's illustrative mini-feed pinned latest→5.4.8; against the REAL shipped feed
        // latest→5→5.5.0 (design §3.2 worked example). The alias/exact/prefix mechanics below
        // are the TC 3 oracle; latest's concrete target is asserted in caretAndEmptyResolveLikeLatest.
        assertEquals("5.4.8", resolve("5.4").version)
        assertEquals("5.1", resolve("5.1.0").version)
        assertEquals("5.4.3", resolve("5.4.3").version)
    }

    @Test
    fun caretAndEmptyResolveLikeLatest() {
        assertEquals("5.5.0", resolve("").version)
        assertEquals("5.5.0", resolve("^").version)
        assertEquals("5.5.0", resolve("latest").version)
    }

    @Test
    fun unknownVersionErrorNamesKindAndKnownVersions() {
        try {
            resolve("9.9")
            fail("expected LuaProvisionException for unknown version")
        } catch (expected: LuaProvisionException) {
            val message = expected.message.orEmpty()
            assertTrue("names kind", message.contains("lua"))
            assertTrue("mentions unknown spec", message.contains("9.9"))
            assertTrue("lists known versions", message.contains("Known:"))
            assertTrue("known list includes a shipped version", message.contains("5.4.8"))
        }
    }

    @Test
    fun windowsBareLineFallsBackToNewestWin64Asset() {
        // 5.4 -> alias -> 5.4.8 (no Win64 asset) -> step 4b re-prefixes over Win64-provisionable -> 5.4.2.
        val resolved = LuaToolchainFeedLoader.resolveVersion(feed(), "lua", "5.4", windows)
        assertEquals("5.4.2", resolved.version)
    }

    @Test
    fun comparatorOrdersPreReleaseAndNumericTokens() {
        assertTrue("pre-release is less than release", LuaFeedVersionComparator.compare("2.1.0-beta3", "2.1.0") < 0)
        assertTrue("release is greater than pre-release", LuaFeedVersionComparator.compare("2.1.0", "2.1.0-beta3") > 0)
        assertTrue("5.4.10 > 5.4.9 numerically", LuaFeedVersionComparator.compare("5.4.10", "5.4.9") > 0)
        assertTrue("beta1 < beta2", LuaFeedVersionComparator.compare("1.0.0-beta1", "1.0.0-beta2") < 0)
        assertTrue("beta3 < rc1", LuaFeedVersionComparator.compare("1.0.0-beta3", "1.0.0-rc1") < 0)
        assertEquals("equal versions", 0, LuaFeedVersionComparator.compare("5.4.8", "5.4.8"))
    }

    private fun resolve(spec: String): LuaFeedVersion =
        LuaToolchainFeedLoader.resolveVersion(feed(), "lua", spec, linux)
}

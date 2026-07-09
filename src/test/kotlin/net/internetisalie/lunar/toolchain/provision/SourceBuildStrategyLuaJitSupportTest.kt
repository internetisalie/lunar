package net.internetisalie.lunar.toolchain.provision

import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedKind
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedSource
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedVersion
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Env-independent gating tests for [SourceBuildStrategy.supports] on `luajit` (design §3.9,
 * requirement TOOLING-04-13). The git/make availability probes are injected via
 * [LuaJitToolProbes] so the assertions never depend on the CI host having git or make; the
 * feed gate (`gatedOn`) is varied in the fixture feed.
 */
class SourceBuildStrategyLuaJitSupportTest {
    private val linux = LuaHostPlatform(LuaOs.LINUX, LuaArch.X86_64)
    private val windows = LuaHostPlatform(LuaOs.WINDOWS, LuaArch.X86_64)
    private val item = LuaProvisionItem("luajit", "v2.1")

    private fun feed(gatedOn: String? = null): LuaToolchainFeed {
        val source = LuaFeedSource(urls = listOf("git"), sha256 = "", size = 0, rootPrefix = "")
        val version = LuaFeedVersion("v2.1", gatedOn, source, emptyList(), null)
        return LuaToolchainFeed(1, mapOf("luajit" to LuaFeedKind(mapOf("latest" to "v2.1"), listOf(version))))
    }

    private fun strategy(git: Boolean, make: Boolean): SourceBuildStrategy =
        SourceBuildStrategy(luaJitProbes = LuaJitToolProbes(gitOnPath = { git }, makeOnPath = { make }))

    @Test
    fun supportedWhenPosixUngatedGitAndMakePresent() {
        assertTrue(strategy(git = true, make = true).supports(item, linux, feed()))
    }

    @Test
    fun unsupportedWhenGitMissing() {
        assertFalse(strategy(git = false, make = true).supports(item, linux, feed()))
    }

    @Test
    fun unsupportedWhenMakeMissing() {
        assertFalse(strategy(git = true, make = false).supports(item, linux, feed()))
    }

    @Test
    fun unsupportedOnWindowsEvenWithGitAndMake() {
        assertFalse(strategy(git = true, make = true).supports(item, windows, feed()))
    }

    @Test
    fun unsupportedWhenFeedEntryGatedClosed() {
        assertFalse(strategy(git = true, make = true).supports(item, linux, feed(gatedOn = "TOOLING-00-03")))
    }
}

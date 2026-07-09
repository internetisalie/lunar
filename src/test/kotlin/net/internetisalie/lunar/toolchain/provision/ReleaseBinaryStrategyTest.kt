package net.internetisalie.lunar.toolchain.provision

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedAsset
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedKind
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedVersion
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Network-free tests for [ReleaseBinaryStrategy] (design §3.7, §4.6). A [LuaArtifactFetcher]
 * test double returns committed fixture archives (`release-win-lua.zip`, `release-single.zip`)
 * so no download or SHA-256 pin is exercised; the strategy's layout handling is what is under
 * test. Inherits [BasePlatformTestCase] only for a live application + a [project] value.
 */
class ReleaseBinaryStrategyTest : BasePlatformTestCase() {
    private val indicator by lazy { EmptyProgressIndicator() }

    private fun fixture(name: String): Path {
        val url = javaClass.classLoader.getResource("toolchain/$name") ?: error("missing fixture $name")
        return File(url.toURI()).toPath()
    }

    private inner class FixtureFetcher(private val archive: Path) : LuaArtifactFetcher {
        override fun fetch(asset: LuaFeedAsset, indicator: ProgressIndicator): Path = archive
    }

    private fun feed(kindId: String, version: LuaFeedVersion): LuaToolchainFeed =
        LuaToolchainFeed(1, mapOf(kindId to LuaFeedKind(emptyMap(), listOf(version))))

    private fun context(feed: LuaToolchainFeed, platform: LuaHostPlatform, rootDir: Path, kindId: String, spec: String): LuaProvisionContext =
        LuaProvisionContext(
            project = project,
            request = LuaProvisionRequest("env", rootDir.toString(), listOf(LuaProvisionItem(kindId, spec))),
            platform = platform,
            feed = feed,
            rootDir = rootDir,
            indicator = indicator,
            toolchain = null,
        )

    fun testWinLuaBinariesCopiesCanonicalLuaExe() {
        val asset = LuaFeedAsset("windows", "x86_64", "https://x/lua.zip", "SHA", 0, "zip", null, "win-lua-binaries", "lua54.exe")
        val version = LuaFeedVersion("5.4.2", null, null, listOf(asset), null)
        val strategy = ReleaseBinaryStrategy(FixtureFetcher(fixture("release-win-lua.zip")))
        val rootDir = createTempDirectory("lunar-winlua")
        val platform = LuaHostPlatform(LuaOs.WINDOWS, LuaArch.X86_64)

        val component = strategy.provision(context(feed("lua", version), platform, rootDir, "lua", "5.4.2"), LuaProvisionItem("lua", "5.4.2"))

        val bin = rootDir.resolve("bin")
        assertTrue("lua.exe canonical copy must exist", bin.resolve("lua.exe").exists())
        assertTrue("luac.exe canonical copy must exist", bin.resolve("luac.exe").exists())
        assertTrue("original lua54.exe kept", bin.resolve("lua54.exe").exists())
        assertTrue("load-bearing dll kept", bin.resolve("lua54.dll").exists())
        assertEquals("lua54-exe", bin.resolve("lua.exe").readText())
        assertEquals(bin.resolve("lua.exe"), component.primaryBinary)
        assertEquals("release-binary", component.strategyId)
    }

    fun testWinLuaRocksWritesLuaRocksConfig() {
        val asset = LuaFeedAsset("windows", "x86_64", "https://x/lr.zip", "SHA", 0, "zip", null, "single-binary", "stylua")
        val version = LuaFeedVersion("3.13.0", null, null, listOf(asset), null)
        val strategy = ReleaseBinaryStrategy(FixtureFetcher(fixture("release-single.zip")))
        val rootDir = createTempDirectory("lunar-winlr")
        val platform = LuaHostPlatform(LuaOs.WINDOWS, LuaArch.X86_64)

        strategy.provision(context(feed("luarocks", version), platform, rootDir, "luarocks", "3.13.0"), LuaProvisionItem("luarocks", "3.13.0"))

        val config = rootDir.resolve("luarocks-config.lua")
        assertTrue("luarocks-config.lua must be written on Windows", config.exists())
        val text = config.readText()
        assertTrue(text.contains("lua_dir = [[$rootDir]]"))
        assertTrue(text.contains("rocks_trees = {"))
    }

    fun testSingleBinaryRestoresExecBitOnPosix() {
        if (com.intellij.openapi.util.SystemInfo.isWindows) return
        val asset = LuaFeedAsset("linux", "x86_64", "https://x/stylua.zip", "SHA", 0, "zip", null, "single-binary", "stylua")
        val version = LuaFeedVersion("2.5.2", null, null, listOf(asset), null)
        val strategy = ReleaseBinaryStrategy(FixtureFetcher(fixture("release-single.zip")))
        val rootDir = createTempDirectory("lunar-single")
        val platform = LuaHostPlatform(LuaOs.LINUX, LuaArch.X86_64)

        val component = strategy.provision(context(feed("stylua", version), platform, rootDir, "stylua", "2.5.2"), LuaProvisionItem("stylua", "2.5.2"))

        val binary = rootDir.resolve("bin/stylua")
        assertTrue("single binary extracted to bin/", binary.exists())
        assertTrue("exec bit restored", Files.isExecutable(binary))
        assertEquals(binary, component.primaryBinary)
    }

    // Regression: Windows single-binary assets already carry `.exe` in binaryPath; appending it
    // unconditionally produced `luacheck.exe.exe` / `luarocks.exe.exe` (broke PATH lookup — found by
    // the live Windows verification 2026-07-09).
    fun testWindowsExecNameDoesNotDoubleExeExtension() {
        assertEquals("luacheck.exe", ReleaseBinaryStrategy.windowsExecName("luacheck.exe", LuaOs.WINDOWS))
        assertEquals("luarocks.exe", ReleaseBinaryStrategy.windowsExecName("luarocks.exe", LuaOs.WINDOWS))
        assertEquals("stylua.exe", ReleaseBinaryStrategy.windowsExecName("stylua", LuaOs.WINDOWS))
        assertEquals("LUACHECK.EXE", ReleaseBinaryStrategy.windowsExecName("LUACHECK.EXE", LuaOs.WINDOWS))
        assertEquals("stylua", ReleaseBinaryStrategy.windowsExecName("stylua", LuaOs.LINUX))
        assertEquals("luacheck.exe", ReleaseBinaryStrategy.windowsExecName("luacheck.exe", LuaOs.LINUX))
    }
}

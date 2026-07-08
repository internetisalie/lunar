package net.internetisalie.lunar.toolchain.provision

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.toolchain.exec.LuaExecOutcome
import net.internetisalie.lunar.toolchain.exec.LuaExecResult
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedKind
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedRock
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedVersion
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/**
 * Network-free tests for [LuaRocksInstallStrategy] (design §3.8, §4.4). The pure argv builder and
 * failure classifier are exercised directly; the success/wrapper-existence path drives
 * [LuaRocksInstallStrategy.provision] with a [LuaRockInstaller] test double returning a canned
 * [LuaExecResult] (no real `luarocks` subprocess). Inherits [BasePlatformTestCase] only for a
 * live application + a [project] value to fill [LuaProvisionContext] (the strategy never
 * dereferences it).
 */
class LuaRocksInstallStrategyTest : BasePlatformTestCase() {
    private val indicator: ProgressIndicator by lazy { EmptyProgressIndicator() }
    private val busted = LuaFeedRock(rockName = "busted", pinnedVersion = "2.2.0-1", binName = "busted", needsCToolchain = true)

    private fun result(exitCode: Int, stderr: String = "", outcome: LuaExecOutcome = LuaExecOutcome.COMPLETED) =
        LuaExecResult(ProcessOutput("", stderr, exitCode, false, false), outcome)

    private inner class CannedInstaller(private val canned: LuaExecResult) : LuaRockInstaller {
        var lastCommand: GeneralCommandLine? = null

        override fun run(cmd: GeneralCommandLine, indicator: ProgressIndicator): LuaExecResult {
            lastCommand = cmd
            return canned
        }
    }

    private fun feed(): LuaToolchainFeed =
        LuaToolchainFeed(1, mapOf("busted" to LuaFeedKind(emptyMap(), listOf(LuaFeedVersion("1.0", null, null, emptyList(), busted)))))

    private fun context(platform: LuaHostPlatform, rootDir: Path): LuaProvisionContext =
        LuaProvisionContext(
            project = project,
            request = LuaProvisionRequest("env", rootDir.toString(), listOf(LuaProvisionItem("busted", "1.0"))),
            platform = platform,
            feed = feed(),
            rootDir = rootDir,
            indicator = indicator,
            toolchain = null,
        )

    // --- TC 9: POSIX argv + pin handling ---

    fun testPosixArgvIsExactlyLuarocksInstallRockPin() {
        val rootDir = Path.of("/env/busted-1.0")
        val argv = LuaRocksInstallStrategy.buildArgv(rootDir, busted, LuaHostPlatform(LuaOs.LINUX, LuaArch.X86_64))
        assertEquals(
            listOf("/env/busted-1.0/bin/luarocks", "install", "busted", "2.2.0-1"),
            argv.map { it.replace('\\', '/') },
        )
    }

    fun testPosixArgvOmitsPinWhenNull() {
        val rootDir = Path.of("/env/luacov-1.0")
        val luacov = LuaFeedRock("luacov", null, "luacov", false)
        val argv = LuaRocksInstallStrategy.buildArgv(rootDir, luacov, LuaHostPlatform(LuaOs.LINUX, LuaArch.X86_64))
        assertEquals(listOf("/env/luacov-1.0/bin/luarocks", "install", "luacov"), argv.map { it.replace('\\', '/') })
    }

    // --- Windows argv form + LUAROCKS_CONFIG env ---

    fun testWindowsArgvUsesLuaDirAndTreeFlags() {
        val rootDir = Path.of("C:\\env\\busted-1.0")
        // Normalize separators on both sides: on a POSIX CI host Path.resolve joins with '/',
        // so assert the flag structure host-independently (mirrors the POSIX cases above).
        val root = rootDir.toString().replace('\\', '/')
        val argv = LuaRocksInstallStrategy.buildArgv(rootDir, busted, LuaHostPlatform(LuaOs.WINDOWS, LuaArch.X86_64))
        assertEquals(
            listOf(
                "$root/bin/luarocks.exe",
                "--lua-dir", root,
                "--tree", root,
                "install", "busted", "2.2.0-1",
            ),
            argv.map { it.replace('\\', '/') },
        )
    }

    fun testWindowsCommandLineSetsLuarocksConfigEnv() {
        val rootDir = createTempDirectory("lunar-rock-win")
        writeWrapper(rootDir, "busted.bat")
        val installer = CannedInstaller(result(exitCode = 0))

        LuaRocksInstallStrategy(installer).provision(context(LuaHostPlatform(LuaOs.WINDOWS, LuaArch.X86_64), rootDir), LuaProvisionItem("busted", "1.0"))

        val cmd = installer.lastCommand ?: error("installer was not invoked")
        assertEquals("$rootDir/luarocks-config.lua", cmd.environment["LUAROCKS_CONFIG"])
        assertEquals(rootDir.toString(), cmd.workDirectory?.toString())
    }

    // --- Success / wrapper-existence check ---

    fun testSuccessReturnsComponentWithRockArtifactHash() {
        val rootDir = createTempDirectory("lunar-rock-ok")
        writeWrapper(rootDir, "busted")

        val component = LuaRocksInstallStrategy(CannedInstaller(result(exitCode = 0)))
            .provision(context(LuaHostPlatform(LuaOs.LINUX, LuaArch.X86_64), rootDir), LuaProvisionItem("busted", "1.0"))

        assertEquals("luarocks-install", component.strategyId)
        assertEquals(rootDir.resolve("bin/busted"), component.primaryBinary)
        assertTrue(component.extraBinaries.isEmpty())
        val expected = LuaIdentifiersHash.compute(
            LuaIdentifiersHashInput("busted", "1.0", "luarocks-install", LuaOs.LINUX, LuaArch.X86_64, rootDir.toString(), "rock=busted@2.2.0-1", ""),
        )
        assertEquals(expected, component.identifiersHash)
    }

    fun testExitZeroWithoutWrapperThrowsNoWrapper() {
        val rootDir = createTempDirectory("lunar-rock-nowrap")
        val ex = runCatching {
            LuaRocksInstallStrategy(CannedInstaller(result(exitCode = 0)))
                .provision(context(LuaHostPlatform(LuaOs.LINUX, LuaArch.X86_64), rootDir), LuaProvisionItem("busted", "1.0"))
        }.exceptionOrNull()

        assertTrue(ex is LuaProvisionException)
        assertEquals("install succeeded but no busted wrapper", ex?.message)
    }

    // --- TC 10: failure classification ---

    fun testCToolchainFailureGivesBuildEssentialGuidance() {
        val output = "Installing busted\ngcc: command not found\nfatal error"
        val ex = LuaRocksInstallStrategy.classify(result(exitCode = 1, stderr = output), busted)
        val message = ex.message ?: error("missing message")

        assertTrue("C-toolchain guidance expected", message.contains("requires a C toolchain"))
        assertTrue(message.contains("build-essential"))
        assertTrue("20-line tail must be included", message.contains("gcc: command not found"))
    }

    fun testGenericFailureGivesExitCodeMessage() {
        val output = "error: something else\nrock repository unreachable"
        val ex = LuaRocksInstallStrategy.classify(result(exitCode = 4, stderr = output), busted)
        val message = ex.message ?: error("missing message")

        assertTrue(message.startsWith("luarocks install busted failed (exit 4)"))
        assertTrue("must not misfire C guidance", !message.contains("requires a C toolchain"))
        assertTrue("20-line tail must be included", message.contains("rock repository unreachable"))
    }

    private fun writeWrapper(rootDir: Path, name: String) {
        val bin = rootDir.resolve("bin").also { it.createDirectories() }
        bin.resolve(name).writeText("#!/bin/sh\n")
    }
}

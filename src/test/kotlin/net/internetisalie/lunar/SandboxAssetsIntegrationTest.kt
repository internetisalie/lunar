package net.internetisalie.lunar

import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Path

class SandboxAssetsIntegrationTest {
    @Test
    fun `sandbox contains copied runtime assets`() {
        val sandboxHome = Path.of(System.getProperty("sandbox.home"))
        val pluginName = System.getProperty("plugin.name")
        val platformName = "GO-2026.1"

        val pluginSandboxRoot = sandboxHome.resolve("${platformName}/plugins/${pluginName}")
        val platformDir = pluginSandboxRoot.resolve("platform")
        val luaDir = pluginSandboxRoot.resolve("lua")

        assertTrue(platformDir.exists(), "Expected platform assets directory to exist: ${platformDir.pathString}")
        assertTrue(platformDir.isDirectory(), "Expected platform assets path to be a directory: ${platformDir.pathString}")

        assertTrue(luaDir.exists(), "Expected lua assets directory to exist: ${luaDir.pathString}")
        assertTrue(luaDir.isDirectory(), "Expected lua assets path to be a directory: ${luaDir.pathString}")

        assertTrue(
            platformDir.listDirectoryEntries().isNotEmpty(),
            "Expected at least one copied platform asset in: ${platformDir.pathString}"
        )
        assertTrue(
            luaDir.listDirectoryEntries().isNotEmpty(),
            "Expected at least one copied lua asset in: ${luaDir.pathString}"
        )
    }
}

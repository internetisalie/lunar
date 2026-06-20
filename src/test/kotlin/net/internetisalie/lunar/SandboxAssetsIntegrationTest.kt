package net.internetisalie.lunar

import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Path

class SandboxAssetsIntegrationTest {
    @Test
    fun `sandbox contains copied runtime assets`() {
        val sandboxHome = Path.of(System.getProperty("sandbox.home"))
        val pluginName = System.getProperty("plugin.name")

        val platformDir = sandboxHome.listDirectoryEntries()
            .find { it.name.startsWith("GO-") || it.name.startsWith("IC-") || it.name.startsWith("IU-") }
            ?: throw AssertionError("Could not find IDE sandbox platform directory in: ${sandboxHome.pathString}")

        val pluginSandboxRoot = platformDir.resolve("plugins/${pluginName}")
        val runtimeDir = pluginSandboxRoot.resolve("runtime")
        val luaDir = pluginSandboxRoot.resolve("lua")

        assertTrue(runtimeDir.exists(), "Expected runtime assets directory to exist: ${runtimeDir.pathString}")
        assertTrue(runtimeDir.isDirectory(), "Expected runtime assets path to be a directory: ${runtimeDir.pathString}")

        assertTrue(luaDir.exists(), "Expected lua assets directory to exist: ${luaDir.pathString}")
        assertTrue(luaDir.isDirectory(), "Expected lua assets path to be a directory: ${luaDir.pathString}")

        assertTrue(
            runtimeDir.listDirectoryEntries().isNotEmpty(),
            "Expected at least one copied runtime asset in: ${runtimeDir.pathString}"
        )
        assertTrue(
            luaDir.listDirectoryEntries().isNotEmpty(),
            "Expected at least one copied lua asset in: ${luaDir.pathString}"
        )
    }
}


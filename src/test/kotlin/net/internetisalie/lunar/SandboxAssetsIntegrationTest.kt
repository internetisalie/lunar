package net.internetisalie.lunar

import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertTrue

class SandboxAssetsIntegrationTest {
    @Test
    fun `sandbox contains copied runtime assets`() {
        val platformDir = Path.of(System.getProperty("sandbox.home"))
        val pluginName = System.getProperty("plugin.name")

        val pluginSandboxRoot = platformDir.resolve("plugins/$pluginName")
        val runtimeDir = pluginSandboxRoot.resolve("runtime")
        val luaDir = pluginSandboxRoot.resolve("lua")

        assertTrue(runtimeDir.exists(), "Expected runtime assets directory to exist: ${runtimeDir.pathString}")
        assertTrue(runtimeDir.isDirectory(), "Expected runtime assets path to be a directory: ${runtimeDir.pathString}")

        assertTrue(luaDir.exists(), "Expected lua assets directory to exist: ${luaDir.pathString}")
        assertTrue(luaDir.isDirectory(), "Expected lua assets path to be a directory: ${luaDir.pathString}")

        assertTrue(
            runtimeDir.listDirectoryEntries().isNotEmpty(),
            "Expected at least one copied runtime asset in: ${runtimeDir.pathString}",
        )
        assertTrue(
            luaDir.listDirectoryEntries().isNotEmpty(),
            "Expected at least one copied lua asset in: ${luaDir.pathString}",
        )
    }

    @Test
    fun `every prepared sandbox carries the plugin assets`() {
        val pluginDirs = sandboxPluginDirs()
        assertTrue(pluginDirs.size > 1, "Expected the default and per-task sandboxes, got: $pluginDirs")
        pluginDirs.forEach(::assertCarriesAssets)
    }

    private fun sandboxPluginDirs(): List<Path> =
        System
            .getProperty("lunar.sandbox.pluginDirs")
            .orEmpty()
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .map { Path.of(it) }

    private fun assertCarriesAssets(pluginDir: Path) {
        ASSET_DIRS.map(pluginDir::resolve).forEach { assetDir ->
            assertTrue(
                assetDir.isDirectory() && assetDir.listDirectoryEntries().isNotEmpty(),
                "Expected a non-empty asset directory: ${assetDir.pathString}",
            )
        }
        val license = pluginDir.resolve("LICENSE")
        assertTrue(license.isRegularFile(), "Expected the license at the plugin root: ${license.pathString}")
    }

    private companion object {
        val ASSET_DIRS = listOf("lua", "platform", "runtime")
    }
}

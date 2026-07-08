package net.internetisalie.lunar.toolchain.provision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Plain-JUnit tests for [LuaEnvManifest] round-trip (design §2.10, §4.5) and the
 * [LuaManifestSkipRule] (design §3.1 step 8). Gson + java.nio only, no platform.
 */
class LuaEnvManifestTest {
    private fun sampleManifest() = LuaEnvManifest(
        manifestVersion = 1,
        environmentId = "3f9c-abcd",
        environmentName = "env54",
        request = LuaProvisionRequest(
            environmentName = "env54",
            rootDir = "/p/.lua",
            items = listOf(
                LuaProvisionItem("lua", "5.4"),
                LuaProvisionItem("luarocks", "latest"),
            ),
        ),
        components = mapOf(
            "lua" to LuaManifestComponent(
                resolvedVersion = "5.4.8",
                strategyId = "source-build",
                identifiersHash = "deadbeef",
                binaries = listOf("bin/lua", "bin/luac"),
                provisionedAtEpochMs = 1751673600000L,
            ),
        ),
    )

    @Test
    fun `write then read yields an equal object`(@TempDir dir: Path) {
        val manifest = sampleManifest()
        LuaEnvManifest.write(dir, manifest)
        assertEquals(manifest, LuaEnvManifest.read(dir))
    }

    @Test
    fun `write uses the canonical file name`(@TempDir dir: Path) {
        LuaEnvManifest.write(dir, sampleManifest())
        assertTrue(Files.isRegularFile(dir.resolve(".lunar-env.json")))
    }

    @Test
    fun `absent file reads as null`(@TempDir dir: Path) {
        assertNull(LuaEnvManifest.read(dir))
    }

    @Test
    fun `corrupt file reads as null`(@TempDir dir: Path) {
        Files.writeString(dir.resolve(LuaEnvManifest.FILE_NAME), "{ this is not valid json ")
        assertNull(LuaEnvManifest.read(dir))
    }

    @Test
    fun `well-formed but shape-invalid file reads as null`(@TempDir dir: Path) {
        Files.writeString(dir.resolve(LuaEnvManifest.FILE_NAME), "{}")
        assertNull(LuaEnvManifest.read(dir))
    }

    @Test
    fun `skip rule skips when hash matches and binaries are executable`(@TempDir dir: Path) {
        val component = executableComponent(dir, "abc")
        assertTrue(LuaManifestSkipRule.shouldSkip(LuaSkipCheck(component, "abc", dir)))
    }

    @Test
    fun `skip rule does not skip on hash mismatch`(@TempDir dir: Path) {
        val component = executableComponent(dir, "abc")
        assertFalse(LuaManifestSkipRule.shouldSkip(LuaSkipCheck(component, "different", dir)))
    }

    @Test
    fun `skip rule does not skip when a binary is missing`(@TempDir dir: Path) {
        val component = LuaManifestComponent(
            resolvedVersion = "5.4.8",
            strategyId = "source-build",
            identifiersHash = "abc",
            binaries = listOf("bin/lua"),
            provisionedAtEpochMs = 0L,
        )
        assertFalse(LuaManifestSkipRule.shouldSkip(LuaSkipCheck(component, "abc", dir)))
    }

    private fun executableComponent(dir: Path, hash: String): LuaManifestComponent {
        val binary = dir.resolve("bin/lua")
        Files.createDirectories(binary.parent)
        Files.writeString(binary, "#!/bin/sh\n")
        binary.toFile().setExecutable(true)
        return LuaManifestComponent(
            resolvedVersion = "5.4.8",
            strategyId = "source-build",
            identifiersHash = hash,
            binaries = listOf("bin/lua"),
            provisionedAtEpochMs = 0L,
        )
    }
}

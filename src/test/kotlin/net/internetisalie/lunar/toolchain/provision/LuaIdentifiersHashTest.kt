package net.internetisalie.lunar.toolchain.provision

import com.google.common.hash.Hashing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

/**
 * Plain-JUnit tests for [LuaIdentifiersHash] (design §3.3) — Guava only, no platform.
 * Proves the fixed nine-line input order, stability, and that each line participates.
 */
class LuaIdentifiersHashTest {
    private fun sourceInput() = LuaIdentifiersHashInput(
        kindId = "lua",
        resolvedVersion = "5.4.8",
        strategyId = "source-build",
        os = LuaOs.LINUX,
        arch = LuaArch.X86_64,
        canonicalRootDir = "/p/.lua",
        artifact = "abc123",
        compatDefines = "-DLUA_COMPAT_5_3",
    )

    @Test
    fun `hash is a 64-char lowercase hex string`() {
        val hash = LuaIdentifiersHash.compute(sourceInput())
        assertEquals(64, hash.length)
        assertEquals(hash.lowercase(), hash)
        assert(hash.all { it in "0123456789abcdef" })
    }

    @Test
    fun `same inputs produce the same hash across runs`() {
        assertEquals(LuaIdentifiersHash.compute(sourceInput()), LuaIdentifiersHash.compute(sourceInput()))
    }

    @Test
    fun `a different rootDir yields a different hash`() {
        val other = sourceInput().copy(canonicalRootDir = "/p/other")
        assertNotEquals(LuaIdentifiersHash.compute(sourceInput()), LuaIdentifiersHash.compute(other))
    }

    @Test
    fun `the compat line participates in the hash`() {
        val differsOnlyByCompat = sourceInput().copy(compatDefines = "-DLUA_COMPAT_5_1 -DLUA_COMPAT_5_2")
        assertNotEquals(
            LuaIdentifiersHash.compute(sourceInput()),
            LuaIdentifiersHash.compute(differsOnlyByCompat),
        )
    }

    @Test
    fun `every field participates in the hash`() {
        val base = LuaIdentifiersHash.compute(sourceInput())
        val mutations = listOf(
            sourceInput().copy(kindId = "luajit"),
            sourceInput().copy(resolvedVersion = "5.4.7"),
            sourceInput().copy(strategyId = "release-binary"),
            sourceInput().copy(os = LuaOs.MACOS),
            sourceInput().copy(arch = LuaArch.AARCH64),
            sourceInput().copy(artifact = "def456"),
        )
        mutations.forEach { mutated ->
            assertNotEquals(base, LuaIdentifiersHash.compute(mutated))
        }
    }

    @Test
    fun `hash matches the exact nine-line joined string including readline false`() {
        val expectedJoined = listOf(
            "kind=lua",
            "version=5.4.8",
            "strategy=source-build",
            "os=LINUX",
            "arch=X86_64",
            "root=/p/.lua",
            "artifact=abc123",
            "readline=false",
            "compat=-DLUA_COMPAT_5_3",
        ).joinToString("\n")
        val expected = Hashing.sha256().hashString(expectedJoined, StandardCharsets.UTF_8).toString()
        assertEquals(expected, LuaIdentifiersHash.compute(sourceInput()))
    }

    @Test
    fun `luarocks-install artifact form participates`() {
        val rockInput = LuaIdentifiersHashInput(
            kindId = "busted",
            resolvedVersion = "2.2.0-1",
            strategyId = "luarocks-install",
            os = LuaOs.LINUX,
            arch = LuaArch.X86_64,
            canonicalRootDir = "/p/.lua",
            artifact = "rock=busted@2.2.0-1",
            compatDefines = "",
        )
        val expectedJoined = listOf(
            "kind=busted",
            "version=2.2.0-1",
            "strategy=luarocks-install",
            "os=LINUX",
            "arch=X86_64",
            "root=/p/.lua",
            "artifact=rock=busted@2.2.0-1",
            "readline=false",
            "compat=",
        ).joinToString("\n")
        val expected = Hashing.sha256().hashString(expectedJoined, StandardCharsets.UTF_8).toString()
        assertEquals(expected, LuaIdentifiersHash.compute(rockInput))
    }
}

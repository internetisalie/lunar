package net.internetisalie.lunar.toolchain.discovery

import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LuaToolDiscoveryTest {

    @Test
    fun testSymlinkDedup_TC12(@TempDir tempDir: Path) {
        val dirA = Files.createDirectory(tempDir.resolve("dirA"))
        val dirB = Files.createDirectory(tempDir.resolve("dirB"))

        val fileA = Files.createFile(dirA.resolve("lua"))
        fileA.toFile().setExecutable(true)

        val linkB = dirB.resolve("lua")
        try {
            Files.createSymbolicLink(linkB, fileA)
        } catch (_: Exception) {
            Files.copy(fileA, linkB)
        }

        val luaKind = LuaToolKindRegistry.findById("lua")!!
        val discovered = LuaToolDiscovery.discoverAll(
            kinds = listOf(luaKind),
            extraRoots = listOf(dirA, dirB)
        )

        // Filter to only elements in tempDir to ignore system-wide installations
        val tempDiscovered = discovered.filter {
            it.file.absolutePath.startsWith(tempDir.toAbsolutePath().toString())
        }

        assertEquals(1, tempDiscovered.size) {
            "Expected 1, got ${tempDiscovered.size}. Elements: ${tempDiscovered.map { it.file.absolutePath }}"
        }
        assertEquals(fileA.toFile().canonicalPath, tempDiscovered[0].file.canonicalPath)
    }

    @Test
    fun testGlobClaimAndExactClaim_TC13(@TempDir tempDir: Path) {
        val lua54File = Files.createFile(tempDir.resolve("lua5.4"))
        lua54File.toFile().setExecutable(true)

        val luajitFile = Files.createFile(tempDir.resolve("luajit"))
        luajitFile.toFile().setExecutable(true)

        val luaKind = LuaToolKindRegistry.findById("lua")!!
        val luajitKind = LuaToolKindRegistry.findById("luajit")!!

        val discovered = LuaToolDiscovery.discoverAll(
            kinds = listOf(luaKind, luajitKind),
            extraRoots = listOf(tempDir)
        )

        // Filter to only elements in tempDir to ignore system-wide installations
        val tempDiscovered = discovered.filter {
            it.file.absolutePath.startsWith(tempDir.toAbsolutePath().toString())
        }

        val luajitDiscovered = tempDiscovered.filter { it.kind == luajitKind }
        val luaDiscovered = tempDiscovered.filter { it.kind == luaKind }

        assertEquals(1, luajitDiscovered.size) {
            "Expected 1 luajit, got ${luajitDiscovered.size}. Elements: ${luajitDiscovered.map { it.file.absolutePath }}. Total temp: ${tempDiscovered.map { "${it.kind.id} -> ${it.file.absolutePath}" }}"
        }
        assertEquals(luajitFile.toFile().canonicalPath, luajitDiscovered[0].file.canonicalPath)

        assertEquals(1, luaDiscovered.size) {
            "Expected 1 lua, got ${luaDiscovered.size}. Elements: ${luaDiscovered.map { it.file.absolutePath }}. Total temp: ${tempDiscovered.map { "${it.kind.id} -> ${it.file.absolutePath}" }}"
        }
        assertEquals(lua54File.toFile().canonicalPath, luaDiscovered[0].file.canonicalPath)
    }

    @Test
    fun testExecutableFilter(@TempDir tempDir: Path) {
        val execFile = Files.createFile(tempDir.resolve("lua"))
        execFile.toFile().setExecutable(true)

        val nonExecFile = Files.createFile(tempDir.resolve("luajit"))
        nonExecFile.toFile().setExecutable(false)

        val dirKind = Files.createDirectory(tempDir.resolve("stylua"))

        val luaKind = LuaToolKindRegistry.findById("lua")!!
        val luajitKind = LuaToolKindRegistry.findById("luajit")!!
        val styluaKind = LuaToolKindRegistry.findById("stylua")!!

        if (!nonExecFile.toFile().canExecute()) {
            val discovered = LuaToolDiscovery.discoverAll(
                kinds = listOf(luaKind, luajitKind, styluaKind),
                extraRoots = listOf(tempDir)
            )

            val discoveredFiles = discovered.map { it.file.canonicalPath }
            assertTrue(discoveredFiles.contains(execFile.toFile().canonicalPath))
            assertFalse(discoveredFiles.contains(nonExecFile.toFile().canonicalPath))
            assertFalse(discoveredFiles.contains(dirKind.toFile().canonicalPath))
        }
    }

    @Test
    fun testPlatformCandidatesOrder() {
        val name = "luarocks"
        val windowsCandidates = LuaToolDiscovery.platformCandidates(name, windows = true)
        val posixCandidates = LuaToolDiscovery.platformCandidates(name, windows = false)

        assertEquals(
            listOf("luarocks.bat", "luarocks.exe", "luarocks.cmd", "luarocks"),
            windowsCandidates
        )
        assertEquals(
            listOf("luarocks"),
            posixCandidates
        )
    }

    @Test
    fun testEnvVarSubstitution() {
        val envKeys = System.getenv().keys
        if (envKeys.isNotEmpty()) {
            val key = envKeys.first()
            val value = System.getenv(key) ?: ""
            val input = "\${$key}/subpath"
            val expected = "$value/subpath"
            val actual = LuaToolDiscovery.substituteEnvVars(input)
            assertEquals(expected, actual)
        }

        val actualNonExistent = LuaToolDiscovery.substituteEnvVars("\${NON_EXISTENT_VAR_12345}/path")
        assertEquals("/path", actualNonExistent)
    }

    @Test
    fun testExpandSearchPathGlob(@TempDir tempDir: Path) {
        val dir1 = Files.createDirectories(tempDir.resolve("Lua 5.1"))
        val dir2 = Files.createDirectories(tempDir.resolve("Lua 5.4"))
        Files.createFile(tempDir.resolve("README"))

        val result = LuaToolDiscovery.expandSearchPath("$tempDir/Lua 5.*")
        assertEquals(listOf(dir1, dir2), result)
    }

    // --- glob-expansion parity coverage ported from the deleted platform.LuaInterpreterSearchPathGlobTest
    //     (TOOLING-05 §6.4 — LuaToolDiscovery.expandSearchPath is byte-for-byte the legacy expander).

    @Test
    fun testExpandsMidSegmentGlob(@TempDir tempDir: Path) {
        val a = Files.createDirectories(tempDir.resolve("lua5.1/bin"))
        val b = Files.createDirectories(tempDir.resolve("lua5.4/bin"))
        Files.createDirectories(tempDir.resolve("lua5.4/share"))

        val result = LuaToolDiscovery.expandSearchPath("$tempDir/lua5.*/bin")

        assertEquals(listOf(a, b), result)
    }

    @Test
    fun testLiteralPathReturnsSingleElement(@TempDir tempDir: Path) {
        Files.createDirectories(tempDir.resolve("Lua 5.1"))

        val result = LuaToolDiscovery.expandSearchPath("$tempDir/Lua 5.1")

        assertEquals(listOf(Path.of("$tempDir/Lua 5.1")), result)
    }

    @Test
    fun testLiteralNonExistentPathReturnsSingleElement() {
        val result = LuaToolDiscovery.expandSearchPath("/no/such/literal/dir")

        assertEquals(listOf(Path.of("/no/such/literal/dir")), result)
    }

    @Test
    fun testDottedGlobRequiresDot(@TempDir tempDir: Path) {
        val a = Files.createDirectories(tempDir.resolve("Lua 5.1"))
        val b = Files.createDirectories(tempDir.resolve("Lua 5.4"))
        Files.createDirectories(tempDir.resolve("Lua 5"))

        val result = LuaToolDiscovery.expandSearchPath("$tempDir/Lua 5.*")

        assertEquals(listOf(a, b), result)
    }

    @Test
    fun testMatchesAreSortedAscending(@TempDir tempDir: Path) {
        val a = Files.createDirectories(tempDir.resolve("lua5.1"))
        val b = Files.createDirectories(tempDir.resolve("lua5.2"))
        val c = Files.createDirectories(tempDir.resolve("lua5.4"))

        val result = LuaToolDiscovery.expandSearchPath("$tempDir/lua5.*")

        assertEquals(listOf(a, b, c), result)
    }

    @Test
    fun testMissingBaseReturnsEmpty() {
        val result = LuaToolDiscovery.expandSearchPath("/no/such/base/Lua 5.*")

        assertEquals(emptyList<Path>(), result)
    }

    @Test
    fun testNoMatchReturnsEmpty(@TempDir tempDir: Path) {
        val result = LuaToolDiscovery.expandSearchPath("$tempDir/Ruby *")

        assertEquals(emptyList<Path>(), result)
    }

    @Test
    fun testQuestionMarkMatchesSingleChar(@TempDir tempDir: Path) {
        val a = Files.createDirectories(tempDir.resolve("lua51"))
        val b = Files.createDirectories(tempDir.resolve("lua54"))
        Files.createDirectories(tempDir.resolve("luaX"))

        val result = LuaToolDiscovery.expandSearchPath("$tempDir/lua5?")

        assertEquals(listOf(a, b), result)
    }
}

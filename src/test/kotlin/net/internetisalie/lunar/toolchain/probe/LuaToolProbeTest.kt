package net.internetisalie.lunar.toolchain.probe

import com.intellij.openapi.util.SystemInfo
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.toolchain.model.LuaToolKind
import net.internetisalie.lunar.toolchain.model.ProbeSpec
import net.internetisalie.lunar.toolchain.model.SemanticVersion
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LuaToolProbeTest {

    private val probe = LuaToolProbeImpl()

    // TC 1: Fake `lua` binary printing `Lua 5.4.6  Copyright (C) 1994-2023 Lua.org, PUC-Rio` on stdout
    @Test
    fun testInterpretLuaHappyPath_TC1() {
        val luaKind = LuaToolKindRegistry.findById("lua")!!
        val output = "Lua 5.4.6  Copyright (C) 1994-2023 Lua.org, PUC-Rio"
        val result = probe.interpret(output, luaKind)

        assertTrue(result.ok)
        assertEquals("5.4.6", result.version)
        assertNull(result.luaVersion)
        val runtime = result.runtime
        assertNotNull(runtime)
        assertEquals("Lua", runtime!!.product)
        assertEquals("5.4.6", runtime.version)
        assertEquals(LuaLanguageLevel.LUA54, runtime.languageLevel)
        assertEquals(LuaPlatform.STANDARD, runtime.platform)
        assertEquals("Lua 5.4.6  Copyright (C) 1994-2023 Lua.org, PUC-Rio", runtime.banner)
        assertNull(result.failure)
    }

    // TC 2: Fake `lua` printing `Lua 5.1.5  Copyright (C) 1994-2012 Lua.org, PUC-Rio` on stderr
    @Test
    fun testInterpretLuaStderr_TC2() {
        val luaKind = LuaToolKindRegistry.findById("lua")!!
        val output = "Lua 5.1.5  Copyright (C) 1994-2012 Lua.org, PUC-Rio"
        val result = probe.interpret(output, luaKind)

        assertTrue(result.ok)
        assertEquals("5.1.5", result.version)
        assertNull(result.luaVersion)
        val runtime = result.runtime
        assertNotNull(runtime)
        assertEquals("Lua", runtime!!.product)
        assertEquals("5.1.5", runtime.version)
        assertEquals(LuaLanguageLevel.LUA51, runtime.languageLevel)
        assertEquals(LuaPlatform.STANDARD, runtime.platform)
        assertEquals("Lua 5.1.5  Copyright (C) 1994-2012 Lua.org, PUC-Rio", runtime.banner)
        assertNull(result.failure)
    }

    // TC 3: Output `LuaJIT 2.1.1700008891 -- Copyright (C) 2005-2023 Mike Pall. https://luajit.org/`
    @Test
    fun testInterpretLuaJit_TC3() {
        val luajitKind = LuaToolKindRegistry.findById("luajit")!!
        val output = "LuaJIT 2.1.1700008891 -- Copyright (C) 2005-2023 Mike Pall. https://luajit.org/"
        val result = probe.interpret(output, luajitKind)

        assertTrue(result.ok)
        assertEquals("2.1.1700008891", result.version)
        assertNull(result.luaVersion)
        val runtime = result.runtime
        assertNotNull(runtime)
        assertEquals("LuaJIT", runtime!!.product)
        assertEquals("2.1.1700008891", runtime.version)
        assertEquals(LuaLanguageLevel.LUA51, runtime.languageLevel)
        assertEquals(LuaPlatform.LUAJIT, runtime.platform)
        assertNull(result.failure)
    }

    // TC 4: Output `/usr/local/bin/luarocks 3.11.0\nLuaRocks main command-line interface`
    @Test
    fun testInterpretLuaRocksNoLuaVersion_TC4() {
        val luarocksKind = LuaToolKindRegistry.findById("luarocks")!!
        val output = "/usr/local/bin/luarocks 3.11.0\nLuaRocks main command-line interface"
        val result = probe.interpret(output, luarocksKind)

        assertTrue(result.ok)
        assertEquals("3.11.0", result.version)
        assertNull(result.luaVersion)
        assertNull(result.runtime)
        assertNull(result.failure)
    }

    // TC 5: Same as #4 plus a line containing `for Lua 5.4`
    @Test
    fun testInterpretLuaRocksWithLuaVersion_TC5() {
        val luarocksKind = LuaToolKindRegistry.findById("luarocks")!!
        val output = "/usr/local/bin/luarocks 3.11.0\nfor Lua 5.4\nLuaRocks main command-line interface"
        val result = probe.interpret(output, luarocksKind)

        assertTrue(result.ok)
        assertEquals("3.11.0", result.version)
        assertEquals("5.4", result.luaVersion)
        assertNull(result.runtime)
        assertNull(result.failure)
    }

    // TC 6: Output `LuaRocks 2.4.4` (below minVersion 3.0.0)
    @Test
    fun testInterpretLuaRocksBelowMinVersion_TC6() {
        val luarocksKind = LuaToolKindRegistry.findById("luarocks")!!
        val output = "LuaRocks 2.4.4"
        val result = probe.interpret(output, luarocksKind)

        assertFalse(result.ok)
        assertEquals("2.4.4", result.version)
        assertNull(result.luaVersion)
        assertNull(result.runtime)
        assertEquals("LuaRocks 2.4.4", result.failure)
    }

    // TC 7: Output `Luacheck: 0.26.0` / `stylua 0.20.0` / bare `2.2.0` (busted) / `--help` text containing `LuaCov 0.15.0 - coverage analyzer for Lua`
    @Test
    fun testInterpretOtherKinds_TC7() {
        val luacheckKind = LuaToolKindRegistry.findById("luacheck")!!
        val luacheckResult = probe.interpret("Luacheck: 0.26.0", luacheckKind)
        assertTrue(luacheckResult.ok)
        assertEquals("0.26.0", luacheckResult.version)

        val styluaKind = LuaToolKindRegistry.findById("stylua")!!
        val styluaResult = probe.interpret("stylua 0.20.0", styluaKind)
        assertTrue(styluaResult.ok)
        assertEquals("0.20.0", styluaResult.version)

        val bustedKind = LuaToolKindRegistry.findById("busted")!!
        val bustedResult = probe.interpret("2.2.0", bustedKind)
        assertTrue(bustedResult.ok)
        assertEquals("2.2.0", bustedResult.version)

        val luacovKind = LuaToolKindRegistry.findById("luacov")!!
        val luacovResult = probe.interpret("LuaCov 0.15.0 - coverage analyzer for Lua", luacovKind)
        assertTrue(luacovResult.ok)
        assertEquals("0.15.0", luacovResult.version)
    }

    // TC 8: `lua`-kind probe of a binary printing `LuaJIT 2.1.0 ...`
    @Test
    fun testInterpretLuaKindWithLuaJitOutput_TC8() {
        val luaKind = LuaToolKindRegistry.findById("lua")!!
        val output = "LuaJIT 2.1.0 -- Copyright (C) 2005-2023 Mike Pall"
        val result = probe.interpret(output, luaKind)

        assertFalse(result.ok)
        assertNull(result.version)
        assertNull(result.runtime)
        assertEquals("LuaJIT 2.1.0 -- Copyright (C) 2005-2023 Mike Pall", result.failure)
    }

    // Test mismatching banner first token when version regex matches
    @Test
    fun testInterpretMismatchingBannerFirstToken() {
        val luaKind = LuaToolKindRegistry.findById("lua")!!
        val output = "AlternativeLua 5.4.6"
        val result = probe.interpret(output, luaKind)

        assertFalse(result.ok)
        assertNull(result.version)
        assertNull(result.runtime)
        assertEquals("AlternativeLua 5.4.6", result.failure)
    }

    // TC 9: Path that does not exist
    @Test
    fun testProcessLevelNonExistent_TC9(@TempDir tempDir: Path) {
        val nonExistent = tempDir.resolve("does-not-exist")
        val luaKind = LuaToolKindRegistry.findById("lua")!!
        val result = probe.probe(luaKind, nonExistent)

        assertFalse(result.ok)
        assertNull(result.version)
        assertNull(result.runtime)
        assertEquals("Not executable", result.failure)
    }

    // Process-level non-executable
    @Test
    fun testProcessLevelNonExecutable(@TempDir tempDir: Path) {
        val file = tempDir.resolve("not-executable")
        Files.writeString(file, "some content")
        file.toFile().setWritable(true)
        file.toFile().setExecutable(false)

        // Only run test if setExecutable(false) actually reports false (non-root)
        if (!file.toFile().canExecute()) {
            val luaKind = LuaToolKindRegistry.findById("lua")!!
            val result = probe.probe(luaKind, file)

            assertFalse(result.ok)
            assertEquals("Not executable", result.failure)
        }
    }

    // TC 10: Fake binary sleeping longer than the spec timeout
    @Test
    fun testProcessLevelTimeout_TC10(@TempDir tempDir: Path) {
        if (SystemInfo.isWindows) return
        val scriptFile = tempDir.resolve("fake-slow")
        Files.writeString(scriptFile, "#!/bin/sh\nsleep 2\n")
        scriptFile.toFile().setExecutable(true)

        val slowKind = LuaToolKind(
            id = "slow-tool",
            displayName = "Slow Tool",
            binaryNames = listOf("slow-tool"),
            probe = ProbeSpec(
                args = emptyList(),
                versionRegex = Regex("""(\d+\.\d+\.\d+)"""),
                timeoutMs = 100
            ),
            capabilities = emptySet()
        )

        val result = probe.probe(slowKind, scriptFile)

        assertFalse(result.ok)
        assertEquals("Timeout", result.failure)
    }

    // Process-level happy path (POSIX only)
    @Test
    fun testProcessLevelHappyPath_TC1(@TempDir tempDir: Path) {
        if (SystemInfo.isWindows) return
        val scriptFile = tempDir.resolve("fake-lua")
        Files.writeString(scriptFile, "#!/bin/sh\necho 'Lua 5.4.6  Copyright (C) 1994-2023 Lua.org, PUC-Rio'\n")
        scriptFile.toFile().setExecutable(true)

        val luaKind = LuaToolKindRegistry.findById("lua")!!
        val result = probe.probe(luaKind, scriptFile)

        assertTrue(result.ok)
        assertEquals("5.4.6", result.version)
        val runtime = result.runtime
        assertNotNull(runtime)
        assertEquals("Lua", runtime!!.product)
        assertEquals("5.4.6", runtime.version)
        assertEquals(LuaLanguageLevel.LUA54, runtime.languageLevel)
        assertEquals(LuaPlatform.STANDARD, runtime.platform)
        assertNull(result.failure)
    }
}

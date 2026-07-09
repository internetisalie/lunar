package net.internetisalie.lunar.toolchain.probe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.toolchain.model.LuaToolKind
import net.internetisalie.lunar.toolchain.model.ProbeSpec
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Process-level probe tests that need a live Application so that
 * [net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService.getInstance] can resolve the
 * application service. Tests run off the EDT via [onPooledThread] because [LuaToolProbeImpl.probe]
 * asserts a background thread. Pure banner-parsing tests live in [LuaToolProbeTest] (plain JUnit 5).
 */
class LuaToolProbeProcessTest : BasePlatformTestCase() {

    private val probe = LuaToolProbeImpl()
    private lateinit var tempDir: Path

    override fun setUp() {
        super.setUp()
        tempDir = Files.createTempDirectory("lua-probe-test")
    }

    override fun tearDown() {
        try {
            tempDir.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    private fun <T> onPooledThread(body: () -> T): T =
        ApplicationManager.getApplication().executeOnPooledThread<T>(body).get(30, TimeUnit.SECONDS)

    // TC 1 (process-level): Fake `lua` binary printing the standard Lua 5.4 banner
    fun testProcessLevelHappyPath_TC1() {
        if (SystemInfo.isWindows) return
        val scriptFile = tempDir.resolve("fake-lua")
        Files.writeString(scriptFile, "#!/bin/sh\necho 'Lua 5.4.6  Copyright (C) 1994-2023 Lua.org, PUC-Rio'\n")
        scriptFile.toFile().setExecutable(true)

        val luaKind = LuaToolKindRegistry.findById("lua")!!
        val result = onPooledThread { probe.probe(luaKind, scriptFile) }

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

    // TC 10 (process-level): Binary sleeping longer than the per-kind probe timeout
    fun testProcessLevelTimeout_TC10() {
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
                timeoutMs = 100,
            ),
            capabilities = emptySet(),
        )

        val result = onPooledThread { probe.probe(slowKind, scriptFile) }

        assertFalse(result.ok)
        assertEquals("Timeout", result.failure)
    }
}

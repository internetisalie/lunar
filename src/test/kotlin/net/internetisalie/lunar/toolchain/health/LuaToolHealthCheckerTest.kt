package net.internetisalie.lunar.toolchain.health

import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.LuaToolKind
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.model.ProbeSpec
import net.internetisalie.lunar.toolchain.probe.LuaToolProbe
import net.internetisalie.lunar.toolchain.probe.LuaToolProbeResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Files

class LuaToolHealthCheckerTest {

    private val stubKind = LuaToolKind(
        id = "lua",
        displayName = "Lua",
        binaryNames = listOf("lua"),
        probe = ProbeSpec(args = listOf("-v"), versionRegex = Regex("""Lua\s+(\d+\.\d+(?:\.\d+)?)""")),
        capabilities = emptySet()
    )

    private fun fakeProbe(invocations: MutableList<Int>, result: LuaToolProbeResult): LuaToolProbe =
        object : LuaToolProbe {
            override fun probe(kind: LuaToolKind, binaryPath: Path): LuaToolProbeResult {
                invocations.add(1)
                return result
            }
        }

    private fun toolWith(path: String, health: LuaToolHealth = pristineHealth(), version: String? = null): LuaRegisteredTool =
        LuaRegisteredTool(
            id = "test-id",
            kindId = "lua",
            path = path,
            version = version,
            luaVersion = null,
            runtime = null,
            origin = Origin.MANUAL,
            environmentId = null,
            health = health
        )

    private fun pristineHealth(): LuaToolHealth =
        LuaToolHealth(fileExists = false, executable = false, probeOk = null, probedAtMtime = null, reason = null)

    // TC-TOOLING-07-01: Fast-check — missing file produces "Binary missing" with zero probe invocations
    @Test
    fun testFastCheck_missingFile_noProbeCalled(@TempDir tempDir: Path) {
        val invocations = mutableListOf<Int>()
        val missingPath = tempDir.resolve("does-not-exist").toString()
        val tool = toolWith(missingPath)
        val probe = fakeProbe(invocations, LuaToolProbeResult(ok = true, version = "1.0", luaVersion = null, runtime = null, failure = null))

        val result = LuaToolHealthChecker.check(tool, stubKind, probe)

        assertFalse(result.health.fileExists)
        assertFalse(result.health.executable)
        assertNull(result.health.probeOk)
        assertNull(result.health.probedAtMtime)
        assertEquals("Binary missing", result.health.reason)
        assertNull(result.version)
        assertNull(result.luaVersion)
        assertNull(result.runtime)
        assertEquals(0, invocations.size, "Probe must not be called when file is missing")
    }

    // TC-TOOLING-07-01: Fast-check — non-executable file produces "Permission denied" with zero probe invocations
    @Test
    fun testFastCheck_nonExecutableFile_noProbeCalled(@TempDir tempDir: Path) {
        val targetFile = tempDir.resolve("notexec").toFile()
        targetFile.writeText("#!/bin/sh")
        targetFile.setExecutable(false)

        // Only meaningful outside root
        if (targetFile.canExecute()) return

        val invocations = mutableListOf<Int>()
        val tool = toolWith(targetFile.absolutePath)
        val probe = fakeProbe(invocations, LuaToolProbeResult(ok = true, version = "1.0", luaVersion = null, runtime = null, failure = null))

        val result = LuaToolHealthChecker.check(tool, stubKind, probe)

        assertTrue(result.health.fileExists)
        assertFalse(result.health.executable)
        assertNull(result.health.probeOk)
        assertNull(result.health.probedAtMtime)
        assertEquals("Permission denied", result.health.reason)
        assertNull(result.version)
        assertEquals(0, invocations.size, "Probe must not be called when file is not executable")
    }

    // TC-TOOLING-07-02: Probe success — health and version populated from probe result
    @Test
    fun testProbeSuccess(@TempDir tempDir: Path) {
        val targetFile = tempDir.resolve("lua").toFile()
        targetFile.writeText("#!/bin/sh\necho 'Lua 1.1.0'")
        targetFile.setExecutable(true)

        val invocations = mutableListOf<Int>()
        val tool = toolWith(targetFile.absolutePath)
        val probe = fakeProbe(
            invocations,
            LuaToolProbeResult(ok = true, version = "1.1.0", luaVersion = null, runtime = null, failure = null)
        )

        val result = LuaToolHealthChecker.check(tool, stubKind, probe)

        assertTrue(result.health.fileExists)
        assertTrue(result.health.executable)
        assertEquals(true, result.health.probeOk)
        assertEquals(targetFile.lastModified(), result.health.probedAtMtime)
        assertEquals("OK 1.1.0", result.health.reason)
        assertEquals("1.1.0", result.version)
        assertNull(result.luaVersion)
        assertNull(result.runtime)
        assertEquals(1, invocations.size, "Probe must be called exactly once")
    }

    // TC-TOOLING-07-02: Probe failure — version/luaVersion/runtime null; probeOk=false; probedAtMtime recorded
    @Test
    fun testProbeFailure(@TempDir tempDir: Path) {
        val targetFile = tempDir.resolve("lua").toFile()
        targetFile.writeText("#!/bin/sh")
        targetFile.setExecutable(true)

        val invocations = mutableListOf<Int>()
        val tool = toolWith(targetFile.absolutePath)
        val probe = fakeProbe(
            invocations,
            LuaToolProbeResult(ok = false, version = null, luaVersion = null, runtime = null, failure = "Not executable")
        )

        val result = LuaToolHealthChecker.check(tool, stubKind, probe)

        assertTrue(result.health.fileExists)
        assertTrue(result.health.executable)
        assertEquals(false, result.health.probeOk)
        assertNotNull(result.health.probedAtMtime)
        assertEquals("Not executable", result.health.reason)
        assertNull(result.version)
        assertNull(result.luaVersion)
        assertNull(result.runtime)
        assertEquals(1, invocations.size)
    }

    // TC-TOOLING-07-02: Probe failure with null failure string falls back to "Not executable"
    @Test
    fun testProbeFailure_nullFailureFallback(@TempDir tempDir: Path) {
        val targetFile = tempDir.resolve("lua").toFile()
        targetFile.writeText("#!/bin/sh")
        targetFile.setExecutable(true)

        val tool = toolWith(targetFile.absolutePath)
        val probe = fakeProbe(
            mutableListOf(),
            LuaToolProbeResult(ok = false, version = null, luaVersion = null, runtime = null, failure = null)
        )

        val result = LuaToolHealthChecker.check(tool, stubKind, probe)

        assertEquals("Not executable", result.health.reason)
    }

    // TC-TOOLING-07-03: mtime gate — stored probeOk=true + matching mtime + version → zero probe invocations
    @Test
    fun testMtimeGate_cacheHit_noProbeCalled(@TempDir tempDir: Path) {
        val targetFile = tempDir.resolve("lua").toFile()
        targetFile.writeText("#!/bin/sh")
        targetFile.setExecutable(true)
        val mtime = targetFile.lastModified()

        val cachedHealth = LuaToolHealth(
            fileExists = true,
            executable = true,
            probeOk = true,
            probedAtMtime = mtime,
            reason = "OK 1.1.0"
        )
        val invocations = mutableListOf<Int>()
        val tool = toolWith(targetFile.absolutePath, health = cachedHealth, version = "1.1.0")
        val probe = fakeProbe(invocations, LuaToolProbeResult(ok = true, version = "1.1.0", luaVersion = null, runtime = null, failure = null))

        val result = LuaToolHealthChecker.check(tool, stubKind, probe)

        assertEquals(cachedHealth, result.health)
        assertEquals("1.1.0", result.version)
        assertEquals(0, invocations.size, "Probe must NOT be called when mtime gate is armed")
    }

    // TC-TOOLING-07-03: mtime gate — after touching file, exactly one probe invocation
    @Test
    fun testMtimeGate_afterTouch_oneProbeInvocation(@TempDir tempDir: Path) {
        val targetFile = tempDir.resolve("lua").toFile()
        targetFile.writeText("#!/bin/sh")
        targetFile.setExecutable(true)
        val originalMtime = targetFile.lastModified()

        val cachedHealth = LuaToolHealth(
            fileExists = true,
            executable = true,
            probeOk = true,
            probedAtMtime = originalMtime,
            reason = "OK 1.1.0"
        )
        val touchedMtime = originalMtime + 2000L
        targetFile.setLastModified(touchedMtime)

        val invocations = mutableListOf<Int>()
        val tool = toolWith(targetFile.absolutePath, health = cachedHealth, version = "1.1.0")
        val probe = fakeProbe(invocations, LuaToolProbeResult(ok = true, version = "1.1.0", luaVersion = null, runtime = null, failure = null))

        LuaToolHealthChecker.check(tool, stubKind, probe)

        assertEquals(1, invocations.size, "Probe must be called exactly once after file is touched")
    }

    // TC-TOOLING-07-03: mtime gate does NOT arm for probeOk=false (failed probe re-probed every call)
    @Test
    fun testMtimeGate_failedProbe_alwaysReprobes(@TempDir tempDir: Path) {
        val targetFile = tempDir.resolve("lua").toFile()
        targetFile.writeText("#!/bin/sh")
        targetFile.setExecutable(true)
        val mtime = targetFile.lastModified()

        val failedHealth = LuaToolHealth(
            fileExists = true,
            executable = true,
            probeOk = false,
            probedAtMtime = mtime,
            reason = "Timeout"
        )
        val invocations = mutableListOf<Int>()
        val tool = toolWith(targetFile.absolutePath, health = failedHealth, version = null)
        val probe = fakeProbe(invocations, LuaToolProbeResult(ok = false, version = null, luaVersion = null, runtime = null, failure = "Timeout"))

        LuaToolHealthChecker.check(tool, stubKind, probe)
        LuaToolHealthChecker.check(tool, stubKind, probe)

        assertEquals(2, invocations.size, "Failed probe must be re-probed on every call (gate not armed)")
    }

    // TC-TOOLING-07-03: mtime gate does NOT arm when version is null (gate requires non-null version)
    @Test
    fun testMtimeGate_nullVersion_doesNotArm(@TempDir tempDir: Path) {
        val targetFile = tempDir.resolve("lua").toFile()
        targetFile.writeText("#!/bin/sh")
        targetFile.setExecutable(true)
        val mtime = targetFile.lastModified()

        val healthWithNullVersion = LuaToolHealth(
            fileExists = true,
            executable = true,
            probeOk = true,
            probedAtMtime = mtime,
            reason = "OK null"
        )
        val invocations = mutableListOf<Int>()
        val tool = toolWith(targetFile.absolutePath, health = healthWithNullVersion, version = null)
        val probe = fakeProbe(invocations, LuaToolProbeResult(ok = true, version = "1.0", luaVersion = null, runtime = null, failure = null))

        LuaToolHealthChecker.check(tool, stubKind, probe)

        assertEquals(1, invocations.size, "Gate must not arm when stored version is null")
    }
}

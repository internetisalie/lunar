package net.internetisalie.lunar.toolchain.health

import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import net.internetisalie.lunar.toolchain.registry.LuaToolchainAppState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.UUID
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * TC-TOOLING-07-08: diagnostics snapshot format (design §4.1).
 *
 * Validates that [LuaToolDiagnostics.logSnapshot] emits every §4.1 line class in the correct
 * prefix format for a populated registry. Uses the [emit] sink to capture output without touching
 * the IDE log.
 */
@RunWith(JUnit4::class)
class LuaToolDiagnosticsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
        LuaToolchainProjectSettings.getInstance(project).loadState(LuaToolchainProjectState())
    }

    override fun tearDown() {
        try {
            LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
            LuaToolchainProjectSettings.getInstance(project).loadState(LuaToolchainProjectState())
        } finally {
            super.tearDown()
        }
    }

    // TC-TOOLING-07-08: all §4.1 line classes present for a populated registry.
    @Test
    fun testSnapshotFormat_allLineClassesPresent() {
        val luaTool = registerTool("lua", usable = true)
        val luacheckTool = registerTool("luacheck", usable = false)

        // Global binding: stylua → a synthetic tool id (need not be in inventory for binding lines)
        val styluaId = UUID.randomUUID().toString()
        LuaToolchainRegistry.getInstance().setGlobalBinding("stylua", styluaId)

        // Project binding: luacheck → the broken tool
        LuaToolchainProjectSettings.getInstance(project).setBinding("luacheck", luacheckTool.id)

        // Active environment containing the lua tool
        val env = LuaEnvironmentState(
            id = UUID.randomUUID().toString(),
            name = "lua54",
            rootDir = "/tmp/test-env",
            toolIds = mutableListOf(luaTool.id)
        )
        LuaToolchainProjectSettings.getInstance(project).upsertEnvironmentAndActivate(env)

        val lines = captureLines()
        assertLinePresent(lines, "[TOOLCHAIN-DIAG] snapshot")
        assertLineContains(lines, "[TOOLCHAIN-DIAG] snapshot", "project=")
        assertLineContains(lines, "[TOOLCHAIN-DIAG] snapshot", "kinds=")
        assertLineContains(lines, "[TOOLCHAIN-DIAG] snapshot", "tools=2")
        assertLineContains(lines, "[TOOLCHAIN-DIAG] snapshot", "envs=1")
        assertToolLinePresent(lines, luaTool, usable = true)
        assertToolLinePresent(lines, luacheckTool, usable = false)
        assertBindingLinePresent(lines, "global", "stylua")
        assertBindingLinePresent(lines, "project", "luacheck")
        assertEnvLinePresent(lines, env)
        assertResolveLinePresent(lines)
    }

    @Test
    fun testSnapshotFormat_toolLineHealthFields() {
        val tool = registerTool("lua", usable = true)
        val lines = captureLines()
        val toolLine = lines.first { it.contains("[TOOLCHAIN-DIAG] tool") && it.contains("kind=lua") }
        assertTrue("health bracket present", toolLine.contains("health=["))
        assertTrue("exists field", toolLine.contains("exists="))
        assertTrue("exec field", toolLine.contains("exec="))
        assertTrue("probe field", toolLine.contains("probe="))
        assertTrue("mtime field", toolLine.contains("mtime="))
        assertTrue("reason field", toolLine.contains("reason="))
        assertTrue("origin field", toolLine.contains("origin=${tool.origin}"))
        assertTrue("id8 present", toolLine.contains("id=${tool.id.take(8)}"))
    }

    @Test
    fun testSnapshotFormat_nullProject_onlyGlobalBindingsAndTools() {
        registerTool("lua", usable = true)
        LuaToolchainRegistry.getInstance().setGlobalBinding("stylua", UUID.randomUUID().toString())
        LuaToolchainProjectSettings.getInstance(project).setBinding("luacheck", UUID.randomUUID().toString())

        val lines = mutableListOf<String>()
        LuaToolDiagnostics.logSnapshot(null) { lines.add(it) }

        assertLineContains(lines, "[TOOLCHAIN-DIAG] snapshot", "project='-'")
        assertLinePresent(lines, "[TOOLCHAIN-DIAG] tool")
        assertBindingLinePresent(lines, "global", "stylua")
        assertTrue("no project bindings emitted", lines.none { it.contains("scope=project") })
        assertTrue("no env lines emitted", lines.none { it.contains("[TOOLCHAIN-DIAG] env") })
        assertTrue("no resolve lines emitted", lines.none { it.contains("[TOOLCHAIN-DIAG] resolve") })
    }

    @Test
    fun testSnapshotFormat_emptyInventory_headerOnly() {
        val lines = captureLines()
        assertEquals("empty inventory emits header line only", 1, lines.size)
        assertTrue(lines.first().startsWith("[TOOLCHAIN-DIAG] snapshot"))
    }

    @Test
    fun testSnapshotFormat_resolveLines_coveredKinds() {
        registerTool("lua", usable = true)
        val lines = captureLines()
        val resolveLines = lines.filter { it.contains("[TOOLCHAIN-DIAG] resolve") }
        val kindIds = LuaToolKindRegistry.all().map { it.id }.toSet()
        assertEquals("one resolve line per registered kind", kindIds.size, resolveLines.size)
        val luaLine = resolveLines.first { it.contains("kind=lua") }
        assertTrue("lua resolves to a tool", luaLine.contains("-> id="))
        val bustedLine = resolveLines.first { it.contains("kind=busted") }
        assertTrue("busted has no tool -> none", bustedLine.endsWith("-> none"))
    }

    @Test
    fun testSnapshotFormat_bindingLines_globalFirst_kindAscending() {
        LuaToolchainRegistry.getInstance().setGlobalBinding("stylua", UUID.randomUUID().toString())
        LuaToolchainRegistry.getInstance().setGlobalBinding("luacheck", UUID.randomUUID().toString())
        registerTool("lua", usable = true)

        val lines = captureLines()
        val bindingLines = lines.filter { it.contains("[TOOLCHAIN-DIAG] binding") }
        val globalLines = bindingLines.filter { it.contains("scope=global") }
        val projectLines = bindingLines.filter { it.contains("scope=project") }
        assertTrue("global bindings present", globalLines.isNotEmpty())
        assertTrue("no project bindings set", projectLines.isEmpty())
        val globalKinds = globalLines.map { line ->
            line.substringAfter("kind=").substringBefore(" ")
        }
        assertEquals("global bindings sorted ascending", globalKinds.sorted(), globalKinds)
    }

    @Test
    fun testSnapshotFormat_envActiveFlag() {
        registerTool("lua", usable = true)
        val envA = LuaEnvironmentState(UUID.randomUUID().toString(), "envA", "/tmp/a", mutableListOf())
        val envB = LuaEnvironmentState(UUID.randomUUID().toString(), "envB", "/tmp/b", mutableListOf())
        LuaToolchainProjectSettings.getInstance(project).upsertEnvironment(envA)
        LuaToolchainProjectSettings.getInstance(project).upsertEnvironmentAndActivate(envB)

        val lines = captureLines()
        val envLines = lines.filter { it.contains("[TOOLCHAIN-DIAG] env") }
        assertEquals("two env lines", 2, envLines.size)
        val activeCount = envLines.count { it.contains("active=true") }
        val inactiveCount = envLines.count { it.contains("active=false") }
        assertEquals("one active env", 1, activeCount)
        assertEquals("one inactive env", 1, inactiveCount)
    }

    private fun captureLines(): List<String> {
        val lines = mutableListOf<String>()
        LuaToolDiagnostics.logSnapshot(project) { lines.add(it) }
        return lines
    }

    private fun registerTool(kindId: String, usable: Boolean): LuaRegisteredTool {
        val id = UUID.randomUUID().toString()
        val health = if (usable) {
            LuaToolHealth(fileExists = true, executable = true, probeOk = true, probedAtMtime = 1L, reason = "OK 1.0.0")
        } else {
            LuaToolHealth(fileExists = false, executable = false, probeOk = null, probedAtMtime = null, reason = "Binary missing")
        }
        val tool = LuaRegisteredTool(
            id = id,
            kindId = kindId,
            path = "/fake/path/$kindId",
            version = if (usable) "1.0.0" else null,
            luaVersion = null,
            runtime = null,
            origin = Origin.MANUAL,
            environmentId = null,
            health = health
        )
        LuaToolchainRegistry.getInstance().registerProvisioned(tool)
        return tool
    }

    private fun assertLinePresent(lines: List<String>, prefix: String) {
        assertTrue("Expected a line starting with '$prefix' in:\n${lines.joinToString("\n")}",
            lines.any { it.startsWith(prefix) })
    }

    private fun assertLineContains(lines: List<String>, prefix: String, fragment: String) {
        val matching = lines.filter { it.startsWith(prefix) }
        assertTrue(
            "Expected line starting with '$prefix' to contain '$fragment'. Lines:\n${matching.joinToString("\n")}",
            matching.any { it.contains(fragment) }
        )
    }

    private fun assertToolLinePresent(lines: List<String>, tool: LuaRegisteredTool, usable: Boolean) {
        val toolLines = lines.filter { it.contains("[TOOLCHAIN-DIAG] tool") && it.contains("id=${tool.id.take(8)}") }
        assertTrue("Expected tool line for id=${tool.id.take(8)}", toolLines.isNotEmpty())
        val line = toolLines.first()
        assertTrue("kind present", line.contains("kind=${tool.kindId}"))
        assertTrue("path present", line.contains("path=${tool.path}"))
        assertTrue("health bracket present", line.contains("health=["))
        if (usable) {
            assertTrue("usable tool: exists=true", line.contains("exists=true"))
        } else {
            assertTrue("broken tool: exists=false", line.contains("exists=false"))
        }
    }

    private fun assertBindingLinePresent(lines: List<String>, scope: String, kindId: String) {
        val found = lines.any { it.contains("[TOOLCHAIN-DIAG] binding") && it.contains("scope=$scope") && it.contains("kind=$kindId") }
        assertTrue("Expected binding line: scope=$scope kind=$kindId in:\n${lines.joinToString("\n")}", found)
    }

    private fun assertEnvLinePresent(lines: List<String>, env: LuaEnvironmentState) {
        val found = lines.any { it.contains("[TOOLCHAIN-DIAG] env") && it.contains("id=${env.id.take(8)}") && it.contains("name='${env.name}'") }
        assertTrue("Expected env line for id=${env.id.take(8)} name='${env.name}'", found)
        val activeLine = lines.firstOrNull { it.contains("[TOOLCHAIN-DIAG] env") && it.contains("id=${env.id.take(8)}") }
        assertNotNull("env line exists", activeLine)
        assertTrue("env is active", activeLine!!.contains("active=true"))
    }

    private fun assertResolveLinePresent(lines: List<String>) {
        val resolveLines = lines.filter { it.contains("[TOOLCHAIN-DIAG] resolve") }
        assertTrue("at least one resolve line present", resolveLines.isNotEmpty())
        val kindCount = LuaToolKindRegistry.all().size
        assertEquals("one resolve line per registered kind", kindCount, resolveLines.size)
    }
}

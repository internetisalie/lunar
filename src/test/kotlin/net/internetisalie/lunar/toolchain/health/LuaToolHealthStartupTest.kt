package net.internetisalie.lunar.toolchain.health

import com.intellij.openapi.progress.EmptyProgressIndicator
import kotlinx.coroutines.runBlocking
import net.internetisalie.lunar.toolchain.exec.LuaExecutionEnvironmentBuilder
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import java.nio.file.Path
import java.util.UUID

/**
 * BUG-422 / [[BUG-442]] — the project-open health pass must not run in unit-test mode.
 *
 * `LuaToolHealthMonitor.revalidateAll` re-probes every tool in the **application-level** registry
 * against the real filesystem and writes the result back. Every toolchain fixture in this suite
 * seeds tools at paths that do not exist and asserts their health directly, so a pass landing
 * mid-test rewrites that health to `Binary missing` and `isUsable` goes false — after which every
 * `LuaToolResolver` tier rejects the tool and `pathPrependDirs()` comes back **empty**.
 *
 * That empty list is what both intermittent failures needed, and it explains their most confusing
 * detail: the assertion *before* the failing one always passed. `LuaInterpreterCommandLines.forProject`
 * resolves the runtime first and builds the environment second, so a pass landing between the two
 * leaves `exePath` correct while the PATH prepend list is already empty. `LuaLaunchEnvironment.applyPath`
 * returns early on an empty list and never assigns PATH at all — read as
 * `expected the runtime dir prepended to PATH` in `LuaInterpreterCommandLinesTest`, and as a bare NPE
 * on `commandLine.environment["PATH"]!!` in `LuaTestRunnerTest`.
 *
 * Because the registry is application-level, the project open that triggered the pass need not be in
 * the same test class — which is the ordering dependency, and why adding unrelated test classes moved
 * the odds.
 */
class LuaToolHealthStartupTest : ToolchainSettingsTestCase() {
    private val builder: LuaExecutionEnvironmentBuilder
        get() = LuaExecutionEnvironmentBuilder.getInstance(project)

    fun testStartupDoesNotRevalidateInUnitTestMode() {
        bindRuntime("/opt/lua/bin/lua")
        assertEquals(listOf(Path.of("/opt/lua/bin")), builder.pathPrependDirs())

        runBlocking { LuaToolHealthStartup().execute(project) }
        builder.invalidate()

        assertEquals(
            "a project-open health pass must not strip a synthetic tool out from under a running test",
            listOf(Path.of("/opt/lua/bin")),
            builder.pathPrependDirs(),
        )
    }

    /**
     * The mechanism itself, pinned so the reasoning above cannot rot: driven explicitly, a
     * revalidation pass *does* mark a nonexistent binary unusable. That is correct behaviour — the
     * defect was only that it ran unbidden, concurrently, against fixtures that had asserted
     * otherwise.
     */
    fun testAnExplicitRevalidationStillMarksAMissingBinaryUnusable() {
        bindRuntime("/opt/lua/bin/lua")

        LuaToolHealthMonitor.getInstance(project).revalidateNow(EmptyProgressIndicator())
        builder.invalidate()

        val tool = registry.tools().single()
        assertFalse("the binary really is missing", tool.health.fileExists)
        assertEquals("Binary missing", tool.health.reason)
        assertEquals(emptyList<Path>(), builder.pathPrependDirs())
    }

    private fun bindRuntime(path: String) {
        val tool =
            LuaRegisteredTool(
                id = UUID.randomUUID().toString(),
                kindId = "lua",
                path = path,
                version = "1.0.0",
                luaVersion = null,
                runtime = null,
                origin = Origin.MANUAL,
                environmentId = null,
                health =
                    LuaToolHealth(
                        fileExists = true,
                        executable = true,
                        probeOk = true,
                        probedAtMtime = 1L,
                        reason = null,
                    ),
            )
        registry.registerProvisioned(tool)
        settings.setBinding("lua", tool.id)
        builder.invalidate()
    }
}

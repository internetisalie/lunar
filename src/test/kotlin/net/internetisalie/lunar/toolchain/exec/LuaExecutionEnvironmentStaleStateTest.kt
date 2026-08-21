package net.internetisalie.lunar.toolchain.exec

import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.LuaToolchainAppState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectState
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import net.internetisalie.lunar.toolchain.registry.toState
import java.nio.file.Path
import java.util.UUID

/**
 * BUG-422 — `loadState` replaces the toolchain without publishing, so a cached PATH-prepend list
 * outlives the tools it was derived from and is **served**.
 *
 * Every other mutator publishes on `LuaToolchainListener.TOPIC`, which
 * [LuaExecutionEnvironmentBuilder] subscribes to. `loadState` is the one that cannot: the platform
 * calls it while loading persisted component state (project open, an external `.idea` edit, a VCS
 * branch switch, settings import), and this topic's other listeners — the health monitor and the
 * target synchronizer — must not run at that point. Hence the generation stamp rather than a new
 * event.
 *
 * **Why an empty list is the dangerous stale value.** It is a legitimate answer, so a caller gets
 * "no PATH entries" rather than a recomputation, and `LuaLaunchEnvironment.applyPath` then returns
 * early and leaves PATH **untouched**. That is the shape both intermittent failures took: the
 * resolver finds the tool (so `exePath` is right) while the prepend list is empty, which reads as
 * `expected the runtime dir prepended to PATH` in `LuaInterpreterCommandLinesTest` and as a bare NPE
 * on `commandLine.environment["PATH"]!!` in `LuaTestRunnerTest` ([[BUG-442]]).
 *
 * It is also why the suite could not isolate itself: `ToolchainSettingsTestCase.resetState` and
 * `LuaTestRunnerTest.resetToolchain` reset the toolchain **exclusively** through `loadState`, so the
 * reset that is supposed to clean up between classes could not clear this cache.
 */
class LuaExecutionEnvironmentStaleStateTest : ToolchainSettingsTestCase() {
    private val builder: LuaExecutionEnvironmentBuilder
        get() = LuaExecutionEnvironmentBuilder.getInstance(project)

    /** The production case: the platform reloads persisted state and the PATH cache must follow. */
    fun testLoadStateRetiresTheCachedPathPrependDirs() {
        bindRuntime("/opt/lua/bin/lua")
        assertEquals(listOf(Path.of("/opt/lua/bin")), builder.pathPrependDirs())

        val appState = registry.state
        val projectState = settings.state

        // Wipe through loadState, exactly as the test bases' reset helpers do.
        registry.loadState(LuaToolchainAppState())
        settings.loadState(LuaToolchainProjectState())
        builder.invalidate()
        assertEquals("the cache is now pinned to the empty list", emptyList<Path>(), builder.pathPrependDirs())

        // Restore through loadState. Nothing is published, so only the generation stamp can save us.
        registry.loadState(appState)
        settings.loadState(projectState)

        assertEquals(
            "loadState changed the toolchain without retiring the PATH cache",
            listOf(Path.of("/opt/lua/bin")),
            builder.pathPrependDirs(),
        )
    }

    /**
     * The consequence at the surface both flakes asserted on: a stale **empty** list makes
     * `applyPath` leave PATH unset, so `environment["PATH"]` is null rather than merely wrong.
     */
    fun testAStaleEmptyListWouldLeavePathUnset() {
        assertEquals("no tools bound yet", emptyList<Path>(), builder.pathPrependDirs())

        bindRuntimeWithoutInvalidating("/opt/lua/bin/lua")

        val cmd =
            LuaInterpreterCommandLines.forProject(project)
                ?: error("expected a command line for a bound runtime")
        assertEquals("/opt/lua/bin/lua", cmd.exePath)
        assertNotNull("PATH must be set once a runtime is bound", cmd.environment["PATH"])
        assertTrue(cmd.environment["PATH"].orEmpty().startsWith("/opt/lua/bin"))
    }

    private fun bindRuntime(path: String) {
        bindRuntimeWithoutInvalidating(path)
        builder.invalidate()
    }

    /** Installs a runtime through `loadState` only — the mutation that publishes nothing. */
    private fun bindRuntimeWithoutInvalidating(path: String) {
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
        val appState = LuaToolchainAppState()
        appState.tools.add(tool.toState())
        val projectState = LuaToolchainProjectState()
        projectState.bindings["lua"] = tool.id
        registry.loadState(appState)
        settings.loadState(projectState)
    }
}

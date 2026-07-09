package net.internetisalie.lunar.rocks.matrix

import com.intellij.execution.configurations.GeneralCommandLine
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import java.nio.file.Path

/**
 * TOOLING-05 Phase 5 (TC 12): matrix rows resolve each env's `luarocks` via the per-environment
 * resolver overload, and an env with no provisioned luarocks fails its own row (exit -1, "not
 * provisioned") without aborting the others.
 */
class MatrixRunnerTest : ToolchainSettingsTestCase() {

    private val rockspec = Path.of("/p/foo-1.0-1.rockspec")

    private fun envWithLuarocks(id: String): Pair<LuaEnvironmentState, String> {
        val luarocks = seedTool("luarocks", usable = true, environmentId = id)
        val env = LuaEnvironmentState(id = id, name = id, rootDir = "/p/$id", toolIds = mutableListOf(luarocks.id))
        return env to luarocks.path
    }

    fun `test commandLineFor uses the env's own luarocks`() {
        val (env, luarocksPath) = envWithLuarocks("E1")
        val cmd = MatrixRunner.commandLineFor(env, "test", rockspec)
        assertNotNull(cmd)
        assertEquals(luarocksPath, cmd!!.exePath)
        assertEquals(listOf("test", "/p/foo-1.0-1.rockspec"), cmd.parametersList.parameters)
    }

    fun `test commandLineFor is null when env has no luarocks`() {
        val env = LuaEnvironmentState(id = "E0", name = "E0", rootDir = "/p/E0", toolIds = mutableListOf())
        assertNull(MatrixRunner.commandLineFor(env, "test", rockspec))
    }

    fun `test each row uses its own env luarocks and a missing one fails without aborting others`() {
        val (env1, luarocks1) = envWithLuarocks("E1")
        val (env2, luarocks2) = envWithLuarocks("E2")
        val env3 = LuaEnvironmentState(id = "E3", name = "E3", rootDir = "/p/E3", toolIds = mutableListOf())

        val seen = mutableListOf<GeneralCommandLine>()
        val runner = MatrixRunner.RowRunner { _, command ->
            seen.add(command)
            RowOutcome(0, "ok")
        }

        val result = MatrixRunner.execute(
            MatrixRunner.Request("test", rockspec, listOf(env1, env2, env3)),
            runner,
        )

        // Rows 1 and 2 spawned against their own env's luarocks.
        assertEquals(luarocks1, seen[0].exePath)
        assertEquals(luarocks2, seen[1].exePath)
        assertEquals(2, seen.size)

        assertEquals(Status.PASS, result.rows[0].status)
        assertEquals(Status.PASS, result.rows[1].status)

        // Row 3 (no luarocks) fails with exit -1 and a "not provisioned" message — not spawned.
        assertEquals(Status.FAIL, result.rows[2].status)
        assertEquals(-1, result.rows[2].exitCode)
        assertTrue(result.rows[2].output.contains("not provisioned"))
        assertTrue(result.rows[2].output.contains("E3"))

        assertFalse(result.allPassed)
    }

    fun `test empty env set yields no runner call`() {
        var invoked = false
        val runner = MatrixRunner.RowRunner { _, _ ->
            invoked = true
            RowOutcome(0, "")
        }
        val result = MatrixRunner.execute(MatrixRunner.Request("test", rockspec, emptyList()), runner)
        assertTrue(result.rows.isEmpty())
        assertFalse(result.allPassed)
        assertFalse(invoked)
    }
}

package net.internetisalie.lunar.rocks.env.matrix

import com.intellij.execution.configurations.GeneralCommandLine
import junit.framework.TestCase
import net.internetisalie.lunar.rocks.env.HererocksEnvState
import net.internetisalie.lunar.rocks.env.HererocksFlavor
import java.nio.file.Path

/** Phase 4: command construction + aggregation (TC-7, TC-8). */
class MatrixRunnerTest : TestCase() {

    private val rockspec = Path.of("/p/foo-1.0-1.rockspec")

    private fun env(id: String, dir: String) =
        HererocksEnvState(id = id, directory = dir, flavor = HererocksFlavor.PUC, luaVersion = "5.3")

    fun testCommandLineFor() {
        val cmd = MatrixRunner.commandLineFor(env("A", "/p/envs/PUC-5.3"), "test", rockspec)
        assertEquals("/p/envs/PUC-5.3/bin/luarocks", cmd.exePath)
        assertEquals(listOf("test", "/p/foo-1.0-1.rockspec"), cmd.parametersList.parameters)
    }

    fun testExecuteAggregatesPerRow() {
        val a = env("A", "/p/envs/PUC-5.3")
        val b = env("B", "/p/envs/PUC-5.4")
        val seen = mutableListOf<GeneralCommandLine>()
        val runner = MatrixRunner.RowRunner { rowEnv, command ->
            seen.add(command)
            if (rowEnv.id == "A") RowOutcome(0, "ok") else RowOutcome(1, "boom")
        }

        val result = MatrixRunner.execute(MatrixRunner.Request("test", rockspec, listOf(a, b)), runner)

        assertEquals(Status.PASS, result.rows[0].status)
        assertEquals(Status.FAIL, result.rows[1].status)
        assertEquals(1, result.rows[1].exitCode)
        assertFalse(result.allPassed)
        assertEquals(2, seen.size)
        assertEquals("/p/envs/PUC-5.3/bin/luarocks", seen[0].exePath)
        assertEquals("/p/envs/PUC-5.4/bin/luarocks", seen[1].exePath)
        assertTrue(seen.all { it.parametersList.parameters == listOf("test", "/p/foo-1.0-1.rockspec") })
    }

    fun testEmptyEnvSet() {
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

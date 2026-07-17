package net.internetisalie.lunar.rocks.matrix

import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import java.nio.file.Path

/**
 * BUG-377: the matrix must cover ALL discovered rockspecs, not just the first one. Tests the
 * per-rockspec [MatrixRow.rockspecLabel] and that [MatrixRunner.execute] produces labelled rows for
 * each rockspec dimension independently.
 */
class RunMatrixRockspecExpansionTest : ToolchainSettingsTestCase() {

    private val env1 = LuaEnvironmentState(id = "E1", name = "lua-5.4", rootDir = "/p/e1")
    private val env2 = LuaEnvironmentState(id = "E2", name = "lua-5.1", rootDir = "/p/e2")

    private val rockspecA = Path.of("/p/a/a-1.0-1.rockspec")
    private val rockspecB = Path.of("/p/b/b-2.0-1.rockspec")

    private val noopRunner = MatrixRunner.RowRunner { _, _ -> RowOutcome(0, "ok") }

    fun `test execute labels each row with the rockspec filename BUG377`() {
        val result = MatrixRunner.execute(
            MatrixRunner.Request("test", rockspecA, listOf(env1)),
            noopRunner,
        )
        assertEquals(1, result.rows.size)
        assertEquals("a-1.0-1.rockspec", result.rows[0].rockspecLabel)
    }

    fun `test two rockspecs produce independent row sets BUG377`() {
        val resultA = MatrixRunner.execute(
            MatrixRunner.Request("test", rockspecA, listOf(env1, env2)),
            noopRunner,
        )
        val resultB = MatrixRunner.execute(
            MatrixRunner.Request("test", rockspecB, listOf(env1, env2)),
            noopRunner,
        )

        // rockspec A: 2 rows, all labelled with A's filename
        assertEquals(2, resultA.rows.size)
        assertTrue(resultA.rows.all { it.rockspecLabel == "a-1.0-1.rockspec" })

        // rockspec B: 2 rows, all labelled with B's filename
        assertEquals(2, resultB.rows.size)
        assertTrue(resultB.rows.all { it.rockspecLabel == "b-2.0-1.rockspec" })

        // Combined: 4 rows (env × rockspec), each env covered per rockspec
        val combined = resultA.rows + resultB.rows
        assertEquals(4, combined.size)
        val aRows = combined.filter { it.rockspecLabel == "a-1.0-1.rockspec" }
        val bRows = combined.filter { it.rockspecLabel == "b-2.0-1.rockspec" }
        assertEquals(listOf("lua-5.4", "lua-5.1"), aRows.map { it.env.name })
        assertEquals(listOf("lua-5.4", "lua-5.1"), bRows.map { it.env.name })
    }

    fun `test tableRows includes rockspec column BUG377`() {
        val result = MatrixRunner.execute(
            MatrixRunner.Request("test", rockspecA, listOf(env1)),
            noopRunner,
        )
        val rows = MatrixResultsToolWindow.tableRows(result)
        assertEquals(1, rows.size)
        // Row: [rockspec, env, status, exit]
        assertEquals("a-1.0-1.rockspec", rows[0][0])
        assertEquals("lua-5.4", rows[0][1])
        assertEquals("PASS", rows[0][2])
        assertEquals(0, rows[0][3])
    }
}

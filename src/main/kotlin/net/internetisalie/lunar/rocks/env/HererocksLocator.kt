package net.internetisalie.lunar.rocks.env

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import net.internetisalie.lunar.util.LuaProcessUtil
import java.io.File

/**
 * Resolves the command prefix used to invoke hererocks (ROCKS-14-02).
 *
 * Resolution order (first hit wins): a `hererocks` executable on `PATH`, then
 * `python3 -m hererocks` / `python -m hererocks` gated on an `import hererocks` probe. Returns
 * `null` when hererocks is unavailable so callers can surface the pip remediation hint. Runs a
 * probe process, so callers must invoke it off the EDT.
 */
object HererocksLocator {
    const val REMEDIATION: String =
        "hererocks not found. Install it with `pip install hererocks` or put it on your PATH."

    private const val PROBE_TIMEOUT_MS = 10_000

    fun resolvePrefix(): List<String>? = resolvePrefix(::findInPath, ::probeImport)

    internal fun resolvePrefix(
        findInPath: (String) -> File?,
        probeImport: (String) -> Boolean,
    ): List<String>? {
        findInPath("hererocks")?.let { return listOf(it.absolutePath) }
        for (python in listOf("python3", "python")) {
            val exe = findInPath(python) ?: continue
            if (probeImport(exe.absolutePath)) return listOf(exe.absolutePath, "-m", "hererocks")
        }
        return null
    }

    private fun findInPath(name: String): File? = PathEnvironmentVariableUtil.findInPath(name)

    private fun probeImport(pythonPath: String): Boolean {
        val output = LuaProcessUtil.capture(
            GeneralCommandLine(pythonPath, "-c", "import hererocks"),
            PROBE_TIMEOUT_MS,
        )
        return output.exitCode == 0
    }
}

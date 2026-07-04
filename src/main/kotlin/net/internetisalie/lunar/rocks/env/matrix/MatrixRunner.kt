package net.internetisalie.lunar.rocks.env.matrix

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.openapi.diagnostic.Logger
import net.internetisalie.lunar.rocks.env.HererocksEnvState
import java.nio.file.Path

/** Per-env matrix row state (ROCKS-15-04, design §2.6). */
data class MatrixRow(
    val env: HererocksEnvState,
    var status: Status = Status.PENDING,
    var exitCode: Int? = null,
    var output: String = "",
)

enum class Status { PENDING, RUNNING, PASS, FAIL }

/** Aggregated matrix outcome; passes iff every row passed and at least one row ran. */
data class MatrixResult(val rows: List<MatrixRow>) {
    val allPassed: Boolean get() = rows.isNotEmpty() && rows.all { it.status == Status.PASS }
}

/** One row's captured process outcome (exit code + accumulated output). */
data class RowOutcome(val exitCode: Int, val output: String)

/**
 * Builds per-env `luarocks` command lines and aggregates their outcomes into a [MatrixResult]
 * (ROCKS-15-04, design §2.6, §3.3). The per-row process invocation is injected as [RowRunner] so
 * command construction and aggregation are unit-testable without spawning real processes.
 */
object MatrixRunner {
    private val LOG = Logger.getInstance(MatrixRunner::class.java)

    /** Executes one row's command line and returns its outcome. */
    fun interface RowRunner {
        fun run(env: HererocksEnvState, command: GeneralCommandLine): RowOutcome
    }

    /** Bundles a matrix request to keep [execute] within the argument tripwire. */
    data class Request(val command: String, val rockspec: Path, val envs: List<HererocksEnvState>)

    /** Builds the `<env bin>/luarocks <command> <rockspec>` command line for [env] (design §3.3). */
    fun commandLineFor(env: HererocksEnvState, command: String, rockspec: Path): GeneralCommandLine =
        GeneralCommandLine(env.luarocksExe(), command, rockspec.toString())

    /**
     * Runs [request] row-by-row via [runner], mutating each [MatrixRow] with its outcome, and
     * returns the aggregate. An empty env set yields `MatrixResult(emptyList())` with no runner call.
     */
    fun execute(request: Request, runner: RowRunner): MatrixResult {
        val rows = request.envs.map { MatrixRow(it) }
        for (row in rows) {
            row.status = Status.RUNNING
            val outcome = safeRun(request, runner, row)
            row.exitCode = outcome.exitCode
            row.output = outcome.output
            row.status = if (outcome.exitCode == 0) Status.PASS else Status.FAIL
        }
        return MatrixResult(rows)
    }

    private fun safeRun(request: Request, runner: RowRunner, row: MatrixRow): RowOutcome =
        try {
            runner.run(row.env, commandLineFor(row.env, request.command, request.rockspec))
        } catch (throwable: Throwable) {
            LOG.warn("Matrix row failed for ${row.env.displayLabel()}", throwable)
            RowOutcome(-1, "Execution failed: ${throwable.message}")
        }

    /** The production [RowRunner]: spawns the process, waits, and captures output (design §3.3). */
    val processRunner: RowRunner = RowRunner { _, command ->
        val handler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(command)
        val captured = StringBuilder()
        handler.addProcessListener(
            object : com.intellij.execution.process.ProcessListener {
                override fun onTextAvailable(event: com.intellij.execution.process.ProcessEvent, outputType: com.intellij.openapi.util.Key<*>) {
                    captured.append(event.text)
                }
            },
        )
        handler.startNotify()
        handler.waitFor()
        RowOutcome(handler.exitCode ?: -1, captured.toString())
    }
}

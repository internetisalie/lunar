package net.internetisalie.lunar.rocks.matrix

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import net.internetisalie.lunar.toolchain.exec.LuaExecTimeout
import net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver
import java.nio.file.Path

/** Per-env matrix row state (ROCKS-15-04, design §2.6). */
data class MatrixRow(
    val env: LuaEnvironmentState,
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
 * command construction and aggregation are unit-testable without spawning real processes. Rows are
 * intended to run concurrently — one background task per env (see [RunMatrixAction]); [runRow]
 * executes a single row so one slow env cannot block the rest. Each env's `luarocks` is resolved
 * per-environment via [LuaToolResolver.resolveIn]; an env with no provisioned luarocks fails its
 * own row without aborting the others (design §3.6).
 */
object MatrixRunner {
    private val LOG = Logger.getInstance(MatrixRunner::class.java)

    /** Executes one row's command line and returns its outcome. */
    fun interface RowRunner {
        fun run(env: LuaEnvironmentState, command: GeneralCommandLine): RowOutcome
    }

    /** Bundles a matrix request to keep [execute] within the argument tripwire. */
    data class Request(val command: String, val rockspec: Path, val envs: List<LuaEnvironmentState>)

    /**
     * Builds the `<env luarocks> <command> <rockspec>` command line for [env] (design §3.3/§3.6), or
     * `null` when [env] has no provisioned `luarocks` tool — the caller marks that row FAIL.
     */
    fun commandLineFor(env: LuaEnvironmentState, command: String, rockspec: Path): GeneralCommandLine? {
        val luarocks = LuaToolResolver.getInstance().resolveIn(env, "luarocks")?.path ?: return null
        return GeneralCommandLine(luarocks, command, rockspec.toString())
    }

    /**
     * Executes a single [row] via [runner], mutating it in place with its outcome and status, and
     * returns the outcome. Honors cancellation via [ProgressManager.checkCanceled] before spawning.
     */
    fun runRow(request: Request, runner: RowRunner, row: MatrixRow): RowOutcome {
        ProgressManager.checkCanceled()
        row.status = Status.RUNNING
        val outcome = safeRun(request, runner, row)
        row.exitCode = outcome.exitCode
        row.output = outcome.output
        row.status = if (outcome.exitCode == 0) Status.PASS else Status.FAIL
        return outcome
    }

    /**
     * Runs [request] row-by-row via [runner], mutating each [MatrixRow] with its outcome, and
     * returns the aggregate. An empty env set yields `MatrixResult(emptyList())` with no runner call.
     * Production callers run one [runRow] per env concurrently; this helper keeps aggregation
     * unit-testable in a single call.
     */
    fun execute(request: Request, runner: RowRunner): MatrixResult {
        val rows = request.envs.map { MatrixRow(it) }
        rows.forEach { runRow(request, runner, it) }
        return MatrixResult(rows)
    }

    private fun safeRun(request: Request, runner: RowRunner, row: MatrixRow): RowOutcome {
        val command = commandLineFor(row.env, request.command, request.rockspec)
            ?: return RowOutcome(-1, "luarocks is not provisioned in ${row.env.name}")
        return try {
            runner.run(row.env, command)
        } catch (throwable: Throwable) {
            LOG.warn("Matrix row failed for ${row.env.name}", throwable)
            RowOutcome(-1, "Execution failed: ${throwable.message}")
        }
    }

    /** The production [RowRunner]: streams the process via the exec service and captures output (design §3.3). */
    val processRunner: RowRunner = RowRunner { _, command ->
        val captured = StringBuilder()
        val listener = object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                captured.append(event.text)
            }
        }
        val result = LuaToolExecutionService.getInstance()
            .stream(command, listener, LuaExecTimeout.COMMAND, colored = true)
        RowOutcome(result.exitCode, captured.toString())
    }
}

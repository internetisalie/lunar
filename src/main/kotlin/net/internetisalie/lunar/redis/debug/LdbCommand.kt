package net.internetisalie.lunar.redis.debug

/**
 * The LDB (Redis/Valkey server-side Lua debugger) command vocabulary and its RESP encoding
 * (design §2.8, §3.2).
 *
 * Pure/stateless model. [LdbWire.encode] produces only the `List<ByteArray>` argument vector; the
 * REDIS-01 `RespCodec.encodeCommand` frames it over the wire, so this layer never touches sockets
 * (epic RISK-R09). Every token is encoded as explicit UTF-8 bytes.
 */
sealed interface LdbCommand {

    /** `SCRIPT DEBUG YES` (forked) or `SCRIPT DEBUG SYNC` — enters the debug session (design §3.2). */
    data class EnterDebug(val mode: LuaRedisDebugMode) : LdbCommand

    /** `eval <expression>` — evaluates an expression in the paused frame. */
    data class Eval(val expression: String) : LdbCommand

    /** `step` — Step Into. */
    data object Step : LdbCommand

    /** `next` — Step Over. */
    data object Next : LdbCommand

    /** `continue` — Resume. */
    data object Continue : LdbCommand

    /** `abort` — Stop the session. */
    data object Abort : LdbCommand

    /** `break <line>` — add a line breakpoint. */
    data class Break(val line: Int) : LdbCommand

    /** `break -<line>` — remove a line breakpoint (negative line = delete, design §3.2). */
    data class RemoveBreak(val line: Int) : LdbCommand

    /** `break 0` — clear all breakpoints. */
    data object ClearBreaks : LdbCommand

    /** `print` (all locals) or `print <var>` (one local). */
    data class Print(val varName: String?) : LdbCommand

    /** `redis <cmd> <args…>` — a Redis command executed in the paused session. */
    data class RedisCmd(val args: List<String>) : LdbCommand

    /** `whole` — source-sync check (fetch the whole script). */
    data object ListSource : LdbCommand
}

/** Debug session mode: forked (writes rolled back) vs sync (server blocked, writes committed). */
enum class LuaRedisDebugMode { FORKED, SYNC }

/**
 * Encodes an [LdbCommand] into a RESP array-of-bulk argument vector (design §3.2). Pure and
 * thread-agnostic; the vector is handed to the REDIS-01 `RespClient.command`, which frames it.
 */
object LdbWire {

    private val UTF_8 = Charsets.UTF_8

    private const val SCRIPT_VERB = "SCRIPT"
    private const val DEBUG_VERB = "DEBUG"
    private const val FORKED_ARG = "YES"
    private const val SYNC_ARG = "SYNC"
    private const val BREAK_VERB = "break"
    private const val PRINT_VERB = "print"
    private const val EVAL_VERB = "eval"
    private const val REDIS_VERB = "redis"
    private const val CLEAR_BREAKS_ARG = "0"

    /** Maps [command] to its ordered token list, then encodes each token as UTF-8 bytes (design §3.2). */
    fun encode(command: LdbCommand): List<ByteArray> = tokensFor(command).map { it.toByteArray(UTF_8) }

    private fun tokensFor(command: LdbCommand): List<String> = when (command) {
        is LdbCommand.EnterDebug -> listOf(SCRIPT_VERB, DEBUG_VERB, modeArg(command.mode))
        LdbCommand.Step -> listOf("step")
        LdbCommand.Next -> listOf("next")
        LdbCommand.Continue -> listOf("continue")
        LdbCommand.Abort -> listOf("abort")
        is LdbCommand.Break -> listOf(BREAK_VERB, command.line.toString())
        is LdbCommand.RemoveBreak -> listOf(BREAK_VERB, "-" + command.line.toString())
        LdbCommand.ClearBreaks -> listOf(BREAK_VERB, CLEAR_BREAKS_ARG)
        is LdbCommand.Print -> printTokens(command.varName)
        is LdbCommand.Eval -> listOf(EVAL_VERB, command.expression)
        is LdbCommand.RedisCmd -> listOf(REDIS_VERB) + command.args
        LdbCommand.ListSource -> listOf("whole")
    }

    private fun modeArg(mode: LuaRedisDebugMode): String = when (mode) {
        LuaRedisDebugMode.FORKED -> FORKED_ARG
        LuaRedisDebugMode.SYNC -> SYNC_ARG
    }

    private fun printTokens(varName: String?): List<String> =
        if (varName == null) listOf(PRINT_VERB) else listOf(PRINT_VERB, varName)
}

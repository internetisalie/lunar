package net.internetisalie.lunar.redis.debug

import net.internetisalie.lunar.redis.resp.RespValue

/**
 * A typed LDB reply-block event (design §2.9, §3.3).
 *
 * Produced by [LdbReplyParser.parse] from a decoded RESP reply block. Pure data; thread-agnostic.
 */
sealed interface LdbEvent {

    /** The debugger stopped at [serverLine] (1-based); [sourceLine] is the gutter-stripped source. */
    data class Stop(val serverLine: Int, val reason: StopReason, val sourceLine: String?) : LdbEvent

    /** An in-band error (compile / runtime / eval); [scriptLine] is the `user_script:<N>` position. */
    data class Error(val kind: LdbErrorKind, val message: String, val scriptLine: Int?) : LdbEvent

    /** The debug session ended (normal, forked-timeout, or aborted). */
    data class SessionEnded(val reason: EndReason) : LdbEvent

    /** A raw Redis reply (the response to a `redis <cmd>` in the paused session). */
    data class Redis(val reply: RespValue) : LdbEvent

    /** `+OK` / an unrecognized status line — a no-op acknowledgement. */
    data object Ack : LdbEvent
}

/** Why the debugger stopped. */
enum class StopReason { BREAKPOINT, STEP, NEXT }

/** The class of an in-band LDB error. */
enum class LdbErrorKind { COMPILE, RUNTIME, EVAL_FAILED }

/** Why the debug session ended. */
enum class EndReason { ENDED, FORK_TIMEOUT, ABORTED }

/**
 * Parses one LDB reply block (a RESP array of status lines) into a typed [LdbEvent] (design §3.3).
 *
 * Pure/stateless. Line-oriented and regex-pinned; never uses `!!` and never throws on an
 * unrecognized block — an unknown block degrades to [LdbEvent.Ack] so a surprise line cannot hang
 * or fatal-error the IDE (contract §1; risks-and-gaps Risk 2.1).
 */
object LdbReplyParser {

    private val STOP_LINE = Regex("""\*\s*Stopped at (\d+)""")
    private val STOP_REASON = Regex("""stop reason\s*=\s*(\w+)""")
    private val GUTTER = Regex("""^\s*\d+\s+""")
    private val USER_SCRIPT_LINE = Regex("""user_script:(\d+)""")
    private const val COMPILE_MESSAGE_SEPARATOR = "): "

    /**
     * The real redis:8 / valkey:8 LDB session-end sentinel, confirmed live in Phase 5 (TC-INT-1/3):
     * a normal completion, `abort`, and end-of-script all reply with a `["<endsession>"]` array (the
     * actual `EVAL` result or the abort `-ERR` arrives as a separate trailing block, drained via
     * `RespClient.readReply`). The design's assumed `"* Lua debugging session ended"` text is never
     * emitted by these servers; both markers are recognized (design §3.3, risks Risk 2.1 / DR-06).
     */
    private const val END_SESSION_MARKER = "<endsession>"

    /** Parse [reply] into an [LdbEvent] (design §3.3). */
    fun parse(reply: RespValue): LdbEvent {
        if (reply is RespValue.Error) return errorReply(reply)
        val lines = statusLines(reply)
        val first = lines.firstOrNull()?.trim() ?: return LdbEvent.Ack
        return sessionEnd(first)
            ?: compileError(first)
            ?: stop(first, lines)
            ?: LdbEvent.Ack
    }

    private fun errorReply(error: RespValue.Error): LdbEvent {
        val message = combineError(error)
        return LdbEvent.Error(LdbErrorKind.RUNTIME, message, extractUserScriptLine(message))
    }

    /** Normalize a reply block into its status lines (design §3.3 step 1). */
    private fun statusLines(reply: RespValue): List<String> = when (reply) {
        is RespValue.Array -> reply.items.orEmpty().map(::lineText)
        else -> listOf(lineText(reply))
    }

    private fun lineText(value: RespValue): String = when (value) {
        is RespValue.Simple -> value.text
        is RespValue.Bulk -> value.asString().orEmpty()
        is RespValue.Error -> combineError(value)
        else -> ""
    }

    private fun combineError(error: RespValue.Error): String =
        if (error.message.isEmpty()) error.klass else "${error.klass} ${error.message}"

    private fun sessionEnd(first: String): LdbEvent? = when {
        first.startsWith("* Forked debugging session") -> LdbEvent.SessionEnded(EndReason.FORK_TIMEOUT)
        first.contains(END_SESSION_MARKER, ignoreCase = true) -> LdbEvent.SessionEnded(EndReason.ENDED)
        first.contains("session ended", ignoreCase = true) -> LdbEvent.SessionEnded(EndReason.ENDED)
        first.contains("Aborted", ignoreCase = true) -> LdbEvent.SessionEnded(EndReason.ABORTED)
        else -> null
    }

    private fun compileError(first: String): LdbEvent? {
        if (!first.startsWith("* Error compiling")) return null
        val message = first.substringAfter(COMPILE_MESSAGE_SEPARATOR, first).trim()
        return LdbEvent.Error(LdbErrorKind.COMPILE, message, extractUserScriptLine(first))
    }

    private fun stop(first: String, lines: List<String>): LdbEvent? {
        val serverLine = STOP_LINE.find(first)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        val reason = reasonFrom(first)
        val sourceLine = stripGutter(lines.getOrNull(1))
        return LdbEvent.Stop(serverLine, reason, sourceLine)
    }

    private fun reasonFrom(first: String): StopReason =
        when (STOP_REASON.find(first)?.groupValues?.getOrNull(1)?.lowercase()) {
            "step" -> StopReason.STEP
            "next" -> StopReason.NEXT
            else -> StopReason.BREAKPOINT
        }

    private fun stripGutter(line: String?): String? =
        line?.replaceFirst(GUTTER, "")?.takeIf { it.isNotEmpty() }

    private fun extractUserScriptLine(text: String): Int? =
        USER_SCRIPT_LINE.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
}

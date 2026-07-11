package net.internetisalie.lunar.redis.debug

/** The LDB debug-session states (design §2.11, §3.5). */
enum class LdbState { HANDSHAKE, ARMED, RUNNING, PAUSED, TERMINATED }

/**
 * The explicit LDB debug-session state machine (design §2.11, §3.5).
 *
 * Guards command legality (no `step` on a `TERMINATED` session) and tracks the current paused line.
 * Confined to the session scope — mutated only from the controller's suspend methods
 * (single-threaded per session, so no cross-thread state; risks-and-gaps Risk 2.2).
 */
class LdbSessionMachine {

    var state: LdbState = LdbState.HANDSHAKE
        private set

    var currentLine: Int? = null
        private set

    /**
     * Records that [command] is about to be sent; returns `false` (a guarded no-op, never a throw)
     * when the command is illegal in [state] (design §3.5). Legal resume commands move the session
     * to [LdbState.RUNNING].
     */
    fun onCommandSent(command: LdbCommand): Boolean {
        if (!isLegal(command)) return false
        applyCommandTransition(command)
        return true
    }

    /** Drives state transitions from a parsed reply [event] (design §3.5). */
    fun onEvent(event: LdbEvent) {
        when (event) {
            is LdbEvent.Ack -> if (state == LdbState.HANDSHAKE) state = LdbState.ARMED
            is LdbEvent.Stop -> enterPaused(event.serverLine)
            is LdbEvent.SessionEnded -> state = LdbState.TERMINATED
            is LdbEvent.Error -> if (event.kind == LdbErrorKind.COMPILE) state = LdbState.TERMINATED
            is LdbEvent.Redis -> Unit
        }
    }

    private fun isLegal(command: LdbCommand): Boolean = when (command) {
        is LdbCommand.EnterDebug -> state == LdbState.HANDSHAKE
        LdbCommand.Step, LdbCommand.Next, LdbCommand.Continue,
        is LdbCommand.Print, is LdbCommand.Eval, is LdbCommand.RedisCmd,
        -> state == LdbState.PAUSED
        is LdbCommand.Break, is LdbCommand.RemoveBreak, LdbCommand.ClearBreaks, LdbCommand.ListSource,
        -> state == LdbState.ARMED || state == LdbState.PAUSED
        LdbCommand.Abort -> state != LdbState.TERMINATED
    }

    private fun applyCommandTransition(command: LdbCommand) {
        when (command) {
            LdbCommand.Step, LdbCommand.Next, LdbCommand.Continue -> {
                state = LdbState.RUNNING
                currentLine = null
            }
            else -> Unit
        }
    }

    private fun enterPaused(serverLine: Int) {
        if (state == LdbState.ARMED || state == LdbState.RUNNING || state == LdbState.PAUSED) {
            state = LdbState.PAUSED
            currentLine = serverLine
        }
    }
}

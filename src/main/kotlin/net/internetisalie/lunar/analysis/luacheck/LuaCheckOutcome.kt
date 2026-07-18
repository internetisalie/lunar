package net.internetisalie.lunar.analysis.luacheck

/**
 * Typed result of a single luacheck run (MAINT-26-06, design §2.1). Distinguishes reported
 * problems from a terminal failure that must be surfaced to the user, so a launch failure or a
 * fatal exit (≥ 2) can never be read as a clean green pass.
 */
sealed interface LuaCheckOutcome {
    data class Problems(val problems: List<Problem>) : LuaCheckOutcome

    data class Failure(val kind: FailureKind, val detail: String) : LuaCheckOutcome

    object NotApplicable : LuaCheckOutcome
}

/** Kind of terminal luacheck failure. `CRASHED` covers a fatal exit code (≥ 2). */
enum class FailureKind { LAUNCH_FAILED, TIMED_OUT, CRASHED }

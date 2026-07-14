package net.internetisalie.lunar.analysis.redis

/**
 * Parsed, in-memory view of a bundled Redis command specification for one target
 * version (design §2.2, §4.1). Immutable; keyed by upper-cased command name.
 *
 * Produced by [RedisCommandSpecService] from the bundled `commandspec/<seg>.json`
 * resources (BSD-3-Clause Valkey-derived). [EMPTY] is returned whenever no spec is
 * bundled for a target, or a resource is absent/unparseable.
 *
 * @see RedisCommandInfo
 * @see RedisCommandSpecService
 */
data class RedisCommandSpec(val commands: Map<String, RedisCommandInfo>) {

    /**
     * Looks up a command by name (case-insensitive; keys are stored upper-cased).
     *
     * @return the command info, or `null` when the command is not in this spec.
     */
    fun lookup(name: String): RedisCommandInfo? = commands[name.uppercase()]

    /** Returns all command names in this spec (upper-cased). */
    fun names(): Set<String> = commands.keys

    companion object {
        /** The empty spec, returned when no bundled data applies (never `null`). */
        val EMPTY = RedisCommandSpec(emptyMap())
    }
}

/**
 * A single command's reduced metadata (design §4.1 schema).
 *
 * @param name Upper-cased command name (e.g. `GET`).
 * @param arity Redis arity convention: a positive value is the exact arg count
 *   (including the command token); a **negative** value means "at least `|arity|`
 *   arguments".
 * @param since Dotted server version the command was introduced in (e.g. `1.0.0`).
 * @param summary Free-text one-line summary (BSD-3-Clause Valkey-sourced).
 * @param flags Command flags, lower-cased (e.g. `write`, `nondeterministic`); the
 *   only flags REDIS-04 reads are `write` and `nondeterministic`, others are
 *   retained verbatim for future use.
 */
data class RedisCommandInfo(
    val name: String,
    val arity: Int,
    val since: String,
    val summary: String,
    val flags: Set<String>,
)

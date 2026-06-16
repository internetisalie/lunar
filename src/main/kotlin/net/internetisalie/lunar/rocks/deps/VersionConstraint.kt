package net.internetisalie.lunar.rocks.deps

/** A relational operator usable in a LuaRocks dependency constraint. */
enum class ConstraintOp(val token: String) {
    EQ("=="),
    NE("~="),
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">="),
    COMPATIBLE("~>"),
}

/**
 * A single `<op> <version>` predicate over a [LuaRocksVersion].
 *
 * Mirrors LuaRocks `match_constraints`. The compatible operator (`~>`) is a partial match: every
 * component the constraint pins must equal the candidate's, while trailing candidate components are
 * unconstrained (so `~> 1.2` matches `1.2.0` and `1.2.99` but not `1.3.0`).
 */
data class VersionConstraint(val op: ConstraintOp, val version: LuaRocksVersion) {
    fun isSatisfiedBy(candidate: LuaRocksVersion): Boolean = when (op) {
        ConstraintOp.EQ -> candidate.compareTo(version) == 0
        ConstraintOp.NE -> candidate.compareTo(version) != 0
        ConstraintOp.LT -> candidate < version
        ConstraintOp.LE -> candidate <= version
        ConstraintOp.GT -> candidate > version
        ConstraintOp.GE -> candidate >= version
        ConstraintOp.COMPATIBLE -> isCompatibleWith(candidate)
    }

    private fun isCompatibleWith(candidate: LuaRocksVersion): Boolean =
        version.components.indices.all { i ->
            candidate.components.getOrElse(i) { 0.0 } == version.components[i]
        }

    companion object {
        // Two-character operators must be tested before one-character ones.
        private val OPERATORS: List<ConstraintOp> = listOf(
            ConstraintOp.EQ, ConstraintOp.NE, ConstraintOp.LE, ConstraintOp.GE,
            ConstraintOp.COMPATIBLE, ConstraintOp.LT, ConstraintOp.GT,
        )

        /**
         * Parses one constraint piece, e.g. `>= 2.0`, `~> 2.1`, or a bare `1.4` (treated as `==`).
         * Returns null only for an empty/blank piece.
         */
        fun parse(piece: String): VersionConstraint? {
            val trimmed = piece.trim()
            if (trimmed.isEmpty()) return null
            val op = OPERATORS.firstOrNull { trimmed.startsWith(it.token) }
            return when {
                op != null ->
                    VersionConstraint(op, LuaRocksVersion.parse(trimmed.substring(op.token.length).trim()))
                // LuaRocks accepts a single `=` as an alias for `==`.
                trimmed.startsWith("=") ->
                    VersionConstraint(ConstraintOp.EQ, LuaRocksVersion.parse(trimmed.substring(1).trim()))
                else ->
                    VersionConstraint(ConstraintOp.EQ, LuaRocksVersion.parse(trimmed))
            }
        }
    }
}

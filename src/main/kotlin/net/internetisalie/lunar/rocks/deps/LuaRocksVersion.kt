package net.internetisalie.lunar.rocks.deps

import kotlin.math.max

/**
 * A parsed LuaRocks version string, comparable per LuaRocks semantics.
 *
 * Mirrors LuaRocks `core/vers.lua` `parse_version`: the version is tokenised into numeric
 * components (letters mapped through [DELTAS]) plus an optional trailing `-<revision>`. Comparison
 * zero-pads the shorter component list and only compares revisions when both are present.
 */
data class LuaRocksVersion(
    val components: List<Double>,
    val revision: Int?,
    val raw: String,
) : Comparable<LuaRocksVersion> {
    override fun compareTo(other: LuaRocksVersion): Int {
        val width = max(components.size, other.components.size)
        for (i in 0 until width) {
            val mine = components.getOrElse(i) { 0.0 }
            val theirs = other.components.getOrElse(i) { 0.0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }
        if (revision != null && other.revision != null && revision != other.revision) {
            return revision.compareTo(other.revision)
        }
        return 0
    }

    companion object {
        /** Symbolic pre-release / source-control tokens, mapped to numeric deltas (from LuaRocks). */
        private val DELTAS: Map<String, Double> = mapOf(
            "dev" to 120000000.0,
            "scm" to 110000000.0,
            "cvs" to 100000000.0,
            "rc" to -1000.0,
            "pre" to -10000.0,
            "beta" to -100000.0,
            "alpha" to -1000000.0,
        )

        private val REVISION = Regex("^(.*)-(\\d+)$")
        private val DIGITS = Regex("^\\d+")
        private val LETTERS = Regex("^[A-Za-z]+")
        private val DELIMITERS = Regex("^[._-]+")

        fun parse(raw: String): LuaRocksVersion {
            val revisionMatch = REVISION.matchEntire(raw)
            val main = revisionMatch?.groupValues?.get(1) ?: raw
            val revision = revisionMatch?.groupValues?.get(2)?.toIntOrNull()

            val components = mutableListOf<Double>()
            var rest = main
            while (rest.isNotEmpty()) {
                val digits = DIGITS.find(rest)
                val letters = LETTERS.find(rest)
                when {
                    digits != null -> {
                        components += digits.value.toDouble()
                        rest = rest.substring(digits.value.length)
                    }
                    letters != null -> {
                        val token = letters.value.lowercase()
                        components += DELTAS[token] ?: (token[0].code.toDouble() / 1000.0)
                        rest = rest.substring(letters.value.length)
                    }
                    else -> {
                        val delimiter = DELIMITERS.find(rest)
                        if (delimiter != null) {
                            rest = rest.substring(delimiter.value.length)
                        } else {
                            break
                        }
                    }
                }
            }
            return LuaRocksVersion(components, revision, raw)
        }
    }
}

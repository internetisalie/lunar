package net.internetisalie.lunar.toolchain.provision.feed

/**
 * Orders two version strings per design §3.11.
 *
 * Each string is split on `[.-]` into tokens. Tokens are compared position-wise:
 * - two numeric tokens compare numerically;
 * - a missing token counts as `0` UNLESS the opposing token is non-numeric — a
 *   pre-release suffix (e.g. `beta3`) makes the shorter version GREATER, so
 *   `2.1.0 > 2.1.0-beta3`;
 * - two non-numeric tokens compare by alpha prefix lexicographically then trailing
 *   digits numerically (`beta1 < beta2 < beta3 < rc1`).
 * The first differing position decides the result.
 */
object LuaFeedVersionComparator : Comparator<String> {
    private val splitPattern = Regex("[.-]")
    private val alphaNumericSplit = Regex("^([^0-9]*)([0-9]*)$")

    override fun compare(left: String, right: String): Int {
        val leftTokens = left.split(splitPattern)
        val rightTokens = right.split(splitPattern)
        val tokenCount = maxOf(leftTokens.size, rightTokens.size)
        for (index in 0 until tokenCount) {
            val result = compareToken(leftTokens.getOrNull(index), rightTokens.getOrNull(index))
            if (result != 0) return result
        }
        return 0
    }

    private fun compareToken(left: String?, right: String?): Int {
        val leftNumeric = left?.toLongOrNull()
        val rightNumeric = right?.toLongOrNull()
        return when {
            left == null -> missingVersus(right)
            right == null -> -missingVersus(left)
            leftNumeric != null && rightNumeric != null -> leftNumeric.compareTo(rightNumeric)
            else -> compareNonNumeric(left, right)
        }
    }

    /** A missing token is `0` versus a numeric token, but the shorter side wins versus a pre-release. */
    private fun missingVersus(present: String?): Int =
        when (val presentNumeric = present?.toLongOrNull()) {
            null -> 1
            else -> 0L.compareTo(presentNumeric)
        }

    private fun compareNonNumeric(left: String, right: String): Int {
        val leftMatch = alphaNumericSplit.find(left)?.destructured
        val rightMatch = alphaNumericSplit.find(right)?.destructured
        if (leftMatch == null || rightMatch == null) return left.compareTo(right)
        val (leftAlpha, leftDigits) = leftMatch
        val (rightAlpha, rightDigits) = rightMatch
        val alphaResult = leftAlpha.compareTo(rightAlpha)
        if (alphaResult != 0) return alphaResult
        return (leftDigits.toLongOrNull() ?: 0L).compareTo(rightDigits.toLongOrNull() ?: 0L)
    }
}

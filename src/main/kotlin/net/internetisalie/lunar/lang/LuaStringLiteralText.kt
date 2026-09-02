package net.internetisalie.lunar.lang

/**
 * Where a Lua string literal's body starts and ends within its own text (BUG-467).
 *
 * The delimiters are parsed as a **grammar**, not as a character set. Membership tests over
 * `"'[=` cannot work: `=` must be in the set because a long bracket is `[==[`, so any module name
 * whose body itself begins or ends with `"`, `'`, `[` or `=` had those characters eaten as
 * delimiter. `require("=m6")` measured its body as `m`, and renaming the file produced
 * `require("=helpers66")` — a broken reference rather than a rebound one, which is why BUG-467 was
 * `low` rather than the BUG-457 class.
 *
 * A literal opens with exactly one of `"`, `'`, or `[` followed by *n* `=` then `[`, and a long
 * bracket closes symmetrically (`[==[` with `]==]`), so the closing run's length is known from the
 * opening one and needs no second scan.
 */
internal object LuaStringLiteralText {
    /**
     * The half-open `[start, endExclusive)` range of [text]'s body, or null when [text] is not a
     * delimited Lua string literal. The range may be empty, for `""` or `[[]]`; callers that need a
     * non-empty module name check that themselves.
     */
    fun bodyRange(text: String): Pair<Int, Int>? =
        when (text.firstOrNull()) {
            '"', '\'' -> quotedBodyRange(text)
            '[' -> longBracketBodyRange(text)
            else -> null
        }

    private fun quotedBodyRange(text: String): Pair<Int, Int>? {
        if (text.length < 2 || text.last() != text.first()) return null
        return 1 to text.length - 1
    }

    private fun longBracketBodyRange(text: String): Pair<Int, Int>? {
        var index = 1
        while (index < text.length && text[index] == '=') index++
        if (index >= text.length || text[index] != '[') return null
        val level = index - 1
        val start = index + 1
        val endExclusive = text.length - (level + 2)
        if (endExclusive < start) return null
        val expectedClose = "]" + "=".repeat(level) + "]"
        if (text.substring(endExclusive) != expectedClose) return null
        return start to endExclusive
    }
}

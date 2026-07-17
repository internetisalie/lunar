package net.internetisalie.lunar.run.test

/**
 * Escapes a busted test name into a literal Lua-pattern fragment (#27b).
 *
 * Busted's `--filter` matches names as Lua patterns, so every Lua magic character must be
 * prefixed with `%` for the name to match literally. This is distinct from Java regex
 * `\Q…\E`, which busted does not understand.
 */
object LuaPatternEscaper {

    private val MAGIC_CHARS = setOf('(', ')', '.', '%', '+', '-', '*', '?', '[', ']', '^', '$')

    fun escape(name: String): String {
        val builder = StringBuilder(name.length)
        for (currentChar in name) {
            if (currentChar in MAGIC_CHARS) {
                builder.append('%')
            }
            builder.append(currentChar)
        }
        return builder.toString()
    }
}

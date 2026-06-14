package net.internetisalie.lunar.lang

/**
 * Lua reserved words (Lua 5.1-5.4). Used to avoid generating an import binding name
 * that collides with a language keyword (which would produce invalid code).
 */
object LuaKeywords {
    val RESERVED: Set<String> = setOf(
        "and", "break", "do", "else", "elseif", "end",
        "false", "for", "function", "goto", "if", "in",
        "local", "nil", "not", "or", "repeat", "return",
        "then", "true", "until", "while",
    )

    fun isReserved(word: String): Boolean = word in RESERVED
}

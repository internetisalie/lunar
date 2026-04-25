package net.internetisalie.lunar.lang.syntax

private val SIMPLE_ESCAPES = mapOf(
    'a' to '\u0007', // bell
    'b' to '\b',      // backspace
    'f' to '\u000C',  // form feed
    'n' to '\n',      // newline
    'r' to '\r',      // carriage return
    't' to '\t',      // tab
    'v' to '\u000B',  // vertical tab
    '\\' to '\\',     // backslash
    '"' to '"',       // double quote
    '\'' to '\''      // single quote
)

private fun unescapeLuaString(str: String): String {
    val result = StringBuilder()
    var i = 0
    while (i < str.length) {
        if (str[i] == '\\' && i + 1 < str.length) {
            val next = str[i + 1]
            when {
                next in SIMPLE_ESCAPES -> {
                    result.append(SIMPLE_ESCAPES[next])
                    i += 2
                }
                next == 'x' -> {
                    // \xHH - hex escape
                    if (i + 3 < str.length) {
                        val hex = str.substring(i + 2, i + 4)
                        val code = hex.toIntOrNull(16)
                        if (code != null && code in 0..255) {
                            result.append(code.toChar())
                            i += 4
                        } else {
                            result.append('\\')
                            i++
                        }
                    } else {
                        result.append('\\')
                        i++
                    }
                }
                next == 'u' -> {
                    // \uHHHH or \u{HHHHH} - Unicode escape
                    if (i + 2 < str.length && str[i + 2] == '{') {
                        // Variable-length: \u{...}
                        val closeIdx = str.indexOf('}', i + 3)
                        if (closeIdx != -1) {
                            val hex = str.substring(i + 3, closeIdx)
                            val code = hex.toIntOrNull(16)
                            if (code != null && code in 0..0x10FFFF) {
                                result.append(Character.toChars(code))
                                i = closeIdx + 1
                            } else {
                                result.append('\\')
                                i++
                            }
                        } else {
                            result.append('\\')
                            i++
                        }
                    } else if (i + 5 < str.length) {
                        // Fixed 4-digit: \uHHHH
                        val hex = str.substring(i + 2, i + 6)
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            result.append(code.toChar())
                            i += 6
                        } else {
                            result.append('\\')
                            i++
                        }
                    } else {
                        result.append('\\')
                        i++
                    }
                }
                next in '0'..'9' -> {
                    // \ddd - decimal escape (up to 3 digits)
                    var j = i + 1
                    while (j < str.length && j < i + 4 && str[j] in '0'..'9') {
                        j++
                    }
                    val decimal = str.substring(i + 1, j)
                    val code = decimal.toInt()
                    if (code in 0..255) {
                        result.append(code.toChar())
                        i = j
                    } else {
                        result.append('\\')
                        i++
                    }
                }
                else -> {
                    result.append('\\')
                    i++
                }
            }
        } else {
            result.append(str[i])
            i++
        }
    }
    return result.toString()
}

fun extractLuaString(str: String): String {
    when {
        str[0] == '"' -> {
            val content = str.substring(1, str.length - 1)
            return unescapeLuaString(content)
        }
        str[0] == '\'' -> {
            val content = str.substring(1, str.length - 1)
            return unescapeLuaString(content)
        }
        str[0] != '[' -> return str // should log a warning perhaps
        else -> { // extended strings (block strings - no escape processing)
            var level = 0
            while (str[level + 1] == '=') level++

            val trimmed = str.substring(
                level + 2,
                str.length - level - 2
            )
            return if (trimmed.startsWith("\n")) trimmed.substring(1) else trimmed
        }
    }
}

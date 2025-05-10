package net.internetisalie.lunar.lang.syntax

fun extractLuaString(str: String): String {
    when {
        str[0] == '"' -> return str.substring(1, str.length - 1) // double-quote
        str[0] == '\'' -> return str.substring(1, str.length - 1) // single-quote
        str[0] != '[' -> return str // should log a warning perhaps
        else -> { // extended strings
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

fun extractLuaComment(str: String): String {
    return when {
        str.startsWith("--[[") -> str.substring(4, str.length - 4)
        else -> str
    }
}

fun summary(str : String) : String{
    val firstLine = str.trim().substringBefore('\n')
    return if (firstLine.length < str.length) {
        "$firstLine..."
    } else {
        firstLine
    }
}

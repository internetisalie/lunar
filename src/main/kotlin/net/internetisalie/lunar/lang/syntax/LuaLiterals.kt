package net.internetisalie.lunar.lang.syntax

fun convertLuaString(str : String) : String {
    when {
        str[0] == '"' -> return str.substring(1, str.length-1) // double-quote
        str[0] == '\'' -> return str.substring(1, str.length-1) // single-quote
        str[0] != '[' -> return str // should log a warning perhaps
        else -> { // extended strings
            var level = 0
            while (str[level+1] == '=') level++

            return str.substring(
                level + 2,
                str.length - level - 2
            )
        }
    }
}
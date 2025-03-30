package net.internetisalie.lunar.lang.syntax

fun convertLuaString(str : String) : String {
    if (str.startsWith('\'')) return str.trim('\'')
    if (str.startsWith('\"')) return str.trim('\"')
    // TODO: Extended strings
    return str
}
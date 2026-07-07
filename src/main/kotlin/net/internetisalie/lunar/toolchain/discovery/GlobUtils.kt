package net.internetisalie.lunar.toolchain.discovery

import java.util.regex.Pattern

fun isGlob(filename: String): Boolean {
    return filename.contains("*") || filename.contains("?")
}

fun matchesGlob(glob: String, filename: String): Boolean {
    val p = patternFromGlob(glob)
    return p.matcher(filename).matches()
}

// http://stackoverflow.com/questions/1247772
fun patternFromGlob(glob: String): Pattern {
    var out = "^"
    for (i in 0..<glob.length) {
        val c = glob[i]
        when (c) {
            '*' -> out += ".*"
            '?' -> out += '.'
            '.' -> out += "\\."
            '\\' -> out += "\\\\"
            else -> out += c
        }
    }
    out += '$'
    return Pattern.compile(out)
}

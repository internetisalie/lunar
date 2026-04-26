package net.internetisalie.lunar.run

import java.io.InputStreamReader

/**
 * Reads a single line, returning all characters up to and including the trailing '\n'.
 * Returns null if the stream is at EOF before any characters are read.
 */
fun InputStreamReader.readLine(): String? {
    val sb = StringBuilder()
    while (true) {
        val ch = read()
        if (ch == -1) return if (sb.isEmpty()) null else sb.toString()
        sb.append(ch.toChar())
        if (ch == '\n'.code) return sb.toString()
    }
}

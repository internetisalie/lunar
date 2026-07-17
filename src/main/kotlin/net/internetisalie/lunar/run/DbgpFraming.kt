package net.internetisalie.lunar.run

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset

/**
 * Byte-accurate DBGp line/length-prefixed framing over raw streams (MAINT-24, #5).
 *
 * The DBGp length prefix counts **bytes**, so a length-prefixed payload must be read as raw bytes
 * and decoded once — reading characters under-reads a multibyte UTF-8 payload and permanently
 * desyncs the protocol. Extracted as a pure object for socket-free unit coverage.
 *
 * Called only from the reader coroutine (`Dispatchers.IO`); performs blocking reads. Never closes a
 * stream it is handed — the caller ([LuaDebugConnection]) owns stream lifecycle.
 */
object DbgpFraming {
    val CHARSET: Charset = Charsets.UTF_8

    private const val LINE_FEED = '\n'.code
    private const val CARRIAGE_RETURN = '\r'.code

    /** Reads one newline-terminated line as raw bytes and decodes UTF-8. Returns null at EOF. */
    fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (buffer.size() == 0) null else buffer.toByteArray().toString(CHARSET)
            }
            if (b == LINE_FEED) break
            if (b == CARRIAGE_RETURN) continue
            buffer.write(b)
        }
        return buffer.toByteArray().toString(CHARSET)
    }

    /** Reads exactly [byteCount] raw bytes then decodes UTF-8 (the DBGp length prefix is BYTES). */
    fun readExactly(input: InputStream, byteCount: Int): String {
        require(byteCount >= 0) { "byteCount must be non-negative: $byteCount" }
        if (byteCount == 0) return ""
        val buf = ByteArray(byteCount)
        var off = 0
        while (off < byteCount) {
            val n = input.read(buf, off, byteCount - off)
            if (n == -1) throw IOException("connection closed after $off of $byteCount bytes")
            off += n
        }
        return buf.toString(CHARSET)
    }

    /** Encodes [line] as UTF-8 bytes with a trailing newline and flushes. */
    fun writeLine(output: OutputStream, line: String) {
        output.write((line + "\n").toByteArray(CHARSET))
        output.flush()
    }
}

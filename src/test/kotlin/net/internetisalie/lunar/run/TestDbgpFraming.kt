package net.internetisalie.lunar.run

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Socket-free coverage of [DbgpFraming] byte-accurate DBGp framing (MAINT-24-01, TC-01a/b/c).
 */
class TestDbgpFraming {

    /** TC-01a: a 6-byte UTF-8 payload `café!` (5 chars, 6 bytes) is read whole by byte count. */
    @Test
    fun testReadExactlyReadsMultibytePayloadByByteCount() {
        val bytes = "café!".toByteArray(Charsets.UTF_8)
        assertEquals(6, bytes.size)
        val input = ByteArrayInputStream(bytes)
        assertEquals("café!", DbgpFraming.readExactly(input, 6))
    }

    /** TC-01b: consecutive lines split on `\n`, with a bare `\r` skipped. */
    @Test
    fun testReadLineSplitsOnNewlineAndSkipsCarriageReturn() {
        val input = ByteArrayInputStream("abc\r\ndef\n".toByteArray(Charsets.UTF_8))
        assertEquals("abc", DbgpFraming.readLine(input))
        assertEquals("def", DbgpFraming.readLine(input))
    }

    /** TC-01c: a short stream throws an IOException naming the byte shortfall. */
    @Test
    fun testReadExactlyThrowsOnShortRead() {
        val input = ByteArrayInputStream("abc".toByteArray(Charsets.UTF_8))
        val ex = assertFailsWith<IOException> { DbgpFraming.readExactly(input, 6) }
        assertEquals("connection closed after 3 of 6 bytes", ex.message)
    }

    /** EOF on an empty stream yields null (clean disconnect). */
    @Test
    fun testReadLineReturnsNullAtEof() {
        assertNull(DbgpFraming.readLine(ByteArrayInputStream(ByteArray(0))))
    }

    /** A trailing unterminated line is returned rather than dropped. */
    @Test
    fun testReadLineReturnsUnterminatedTrailingLine() {
        val input = ByteArrayInputStream("tail".toByteArray(Charsets.UTF_8))
        assertEquals("tail", DbgpFraming.readLine(input))
        assertNull(DbgpFraming.readLine(input))
    }

    /** writeLine appends exactly one newline and UTF-8 encodes the payload. */
    @Test
    fun testWriteLineAppendsNewlineUtf8() {
        val output = ByteArrayOutputStream()
        DbgpFraming.writeLine(output, "SETB café 3")
        assertEquals("SETB café 3\n", output.toByteArray().toString(Charsets.UTF_8))
    }

    /** A zero-length payload short-circuits to the empty string. */
    @Test
    fun testReadExactlyZeroBytes() {
        assertEquals("", DbgpFraming.readExactly(ByteArrayInputStream(ByteArray(0)), 0))
    }
}

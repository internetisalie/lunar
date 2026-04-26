package net.internetisalie.lunar.run

import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestInputStreamReaderExt {

    private fun readerOf(s: String) = InputStreamReader(s.byteInputStream())

    @Test
    fun testReadsSingleLineWithNewline() {
        val reader = readerOf("hello\n")
        assertEquals("hello\n", reader.readLine())
    }

    @Test
    fun testReadsFirstLineAndStopsAtNewline() {
        val reader = readerOf("first\nsecond\n")
        assertEquals("first\n", reader.readLine())
        assertEquals("second\n", reader.readLine())
    }

    @Test
    fun testReadsPartialLineAtEof() {
        val reader = readerOf("no newline")
        assertEquals("no newline", reader.readLine())
    }

    @Test
    fun testReturnsNullOnEmptyStream() {
        val reader = readerOf("")
        assertNull(reader.readLine())
    }

    @Test
    fun testReturnsNullAfterLastLine() {
        val reader = readerOf("line\n")
        reader.readLine()
        assertNull(reader.readLine())
    }
}

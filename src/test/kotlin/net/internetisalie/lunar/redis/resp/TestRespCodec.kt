package net.internetisalie.lunar.redis.resp

import java.io.InputStream
import java.io.PushbackInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Socket-free coverage of [RespCodec] (design §3.2, §3.3, §3.4).
 *
 * Covers TC-RESP-1 (encode array-of-bulk), TC-RESP-2 (decode each RESP2/RESP3 type),
 * TC-RESP-3 (multi-byte UTF-8 bulk byte-length), TC-RESP-4 (fragmented/partial-stream reassembly).
 */
class TestRespCodec {

    private fun pushbackOf(vararg bytes: ByteArray): PushbackInputStream =
        PushbackInputStream(ChunkedInputStream(bytes.toList()))

    private fun pushbackOfWire(wire: String): PushbackInputStream =
        pushbackOf(wire.toByteArray(Charsets.UTF_8))

    /** TC-RESP-1: `["SET", "café"]` → `*2\r\n$3\r\nSET\r\n$5\r\ncafé\r\n` (`$5` is the byte length). */
    @Test
    fun testEncodeCommandArrayOfBulkUsesByteLength() {
        val args = listOf("SET".toByteArray(Charsets.UTF_8), "café".toByteArray(Charsets.UTF_8))
        val encoded = RespCodec.encodeCommand(args)
        val expected = "*2\r\n\$3\r\nSET\r\n\$5\r\ncafé\r\n".toByteArray(Charsets.UTF_8)
        assertTrue(encoded.contentEquals(expected), "encoded bytes must be byte-accurate RESP")
    }

    /** TC-RESP-2: simple string `+OK\r\n` → `Simple("OK")`. */
    @Test
    fun testDecodeSimpleString() {
        assertEquals(RespValue.Simple("OK"), RespCodec.decode(pushbackOfWire("+OK\r\n")))
    }

    /** TC-RESP-2: integer `:42\r\n` → `Integer(42)`. */
    @Test
    fun testDecodeInteger() {
        assertEquals(RespValue.Integer(42), RespCodec.decode(pushbackOfWire(":42\r\n")))
    }

    /** TC-RESP-2: error `-WRONGTYPE bad\r\n` → `Error("WRONGTYPE", "bad")` (design §3.4 split). */
    @Test
    fun testDecodeErrorSplitsClassAndMessage() {
        assertEquals(
            RespValue.Error("WRONGTYPE", "bad"),
            RespCodec.decode(pushbackOfWire("-WRONGTYPE bad\r\n")),
        )
    }

    /** TC-RESP-2: bulk `$3\r\nfoo\r\n` → `Bulk("foo")`. */
    @Test
    fun testDecodeBulkString() {
        val decoded = RespCodec.decode(pushbackOfWire("\$3\r\nfoo\r\n"))
        assertEquals(RespValue.Bulk("foo".toByteArray(Charsets.UTF_8)), decoded)
        assertEquals("foo", (decoded as RespValue.Bulk).asString())
    }

    /** TC-RESP-2: null bulk `$-1\r\n` → `Bulk(null)`. */
    @Test
    fun testDecodeNullBulk() {
        val decoded = RespCodec.decode(pushbackOfWire("\$-1\r\n"))
        assertEquals(RespValue.Bulk(null), decoded)
        assertNull((decoded as RespValue.Bulk).asString())
    }

    /** TC-RESP-2: array `*2\r\n:1\r\n:2\r\n` → `Array([Integer(1), Integer(2)])`. */
    @Test
    fun testDecodeArrayOfIntegers() {
        val decoded = RespCodec.decode(pushbackOfWire("*2\r\n:1\r\n:2\r\n"))
        assertEquals(
            RespValue.Array(listOf(RespValue.Integer(1), RespValue.Integer(2))),
            decoded,
        )
    }

    /** TC-RESP-2: RESP3 map `%1\r\n$1\r\nk\r\n$1\r\nv\r\n` → `Map([(Bulk("k"), Bulk("v"))])`. */
    @Test
    fun testDecodeResp3Map() {
        val decoded = RespCodec.decode(pushbackOfWire("%1\r\n\$1\r\nk\r\n\$1\r\nv\r\n"))
        val expected = RespValue.Map(
            listOf(
                RespValue.Bulk("k".toByteArray(Charsets.UTF_8)) to
                    RespValue.Bulk("v".toByteArray(Charsets.UTF_8)),
            ),
        )
        assertEquals(expected, decoded)
    }

    /** TC-RESP-2: RESP3 double `,3.14\r\n` → `Double(3.14)`. */
    @Test
    fun testDecodeResp3Double() {
        assertEquals(RespValue.Double(3.14), RespCodec.decode(pushbackOfWire(",3.14\r\n")))
    }

    /** TC-RESP-2: RESP3 boolean `#t\r\n` → `Bool(true)`. */
    @Test
    fun testDecodeResp3BoolTrue() {
        assertEquals(RespValue.Bool(true), RespCodec.decode(pushbackOfWire("#t\r\n")))
    }

    /** TC-RESP-2: RESP3 boolean `#f\r\n` → `Bool(false)`. */
    @Test
    fun testDecodeResp3BoolFalse() {
        assertEquals(RespValue.Bool(false), RespCodec.decode(pushbackOfWire("#f\r\n")))
    }

    /** TC-RESP-2: RESP3 null `_\r\n` → `Null`. */
    @Test
    fun testDecodeResp3Null() {
        assertEquals(RespValue.Null, RespCodec.decode(pushbackOfWire("_\r\n")))
    }

    /** TC-RESP-3: bulk `café` = 5 UTF-8 bytes; exactly the payload consumed, trailing `\r\n` consumed. */
    @Test
    fun testDecodeMultiByteUtf8BulkByteLength() {
        val stream = pushbackOfWire("\$5\r\ncafé\r\n")
        val decoded = RespCodec.decode(stream)
        assertEquals("café", (decoded as RespValue.Bulk).asString())
        assertEquals(-1, stream.read(), "stream must be fully consumed (no leftover bytes)")
    }

    /** TC-RESP-4: `$6\r\nabcdef\r\n` delivered in fragments reassembles to `Bulk("abcdef")`. */
    @Test
    fun testDecodeFragmentedBulkReassembles() {
        val stream = pushbackOf(
            "\$6\r\nab".toByteArray(Charsets.UTF_8),
            "cd".toByteArray(Charsets.UTF_8),
            "ef\r\n".toByteArray(Charsets.UTF_8),
        )
        val decoded = RespCodec.decode(stream)
        assertEquals("abcdef", (decoded as RespValue.Bulk).asString())
    }

    /**
     * Feeds bytes one caller-supplied chunk at a time. A single `read(buf, off, len)` never spans
     * two chunks, so a multi-byte payload split across chunks forces [RespCodec]'s length-prefixed
     * loop to reassemble across chunk boundaries (TC-RESP-4).
     */
    private class ChunkedInputStream(chunks: List<ByteArray>) : InputStream() {

        private val queue = ArrayDeque(chunks.filter { it.isNotEmpty() }.map { it.copyOf() })
        private var offset = 0

        override fun read(): Int {
            val head = queue.firstOrNull() ?: return -1
            val value = head[offset].toInt() and 0xFF
            offset += 1
            dropExhaustedHead(head)
            return value
        }

        override fun read(destination: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val head = queue.firstOrNull() ?: return -1
            val count = minOf(len, head.size - offset)
            System.arraycopy(head, offset, destination, off, count)
            offset += count
            dropExhaustedHead(head)
            return count
        }

        private fun dropExhaustedHead(head: ByteArray) {
            if (offset >= head.size) {
                queue.removeFirst()
                offset = 0
            }
        }
    }
}

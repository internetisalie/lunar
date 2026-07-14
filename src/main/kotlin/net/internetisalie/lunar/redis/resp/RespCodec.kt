package net.internetisalie.lunar.redis.resp

import java.io.ByteArrayOutputStream
import java.io.PushbackInputStream

/**
 * Byte-accurate RESP2/RESP3 encode and decode (design §3.2, §3.3, §3.4).
 *
 * Pure/stateless object; intended to run only on the pooled reader coroutine (never the EDT). All
 * string↔byte conversions are explicit UTF-8. Decoding reads raw bytes from a [PushbackInputStream]
 * — never `BufferedReader.readLine` — so the length-prefixed loops reassemble a fragmented stream
 * (engineering-contract THREADING; risks-and-gaps Risk 1.1 / epic RISK-R09).
 */
object RespCodec {

    private const val CR = '\r'.code
    private const val LF = '\n'.code
    private const val COLON = ':'.code.toByte()
    private const val VERBATIM_PREFIX_LENGTH = 4
    private val UTF_8 = Charsets.UTF_8

    /**
     * Encode a command as a RESP2 array-of-bulk-strings (design §3.2). Accepted by every server
     * regardless of the negotiated reply protocol. Bulk lengths are byte lengths, never `String.length`.
     */
    fun encodeCommand(args: List<ByteArray>): ByteArray {
        val buffer = ByteArrayOutputStream()
        writeHeader(buffer, '*', args.size)
        for (argBytes in args) {
            writeHeader(buffer, '$', argBytes.size)
            buffer.write(argBytes)
            writeTerminator(buffer)
        }
        return buffer.toByteArray()
    }

    /** Decode exactly one reply from [input], dispatching on the leading type byte (design §3.3). */
    fun decode(input: PushbackInputStream): RespValue {
        val typeByte = input.read()
        if (typeByte < 0) throw RespException.Protocol("unexpected end of stream before a reply")
        return when (typeByte.toChar()) {
            '+' -> RespValue.Simple(readLine(input))
            '-' -> decodeError(input)
            ':' -> RespValue.Integer(readLine(input).toLongOrThrow())
            '$' -> decodeBulk(input)
            '=' -> decodeVerbatim(input)
            '*' -> decodeArray(input)
            '~' -> decodeArray(input)
            '%' -> decodeMap(input)
            ',' -> RespValue.Double(readLine(input).toDoubleOrThrow())
            '#' -> RespValue.Bool(readBool(input))
            '_' -> decodeNull(input)
            else -> throw RespException.Protocol("unknown reply type byte '${typeByte.toChar()}'")
        }
    }

    private fun decodeError(input: PushbackInputStream): RespValue.Error {
        val line = readLine(input)
        val klass = line.substringBefore(' ')
        val message = line.substringAfter(' ', "")
        return RespValue.Error(klass, message)
    }

    private fun decodeBulk(input: PushbackInputStream): RespValue.Bulk {
        val length = readLine(input).toIntOrThrow()
        if (length < 0) return RespValue.Bulk(null)
        val payload = readExactly(input, length)
        consumeTerminator(input)
        return RespValue.Bulk(payload)
    }

    /**
     * RESP3 verbatim string `=<len>\r\n<fmt3>:<content>\r\n` (design §3.3). `<len>` counts the bytes
     * of `<fmt3>:<content>`; the leading 3-char format + ':' prefix is stripped so [RespValue.Bulk.asString]
     * yields the real content (e.g. an `INFO` body), keeping the version gate and Test Connection working.
     */
    private fun decodeVerbatim(input: PushbackInputStream): RespValue.Bulk {
        val length = readLine(input).toIntOrThrow()
        if (length < 0) return RespValue.Bulk(null)
        val payload = readExactly(input, length)
        consumeTerminator(input)
        return RespValue.Bulk(stripVerbatimPrefix(payload))
    }

    /** Drop the mandatory `<fmt3>:` header (4 bytes) when present, else keep the raw payload. */
    private fun stripVerbatimPrefix(payload: ByteArray): ByteArray {
        if (payload.size >= VERBATIM_PREFIX_LENGTH && payload[VERBATIM_PREFIX_LENGTH - 1] == COLON) {
            return payload.copyOfRange(VERBATIM_PREFIX_LENGTH, payload.size)
        }
        return payload
    }

    private fun decodeArray(input: PushbackInputStream): RespValue.Array {
        val count = readLine(input).toIntOrThrow()
        if (count < 0) return RespValue.Array(null)
        val items = ArrayList<RespValue>(count)
        repeat(count) { items.add(decode(input)) }
        return RespValue.Array(items)
    }

    private fun decodeMap(input: PushbackInputStream): RespValue.Map {
        val pairCount = readLine(input).toIntOrThrow()
        val entries = ArrayList<Pair<RespValue, RespValue>>(maxOf(pairCount, 0))
        repeat(pairCount) {
            val key = decode(input)
            val value = decode(input)
            entries.add(key to value)
        }
        return RespValue.Map(entries)
    }

    private fun decodeNull(input: PushbackInputStream): RespValue {
        consumeTerminator(input)
        return RespValue.Null
    }

    private fun readBool(input: PushbackInputStream): Boolean {
        return when (val token = readLine(input)) {
            "t" -> true
            "f" -> false
            else -> throw RespException.Protocol("invalid boolean token '$token'")
        }
    }

    private fun writeHeader(buffer: ByteArrayOutputStream, marker: Char, count: Int) {
        buffer.write(marker.code)
        buffer.write(count.toString().toByteArray(UTF_8))
        writeTerminator(buffer)
    }

    private fun writeTerminator(buffer: ByteArrayOutputStream) {
        buffer.write(CR)
        buffer.write(LF)
    }

    /** Read raw bytes up to (not including) the terminating `\r\n`, decoding the line as UTF-8. */
    private fun readLine(input: PushbackInputStream): String {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next < 0) throw RespException.Protocol("unterminated line (end of stream)")
            if (next == CR) {
                expectLf(input)
                return buffer.toByteArray().toString(UTF_8)
            }
            if (next == LF) throw RespException.Protocol("bare LF without preceding CR")
            buffer.write(next)
        }
    }

    /** Read exactly [length] bytes, looping until satisfied so a fragmented stream reassembles. */
    private fun readExactly(input: PushbackInputStream, length: Int): ByteArray {
        val payload = ByteArray(length)
        var filled = 0
        while (filled < length) {
            val read = input.read(payload, filled, length - filled)
            if (read < 0) throw RespException.Protocol("stream ended after $filled of $length bytes")
            filled += read
        }
        return payload
    }

    private fun consumeTerminator(input: PushbackInputStream) {
        val cr = input.read()
        if (cr != CR) throw RespException.Protocol("expected CR terminator, got byte $cr")
        expectLf(input)
    }

    private fun expectLf(input: PushbackInputStream) {
        val lf = input.read()
        if (lf != LF) throw RespException.Protocol("expected LF after CR, got byte $lf")
    }

    private fun String.toLongOrThrow(): Long =
        toLongOrNull() ?: throw RespException.Protocol("invalid integer '$this'")

    private fun String.toIntOrThrow(): Int =
        toIntOrNull() ?: throw RespException.Protocol("invalid length '$this'")

    private fun String.toDoubleOrThrow(): Double =
        toDoubleOrNull() ?: throw RespException.Protocol("invalid double '$this'")
}

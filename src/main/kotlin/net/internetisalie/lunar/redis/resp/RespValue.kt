package net.internetisalie.lunar.redis.resp

/**
 * Immutable sealed model of a decoded RESP2/RESP3 reply (design §2.1).
 *
 * Pure data — thread-agnostic. Produced by [RespCodec.decode]; consumed downstream by the reply-tree
 * console (REDIS-01 Phase 5). RESP3 markers (`%`, `,`, `#`, `_`) are represented alongside the RESP2
 * types so a single model spans both negotiated protocols.
 */
sealed interface RespValue {

    /** `+OK\r\n` (RESP2 simple string) and RESP3 verbatim `=…\r\n`. */
    data class Simple(val text: String) : RespValue

    /** `-WRONGTYPE bad\r\n` → `klass` = first token, `message` = remainder (design §3.4). */
    data class Error(val klass: String, val message: String) : RespValue

    /** `:123\r\n`. */
    data class Integer(val value: Long) : RespValue

    /** `$len\r\n…\r\n`; `$-1\r\n` (null bulk) → [bytes] is null. */
    data class Bulk(val bytes: ByteArray?) : RespValue {

        fun asString(): String? = bytes?.toString(Charsets.UTF_8)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            val otherBulk = other as? Bulk ?: return false
            val theseBytes = bytes ?: return otherBulk.bytes == null
            val otherBytes = otherBulk.bytes ?: return false
            return theseBytes.contentEquals(otherBytes)
        }

        override fun hashCode(): Int = bytes?.contentHashCode() ?: 0
    }

    /** `*len\r\n…`; `*-1\r\n` (null array) → [items] is null. */
    data class Array(val items: List<RespValue>?) : RespValue

    /** RESP3 `%len\r\n…` — `len` key/value pairs. */
    data class Map(val entries: List<Pair<RespValue, RespValue>>) : RespValue

    /** RESP3 `,3.14\r\n`. */
    data class Double(val value: kotlin.Double) : RespValue

    /** RESP3 `#t\r\n` / `#f\r\n`. */
    data class Bool(val value: Boolean) : RespValue

    /** RESP3 `_\r\n`. */
    object Null : RespValue
}

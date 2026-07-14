package net.internetisalie.lunar.redis.functions

import net.internetisalie.lunar.redis.resp.RespValue

/** An individual function registered within a Redis Function library (design §2.7). */
data class RedisFunctionEntry(val name: String, val flags: Set<String>)

/** A Redis Function library as reported by `FUNCTION LIST [WITHCODE]` (design §2.7). */
data class RedisLibraryEntry(
    val name: String,
    val functions: List<RedisFunctionEntry>,
    val libraryCode: String?,
)

/**
 * Decodes a `FUNCTION LIST [WITHCODE]` [RespValue] reply into a typed model (design §2.7, §3.8).
 *
 * Handles both RESP3 `RespValue.Map` and RESP2 `RespValue.Array`-of-pairs wire shapes via the
 * private [asPairs] helper. Defensive — malformed / absent fields degrade to empty results and
 * never throw (matching REDIS-04 §4.1 discipline). No `!!`.
 */
object LuaRedisFunctionListParser {

    /**
     * Parses [reply] from `FUNCTION LIST [WITHCODE]` into library entries (design §3.8).
     *
     * Returns an empty list when the reply is not a non-null [RespValue.Array], or when all
     * entries are malformed.
     */
    fun parse(reply: RespValue): List<RedisLibraryEntry> {
        val items = (reply as? RespValue.Array)?.items ?: return emptyList()
        return items.mapNotNull { parseLibrary(it) }
    }

    private fun parseLibrary(item: RespValue): RedisLibraryEntry? {
        val pairs = asPairs(item)
        val name = pairs["library_name"]?.bulkString() ?: return null
        val libraryCode = pairs["library_code"]?.bulkString()
        val functions = parseFunctions(pairs["functions"])
        return RedisLibraryEntry(name = name, functions = functions, libraryCode = libraryCode)
    }

    private fun parseFunctions(value: RespValue?): List<RedisFunctionEntry> {
        val items = (value as? RespValue.Array)?.items ?: return emptyList()
        return items.mapNotNull { parseFunctionEntry(it) }
    }

    private fun parseFunctionEntry(item: RespValue): RedisFunctionEntry? {
        val pairs = asPairs(item)
        val name = pairs["name"]?.bulkString() ?: return null
        val flags = parseFlagsArray(pairs["flags"])
        return RedisFunctionEntry(name = name, flags = flags)
    }

    private fun parseFlagsArray(value: RespValue?): Set<String> {
        val items = (value as? RespValue.Array)?.items ?: return emptySet()
        return items.mapNotNull { it.bulkString() }.toSet()
    }

    /**
     * Coerces [v] to a key→value map, handling both RESP3 [RespValue.Map] and the RESP2
     * even-length [RespValue.Array]-of-pairs wire shape (design §3.8 step 2).
     *
     * Keys are read via [bulkString]; any null key or odd-length array is silently skipped.
     */
    private fun asPairs(v: RespValue): Map<String, RespValue> {
        return when (v) {
            is RespValue.Map -> buildFromMapEntries(v.entries)
            is RespValue.Array -> buildFromArrayPairs(v.items)
            else -> emptyMap()
        }
    }

    private fun buildFromMapEntries(entries: List<Pair<RespValue, RespValue>>): Map<String, RespValue> {
        val result = mutableMapOf<String, RespValue>()
        for ((k, v) in entries) {
            val key = k.bulkString() ?: continue
            result[key] = v
        }
        return result
    }

    private fun buildFromArrayPairs(items: List<RespValue>?): Map<String, RespValue> {
        if (items == null) return emptyMap()
        val result = mutableMapOf<String, RespValue>()
        var i = 0
        while (i + 1 < items.size) {
            val key = items[i].bulkString()
            if (key != null) result[key] = items[i + 1]
            i += 2
        }
        return result
    }

    /** Reads [RespValue.Bulk.asString] or [RespValue.Simple.text]; else null. */
    private fun RespValue.bulkString(): String? = when (this) {
        is RespValue.Bulk -> asString()
        is RespValue.Simple -> text
        else -> null
    }
}

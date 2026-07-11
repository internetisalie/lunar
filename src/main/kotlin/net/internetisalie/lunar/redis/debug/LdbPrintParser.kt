package net.internetisalie.lunar.redis.debug

import net.internetisalie.lunar.redis.resp.RespValue

/** A single local variable from an LDB `print` reply (design §2.9). */
data class LuaLdbLocal(val name: String, val value: LdbValueNode)

/**
 * A parsed LDB value: an expandable value tree (design §2.9, §3.4).
 *
 * [truncated] marks a node whose repr the server cut at its `maxlen` (design §3.4, TC-LDB-PRINT-2).
 */
sealed interface LdbValueNode {

    /** A leaf value (`10`, `"str"`, `nil`, `true`). */
    data class Scalar(val text: String, val truncated: Boolean = false) : LdbValueNode

    /** A table rendered as key→value entries; [entries] may be partial when [truncated]. */
    data class Table(
        val entries: List<Pair<String, LdbValueNode>>,
        val truncated: Boolean = false,
    ) : LdbValueNode
}

/**
 * Parses LDB `print` output into a value tree (design §3.4).
 *
 * Pure/stateless recursive-descent over the table repr. Never throws and never uses `!!`: an
 * unterminated/truncated table (cut at the server `maxlen`) yields a partial node with
 * `truncated = true`, and a hard depth cap prevents pathological input from stack-overflowing
 * (contract §1, §3; TC-LDB-PRINT-2).
 */
object LdbPrintParser {

    private const val KEY_VALUE_SEPARATOR = " = "
    private const val VALUE_SENTINEL = "<value> "
    private const val TRUNCATION_MARKER = "(truncated)"
    private const val MAX_DEPTH = 64

    /** Parse a `print` (no-arg) reply block into the frame's locals (design §3.4). */
    fun parseLocals(reply: RespValue): List<LuaLdbLocal> =
        statusLines(reply).mapNotNull(::parseLocalLine)

    /** Parse a single `eval`/`print <var>` reply block into one value node (design §3.4). */
    fun parseValue(reply: RespValue): LdbValueNode =
        parseRepr(statusLines(reply).firstOrNull()?.removePrefix(VALUE_SENTINEL).orEmpty(), 0)

    private fun parseLocalLine(rawLine: String): LuaLdbLocal? {
        val line = rawLine.removePrefix(VALUE_SENTINEL)
        val separatorIndex = line.indexOf(KEY_VALUE_SEPARATOR)
        if (separatorIndex < 0) return null
        val name = line.substring(0, separatorIndex).trim()
        val repr = line.substring(separatorIndex + KEY_VALUE_SEPARATOR.length)
        return LuaLdbLocal(name, parseRepr(repr, 0))
    }

    private fun statusLines(reply: RespValue): List<String> = when (reply) {
        is RespValue.Array -> reply.items.orEmpty().map(::lineText)
        else -> listOf(lineText(reply))
    }

    private fun lineText(value: RespValue): String = when (value) {
        is RespValue.Simple -> value.text
        is RespValue.Bulk -> value.asString().orEmpty()
        else -> ""
    }

    private fun parseRepr(repr: String, depth: Int): LdbValueNode {
        val text = repr.trim()
        if (depth >= MAX_DEPTH) return LdbValueNode.Scalar(text, truncated = true)
        if (text.startsWith("{")) return parseTable(text, depth)
        return scalarOf(text)
    }

    private fun scalarOf(text: String): LdbValueNode.Scalar {
        val truncated = text.endsWith(TRUNCATION_MARKER)
        val value = text.removeSuffix(TRUNCATION_MARKER).trim()
        return LdbValueNode.Scalar(value, truncated)
    }

    private fun parseTable(text: String, depth: Int): LdbValueNode.Table {
        val closeIndex = matchingBrace(text)
        val body = text.substring(1, closeIndex ?: text.length)
        val truncated = closeIndex == null || text.endsWith(TRUNCATION_MARKER)
        val entries = splitEntries(body).mapIndexedNotNull { index, segment ->
            entryOf(segment, index, depth)
        }
        return LdbValueNode.Table(entries, truncated)
    }

    private fun entryOf(segment: String, index: Int, depth: Int): Pair<String, LdbValueNode>? {
        val trimmed = segment.trim().removeSuffix(TRUNCATION_MARKER).trim()
        if (trimmed.isEmpty()) return null
        val (separatorOffset, separatorLength) = topLevelKeySeparator(trimmed)
        if (separatorOffset < 0) return "[${index + 1}]" to parseRepr(trimmed, depth + 1)
        val key = normalizeKey(trimmed.substring(0, separatorOffset).trim())
        val value = trimmed.substring(separatorOffset + separatorLength)
        return key to parseRepr(value, depth + 1)
    }

    private fun normalizeKey(rawKey: String): String {
        val stripped = rawKey.removePrefix("[").removeSuffix("]").trim()
        val unquoted = unquote(stripped)
        if (unquoted != stripped) return unquoted
        val asInt = stripped.toIntOrNull()
        return if (asInt != null) "[$asInt]" else unquoted
    }

    private fun unquote(text: String): String =
        if (text.length >= 2 && text.first() == '"' && text.last() == '"') {
            text.substring(1, text.length - 1)
        } else {
            text
        }

    /** Index (offset, length) of the top-level key separator (`: ` JSON or `=` Lua), or -1. */
    private fun topLevelKeySeparator(entry: String): Pair<Int, Int> {
        var depthLevel = 0
        var inQuote = false
        for (index in entry.indices) {
            val current = entry[index]
            if (current == '"') inQuote = !inQuote
            if (inQuote) continue
            if (current == '{' || current == '[') depthLevel += 1
            if (current == '}' || current == ']') depthLevel -= 1
            if (depthLevel == 0 && current == ':') return index to 1
            if (depthLevel == 0 && current == '=') return index to 1
        }
        return -1 to 0
    }

    /** Split a table body into top-level entries on commas outside braces/quotes. */
    private fun splitEntries(body: String): List<String> {
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        var depthLevel = 0
        var inQuote = false
        for (character in body) {
            if (character == '"') inQuote = !inQuote
            if (!inQuote && (character == '{' || character == '[')) depthLevel += 1
            if (!inQuote && (character == '}' || character == ']')) depthLevel -= 1
            if (!inQuote && character == ',' && depthLevel == 0) {
                segments.add(current.toString())
                current.setLength(0)
            } else {
                current.append(character)
            }
        }
        if (current.isNotBlank()) segments.add(current.toString())
        return segments
    }

    /** Index of the `}` closing the leading `{`, tracking quotes; null when unbalanced (truncated). */
    private fun matchingBrace(text: String): Int? {
        var depthLevel = 0
        var inQuote = false
        for (index in text.indices) {
            val current = text[index]
            if (current == '"') inQuote = !inQuote
            if (inQuote) continue
            if (current == '{') depthLevel += 1
            if (current == '}') {
                depthLevel -= 1
                if (depthLevel == 0) return index
            }
        }
        return null
    }
}

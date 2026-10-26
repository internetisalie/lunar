package net.internetisalie.lunar.run.test

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * One test entry of busted's `--output=json` report, located by busted's own fields.
 *
 * busted's `name` is the describe/it path joined by single spaces (`base.lua` `getFullName`), and
 * describe and it names may themselves contain spaces, so the path cannot be split back into its
 * levels. The leaf name is read from `element.name` instead; everything before it in `name` is the
 * enclosing describe path, kept whole. The spec file comes from `element.trace` (the `it` call
 * site), falling back to the entry's own `trace`. busted writes an empty Lua table as `[]`, so every
 * object field is read defensively.
 */
internal data class LuaBustedReportEntry(
    val testName: String,
    val describePath: String?,
    val file: String?,
    val line: Long?,
    val durationMs: Long,
    val message: String?,
    val traceback: String?,
) {
    val locationUrl: String?
        get() = if (file != null && line != null) "lua://$file:$line" else null

    companion object {
        fun parse(item: JsonObject): LuaBustedReportEntry {
            val element = item.objectOrNull("element")
            val fullName = item.stringOrNull("name").orEmpty()
            val leafName = element?.stringOrNull("name") ?: element?.stringOrNull("descriptor") ?: fullName
            val trace = element?.objectOrNull("trace") ?: item.objectOrNull("trace")
            val durationSec = element?.doubleOrNull("duration") ?: item.doubleOrNull("duration") ?: 0.0
            return LuaBustedReportEntry(
                testName = if (fullName.endsWith(leafName)) leafName else fullName,
                describePath = describePathOf(fullName, leafName),
                file = trace?.stringOrNull("source")?.removePrefix("@"),
                line = trace?.longOrNull("currentline"),
                durationMs = (durationSec * 1000).toLong(),
                message = item.stringOrNull("message"),
                traceback = item.objectOrNull("trace")?.stringOrNull("traceback"),
            )
        }

        private fun describePathOf(
            fullName: String,
            leafName: String,
        ): String? = fullName.removeSuffix(" $leafName").takeIf { it != fullName && it.isNotEmpty() }

        private fun JsonObject.primitive(key: String): JsonElement? = get(key)?.takeIf { it.isJsonPrimitive }

        private fun JsonObject.objectOrNull(key: String): JsonObject? =
            get(key)?.takeIf { it.isJsonObject }?.asJsonObject

        private fun JsonObject.stringOrNull(key: String): String? = primitive(key)?.asString

        private fun JsonObject.numberOrNull(key: String): JsonPrimitive? =
            primitive(key)?.asJsonPrimitive?.takeIf { it.isNumber }

        private fun JsonObject.longOrNull(key: String): Long? = numberOrNull(key)?.asLong

        private fun JsonObject.doubleOrNull(key: String): Double? = numberOrNull(key)?.asDouble
    }
}

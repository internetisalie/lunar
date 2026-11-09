package net.internetisalie.lunar.rocks.browser

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

/**
 * Reads the browser's metadata from the rockspec bridge's JSON (BUG-68). Used for a server
 * rockspec that computes its fields (locals, concatenation, conditionals), which
 * [RockspecMetadataReader]'s literal-only reading cannot follow.
 */
internal object RockspecJsonMetadata {
    /** The metadata in [json], or null when it is not an object naming `package` and `version`. */
    fun read(json: String): LuaRockMetadata? {
        val root = parseObject(json) ?: return null
        val name = root.text("package") ?: return null
        val version = root.text("version") ?: return null
        val description = root.objectAt("description")
        return LuaRockMetadata(
            name = name,
            version = version,
            summary = description?.text("summary"),
            detailed = description?.text("detailed"),
            license = description?.text("license"),
            homepage = description?.text("homepage"),
            issues = description?.text("issues_url"),
            location = null,
            dependencies = root.get("dependencies").strings(),
            modules =
                root
                    .objectAt("build")
                    ?.objectAt("modules")
                    ?.keySet()
                    ?.toList()
                    .orEmpty(),
        )
    }

    private fun parseObject(json: String): JsonObject? =
        try {
            JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: JsonSyntaxException) {
            null
        }

    private fun JsonObject.text(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.objectAt(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    /** A JSON array of strings, or an object with string values (how the encoder writes a sequence it cannot tell apart). */
    private fun JsonElement?.strings(): List<String> =
        when {
            this == null -> emptyList()
            isJsonArray -> asJsonArray.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
            isJsonObject ->
                asJsonObject.entrySet().sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }.mapNotNull {
                    it.value
                        .takeIf { e ->
                            e.isJsonPrimitive
                        }?.asString
                }
            else -> emptyList()
        }
}

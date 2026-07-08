package net.internetisalie.lunar.toolchain.provision.feed

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.internetisalie.lunar.toolchain.provision.LuaProvisionException

/**
 * Parses the feed JSON tree into the typed model with strict null checks — every declared
 * field must be present and correctly typed, so a missing pin (`sha256`/`size`) is a
 * corrupt-feed error rather than a silently defaulted value (design §4.1 parse strategy).
 */
internal object LuaFeedJsonParser {
    fun parseFeed(root: JsonObject): LuaToolchainFeed {
        val feedVersion = root.requireInt("feedVersion")
        val kindsObject = root.requireObject("kinds")
        val kinds = kindsObject.keySet().associateWith { kindId ->
            parseKind(kindsObject.requireObject(kindId))
        }
        return LuaToolchainFeed(feedVersion, kinds)
    }

    private fun parseKind(kind: JsonObject): LuaFeedKind {
        val aliasesObject = kind.requireObject("aliases")
        val aliases = aliasesObject.keySet().associateWith { aliasesObject.requireString(it) }
        val versions = kind.requireArray("versions").map { parseVersion(it.asJsonObjectOrFail("versions[]")) }
        return LuaFeedKind(aliases, versions)
    }

    private fun parseVersion(version: JsonObject): LuaFeedVersion =
        LuaFeedVersion(
            version = version.requireString("version"),
            gatedOn = version.optString("gatedOn"),
            source = version.optObject("source")?.let(::parseSource),
            assets = version.optArray("assets")?.map { parseAsset(it.asJsonObjectOrFail("assets[]")) } ?: emptyList(),
            rock = version.optObject("rock")?.let(::parseRock),
        )

    private fun parseSource(source: JsonObject): LuaFeedSource =
        LuaFeedSource(
            urls = source.requireStringArray("urls"),
            sha256 = source.requireString("sha256"),
            size = source.requireLong("size"),
            rootPrefix = source.requireString("rootPrefix"),
        )

    private fun parseAsset(asset: JsonObject): LuaFeedAsset =
        LuaFeedAsset(
            os = asset.requireString("os"),
            arch = asset.requireString("arch"),
            url = asset.requireString("url"),
            sha256 = asset.requireString("sha256"),
            size = asset.requireLong("size"),
            packaging = asset.requireString("packaging"),
            rootPrefix = asset.optString("rootPrefix"),
            layout = asset.requireString("layout"),
            binaryPath = asset.requireString("binaryPath"),
        )

    private fun parseRock(rock: JsonObject): LuaFeedRock =
        LuaFeedRock(
            rockName = rock.requireString("rockName"),
            pinnedVersion = rock.optString("pinnedVersion"),
            binName = rock.requireString("binName"),
            needsCToolchain = rock.requireBoolean("needsCToolchain"),
        )

    private fun corrupt(detail: String): Nothing =
        throw LuaProvisionException("Corrupt toolchain feed: $detail")

    private fun JsonObject.requirePrimitive(field: String) =
        get(field)?.takeUnless { it.isJsonNull } ?: corrupt("missing field '$field'")

    private fun JsonObject.requireString(field: String): String =
        runCatching { requirePrimitive(field).asString }.getOrElse { corrupt("field '$field' is not a string") }

    private fun JsonObject.requireInt(field: String): Int =
        runCatching { requirePrimitive(field).asInt }.getOrElse { corrupt("field '$field' is not an int") }

    private fun JsonObject.requireLong(field: String): Long =
        runCatching { requirePrimitive(field).asLong }.getOrElse { corrupt("field '$field' is not a number") }

    private fun JsonObject.requireBoolean(field: String): Boolean =
        runCatching { requirePrimitive(field).asBoolean }.getOrElse { corrupt("field '$field' is not a boolean") }

    private fun JsonObject.requireObject(field: String): JsonObject =
        requirePrimitive(field).let { if (it.isJsonObject) it.asJsonObject else corrupt("field '$field' is not an object") }

    private fun JsonObject.requireArray(field: String): JsonArray =
        requirePrimitive(field).let { if (it.isJsonArray) it.asJsonArray else corrupt("field '$field' is not an array") }

    private fun JsonObject.requireStringArray(field: String): List<String> =
        requireArray(field).map { runCatching { it.asString }.getOrElse { corrupt("field '$field' has a non-string element") } }

    private fun JsonObject.optString(field: String): String? =
        get(field)?.takeUnless { it.isJsonNull }?.let {
            runCatching { it.asString }.getOrElse { corrupt("field '$field' is not a string") }
        }

    private fun JsonObject.optObject(field: String): JsonObject? =
        get(field)?.takeUnless { it.isJsonNull }?.let {
            if (it.isJsonObject) it.asJsonObject else corrupt("field '$field' is not an object")
        }

    private fun JsonObject.optArray(field: String): JsonArray? =
        get(field)?.takeUnless { it.isJsonNull }?.let {
            if (it.isJsonArray) it.asJsonArray else corrupt("field '$field' is not an array")
        }

    private fun com.google.gson.JsonElement.asJsonObjectOrFail(context: String): JsonObject =
        if (isJsonObject) asJsonObject else corrupt("element of '$context' is not an object")
}

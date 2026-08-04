package net.internetisalie.lunar.definitions

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import net.internetisalie.lunar.toolchain.provision.LuaProvisionException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Loads and caches the bundled definition catalog (TARGET-08-01).
 *
 * Pure JVM code — safe on any thread; no PSI/VFS/EDT contact. Modelled on
 * `LuaToolchainFeedLoader`, including its strict-parse contract: every declared field must be
 * present and correctly typed, so a missing `sha256` is a corrupt-catalog error rather than a
 * silently defaulted value that would later disable verification.
 */
object LuaDefinitionCatalogLoader {
    const val RESOURCE = "/definitions/lunar-definitions-catalog.json"

    /** The only `catalogVersion` this build can read; anything else is rejected, not guessed at. */
    const val SUPPORTED_CATALOG_VERSION = 1

    @Volatile
    private var cached: LuaDefinitionCatalog? = null

    /** Parses the bundled resource once and caches it. Corrupt/missing resource → [LuaProvisionException]. */
    fun load(): LuaDefinitionCatalog = cached ?: synchronized(this) { cached ?: parse(read()).also { cached = it } }

    /** Parses [json] without touching the cache — the seam tests use for corrupt-input cases. */
    fun parse(json: String): LuaDefinitionCatalog {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }
            .getOrElse { failure -> throw LuaProvisionException("Corrupt definitions catalog: ${describe(failure)}", failure) }
        return parseCatalog(root)
    }

    private fun read(): String {
        val stream = LuaDefinitionCatalogLoader::class.java.getResourceAsStream(RESOURCE)
            ?: throw LuaProvisionException("Corrupt definitions catalog: bundled resource '$RESOURCE' is missing.")
        return stream.use { InputStreamReader(it, StandardCharsets.UTF_8).readText() }
    }

    private fun describe(failure: Throwable): String =
        when (failure) {
            is JsonSyntaxException -> "invalid JSON syntax."
            else -> "top-level value is not a JSON object."
        }

    private fun parseCatalog(root: JsonObject): LuaDefinitionCatalog {
        val version = root.requireInt("catalogVersion")
        // Actually enforced, so the field earns its keep: an unknown version must be a loud error
        // rather than a misparse against a format this build does not understand.
        if (version != SUPPORTED_CATALOG_VERSION) {
            corrupt("catalogVersion $version is not supported (this build reads $SUPPORTED_CATALOG_VERSION)")
        }
        val libraries = root.requireArray("libraries").map { parseEntry(it.asObjectOrFail("libraries[]")) }
        // Ids key the per-project enable list, so a duplicate would make "enabled" ambiguous.
        libraries.groupingBy { it.id }.eachCount().forEach { (id, count) ->
            if (count > 1) corrupt("duplicate library id '$id'")
        }
        // ADVISORY verification's whole safety argument is that the URL identifies immutable
        // content by embedding the pinned commit SHA. Left unchecked that is a comment, not an
        // invariant — one mirror added without the SHA and the rationale is silently void.
        libraries.forEach { entry ->
            if (entry.urls.none { it.contains(entry.version) }) {
                corrupt("library '${entry.id}' has a URL that does not pin version '${entry.version}'")
            }
        }
        // An unresolvable `requires` silently reproduces the half-enabled library the field exists
        // to prevent (busted without luassert), so a typo is corruption, not a runtime surprise.
        val ids = libraries.mapTo(mutableSetOf()) { it.id }
        libraries.forEach { entry ->
            entry.requires.firstOrNull { it !in ids }
                ?.let { corrupt("library '${entry.id}' requires unknown library '$it'") }
        }
        return LuaDefinitionCatalog(catalogVersion = version, libraries = libraries)
    }

    private fun parseEntry(entry: JsonObject): LuaDefinitionEntry =
        LuaDefinitionEntry(
            id = entry.requireString("id"),
            displayName = entry.requireString("displayName"),
            version = entry.requireString("version"),
            urls = entry.requireStringArray("urls").ifEmpty { corrupt("field 'urls' is empty") },
            // The verification pins specifically: a blank hash or a zero size would parse cleanly
            // and then silently disable the integrity check the fetcher depends on.
            sha256 = entry.requireString("sha256"),
            size = entry.requireLong("size").also { if (it <= 0) corrupt("field 'size' must be positive") },
            rootPrefix = entry.requireString("rootPrefix"),
            license = entry.requireString("license"),
            attributionUrl = entry.requireString("attributionUrl"),
            // Optional: most libraries stand alone, and absent must mean "none", not corrupt.
            requires = entry.optStringArray("requires") ?: emptyList(),
        )

    private fun corrupt(detail: String): Nothing =
        throw LuaProvisionException("Corrupt definitions catalog: $detail")

    private fun JsonObject.requirePresent(field: String) =
        get(field)?.takeUnless { it.isJsonNull } ?: corrupt("missing field '$field'")

    /**
     * Note the `requirePresent` call sits **outside** `runCatching`. Inlining it — as the
     * `LuaFeedJsonParser` shape this mirrors does — makes the "missing field" message unreachable:
     * `corrupt` throws, `runCatching` swallows it, and an absent `sha256` reports the misleading
     * "is not a string" instead.
     */
    private fun JsonObject.requireString(field: String): String {
        val value = requirePresent(field)
        // Typed check rather than `asString`, which happily coerces `"sha256": 12345` to "12345".
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) corrupt("field '$field' is not a string")
        return value.asString.ifBlank { corrupt("field '$field' is blank") }
    }

    private fun JsonObject.requireInt(field: String): Int {
        val value = requirePresent(field)
        return runCatching { value.asInt }.getOrElse { corrupt("field '$field' is not an int") }
    }

    private fun JsonObject.requireLong(field: String): Long {
        val value = requirePresent(field)
        return runCatching { value.asLong }.getOrElse { corrupt("field '$field' is not a number") }
    }

    private fun JsonObject.requireArray(field: String): JsonArray =
        requirePresent(field).let { if (it.isJsonArray) it.asJsonArray else corrupt("field '$field' is not an array") }

    private fun JsonObject.requireStringArray(field: String): List<String> =
        requireArray(field).map { runCatching { it.asString }.getOrElse { corrupt("field '$field' has a non-string element") } }

    private fun JsonObject.optStringArray(field: String): List<String>? =
        get(field)?.takeUnless { it.isJsonNull }?.let { _ -> requireStringArray(field) }

    private fun com.google.gson.JsonElement.asObjectOrFail(context: String): JsonObject =
        if (isJsonObject) asJsonObject else corrupt("element of '$context' is not an object")
}

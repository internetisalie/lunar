package net.internetisalie.lunar.definitions

import net.internetisalie.lunar.toolchain.provision.LuaProvisionException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TARGET-08-01. Pure JVM — no fixture, no PSI, no network.
 *
 * The strict-parse cases matter more than the happy path: every field this catalog declares is a
 * verification input, so a silently defaulted `sha256` would disable integrity checking rather
 * than fail loudly.
 */
class LuaDefinitionCatalogLoaderTest {

    private fun entryJson(
        id: String = "busted",
        omit: String? = null,
        extra: String = "",
    ): String {
        val fields = linkedMapOf(
            "id" to "\"$id\"",
            "displayName" to "\"Busted\"",
            "version" to "\"5ed85d0e\"",
            "urls" to "[\"https://example.invalid/$id.tar.gz\"]",
            "sha256" to "\"c33499e7\"",
            "size" to "2040",
            "rootPrefix" to "\"$id-5ed85d0e/library\"",
            "license" to "\"MIT\"",
            "attributionUrl" to "\"https://example.invalid/$id\"",
        )
        omit?.let { fields.remove(it) }
        val body = fields.entries.joinToString(",") { "\"${it.key}\":${it.value}" }
        return "{$body${if (extra.isEmpty()) "" else ",$extra"}}"
    }

    private fun catalogJson(vararg entries: String) =
        """{"catalogVersion":1,"libraries":[${entries.joinToString(",")}]}"""

    /** TC 1 — the bundled resource parses and carries real pins. */
    @Test
    fun bundledCatalogLoads() {
        val catalog = LuaDefinitionCatalogLoader.load()
        assertEquals(1, catalog.catalogVersion)
        val love2d = assertNotNull(catalog.entry("love2d"), "expected a love2d entry")
        assertTrue(love2d.sha256.isNotBlank(), "sha256 must be pinned")
        assertTrue(love2d.size > 0, "size must be pinned")
        assertEquals("MIT", love2d.license)
        assertTrue(love2d.urls.isNotEmpty(), "at least one URL required")
    }

    /** Every bundled entry must be verifiable and attributable — these are licensing obligations. */
    @Test
    fun everyBundledEntryIsPinnedAndAttributed() {
        LuaDefinitionCatalogLoader.load().libraries.forEach { entry ->
            assertTrue(entry.sha256.isNotBlank(), "${entry.id}: sha256 missing")
            assertTrue(entry.size > 0, "${entry.id}: size missing")
            assertTrue(entry.license.isNotBlank(), "${entry.id}: license missing")
            assertTrue(entry.attributionUrl.isNotBlank(), "${entry.id}: attributionUrl missing")
            assertTrue(
                entry.rootPrefix.endsWith("/library"),
                "${entry.id}: rootPrefix should name the library dir to register, was '${entry.rootPrefix}'",
            )
        }
    }

    /**
     * TC 2 — a missing pin is corruption, never a default.
     *
     * Asserts the message says **missing**, not merely that it mentions the field: the helper this
     * mirrors evaluates its presence check inside `runCatching`, which swallows the missing-field
     * error and misreports it as a type error. Matching only on "sha256" would pass either way.
     */
    @Test
    fun missingSha256ThrowsMissingNotTypeError() {
        val failure = assertFailsWith<LuaProvisionException> {
            LuaDefinitionCatalogLoader.parse(catalogJson(entryJson(omit = "sha256")))
        }
        assertContains(failure.message.orEmpty(), "missing field 'sha256'")
    }

    /** A blank hash parses as a string but silently disables the integrity check it exists for. */
    @Test
    fun blankSha256Throws() {
        val json = catalogJson(entryJson().replace("\"sha256\":\"c33499e7\"", "\"sha256\":\"\""))
        val failure = assertFailsWith<LuaProvisionException> { LuaDefinitionCatalogLoader.parse(json) }
        assertContains(failure.message.orEmpty(), "blank")
    }

    /** Gson's `asString` coerces a JSON number to text, so a mistyped hash must be rejected typed. */
    @Test
    fun numericSha256Throws() {
        val json = catalogJson(entryJson().replace("\"sha256\":\"c33499e7\"", "\"sha256\":12345"))
        val failure = assertFailsWith<LuaProvisionException> { LuaDefinitionCatalogLoader.parse(json) }
        assertContains(failure.message.orEmpty(), "not a string")
    }

    @Test
    fun zeroSizeThrows() {
        val json = catalogJson(entryJson().replace("\"size\":2040", "\"size\":0"))
        val failure = assertFailsWith<LuaProvisionException> { LuaDefinitionCatalogLoader.parse(json) }
        assertContains(failure.message.orEmpty(), "size")
    }

    /**
     * An unresolvable `requires` reproduces the exact half-enabled library the field was added to
     * prevent, so a typo must fail at parse rather than at resolve time.
     */
    @Test
    fun unknownRequiresIdThrows() {
        val json = catalogJson(entryJson(extra = "\"requires\":[\"ghost\"]"))
        val failure = assertFailsWith<LuaProvisionException> { LuaDefinitionCatalogLoader.parse(json) }
        assertContains(failure.message.orEmpty(), "ghost")
    }

    /** The version field must actually gate, or its stated purpose is fiction. */
    @Test
    fun unsupportedCatalogVersionThrows() {
        val json = """{"catalogVersion":99,"libraries":[]}"""
        val failure = assertFailsWith<LuaProvisionException> { LuaDefinitionCatalogLoader.parse(json) }
        assertContains(failure.message.orEmpty(), "catalogVersion 99")
    }

    @Test
    fun missingCatalogVersionThrows() {
        val failure = assertFailsWith<LuaProvisionException> {
            LuaDefinitionCatalogLoader.parse("""{"libraries":[]}""")
        }
        assertContains(failure.message.orEmpty(), "catalogVersion")
    }

    @Test
    fun malformedJsonThrows() {
        assertFailsWith<LuaProvisionException> { LuaDefinitionCatalogLoader.parse("not json at all") }
    }

    @Test
    fun emptyUrlListThrows() {
        val json = catalogJson(entryJson().replace("""["https://example.invalid/busted.tar.gz"]""", "[]"))
        val failure = assertFailsWith<LuaProvisionException> { LuaDefinitionCatalogLoader.parse(json) }
        assertContains(failure.message.orEmpty(), "urls")
    }

    /** Ids key the per-project enable list, so a duplicate makes "enabled" ambiguous. */
    @Test
    fun duplicateIdThrows() {
        val failure = assertFailsWith<LuaProvisionException> {
            LuaDefinitionCatalogLoader.parse(catalogJson(entryJson(), entryJson()))
        }
        assertContains(failure.message.orEmpty(), "duplicate")
    }

    /** `requires` is optional — most libraries stand alone and absent must mean none. */
    @Test
    fun absentRequiresIsEmptyNotCorrupt() {
        val catalog = LuaDefinitionCatalogLoader.parse(catalogJson(entryJson()))
        assertEquals(emptyList(), catalog.entry("busted")?.requires)
    }

    /**
     * busted's definitions open with `assert = require("luassert")`, so enabling busted alone would
     * leave `assert`/`spy`/`stub`/`mock` unresolved. The bundled entry must declare the dependency
     * and resolution must pull it in.
     */
    @Test
    fun bundledBustedPullsInLuassert() {
        val catalog = LuaDefinitionCatalogLoader.load()
        assertEquals(listOf("luassert"), catalog.entry("busted")?.requires)
        val resolved = catalog.withDependencies(listOf("busted")).map { it.id }
        assertEquals(listOf("busted", "luassert"), resolved)
    }

    @Test
    fun dependencyResolutionSurvivesCycles() {
        val a = entryJson(id = "a", extra = "\"requires\":[\"b\"]")
        val b = entryJson(id = "b", extra = "\"requires\":[\"a\"]")
        val catalog = LuaDefinitionCatalogLoader.parse(catalogJson(a, b))
        assertEquals(listOf("a", "b"), catalog.withDependencies(listOf("a")).map { it.id })
    }

    /**
     * An id absent from the catalog must be skipped, not raised: the enable list is user data in
     * `lunar.xml` and can name a library a newer or older plugin build does not carry. This is the
     * opposite of [unknownRequiresIdThrows] — bad *catalog* data is corruption, stale *user* data
     * is not.
     */
    @Test
    fun dependencyResolutionIgnoresIdsAbsentFromCatalog() {
        val catalog = LuaDefinitionCatalogLoader.parse(catalogJson(entryJson()))
        assertEquals(listOf("busted"), catalog.withDependencies(listOf("busted", "ghost")).map { it.id })
        assertEquals(emptyList(), catalog.withDependencies(listOf("ghost")).map { it.id })
    }
}

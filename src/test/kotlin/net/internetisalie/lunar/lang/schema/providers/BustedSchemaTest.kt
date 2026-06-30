package net.internetisalie.lunar.lang.schema.providers

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import net.internetisalie.lunar.lang.schema.LuaJsonSchemaComplianceInspection
import net.internetisalie.lunar.lang.schema.LuaSchemaProviderFactory

/**
 * SCHEMA-04 validation + completion against the bundled busted-config schema (requirements
 * TC #1-#4). `.busted` is associated with the Lua file type (plugin.xml `fileNames`) and the busted
 * provider comes from the `schemaFileProvider` EP; registering the real Lunar factory through the
 * platform provider-factory EP forces JsonSchemaService to (re)discover and resolve it in the light
 * fixture (mirrors LuacheckrcSchemaTest). `.busted` is shape B (`return { default = { … } }`).
 */
class BustedSchemaTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        JsonSchemaProviderFactory.EP_NAME.point.registerExtension(LuaSchemaProviderFactory(), testRootDisposable)
        myFixture.enableInspections(LuaJsonSchemaComplianceInspection())
    }

    private fun warningsFor(fileName: String, text: String): List<String> {
        myFixture.configureByText(fileName, text)
        return myFixture.doHighlighting(HighlightSeverity.WARNING).mapNotNull { it.description }
    }

    /** TC #1: a valid `verbose = true` profile option produces no schema warnings. */
    fun testTc1_ValidProfileNoWarnings() {
        val warnings = warningsFor(".busted", "return { default = { verbose = true } }\n")
        assertTrue(
            "A valid busted profile must not warn, was: $warnings",
            warnings.none { it.contains("not allowed", ignoreCase = true) || it.contains("Required", ignoreCase = true) },
        )
    }

    /** TC #2: an unknown profile key warns — `additionalProperties: false` rejects `bogus`. */
    fun testTc2_UnknownKeyNotAllowed() {
        val warnings = warningsFor(".busted", "return { default = { bogus = true } }\n")
        assertTrue(
            "Expected a 'not allowed' warning for the unknown 'bogus' key, was: $warnings",
            warnings.any { it.contains("not allowed", ignoreCase = true) },
        )
    }

    /** TC #3: a string for the boolean `verbose` option warns — type mismatch (boolean expected). */
    fun testTc3_VerboseStringTypeMismatch() {
        val warnings = warningsFor(".busted", "return { default = { verbose = \"yes\" } }\n")
        assertTrue(
            "Expected a type-mismatch warning naming 'boolean' for verbose = \"yes\", was: $warnings",
            warnings.any { it.contains("boolean", ignoreCase = true) },
        )
    }

    /** TC #4: profile-level completion suggests busted profile option keys. */
    fun testTc4_ProfileKeyCompletion() {
        myFixture.configureByText(".busted", "return { default = { <caret> } }\n")
        val variants = myFixture.completeBasic().map { it.lookupString }
        assertContainsElements(variants, "output", "verbose", "coverage", "pattern", "ROOT", "tags")
    }
}

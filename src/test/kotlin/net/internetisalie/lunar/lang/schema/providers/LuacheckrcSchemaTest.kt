package net.internetisalie.lunar.lang.schema.providers

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import net.internetisalie.lunar.lang.schema.LuaJsonSchemaComplianceInspection
import net.internetisalie.lunar.lang.schema.LuaSchemaProviderFactory

/**
 * SCHEMA-03 validation + completion against the bundled luacheck-config schema (requirements
 * TC #1-#5). `.luacheckrc` is associated with the Lua file type (plugin.xml `fileNames`) and the
 * luacheckrc provider comes from the `schemaFileProvider` EP; registering the real Lunar factory
 * through the platform provider-factory EP forces JsonSchemaService to (re)discover and resolve it
 * in the light fixture (mirrors RockspecSchemaValidationTest).
 */
class LuacheckrcSchemaTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        JsonSchemaProviderFactory.EP_NAME.point.registerExtension(LuaSchemaProviderFactory(), testRootDisposable)
        myFixture.enableInspections(LuaJsonSchemaComplianceInspection())
    }

    private fun warningsFor(fileName: String, text: String): List<String> {
        myFixture.configureByText(fileName, text)
        return myFixture.doHighlighting(HighlightSeverity.WARNING).mapNotNull { it.description }
    }

    /** TC #1: `globals = true` must warn — boolean given where a string array is required. */
    fun testTc1_GlobalsBooleanTypeMismatch() {
        val warnings = warningsFor(".luacheckrc", "globals = true\n")
        assertTrue(
            "Expected a type-mismatch warning naming 'array' for globals = true, was: $warnings",
            warnings.any { it.contains("array", ignoreCase = true) },
        )
    }

    /** Control for TC #1's boolean walker gap: an integer-valued array mismatch is known to fire. */
    fun testTc1Control_GlobalsIntegerTypeMismatch() {
        val warnings = warningsFor(".luacheckrc", "globals = 1\n")
        assertTrue(
            "Expected a type-mismatch warning for globals = 1 (integer where array required), was: $warnings",
            warnings.any { it.contains("array", ignoreCase = true) },
        )
    }

    /** TC #2: `max_line_length = 120` is a valid integer — no schema warning. */
    fun testTc2_MaxLineLengthIntegerAllowed() {
        val warnings = warningsFor(".luacheckrc", "max_line_length = 120\n")
        assertFalse(
            "max_line_length = 120 must not warn, was: $warnings",
            warnings.any { it.contains("max_line_length") },
        )
    }

    /** TC #3: `max_line_length = false` is allowed (boolean disables the check) — no schema warning. */
    fun testTc3_MaxLineLengthBooleanFalseAllowed() {
        val warnings = warningsFor(".luacheckrc", "max_line_length = false\n")
        assertFalse(
            "max_line_length = false must not warn, was: $warnings",
            warnings.any { it.contains("max_line_length") },
        )
    }

    /** TC #4: top-level completion suggests luacheck option keys. */
    fun testTc4_TopLevelKeyCompletion() {
        myFixture.configureByText(".luacheckrc", "<caret>\n")
        val variants = myFixture.completeBasic().map { it.lookupString }
        assertContainsElements(variants, "std", "globals", "ignore", "max_line_length")
    }

    /** TC #5 (isolation): a plain `main.lua` is not claimed by the provider — no schema warnings. */
    fun testTc5_PlainLuaFileNotValidated() {
        val warnings = warningsFor("main.lua", "globals = true\n")
        assertTrue(
            "A plain main.lua must not receive luacheck schema warnings, was: $warnings",
            warnings.none { it.contains("array", ignoreCase = true) },
        )
    }
}

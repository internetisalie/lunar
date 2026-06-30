package net.internetisalie.lunar.lang.schema.providers

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import net.internetisalie.lunar.lang.schema.LuaJsonSchemaComplianceInspection
import net.internetisalie.lunar.lang.schema.LuaSchemaProviderFactory

/**
 * SCHEMA-02 validation + completion against the bundled rockspec schemas (requirements TC #1-#6).
 * `.rockspec` is associated with the Lua file type and the rockspec providers come from plugin.xml's
 * `schemaFileProvider` EP; registering the real Lunar factory through the platform provider-factory
 * EP forces JsonSchemaService to (re)discover and resolve them in the light fixture.
 */
class RockspecSchemaValidationTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        JsonSchemaProviderFactory.EP_NAME.point.registerExtension(LuaSchemaProviderFactory(), testRootDisposable)
        myFixture.enableInspections(LuaJsonSchemaComplianceInspection())
    }

    private fun warningsFor(fileName: String, text: String): List<String> {
        myFixture.configureByText(fileName, text)
        return myFixture.doHighlighting(HighlightSeverity.WARNING).mapNotNull { it.description }
    }

    fun testTc1_MissingRequiredPackageOrVersion() {
        // v3.0 requires package + version (among others); a rockspec missing version warns.
        val warnings = warningsFor(
            "foo-1.0-1.rockspec",
            "package = \"foo\"\nsource = { url = \"git://x\" }\nbuild = { type = \"builtin\" }\n",
        )
        assertTrue(
            "Expected a missing-required-property warning naming 'version', was: $warnings",
            warnings.any { it.contains("Missing required") && it.contains("version") },
        )
    }

    fun testTc2_VersionNumberTypeMismatch() {
        myFixture.configureByText(
            "foo-1.0-1.rockspec",
            """
            package = "foo"
            version = <warning descr="Incompatible types.___ Required: string. Actual: number.">1.0</warning>
            source = { url = "git://x" }
            build = { type = "builtin" }
            """.trimIndent().replace("___", "\n"),
        )
        myFixture.checkHighlighting(true, false, true)
    }

    fun testTc3_V30RejectsTestDependencies() {
        myFixture.configureByText(
            "foo-1.0-1.rockspec",
            """
            package = "foo"
            version = "1.0-1"
            source = { url = "git://x" }
            build = { type = "builtin" }
            <warning descr="Property 'test_dependencies' is not allowed">test_dependencies</warning> = {}
            """.trimIndent(),
        )
        myFixture.checkHighlighting(true, false, true)
    }

    fun testTc4_V31AllowsTestDependencies() {
        val warnings = warningsFor(
            "foo-1.0-1.rockspec",
            """
            rockspec_format = "3.1"
            package = "foo"
            version = "1.0-1"
            source = { url = "git://x" }
            test_dependencies = {}
            """.trimIndent(),
        )
        assertFalse(
            "v3.1 must permit test_dependencies, was: $warnings",
            warnings.any { it.contains("test_dependencies") },
        )
    }

    fun testTc5_V31RejectsUnknownKey() {
        // The v3.1 schema's `version` pattern is a literal Lua pattern ('^[%w.]+-[%d]+$'), which the
        // JSON-Schema engine treats as a regex, so any conventional version string also warns; the
        // assertion of interest is the unknown-key rejection.
        myFixture.configureByText(
            "foo-1.0-1.rockspec",
            """
            rockspec_format = "3.1"
            package = "foo"
            version = <warning descr="String violates the pattern: '^[%w.]+-[%d]+$'">"1.0-1"</warning>
            source = { url = "git://x" }
            <warning descr="Property 'invalid_key' is not allowed">invalid_key</warning> = 1
            """.trimIndent(),
        )
        myFixture.checkHighlighting(true, false, true)
    }

    fun testTc6_TopLevelKeyCompletion() {
        myFixture.configureByText(
            "foo-1.0-1.rockspec",
            "<caret>\n",
        )
        val variants = myFixture.completeBasic().map { it.lookupString }
        assertContainsElements(variants, "package", "version", "source", "description")
    }
}

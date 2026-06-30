package net.internetisalie.lunar.lang.schema.providers

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import net.internetisalie.lunar.lang.schema.LuaSchemaProviderFactory

/**
 * SCHEMA-02 schema-selection tests: the `rockspec_format` global picks v3.0 (default) vs v3.1
 * (requirements TC #3/#4 selection layer). The rockspec providers are contributed via plugin.xml's
 * `schemaFileProvider` EP; registering the real Lunar factory through the platform provider-factory
 * EP forces JsonSchemaService to (re)discover and resolve them in the light fixture.
 */
class RockspecSchemaProviderTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        JsonSchemaProviderFactory.EP_NAME.point.registerExtension(LuaSchemaProviderFactory(), testRootDisposable)
    }

    private fun schemaNameFor(text: String): String? {
        val file = myFixture.configureByText("foo-1.0-1.rockspec", text).virtualFile
        val service = JsonSchemaService.Impl.get(myFixture.project)
        return service.getSchemaFilesForFile(file).firstOrNull()?.name
    }

    fun testDefaultsToV30WhenFormatAbsent() {
        val name = schemaNameFor("package = \"foo\"\nversion = \"1.0-1\"\n")
        assertEquals("rockspec-schema-v30.json", name)
    }

    fun testSelectsV30WhenFormatIs30() {
        val name = schemaNameFor("rockspec_format = \"3.0\"\npackage = \"foo\"\nversion = \"1.0-1\"\n")
        assertEquals("rockspec-schema-v30.json", name)
    }

    fun testSelectsV31WhenFormatIs31() {
        val name = schemaNameFor("rockspec_format = \"3.1\"\npackage = \"foo\"\nversion = \"1.0-1\"\n")
        assertEquals("rockspec-schema-v31.json", name)
    }
}

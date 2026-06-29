package net.internetisalie.lunar.lang.schema

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import net.internetisalie.lunar.lang.LuaFileType

class LuaJsonSchemaCompletionTest : BasePlatformTestCase() {

    class TestSchemaProvider : LuaSchemaFileProvider() {
        override fun isAvailable(file: VirtualFile): Boolean {
            return file.extension == "testcfg"
        }

        override fun getName(): String = "Test Lua Schema"

        override fun getSchemaFile(): VirtualFile? {
            return JsonSchemaProviderFactory.getResourceFile(
                TestSchemaProvider::class.java,
                "/schema/test-config.schema.json"
            )
        }
    }

    class TestSchemaProviderFactory : JsonSchemaProviderFactory {
        override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
            return listOf(TestSchemaProvider())
        }
    }

    override fun setUp() {
        super.setUp()
        val fileTypeManager = FileTypeManager.getInstance()
        WriteAction.run<RuntimeException> {
            fileTypeManager.associateExtension(LuaFileType, "testcfg")
        }

        ExtensionTestUtil.maskExtensions(
            JsonSchemaProviderFactory.EP_NAME,
            listOf(TestSchemaProviderFactory()),
            testRootDisposable
        )
    }

    override fun tearDown() {
        try {
            val fileTypeManager = FileTypeManager.getInstance()
            WriteAction.run<RuntimeException> {
                fileTypeManager.removeAssociatedExtension(LuaFileType, "testcfg")
            }
        } finally {
            super.tearDown()
        }
    }

    fun testTc1_FindsSchema() {
        val file = myFixture.configureByText("test.testcfg", "name = \"x\"\n").virtualFile
        val service = com.jetbrains.jsonSchema.ide.JsonSchemaService.Impl.get(myFixture.project)
        val schemaFile = service.getSchemaFilesForFile(file).firstOrNull()
        assertNotNull("Should find a schema for test.testcfg", schemaFile)
        assertEquals("test-config.schema.json", schemaFile?.name)
    }

    fun testTc2_CompletionKeys() {
        // TC #2
        myFixture.configureByText("test.testcfg", """
            name = "x"
            <caret>
        """.trimIndent())
        
        val variants = myFixture.completeBasic().map { it.lookupString }
        assertContainsElements(variants, "opts", "tags")
    }

    fun testTc3_CompletionValues() {
        // TC #3
        myFixture.configureByText("test.testcfg", """
            name = "x"
            opts = { level = "<caret>" }
        """.trimIndent())
        
        val variants = myFixture.completeBasic().map { it.lookupString }
        assertContainsElements(variants, "low", "high")
    }
}

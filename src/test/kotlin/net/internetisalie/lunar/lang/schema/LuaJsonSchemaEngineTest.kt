package net.internetisalie.lunar.lang.schema

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import com.intellij.openapi.extensions.LoadingOrder

@RunWith(JUnit4::class)
class LuaJsonSchemaEngineTest : BasePlatformTestCase() {

    class TestSchemaProvider : LuaSchemaFileProvider() {
        override fun isAvailable(file: VirtualFile): Boolean {
            return file.extension == "testcfg" || file.extension == "testret"
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
            fileTypeManager.associateExtension(LuaFileType, "testret")
        }

        ExtensionTestUtil.maskExtensions(
            JsonSchemaProviderFactory.EP_NAME,
            listOf(TestSchemaProviderFactory()),
            testRootDisposable
        )
        
        myFixture.enableInspections(LuaJsonSchemaComplianceInspection())
    }

    override fun tearDown() {
        val fileTypeManager = FileTypeManager.getInstance()
        WriteAction.run<RuntimeException> {
            fileTypeManager.removeAssociatedExtension(LuaFileType, "testcfg")
            fileTypeManager.removeAssociatedExtension(LuaFileType, "testret")
        }
        super.tearDown()
    }

    @Test
    fun testShapeA_AdditionalProperties() {
        // TC #1
        myFixture.configureByText("test.testcfg", """
            name = "x"
            <warning descr="Property 'bogus' is not allowed">bogus</warning> = 1
        """.trimIndent())
        
        myFixture.checkHighlighting(true, false, true)
    }

    @Test
    fun testShapeA_Enum() {
        // TC #2
        myFixture.configureByText("test.testcfg", """
            name = "x"
            opts = { level = <warning descr="Value should be one of: \"low\", \"high\"">"mid"</warning> }
        """.trimIndent())
        
        myFixture.checkHighlighting(true, false, true)
    }

    @Test
    fun testShapeA_ArrayPass() {
        // TC #3
        myFixture.configureByText("test.testcfg", """
            name = "x"
            tags = { "a", "b" }
        """.trimIndent())
        
        myFixture.checkHighlighting(true, false, true)
    }

    @Test
    fun testShapeA_ArrayTypeMismatch() {
        // TC #4
        myFixture.configureByText("test.testcfg", """
            name = "x"
            tags = <warning descr="Incompatible types.___ Required: array. Actual: string.">"a"</warning>
        """.trimIndent().replace("___", "\n"))
        
        myFixture.checkHighlighting(true, false, true)
    }

    @Test
    fun testShapeB_ReturnTable() {
        // TC #5
        myFixture.configureByText("test.testret", """
            return {
                name = "x",
                <warning descr="Property 'bogus' is not allowed">bogus</warning> = 1
            }
        """.trimIndent())
        
        myFixture.checkHighlighting(true, false, true)
    }

    @Test
    fun testShape_PlainLuaSafe() {
        // TC #6
        myFixture.configureByText("test.lua", """
            name = 1
            bogus = 2
        """.trimIndent())
        
        myFixture.checkHighlighting(true, false, true)
    }
}

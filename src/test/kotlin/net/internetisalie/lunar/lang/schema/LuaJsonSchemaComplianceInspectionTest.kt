package net.internetisalie.lunar.lang.schema

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaJsonSchemaComplianceInspectionTest : BasePlatformTestCase() {
    
    override fun setUp() {
        super.setUp()
        System.setProperty("lunar.test.schema.enabled", "true")
        
        com.jetbrains.jsonSchema.extension.JsonSchemaEnabler.EXTENSION_POINT_NAME.point.registerExtension(LuaJsonSchemaEnabler(), testRootDisposable)
        com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory.EP_NAME.point.registerExtension(LuaSchemaProviderFactory(), testRootDisposable)
        com.jetbrains.jsonSchema.extension.JsonLikePsiWalkerFactory.EXTENSION_POINT_NAME.point.registerExtension(LuaJsonLikePsiWalkerFactory(), testRootDisposable)
        
        myFixture.enableInspections(LuaJsonSchemaComplianceInspection())
    }

    override fun tearDown() {
        System.clearProperty("lunar.test.schema.enabled")
        super.tearDown()
    }

    @Test
    fun testComplianceWarning() {
        myFixture.configureByText("test.lua", """
            a = <warning descr="Incompatible types.___ Required: integer. Actual: string.">"hello"</warning>
        """.trimIndent().replace("___", "\n"))
        
        myFixture.checkHighlighting(true, false, true)
    }

    @Test
    fun testCompliancePass() {
        myFixture.configureByText("test.lua", """
            a = 42
        """.trimIndent())
        myFixture.checkHighlighting(true, false, true)
    }
}

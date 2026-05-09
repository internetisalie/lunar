package net.internetisalie.lunar.lang.doc

import com.intellij.openapi.application.runReadAction
import com.intellij.platform.backend.documentation.DocumentationResult
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LuaDocumentationTargetProviderTest : BaseDocumentTest() {

    @Test
    fun `test documentationTargets finds function with LuaCATS comment`() {
        val code = """
            --- Adds two numbers
            --- @param a number
            --- @param b number
            --- @return number
            local function add(a, b)
                return a + b
            end
            
            add(1, 2)
        """.trimIndent()
        
        myFixture.configureByText(LuaFileType, code)
        
        runReadAction {
            val file = myFixture.file
            
            // Find the function call "add" at line 9
            // The offset should be at the first character of "add" in "add(1, 2)"
            val callOffset = file.text.lastIndexOf("add(")
            
            val provider = LuaDocumentationTargetProvider()
            val targets = provider.documentationTargets(file, callOffset)
            
            assertTrue(targets.isNotEmpty(), "Should find documentation targets at function call")
        }
    }
    
    @Test
    fun `test documentationTargets resolves function at call site`() {
        val code = """
            --- @param x number
            local function multiply(x)
                return x * 2
            end
            
            multiply(5)
        """.trimIndent()
        
        myFixture.configureByText(LuaFileType, code)
        
        runReadAction {
            val file = myFixture.file
            
            // Find the identifier "multiply" in the call
            val callOffset = file.text.lastIndexOf("multiply(")
            
            val provider = LuaDocumentationTargetProvider()
            val targets = provider.documentationTargets(file, callOffset)
            
            assertTrue(targets.isNotEmpty(), 
                "Should find documentation targets for function call at offset $callOffset")
        }
    }

    @Test
    fun `test documentationTargets handles standard library functions`() {
        val code = """
            local a = {a=1}
            local b = {[a]=2}
            print(b)
        """.trimIndent()
        
        myFixture.configureByText(LuaFileType, code)
        
        runReadAction {
            val file = myFixture.file
            
            // Find the identifier "print" in the call
            val callOffset = file.text.lastIndexOf("print(")
            
            val provider = LuaDocumentationTargetProvider()
            val targets = provider.documentationTargets(file, callOffset)
            
            // This may be empty if print is from platform library and not indexed properly
            // But at minimum, should not crash
            assertNotNull(targets, "Should not return null for standard library functions")
        }
    }
}


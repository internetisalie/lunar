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
    fun `test documentationTargets resolves types in same file`() {
        val code = """
            --- @class MyType
            local MyType = {}

            --- @param x MyType
            local function test(x) end
        """.trimIndent()

        myFixture.configureByText(LuaFileType, code)
        
        runReadAction {
            val file = myFixture.file
            val offset = file.text.lastIndexOf("MyType")
            
            val provider = LuaDocumentationTargetProvider()
            val targets = provider.documentationTargets(file, offset)
            
            assertTrue(targets.isNotEmpty(), "Should find documentation targets for 'MyType'")
        }
    }

    @Test
    fun `test documentationTargets resolves global functions in same file`() {
        val code = """
            --- Prints the arguments
            function my_print(...) end

            my_print(1)
        """.trimIndent()

        myFixture.configureByText(LuaFileType, code)
        
        runReadAction {
            val file = myFixture.file
            val offset = file.text.lastIndexOf("my_print")
            
            val provider = LuaDocumentationTargetProvider()
            val targets = provider.documentationTargets(file, offset)
            
            assertTrue(targets.isNotEmpty(), "Should find documentation targets for 'my_print'")
        }
    }
}


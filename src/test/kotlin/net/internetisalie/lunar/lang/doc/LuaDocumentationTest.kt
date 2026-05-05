package net.internetisalie.lunar.lang.doc

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.doc.buildTypeLink
import net.internetisalie.lunar.luacats.lang.doc.LuaCatsDocumentationRenderer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LuaDocumentationTest : BaseDocumentTest() {

    // Priority 1: Test 1 - buildTypeLink for user-defined types
    @Test
    fun `test buildTypeLink generates psi_element link for user types`() {
        val html = buildTypeLink("MyClass")
        
        // Should contain hyperlink with psi_element:// protocol
        assertTrue(html.contains("psi_element://MyClass"), 
            "Expected psi_element:// URL in: $html")
        assertTrue(html.contains("<a href="), 
            "Expected anchor tag in: $html")
        assertTrue(html.contains("MyClass"), 
            "Expected type name in link text: $html")
    }

    // Priority 1: Test 2 - buildTypeLink for primitive types
    @Test
    fun `test buildTypeLink does not link primitive types`() {
        val primitives = listOf("string", "number", "boolean", "nil", "any", "void", "unknown")
        
        for (primitive in primitives) {
            val html = buildTypeLink(primitive)
            
            // Should be colored text, not a link
            assertTrue(html.contains("<font") || html.contains("<b>"), 
                "Expected formatted text for primitive '$primitive': $html")
            assertFalse(html.contains("<a href="), 
                "Should not be a link for primitive '$primitive': $html")
            assertTrue(html.contains(primitive), 
                "Expected type name in output for '$primitive': $html")
        }
    }

    // Priority 1: Test 3 - Link handler resolves class in same file
    @Test
    fun `test link handler resolves class in same file`() {
        val code = """
            ---@class Animal
            ---@field name string
            local Animal = {}
        """.trimIndent()
        
        myFixture.configureByText(LuaFileType, code)
        
        runReadAction {
            val file = myFixture.file
            
            // Find the local variable declaration
            val localVarDecl = PsiTreeUtil.findChildOfType(
                file, 
                LuaLocalVarDecl::class.java
            )
            assertNotNull(localVarDecl, "Should find LuaLocalVarDecl")
            
            // Should have LuaCATS comment
            val catsComment = localVarDecl!!.catsComment
            assertNotNull(catsComment, "Should have LuaCATS comment for @class annotation")
            
            // Create documentation target
            val target = LuaCatsDocumentationTarget(localVarDecl)
            
            // Create link handler
            val linkHandler = LuaDocumentationLinkHandler()
            
            // Test resolving psi_element URL
            val result = linkHandler.resolveLink(target, "psi_element://Animal")
            
            assertNotNull(result, "Link handler should resolve psi_element://Animal")
        }
    }

    // Priority 1: Test 4 - Class recognition
    @Test
    fun `test class tag is recognized on local var`() {
        val code = """
            ---@class Person
            local Person = {}
        """.trimIndent()
        
        myFixture.configureByText(LuaFileType, code)
        
        runReadAction {
            val file = myFixture.file
            
            // Find the local variable declaration
            val localVarDecl = PsiTreeUtil.findChildOfType(
                file, 
                LuaLocalVarDecl::class.java
            )
            assertNotNull(localVarDecl, "Should find LuaLocalVarDecl")
            
            val catsComment = localVarDecl!!.catsComment
            assertNotNull(catsComment, "Should have LuaCATS comment")
            
            // Verify we have class tags
            val classTagList = catsComment!!.classTagList
            assertTrue(classTagList.isNotEmpty(), "Should have @class tag")
            
            val classTag = classTagList.first()
            assertNotNull(classTag.argType, "Class tag should have argType")
            assertEquals("Person", classTag.argType?.text, "Class name should be Person")
        }
    }

    // Priority 1: Test 5 - Class inheritance recognition
    @Test
    fun `test class inheritance tag is recognized`() {
        val code = """
            ---@class Dog : Animal
            local Dog = {}
        """.trimIndent()
        
        myFixture.configureByText(LuaFileType, code)
        
        runReadAction {
            val file = myFixture.file
            
            // Find the Dog class declaration
            val localVarDecl = PsiTreeUtil.findChildOfType(
                file, 
                LuaLocalVarDecl::class.java
            )
            assertNotNull(localVarDecl, "Should find LuaLocalVarDecl for Dog")
            
            val catsComment = localVarDecl!!.catsComment
            assertNotNull(catsComment, "Should have LuaCATS comment")
            
            // Verify we have class tags
            val classTagList = catsComment!!.classTagList
            assertTrue(classTagList.isNotEmpty(), "Should have @class tag")
            
            val classTag = classTagList.first()
            assertNotNull(classTag.argType, "Class tag should have argType")
            assertEquals("Dog", classTag.argType?.text, "Class name should be Dog")
            
            // Verify parent types
            assertNotNull(classTag.parentTypes, "Should have parent types")
            assertEquals("Animal", classTag.parentTypes?.text, "Parent should be Animal")
        }
    }
}

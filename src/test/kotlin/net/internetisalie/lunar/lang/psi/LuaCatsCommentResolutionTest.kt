package net.internetisalie.lunar.lang.psi

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaCatsCommentResolutionTest : BasePlatformTestCase() {

    @Test
    fun testTypeCommentResolvedForLocalVar() {
        val file = myFixture.configureByText("test.lua", """
            ---@type string
            local s = ""
        """.trimIndent())
        runReadAction {
            val localVar = PsiTreeUtil.findChildOfType(file, LuaLocalVarDecl::class.java)
            assertNotNull("Local variable declaration not found", localVar)
            val comment = LuaPsiImplUtil.getCatsComment(localVar)
            assertNotNull("Cats comment should be found", comment)
            val typeTag = comment?.getTypeTagList()?.firstOrNull()
            assertNotNull("Type tag should be found", typeTag)
            assertEquals("string", typeTag?.argType?.text)
        }
    }

    @Test
    fun testCatsCommentResolvedForFuncDecl() {
        val file = myFixture.configureByText("test.lua", """
            ---@return string
            function f() end
        """.trimIndent())
        runReadAction {
            val funcDecl = PsiTreeUtil.findChildOfType(file, LuaFuncDecl::class.java)
            assertNotNull("Function declaration not found", funcDecl)
            val comment = LuaPsiImplUtil.getCatsComment(funcDecl)
            assertNotNull("Cats comment should be found", comment)
            val returnTag = comment?.getReturnTagList()?.firstOrNull()
            assertNotNull("Return tag should be found", returnTag)
            assertEquals("string", returnTag?.returnTypeDescriptorList?.firstOrNull()?.argType?.text)
        }
    }

    @Test
    fun testNearestPrecedingCommentIsChosen() {
        val file = myFixture.configureByText("test.lua", """
            ---@class Animal
            
            -- some regular comment
            local Animal = {}
        """.trimIndent())
        runReadAction {
            val localVar = PsiTreeUtil.findChildOfType(file, LuaLocalVarDecl::class.java)
            assertNotNull("Local variable declaration not found", localVar)
            val comment = LuaPsiImplUtil.getCatsComment(localVar)
            assertNotNull("Cats comment should be found", comment)
            val classTag = comment?.getClassTagList()?.firstOrNull()
            assertNotNull("Class tag should be found", classTag)
            assertEquals("Animal", classTag?.argType?.text)
        }
    }

    @Test
    fun testInterveningStatementBreaksAssociation() {
        val file = myFixture.configureByText("test.lua", """
            ---@type string
            local a = 1
            local b = 2
        """.trimIndent())
        runReadAction {
            val varDecls = PsiTreeUtil.findChildrenOfType(file, LuaLocalVarDecl::class.java).toList()
            assertEquals(2, varDecls.size)
            val secondVar = varDecls[1]
            val comment = LuaPsiImplUtil.getCatsComment(secondVar)
            assertNull("Cats comment should not be found for b because a is intervening", comment)
        }
    }
}

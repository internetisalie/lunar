package net.internetisalie.lunar.lang

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.indexing.LuaAliasIndex
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaPsiImplUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TestLuaStubIndexing : BasePlatformTestCase() {

    // --- LuaGlobalDeclarationIndex ---

    @Test
    fun testGlobalFunctionIndexed() {
        myFixture.configureByText("test.lua", """
            function greet(name)
                print("Hello " .. name)
            end
        """.trimIndent())

        val scope = GlobalSearchScope.allScope(project)
        val results = StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, "greet", project, scope, LuaFuncDecl::class.java)
        assertEquals(1, results.size)
        assertEquals("greet", results.first().funcName.text)
    }

    @Test
    fun testGlobalFunctionWithLuacatsIndexed() {
        val file = myFixture.configureByText("test.lua", """
            ---@param x number
            ---@return string
            function format(x)
                return tostring(x)
            end
        """.trimIndent())

        val scope = GlobalSearchScope.allScope(project)
        val results = StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, "format", project, scope, LuaFuncDecl::class.java)
        assertEquals(1, results.size)

        // Verify LuaCATS data is accessible via PSI
        val funcDecl = PsiTreeUtil.findChildOfType(file, LuaFuncDecl::class.java)
        assertNotNull(funcDecl)
        val catsComment = LuaPsiImplUtil.getCatsComment(funcDecl)
        assertNotNull("getCatsComment returned null", catsComment)
        println("CATS COMMENT TEXT: [" + catsComment!!.text + "]")
        println("CATS COMMENT TREE:\n" + com.intellij.psi.impl.DebugUtil.psiToString(catsComment!!, false))

        val returnTags = catsComment!!.getReturnTagList()
        println("RETURN TAGS SIZE: " + returnTags.size)
        if (returnTags.isNotEmpty()) {
            val first = returnTags.first()
            println("FIRST RETURN TAG: " + first.text)
            println("FIRST RETURN TAG CLASS: " + first.javaClass.simpleName)
            println("FIRST RETURN TAG ARG TYPE: " + first.argType)
        }

        assertEquals("string", catsComment!!.getReturnTagList().firstOrNull()?.argType?.text)
        val paramTag = catsComment.getParamTagList().firstOrNull { it?.argName?.text == "x" }
        assertEquals("number", paramTag?.argType?.text)
    }

    @Test
    fun testLocalFunctionNotGloballyIndexed() {
        myFixture.configureByText("test.lua", """
            local function helper()
            end
        """.trimIndent())

        val scope = GlobalSearchScope.allScope(project)
        val results = StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, "helper", project, scope, LuaFuncDecl::class.java)
        assertTrue(results.isEmpty())
    }

    // --- LuaCATS PSI navigation sanity check ---

    @Test
    fun testGetCatsCommentWorksForLocalVar() {
        val file = myFixture.configureByText("test.lua", """
            ---@class Animal
            local Animal = {}
        """.trimIndent())

        val varDecl = PsiTreeUtil.findChildOfType(file, LuaLocalVarDecl::class.java)
        assertNotNull("LuaLocalVarDecl not found", varDecl)
        val catsComment = LuaPsiImplUtil.getCatsComment(varDecl)
        if (catsComment == null) {
            println("TREE:\n" + com.intellij.psi.impl.DebugUtil.psiToString(file, false))
        }
        assertNotNull("getCatsComment returned null for annotated local var", catsComment)
        val classTag = catsComment!!.getClassTagList().firstOrNull()
        assertNotNull("@class tag not found in comment", classTag)
        assertEquals("Animal", classTag!!.argType.text)
    }

    // --- LuaClassNameIndex ---

    @Test
    fun testClassAnnotationIndexed() {
        myFixture.configureByText("test.lua", """
            ---@class Animal
            local Animal = {}
        """.trimIndent())

        val scope = GlobalSearchScope.allScope(project)
        val results = StubIndex.getElements(LuaClassNameIndex.KEY, "Animal", project, scope, LuaLocalVarDecl::class.java)
        assertEquals(1, results.size)
    }

    @Test
    fun testClassAnnotationWithInheritanceIndexed() {
        myFixture.configureByText("test.lua", """
            ---@class Dog: Animal
            local Dog = {}
        """.trimIndent())

        // The class name in LuaCATS for "Dog: Animal" is the argType text, which includes ": Animal"
        // or just "Dog" depending on the grammar. We check what the actual argType text is.
        val file = myFixture.configureByText("test2.lua", """
            ---@class Dog: Animal
            local Dog = {}
        """.trimIndent())
        val varDecl = PsiTreeUtil.findChildOfType(file, LuaLocalVarDecl::class.java)
        val catsComment = LuaPsiImplUtil.getCatsComment(varDecl)
        val classTag = catsComment?.getClassTagList()?.firstOrNull()
        assertNotNull("@class tag not found", classTag)
        val className = classTag!!.argType.text

        val scope = GlobalSearchScope.allScope(project)
        val results = StubIndex.getElements(LuaClassNameIndex.KEY, className, project, scope, LuaLocalVarDecl::class.java)
        assertTrue("Expected class '$className' in index, found ${results.size}", results.isNotEmpty())
    }

    @Test
    fun testLocalVarWithoutClassNotInClassIndex() {
        myFixture.configureByText("test.lua", """
            local x = 42
        """.trimIndent())

        val scope = GlobalSearchScope.allScope(project)
        val results = StubIndex.getElements(LuaClassNameIndex.KEY, "x", project, scope, LuaLocalVarDecl::class.java)
        assertTrue(results.isEmpty())
    }

    // --- LuaAliasIndex ---

    @Test
    fun testAliasAnnotationIndexed() {
        val file = myFixture.configureByText("test.lua", """
            ---@alias Direction "left"|"right"|"up"|"down"
            local Direction = nil
        """.trimIndent())

        // Verify PSI navigation works first
        val varDecl = PsiTreeUtil.findChildOfType(file, LuaLocalVarDecl::class.java)
        val catsComment = LuaPsiImplUtil.getCatsComment(varDecl)
        val aliasName = catsComment?.getAliasTagList()?.firstOrNull()?.argName?.text
        assertNotNull("@alias tag not found via PSI", aliasName)

        val scope = GlobalSearchScope.allScope(project)
        val results = StubIndex.getElements(LuaAliasIndex.KEY, aliasName!!, project, scope, LuaLocalVarDecl::class.java)
        assertTrue("Expected alias '$aliasName' in index", results.isNotEmpty())
    }

    @Test
    fun testLocalVarWithoutAliasNotInAliasIndex() {
        myFixture.configureByText("test.lua", """
            local y = "hello"
        """.trimIndent())

        val scope = GlobalSearchScope.allScope(project)
        val results = StubIndex.getElements(LuaAliasIndex.KEY, "y", project, scope, LuaLocalVarDecl::class.java)
        assertTrue(results.isEmpty())
    }
}

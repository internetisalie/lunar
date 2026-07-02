package net.internetisalie.lunar.lang.indexing

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaGlobalDottedIndexTest : BasePlatformTestCase() {

    @Test
    fun testDottedFunctionIndexesFullKey() {
        myFixture.configureByText("t.lua", "function cjson.decode() end")
        val scope = GlobalSearchScope.allScope(project)
        val results = StubIndex.getElements(
            LuaGlobalDeclarationIndex.KEY, "cjson.decode", project, scope, LuaFuncDecl::class.java,
        )
        assertEquals("Full dotted key 'cjson.decode' should resolve one decl", 1, results.size)
    }

    @Test
    fun testDottedFunctionIndexesBaseKey() {
        myFixture.configureByText("t.lua", "function cjson.decode() end")
        val scope = GlobalSearchScope.allScope(project)
        val results = StubIndex.getElements(
            LuaGlobalDeclarationIndex.KEY, "cjson", project, scope, LuaFuncDecl::class.java,
        )
        assertEquals("Base key 'cjson' should also be indexed", 1, results.size)
    }
}

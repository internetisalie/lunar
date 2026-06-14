package net.internetisalie.lunar.lang.insight

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Cross-file Find Usages tests requiring the stub index.
 *
 * TC-NAV-02-02: a global function declared in one file must be found as a
 * usage-site in a second file via [net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex].
 */
@RunWith(JUnit4::class)
class LuaFindUsagesCrossFileTest : IndexedBasePlatformTestCase() {

    /**
     * TC-NAV-02-02: cross-file global function usages via stub index.
     *
     * a.lua  declares  `function Helper() end`
     * b.lua  calls     `Helper()`
     *
     * Find Usages on the `Helper` declaration in a.lua must return 1 usage
     * (the call in b.lua).
     */
    @Test
    fun testCrossFileGlobalFunctionUsage() {
        val aFile = myFixture.addFileToProject("a.lua", "function Helper() end")
        myFixture.addFileToProject("b.lua", "Helper()")
        myFixture.configureFromExistingVirtualFile(aFile.virtualFile)

        val funcDecl = PsiTreeUtil.findChildOfType(myFixture.file, LuaFuncDecl::class.java)
        assertNotNull("Expected a LuaFuncDecl for 'Helper' in a.lua", funcDecl)
        val declIdentifier = funcDecl!!.funcName.nameRef.identifier

        // Cross-file search needs project scope; the global function resolves via the stub index.
        val usages = ReferencesSearch.search(declIdentifier, GlobalSearchScope.allScope(project)).findAll()
        assertEquals("Expected 1 cross-file usage of 'Helper'", 1, usages.size)

        val usageFile = usages.first().element.containingFile?.name
        assertEquals("Usage should be in b.lua", "b.lua", usageFile)
    }
}

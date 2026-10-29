package net.internetisalie.lunar.analysis

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-554. A method's `---@param b? string` is optional in its declared signature, so `c:m("x")`
 * must not report *Too few arguments*. The method's type is built by `funcTypeFromStub`, which has
 * an AST arm (the method is in the open file) and a stub arm (it is in another file); each case
 * runs on both, and each arm asserts the branch it took before asserting any hit.
 */
@RunWith(JUnit4::class)
class LuaOptionalMethodParamArityTest : IndexedBasePlatformTestCase() {
    private val methodDecl =
        """
        ---@class C
        local C = {}
        ---@param a string
        ---@param b? string
        function C:m(a, b) end
        """.trimIndent()

    private val freeDecl =
        """
        ---@param a string
        ---@param b? string
        function f(a, b) end
        """.trimIndent()

    private fun arityHits(text: String): List<String> {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText("t.lua", text)
        return myFixture
            .doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Too few arguments") || it.startsWith("Too many arguments") }
            .distinct()
    }

    private fun methodStubOrNull() =
        runReadAction {
            StubIndex
                .getElements(
                    LuaGlobalDeclarationIndex.KEY,
                    "C:m",
                    project,
                    GlobalSearchScope.allScope(project),
                    LuaFuncDecl::class.java,
                ).single()
                .stub
        }

    private fun astArmHits(call: String): List<String> {
        val hits = arityHits("$methodDecl\n---@type C\nlocal c = C\n$call\n")
        val decl = runReadAction { PsiTreeUtil.findChildrenOfType(myFixture.file, LuaFuncDecl::class.java).single() }
        assertNull("AST arm: the in-file method must be AST-backed", runReadAction { decl.stub })
        return hits
    }

    /**
     * Measured: after highlighting, `defs.lua`'s method is no longer stub-backed, so the branch can
     * only be asserted before it. A stub codec that drops the marker still turns this arm red, so
     * the signature is read from the stub. `LuaCatsStubAstParityTest` TC-11 asserts the branch.
     */
    private fun stubArmHits(call: String): List<String> {
        myFixture.addFileToProject("defs.lua", methodDecl)
        assertNotNull("STUB arm: defs.lua's method must be stub-backed before highlighting", methodStubOrNull())
        return arityHits("---@type C\nlocal c\n$call\n")
    }

    @Test
    fun testOmittedOptionalMethodParamIsSilentInFile() = assertEquals(emptyList<String>(), astArmHits("c:m(\"x\")"))

    @Test
    fun testOmittedOptionalMethodParamIsSilentCrossFile() = assertEquals(emptyList<String>(), stubArmHits("c:m(\"x\")"))

    @Test
    fun testTooFewRequiredMethodArgsIsReportedInFile() =
        assertEquals(listOf("Too few arguments: expected at least 1, got 0"), astArmHits("c:m()"))

    @Test
    fun testTooFewRequiredMethodArgsIsReportedCrossFile() =
        assertEquals(listOf("Too few arguments: expected at least 1, got 0"), stubArmHits("c:m()"))

    @Test
    fun testTooManyMethodArgsIsReportedInFile() =
        assertEquals(listOf("Too many arguments: expected at most 2, got 3"), astArmHits("c:m(\"x\", \"y\", \"z\")"))

    @Test
    fun testTooManyMethodArgsIsReportedCrossFile() =
        assertEquals(listOf("Too many arguments: expected at most 2, got 3"), stubArmHits("c:m(\"x\", \"y\", \"z\")"))

    @Test
    fun testFullMethodCallIsSilentInFile() = assertEquals(emptyList<String>(), astArmHits("c:m(\"x\", \"y\")"))

    @Test
    fun testFullMethodCallIsSilentCrossFile() = assertEquals(emptyList<String>(), stubArmHits("c:m(\"x\", \"y\")"))

    @Test
    fun testOmittedOptionalFreeParamIsSilentInFile() =
        assertEquals(emptyList<String>(), arityHits("$freeDecl\nf(\"x\")\n"))

    @Test
    fun testOmittedOptionalFreeParamIsSilentCrossFile() {
        myFixture.addFileToProject("defs.lua", freeDecl)
        assertEquals(emptyList<String>(), arityHits("f(\"x\")\n"))
    }
}

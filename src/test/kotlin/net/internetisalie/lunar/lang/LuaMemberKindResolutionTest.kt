package net.internetisalie.lunar.lang

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.analysis.inspections.LuaUnusedLocalInspection
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * MAINT-53 Phase 2 (BUG-490, BUG-512): a member name or a function-name segment is a table key, never
 * a variable, so no bare-name lookup, lexical binding or file binding can be its target.
 *
 * The cross-file cases need no `require`: a bare global function is reached through the global
 * declaration index from any file.
 */
@RunWith(JUnit4::class)
class LuaMemberKindResolutionTest : IndexedBasePlatformTestCase() {
    /**
     * TC-20 — a property function-name segment is not a usage of a same-named global.
     *
     * **Mutation**: remove `LuaFuncNameProperty` from `namesATableKey`; the global `g` gains `[41]`.
     */
    @Test
    fun aPropertyFunctionNameSegmentIsNotAUsageOfAGlobal() {
        myFixture.configureByText("test.lua", "function g() end\nlocal t = {}\nfunction t.g() end\nt.g()\n")

        assertEquals("the property's own name is not a usage", emptyList<Int>(), usageOffsetsAt(9))
        assertEquals("the call site is a usage of the property", listOf(51), usageOffsetsAt(41))
    }

    /**
     * TC-05 — an index expression member does not reach a bare global function.
     *
     * **Mutation**: remove `LuaIndexExpr` from `namesATableKey`; `legacy` resolves to `legacy_lib.lua`.
     */
    @Test
    fun aMemberOfAnIndexedReceiverDoesNotResolveToABareGlobalFunction() {
        myFixture.addFileToProject("legacy_lib.lua", "---@deprecated gone\nfunction legacy() end\n")
        val file =
            myFixture.addFileToProject(
                "user.lua",
                "local handlers = {{}}\nlocal a = handlers[1].legacy\nreturn a\n",
            )

        assertEquals(emptyList<String>(), targetsOfEvery(file, "legacy"))
    }

    /**
     * TC-06 — a member read does not bind to a same-named local function.
     *
     * **Mutation**: remove the `LuaIndexExpr` clause of `lexicalTargetOf`; the read resolves to
     * `local function timeout`'s name instead of the write `config.timeout = ms`.
     */
    @Test
    fun aMemberReadDoesNotBindToASameNamedLocalFunction() {
        val source =
            "local config = {}\n" +
                "local function timeout(ms) config.timeout = ms end\n" +
                "local function get() return config.timeout end\n"
        val file = myFixture.addFileToProject("c.lua", source)
        val read = source.lastIndexOf("timeout")

        val targets = targetsAt(file, read)

        assertEquals(
            "the write of the member, not the local function",
            listOf(source.indexOf("timeout = ms")),
            targets.map { it.textOffset },
        )
    }

    /**
     * TC-07 — a local that shares a member's name is not used by that member read.
     *
     * **Mutation**: as TC-06.
     */
    @Test
    fun aLocalSharingAMembersNameIsNotUsedByTheMember() {
        myFixture.enableInspections(LuaUnusedLocalInspection())
        myFixture.configureByText("test.lua", "local t = {}\nlocal version\nprint(t.version)\n")

        val unused = myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("Unused") }

        assertEquals(listOf("Unused local variable 'version'"), unused)
    }

    private fun usageOffsetsAt(offset: Int): List<Int> {
        val leaf = requireNotNull(myFixture.file.findElementAt(offset)) { "no leaf at $offset" }
        return ReferencesSearch
            .search(leaf, GlobalSearchScope.allScope(project))
            .findAll()
            .map { it.element.textOffset }
            .sorted()
    }

    private fun targetsOfEvery(
        file: PsiFile,
        name: String,
    ): List<String> =
        PsiTreeUtil
            .findChildrenOfType(file, LuaNameRef::class.java)
            .filter { it.text == name }
            .flatMap { targetsOf(it) }
            .map { "${it.containingFile.name}:${it.text}" }

    private fun targetsAt(
        file: PsiFile,
        offset: Int,
    ): List<PsiElement> =
        targetsOf(requireNotNull(PsiTreeUtil.getParentOfType(file.findElementAt(offset), LuaNameRef::class.java)))

    private fun targetsOf(nameRef: LuaNameRef): List<PsiElement> =
        (nameRef.reference as PsiPolyVariantReference).multiResolve(false).mapNotNull { it.element }
}

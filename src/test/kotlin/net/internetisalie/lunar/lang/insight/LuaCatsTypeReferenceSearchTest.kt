package net.internetisalie.lunar.lang.insight

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsClassTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeDeclarations
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * `REFACT-08` Phase 3: [LuaCatsTypeReferenceSearcher] and the `LuaFindUsagesProvider` clauses
 * (`design.md` §2.5, §2.10, §3.5).
 */
@RunWith(JUnit4::class)
class LuaCatsTypeReferenceSearchTest : BasePlatformTestCase() {
    private val elevenSlotUses =
        "--- @type Widget\n" +
            "--- @param p Widget\n" +
            "--- @return Widget\n" +
            "--- @class Panel : Widget\n" +
            "--- @field w Widget\n" +
            "--- @type Widget[]\n" +
            "--- @type table<string, Widget>\n" +
            "--- @alias Handle Widget|nil\n" +
            "--- @type fun(a: Widget): Widget\n" +
            "--- @cast v Widget\n" +
            "local function f(p) end\n"

    /** TC-2 — the eleven-slot fixture. */
    @Test
    fun testSearchOverTheElevenSlotFixture() {
        val typesFile = myFixture.addFileToProject("types.lua", "--- @class Widget\nlocal Widget = {}\n")
        myFixture.configureByText("uses.lua", elevenSlotUses)
        runReadAction {
            val declarationLeaf = declarationLeafOf(typesFile)
            val results = ReferencesSearch.search(declarationLeaf, GlobalSearchScope.allScope(project)).findAll()
            assertEquals(11, results.size)
            assertEquals(mapOf("NAMED_TYPE" to 10, "TYPE_PARAM" to 1), byHolder(results))
        }
    }

    /**
     * TC-21 (search half) — every use spelling moves through the searcher, including the
     * `GenericType` head of a parameterized use (`Box<string>`) alongside a plain `NamedType` use.
     * Necessary for mutation O's demonstration below, which needs a `ReferencesSearch`-backed
     * assertion for every row mutation O is required to zero out.
     */
    @Test
    fun testSearchOverAllThreeHoldersIncludingAGenericTypeUse() {
        val typesFile = myFixture.addFileToProject("types.lua", "--- @class Box\nlocal Box = {}\n")
        myFixture.configureByText(
            "uses.lua",
            "--- @type Box<string>\n--- @param p Box\nlocal function f(p) end\n",
        )
        runReadAction {
            val declarationLeaf = declarationLeafOf(typesFile)
            val results = ReferencesSearch.search(declarationLeaf, GlobalSearchScope.allScope(project)).findAll()
            assertEquals(2, results.size)
            assertEquals(mapOf("GENERIC_TYPE" to 1, "NAMED_TYPE" to 1), byHolder(results))
        }
    }

    /**
     * TC-22 (search half) — a parameterized class DECLARATION head (`params.lua`'s `Box<T>`) is
     * not a use and must not be found by the search for the unrelated bare `Box` declaration.
     */
    @Test
    fun testSearchExcludesAParameterizedDeclarationHead() {
        val typesFile = myFixture.addFileToProject("types.lua", "--- @class Box\nlocal Box = {}\n")
        myFixture.addFileToProject("params.lua", "--- @class Box<T>\n--- @field item T\nlocal Box2 = {}\n")
        myFixture.addFileToProject("uses.lua", "--- @type Box<string>\n--- @param p Box\nlocal function f(p) end\n")
        runReadAction {
            val declarationLeaf = declarationLeafOf(typesFile)
            val results = ReferencesSearch.search(declarationLeaf, GlobalSearchScope.allScope(project)).findAll()
            assertEquals(2, results.size)
            assertTrue(results.all { it.element.containingFile.name == "uses.lua" })
        }
    }

    /**
     * TC-23 (search half) — the negative control for TC-22: a generic head in a PARENT-TYPE
     * position (`---@class Panel : Box<string>`) is still a use and is still found.
     */
    @Test
    fun testSearchFindsAParentTypeGenericHead() {
        val typesFile = myFixture.addFileToProject("types.lua", "--- @class Box\nlocal Box = {}\n")
        myFixture.configureByText("panel.lua", "--- @class Panel : Box<string>\nlocal Panel = {}\n")
        runReadAction {
            val declarationLeaf = declarationLeafOf(typesFile)
            val results = ReferencesSearch.search(declarationLeaf, GlobalSearchScope.allScope(project)).findAll()
            assertEquals(1, results.size)
            assertEquals(mapOf("GENERIC_TYPE" to 1), byHolder(results))
        }
    }

    /** TC-17: `canFindUsagesFor` on a `@class` name is true and `getType` is non-empty. */
    @Test
    fun testCanFindUsagesForAndGetType() {
        val typesFile = myFixture.configureByText("types.lua", "--- @class Widget\nlocal Widget = {}\n")
        runReadAction {
            val declarationLeaf = declarationLeafOf(typesFile)
            val provider = LuaFindUsagesProvider()
            assertTrue(provider.canFindUsagesFor(declarationLeaf))
            assertEquals("type", provider.getType(declarationLeaf))
        }
    }

    /** A [LocalSearchScope] search returns only that file's uses. */
    @Test
    fun testLocalSearchScopeReturnsOnlyThatFilesUses() {
        val typesFile = myFixture.addFileToProject("types.lua", "--- @class Widget\nlocal Widget = {}\n")
        val usesFile = myFixture.addFileToProject("uses.lua", elevenSlotUses)
        myFixture.configureByText("other.lua", "--- @type Widget\nlocal w\n")
        runReadAction {
            val declarationLeaf = declarationLeafOf(typesFile)
            val results = ReferencesSearch.search(declarationLeaf, LocalSearchScope(usesFile)).findAll()
            assertEquals(11, results.size)
            assertTrue(results.all { it.element.containingFile == usesFile })
        }
    }

    private fun declarationLeafOf(file: PsiFile): PsiElement =
        requireNotNull(
            LuaCatsTypeDeclarations.classDeclarationLeaf(
                requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsClassTag::class.java)),
            ),
        )

    private fun byHolder(results: Collection<PsiReference>): Map<String, Int> =
        results
            .map { holderKindOf(it.element) }
            .groupingBy { it }
            .eachCount()

    private fun holderKindOf(holder: PsiElement): String =
        when (holder.node.elementType) {
            LuaCatsElementTypes.NAMED_TYPE -> "NAMED_TYPE"
            LuaCatsElementTypes.TYPE_PARAM -> "TYPE_PARAM"
            LuaCatsElementTypes.GENERIC_TYPE -> "GENERIC_TYPE"
            else -> "OTHER"
        }
}

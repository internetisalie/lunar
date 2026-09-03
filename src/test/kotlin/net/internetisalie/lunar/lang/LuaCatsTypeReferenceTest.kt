package net.internetisalie.lunar.lang

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsClassTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsGenericType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsNamedType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsParamTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsParameterizedName
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeDeclarations
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * `REFACT-08` Phase 2: [LuaCatsTypeReference] and [LuaCatsTypeReferenceContributor], registered
 * declaratively through `plugin.xml`'s `psi.referenceContributor` entry — unlike Phase 1's
 * `LuaCatsTypeDeclarationsTest`, which stood in a throwaway probe contributor because the real one
 * did not exist yet.
 */
@RunWith(JUnit4::class)
class LuaCatsTypeReferenceTest : BasePlatformTestCase() {
    /** TC-16: a use resolves cross-file to its declaration's NAME leaf. */
    @Test
    fun testUseResolvesToTheDeclarationInAnotherFile() {
        myFixture.addFileToProject("types.lua", "--- @class Widget\nlocal Widget = {}\n")
        val usesFile = myFixture.configureByText("uses.lua", "--- @param p Widget\nlocal function f(p) end\n")
        runReadAction {
            val namedType = requireNotNull(PsiTreeUtil.findChildOfType(usesFile, LuaCatsNamedType::class.java))
            val resolved = requireNotNull(namedType.reference?.resolve())
            assertEquals("Widget", resolved.text)
            assertTrue(LuaCatsTypeDeclarations.isDeclarationLeaf(resolved))
        }
    }

    /** `multiResolve` over two re-opened declarations returns both. */
    @Test
    fun testMultiResolveReturnsEveryDeclaration() {
        myFixture.addFileToProject("types.lua", "--- @class Widget\nlocal Widget = {}\n")
        myFixture.addFileToProject("more.lua", "--- @class Widget\n--- @field extra string\nlocal Widget = {}\n")
        val usesFile = myFixture.configureByText("uses.lua", "--- @type Widget\nlocal w\n")
        runReadAction {
            val namedType = requireNotNull(PsiTreeUtil.findChildOfType(usesFile, LuaCatsNamedType::class.java))
            val reference = namedType.reference as LuaCatsTypeReference
            val results = reference.multiResolve(false)
            assertEquals(2, results.size)
            assertTrue(results.all { it.element?.text == "Widget" })
        }
    }

    /** `isReferenceTo` is true for a peer declaration leaf and false for a `@param` name of the same text. */
    @Test
    fun testIsReferenceToDistinguishesADeclarationFromAUse() {
        val typesFile = myFixture.addFileToProject("types.lua", "--- @class Widget\nlocal Widget = {}\n")
        val usesFile =
            myFixture.configureByText(
                "uses.lua",
                "--- @type Widget\nlocal w\n--- @param p Widget\nlocal function f(p) end\n",
            )
        runReadAction {
            val declarationTag = requireNotNull(PsiTreeUtil.findChildOfType(typesFile, LuaCatsClassTag::class.java))
            val declarationLeaf = requireNotNull(LuaCatsTypeDeclarations.classDeclarationLeaf(declarationTag))

            val typeUse = requireNotNull(PsiTreeUtil.findChildOfType(usesFile, LuaCatsNamedType::class.java))
            val reference = typeUse.reference as LuaCatsTypeReference
            assertTrue(reference.isReferenceTo(declarationLeaf))

            val paramTag = requireNotNull(PsiTreeUtil.findChildOfType(usesFile, LuaCatsParamTag::class.java))
            val paramNameLeaf = requireNotNull(paramTag.argName).firstChild
            assertFalse(reference.isReferenceTo(paramNameLeaf))
        }
    }

    /** TC-21: the reference resolves for all three use holders. */
    @Test
    fun testReferenceResolvesForAllThreeHolders() {
        myFixture.addFileToProject("types.lua", "--- @class Widget\nlocal Widget = {}\n")
        val usesFile =
            myFixture.configureByText(
                "uses.lua",
                "--- @type Widget\n" +
                    "--- @param p table<string, Widget>\n" +
                    "--- @return Widget<string>\n" +
                    "local function f(p) end\n",
            )
        runReadAction {
            val namedType = requireNotNull(PsiTreeUtil.findChildOfType(usesFile, LuaCatsNamedType::class.java))
            assertEquals("Widget", requireNotNull(namedType.reference?.resolve()).text)

            val paramType =
                requireNotNull(PsiTreeUtil.findChildOfType(usesFile, LuaCatsParamTag::class.java)).argType
            val typeParamHolder =
                requireNotNull(
                    PsiTreeUtil
                        .findChildOfType(paramType, LuaCatsParameterizedName::class.java),
                ).typeParamList.single { it.text == "Widget" }
            assertEquals("Widget", requireNotNull(typeParamHolder.reference?.resolve()).text)

            val genericHead =
                PsiTreeUtil
                    .findChildrenOfType(usesFile, LuaCatsGenericType::class.java)
                    .single { it.text == "Widget" }
            assertEquals("Widget", requireNotNull(genericHead.reference?.resolve()).text)
        }
    }

    /** TC-22: the head of a parameterized class DECLARATION is not a use. */
    @Test
    fun testProviderReturnsNoReferenceForAParameterizedDeclarationHead() {
        val file = myFixture.configureByText("box.lua", "--- @class Box<T>\nlocal Box = {}\n")
        runReadAction {
            val head = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsGenericType::class.java))
            assertTrue(head.references.isEmpty())
            assertNull(head.reference)
        }
    }

    /** TC-23: the negative control — a generic head in a PARENT-TYPE position is still a use. */
    @Test
    fun testProviderReturnsALiveReferenceForAParentTypeGenericHead() {
        myFixture.addFileToProject("box.lua", "--- @class Box\nlocal Box = {}\n")
        val panelFile =
            myFixture.configureByText("panel.lua", "--- @class Panel : Box<string>\nlocal Panel = {}\n")
        runReadAction {
            val head = requireNotNull(PsiTreeUtil.findChildOfType(panelFile, LuaCatsGenericType::class.java))
            val reference = requireNotNull(head.reference)
            assertEquals("Box", requireNotNull(reference.resolve()).text)
        }
    }
}

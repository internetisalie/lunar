package net.internetisalie.lunar.luacats.lang.psi

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceContributorEP
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import net.internetisalie.lunar.definitions.LibraryRootTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * `REFACT-08` Phase 1: [LuaCatsTypeDeclarations] (the slot reader) and [LuaCatsBaseElement]'s
 * `getReferences()`/`getReference()` overrides (the reference host), verified independently of
 * Phase 2's actual reference/contributor classes — a throwaway [ProbeReferenceContributor] stands
 * in for [net.internetisalie.lunar.lang.LuaCatsTypeReferenceContributor] so TC-3 is executed rather
 * than read off `design.md`.
 */
@RunWith(JUnit4::class)
class LuaCatsTypeDeclarationsTest : LibraryRootTestCase() {
    // classDeclarationLeaf / aliasDeclarationLeaf

    @Test
    fun testClassDeclarationLeafForAPlainClass() {
        val file = myFixture.configureByText("plain.lua", "--- @class Widget\nlocal Widget = {}\n")
        runReadAction {
            val tag = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsClassTag::class.java))
            val leaf = requireNotNull(LuaCatsTypeDeclarations.classDeclarationLeaf(tag))
            assertEquals("Widget", leaf.text)
            assertEquals(LuaCatsElementTypes.NAME, leaf.node.elementType)
        }
    }

    /** The parent type in `@class Panel : Widget` must NOT be mistaken for the declaration leaf. */
    @Test
    fun testClassDeclarationLeafExcludesTheParentType() {
        val file = myFixture.configureByText("panel.lua", "--- @class Panel : Widget\nlocal Panel = {}\n")
        runReadAction {
            val tag = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsClassTag::class.java))
            val leaf = requireNotNull(LuaCatsTypeDeclarations.classDeclarationLeaf(tag))
            assertEquals("Panel", leaf.text)
        }
    }

    /** TC-12: a parameterized class head has no single-NAME declaration leaf. */
    @Test
    fun testClassDeclarationLeafIsNullForAParameterizedHead() {
        val file = myFixture.configureByText("box.lua", "--- @class Box<T>\nlocal Box = {}\n")
        runReadAction {
            val tag = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsClassTag::class.java))
            assertNull(LuaCatsTypeDeclarations.classDeclarationLeaf(tag))
        }
    }

    /** TC-20. */
    @Test
    fun testAliasDeclarationLeaf() {
        val file = myFixture.configureByText("handle.lua", "--- @alias Handle string\n")
        runReadAction {
            val tag = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsAliasTag::class.java))
            val leaf = requireNotNull(LuaCatsTypeDeclarations.aliasDeclarationLeaf(tag))
            assertEquals("Handle", leaf.text)
        }
    }

    // isDeclarationLeaf — every residue class DR-01 measured

    @Test
    fun testIsDeclarationLeafFalseForAParamName() {
        val file = myFixture.configureByText("param.lua", "--- @param p Widget\nlocal function f(p) end\n")
        runReadAction {
            val paramTag = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsParamTag::class.java))
            val nameLeaf = requireNotNull(paramTag.argName).firstChild
            assertFalse(LuaCatsTypeDeclarations.isDeclarationLeaf(nameLeaf))
        }
    }

    @Test
    fun testIsDeclarationLeafFalseForAFieldName() {
        val file =
            myFixture.configureByText(
                "field.lua",
                "--- @class C\n--- @field a string\nlocal C = {}\n",
            )
        runReadAction {
            val fieldTag = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsFieldTag::class.java))
            val descriptor =
                requireNotNull(
                    PsiTreeUtil.findChildOfType(fieldTag.fieldDescriptor, LuaCatsFieldNameDescriptor::class.java),
                )
            assertFalse(LuaCatsTypeDeclarations.isDeclarationLeaf(descriptor.firstChild))
        }
    }

    @Test
    fun testIsDeclarationLeafFalseForACastName() {
        val file = myFixture.configureByText("cast.lua", "--- @cast x string\nlocal x = 1\n")
        runReadAction {
            val castTag = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsCastTag::class.java))
            assertFalse(LuaCatsTypeDeclarations.isDeclarationLeaf(castTag.argName.firstChild))
        }
    }

    @Test
    fun testIsDeclarationLeafFalseForAGenericParameter() {
        val file = myFixture.configureByText("generic.lua", "--- @generic T\nlocal function id(v) return v end\n")
        runReadAction {
            val genericTag = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsGenericTag::class.java))
            val typeParamHolder =
                requireNotNull(genericTag.genericTypeParams)
                    .genericTypeParamList
                    .single()
                    .argName
                    .let { requireNotNull(PsiTreeUtil.findChildOfType(it, LuaCatsTypeParam::class.java)) }
            assertFalse(LuaCatsTypeDeclarations.isDeclarationLeaf(typeParamHolder.firstChild))
        }
    }

    // declarationLeaves

    /** TC-6's precondition: two files declaring the same class name are both found. */
    @Test
    fun testDeclarationLeavesAcrossTwoFilesReturnsBoth() {
        myFixture.configureByText("types.lua", "--- @class Widget\nlocal Widget = {}\n")
        myFixture.addFileToProject("more.lua", "--- @class Widget\n--- @field extra string\nlocal Widget = {}\n")
        runReadAction {
            val leaves =
                LuaCatsTypeDeclarations.declarationLeaves("Widget", project, GlobalSearchScope.allScope(project))
            assertEquals(2, leaves.size)
            assertTrue(leaves.all { it.text == "Widget" })
        }
    }

    /**
     * `declarationLeaves(name, …, projectScope)` over a fixture whose project file AND an attached
     * library both declare the name returns only the project's — [GlobalSearchScope.allScope]
     * returns both.
     */
    @Test
    fun testDeclarationLeavesWithProjectScopeExcludesTheLibrary() {
        myFixture.configureByText("types.lua", "--- @class Widget\nlocal Widget = {}\n")
        registerLibraryRoot(mapOf("lib.lua" to "--- @class Widget\nlocal Widget = {}\n"))
        runReadAction {
            val all = LuaCatsTypeDeclarations.declarationLeaves("Widget", project, GlobalSearchScope.allScope(project))
            val projectOnly =
                LuaCatsTypeDeclarations.declarationLeaves(
                    "Widget",
                    project,
                    GlobalSearchScope.projectScope(project),
                )
            assertEquals(2, all.size)
            assertEquals(1, projectOnly.size)
        }
    }

    // isDeclarationSlotHolder — TC-22, TC-23, TC-26, TC-27, TC-28's shapes

    @Test
    fun testIsDeclarationSlotHolderShapes() {
        val file =
            myFixture.configureByText(
                "shapes.lua",
                """
                --- @class Box<T>
                local Box = {}

                --- @generic T
                --- @param v T
                local function id(v) return v end

                --- @type Box<Widget>
                local w

                --- @param p table<string, Widget>
                local function f(p) end

                --- @class Panel : Box<Widget>
                local Panel = {}
                """.trimIndent(),
            )
        runReadAction {
            val classTags = PsiTreeUtil.findChildrenOfType(file, LuaCatsClassTag::class.java).toList()
            val boxTag = classTags[0]
            val panelTag = classTags[1]

            // ---@class Box<T>: both the head and the parameter are declaration slot holders.
            val boxParameterizedName =
                requireNotNull(PsiTreeUtil.findChildOfType(boxTag.argType, LuaCatsParameterizedName::class.java))
            assertTrue(LuaCatsTypeDeclarations.isDeclarationSlotHolder(boxParameterizedName.genericType))
            assertTrue(LuaCatsTypeDeclarations.isDeclarationSlotHolder(boxParameterizedName.typeParamList.single()))

            // ---@generic T: the parameter is a declaration slot holder.
            val genericTag = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsGenericTag::class.java))
            val genericTypeParamHolder =
                requireNotNull(
                    PsiTreeUtil.findChildOfType(
                        requireNotNull(genericTag.genericTypeParams).genericTypeParamList.single().argName,
                        LuaCatsTypeParam::class.java,
                    ),
                )
            assertTrue(LuaCatsTypeDeclarations.isDeclarationSlotHolder(genericTypeParamHolder))

            // ---@type Box<Widget>: a use — neither the head nor its parameter is a slot holder.
            val typeTag = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsTypeTag::class.java))
            val typeUse =
                requireNotNull(PsiTreeUtil.findChildOfType(typeTag.argType, LuaCatsParameterizedName::class.java))
            assertFalse(LuaCatsTypeDeclarations.isDeclarationSlotHolder(typeUse.genericType))
            assertFalse(LuaCatsTypeDeclarations.isDeclarationSlotHolder(typeUse.typeParamList.single()))

            // ---@class Panel : Box<Widget>: the parent type's head is a use, not a slot holder.
            val panelParentTypes = requireNotNull(panelTag.parentTypes)
            val panelParentUse =
                requireNotNull(PsiTreeUtil.findChildOfType(panelParentTypes, LuaCatsParameterizedName::class.java))
            assertFalse(LuaCatsTypeDeclarations.isDeclarationSlotHolder(panelParentUse.genericType))

            // ---@param p table<string, Widget>: Widget is a use, not a slot holder.
            val tableParamTag =
                PsiTreeUtil
                    .findChildrenOfType(file, LuaCatsParamTag::class.java)
                    .single { it.argType.text.startsWith("table") }
            val tableUse =
                requireNotNull(PsiTreeUtil.findChildOfType(tableParamTag.argType, LuaCatsParameterizedName::class.java))
            assertFalse(LuaCatsTypeDeclarations.isDeclarationSlotHolder(tableUse.typeParamList[1]))
        }
    }

    // shadowedTypeParameterNames, and its gate on useHolderOf

    @Test
    fun testShadowedTypeParameterNamesForAGenericFunction() {
        val file =
            myFixture.configureByText(
                "gen.lua",
                """
                --- @generic T
                --- @param v T
                --- @return T
                local function id(v) return v end
                """.trimIndent(),
            )
        runReadAction {
            val comment = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsComment::class.java))
            assertEquals(setOf("T"), LuaCatsTypeDeclarations.shadowedTypeParameterNames(comment))

            val paramUse =
                requireNotNull(
                    PsiTreeUtil.findChildOfType(comment.paramTagList.single().argType, LuaCatsNamedType::class.java),
                )
            assertNull(
                "a use bound by @generic T must not be a renameable holder",
                LuaCatsTypeDeclarations.useHolderOf(paramUse.firstChild),
            )
        }
    }

    @Test
    fun testShadowedTypeParameterNamesForAParameterizedClassTag() {
        val file =
            myFixture.configureByText(
                "boxfield.lua",
                "--- @class Box<T>\n--- @field item T\nlocal Box = {}\n",
            )
        runReadAction {
            val comment = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsComment::class.java))
            assertEquals(setOf("T"), LuaCatsTypeDeclarations.shadowedTypeParameterNames(comment))
        }
    }

    /** The control: a comment declaring no type parameter shadows nothing, and the use is renameable. */
    @Test
    fun testShadowedTypeParameterNamesEmptyWithNoGenericDeclaration() {
        val file = myFixture.configureByText("plain.lua", "--- @param w T\nlocal function h(w) return w end\n")
        runReadAction {
            val comment = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsComment::class.java))
            assertEquals(emptySet<String>(), LuaCatsTypeDeclarations.shadowedTypeParameterNames(comment))

            val paramUse =
                requireNotNull(
                    PsiTreeUtil.findChildOfType(comment.paramTagList.single().argType, LuaCatsNamedType::class.java),
                )
            assertNotNull(
                "an unshadowed T is an ordinary use and must be renameable",
                LuaCatsTypeDeclarations.useHolderOf(paramUse.firstChild),
            )
        }
    }

    // TC-3 — the negative control for LuaCatsBaseElement's getReferences()/getReference(), executed
    // rather than read: a throwaway contributor stands in for Phase 2's real one.
    //
    // Targets LuaCatsFieldNameDescriptor rather than LuaCatsNamedType: since Phase 2 landed,
    // LuaCatsTypeReferenceContributor is registered for real against every LuaCatsNamedType, so a
    // probe on that same shape would double-count getReferences() rather than isolate the registry
    // reach this test is proving. A field name descriptor has no real contributor anywhere in this
    // feature.

    @Test
    fun testGetReferencesReachesTheRegistryAndGetReferenceReadsIt() {
        PsiReferenceContributor.EP_NAME.point.registerExtension(
            PsiReferenceContributorEP().apply {
                language = "Lua"
                implementationClass = ProbeReferenceContributor::class.java.name
                pluginDescriptor = DefaultPluginDescriptor("lunar-test-refact-08")
            },
            testRootDisposable,
        )

        val file =
            myFixture.configureByText("probe.lua", "--- @class C\n--- @field a string\nlocal C = {}\n")
        runReadAction {
            val fieldTag = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsFieldTag::class.java))
            val descriptor =
                requireNotNull(
                    PsiTreeUtil.findChildOfType(fieldTag.fieldDescriptor, LuaCatsFieldNameDescriptor::class.java),
                )
            assertEquals("getReferences() must reach the registered contributor", 1, descriptor.references.size)
            assertNotNull(
                "getReference() must surface the contributed reference, not just getReferences()",
                descriptor.reference,
            )
        }
    }

    /** Matches every [LuaCatsFieldNameDescriptor] with a single no-op reference — enough to prove reach. */
    private class ProbeReferenceContributor : PsiReferenceContributor() {
        override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
            registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(LuaCatsFieldNameDescriptor::class.java),
                object : PsiReferenceProvider() {
                    override fun getReferencesByElement(
                        element: PsiElement,
                        context: ProcessingContext,
                    ): Array<PsiReference> =
                        arrayOf(
                            object : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength)) {
                                override fun resolve(): PsiElement? = null
                            },
                        )
                },
            )
        }
    }
}

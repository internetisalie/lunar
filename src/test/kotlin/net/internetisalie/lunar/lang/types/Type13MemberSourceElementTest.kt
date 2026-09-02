package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl
import net.internetisalie.lunar.lang.psi.types.LuaTypeMember
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.LuaUnionType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-13 Phase 3: `putGraphMember` wired into both `tableToLuaType` branches, and `LuaUnionType`
 * carrying `sourceElement` through both its entry points — `implementation-plan.md` Phase 3.
 * Covers every `requirements.md` test case whose Requirement column is `TYPE-13-01` or `TYPE-13-06`
 * (case 2, `TYPE-13-02`, is already pinned by `Type13ProvenanceTest`, Phase 1 — it asserts the
 * mint-site mark, not `sourceElement`, so it needs no new coverage here).
 *
 * Cases 8, 9, 10 and 17 are each alone in their own test method with exactly one file configured:
 * `LuaTypeManagerImpl.resolveType` searches the project-wide stub index, so a sibling fixture
 * declaring the same class name would silently bind an arm to the wrong file's declaration
 * (measured on case 17, `requirements.md`).
 */
@RunWith(JUnit4::class)
class Type13MemberSourceElementTest : BasePlatformTestCase() {
    private fun nameRefs(
        source: String,
        name: String,
    ): List<LuaNameRef> {
        val file = myFixture.configureByText("a.lua", source)
        return runReadAction {
            PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java).filter { it.text == name }
        }
    }

    private fun classArmOf(union: LuaUnionType): LuaClassType = union.types.filterIsInstance<LuaClassType>().first()

    // ---- TYPE-13-01: a structural member carries its declaration -----------------------------

    /** Case 1: a plain local table's colon method resolves with `sourceElement` populated. */
    @Test
    fun localColonMethodMemberCarriesFuncNameMethodAsSource() {
        val receiver =
            nameRefs(
                """
                local t = {}
                function t:m() end
                t:m()
                """.trimIndent(),
                "t",
            ).last()
        val member =
            runReadAction {
                val types = LuaTypesSnapshot.forFile(receiver.containingFile)
                types.graphTypeToLuaType(types.getValueType(receiver)).resolveMember("m")
            }
        assertNotNull(member)
        val source = member!!.sourceElement
        assertNotNull(source)
        assertEquals("LuaFuncNameMethodImpl", source!!.javaClass.simpleName)
        assertEquals(23, source.textOffset)
    }

    // ---- TYPE-13-06: the nominal route is not regressed --------------------------------------

    private val builderFixture =
        """
        ---@class Builder
        local Builder = {}
        function Builder:setName(n) end
        local b = Builder
        b:setName("x")
        """.trimIndent()

    /** Case 8: the pre-existing nominal route, asserted as a baseline for case 9 to diverge from. */
    @Test
    fun nominalRouteResolvesSetNameToItsFuncDecl() {
        val file = myFixture.configureByText("a.lua", builderFixture)
        val member =
            runReadAction {
                LuaTypeManagerImpl(project).resolveType("Builder", file)!!.resolveMember("setName")
            }
        assertNotNull(member)
        val source = member!!.sourceElement
        assertNotNull(source)
        assertEquals("LuaFuncDeclImpl", source!!.javaClass.simpleName)
        assertEquals(37, source.textOffset)
    }

    /**
     * Case 9: resolving `setName` through the *structural* route — the receiver's `LuaUnionType`'s
     * `LuaClassType` arm — must report the SAME nominal `LuaFuncDecl`, never the coarser
     * `LuaFuncNameMethod` the graph route would otherwise supply. This is the row `putGraphMember`'s
     * nominal-preservation guard exists for.
     */
    @Test
    fun unionClassArmPreservesNominalSetNameDeclaration() {
        val receiver = nameRefs(builderFixture, "b").last()
        val member =
            runReadAction {
                val types = LuaTypesSnapshot.forFile(receiver.containingFile)
                val union = types.graphTypeToLuaType(types.getValueType(receiver)) as LuaUnionType
                classArmOf(union).resolveMember("setName")
            }
        assertNotNull(member)
        val source = member!!.sourceElement
        assertNotNull(source)
        assertEquals("LuaFuncDeclImpl", source!!.javaClass.simpleName)
        assertEquals(37, source.textOffset)
    }

    private val boxFixture =
        """
        ---@class Box
        ---@field lid string
        local Box = {}
        function Box:open() end
        print(Box.lid)
        Box:open()
        """.trimIndent()

    /**
     * Case 10: the other half of the nominal-preservation guard — a `@field` member has no
     * declaring graph node at all, so without the guard `sourceElement` would be blanked to null
     * rather than merely coarsened. `lid`'s `LuaCatsFieldTag` must survive.
     */
    @Test
    fun unionClassArmPreservesNominalFieldDeclaration() {
        val receiver = nameRefs(boxFixture, "Box").last()
        val member =
            runReadAction {
                val types = LuaTypesSnapshot.forFile(receiver.containingFile)
                val union = types.graphTypeToLuaType(types.getValueType(receiver)) as LuaUnionType
                classArmOf(union).resolveMember("lid")
            }
        assertNotNull(member)
        val source = member!!.sourceElement
        assertNotNull(source)
        assertEquals("LuaCatsFieldTagImpl", source!!.javaClass.simpleName)
        assertEquals(17, source.textOffset)
    }

    /**
     * Case 17: the only case that reaches `LuaUnionType` itself (design §4.4) rather than one of its
     * arms directly. `---@type A|B`, both declaring `m`, satisfies `resolveMember`'s all-arms rule;
     * both entry points — `resolveMember` and `getMembers` — must report the first arm's declaration.
     */
    @Test
    fun unionTypeItselfCarriesFirstArmsDeclarationOnBothEntryPoints() {
        val source =
            """
            ---@class A
            local A = {}
            function A:m() end
            ---@class B
            local B = {}
            function B:m() end
            ---@type A|B
            local u
            u:m()
            """.trimIndent()
        val receiver = nameRefs(source, "u").last()
        assertEquals(109, receiver.textOffset)

        val union =
            runReadAction {
                val types = LuaTypesSnapshot.forFile(receiver.containingFile)
                types.graphTypeToLuaType(types.getValueType(receiver))
            } as LuaUnionType

        val resolved: LuaTypeMember? = runReadAction { union.resolveMember("m") }
        assertNotNull(resolved)
        val resolvedSource = resolved!!.sourceElement
        assertNotNull(resolvedSource)
        assertEquals("LuaFuncDeclImpl", resolvedSource!!.javaClass.simpleName)
        assertEquals(25, resolvedSource.textOffset)

        val fromMembers: LuaTypeMember? = runReadAction { union.getMembers()["m"] }
        assertNotNull(fromMembers)
        val membersSource = fromMembers!!.sourceElement
        assertNotNull(membersSource)
        assertEquals("LuaFuncDeclImpl", membersSource!!.javaClass.simpleName)
        assertEquals(25, membersSource.textOffset)
    }
}

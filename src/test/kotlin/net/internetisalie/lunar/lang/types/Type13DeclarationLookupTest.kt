package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaMemberDeclarations
import net.internetisalie.lunar.lang.psi.types.LuaPrimitiveType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
import net.internetisalie.lunar.lang.psi.types.LuaTypeMember
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.VariableNode
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-13 Phase 2: `LuaMemberDeclarations.declaringNodeOf` and `.declarationOf`, exercised directly
 * over `requirements.md` DR-05a's fixtures — `implementation-plan.md` Phase 2. There is no caller in
 * `tableToLuaType` yet (that is Phase 3); every fixture here reaches the mint-site winning node the
 * same way `Type13ProvenanceTest` does and feeds it straight into the two functions under test.
 *
 * One fixture per test method, per the fixture-isolation note: `LuaTypeManagerImpl` searches
 * `GlobalSearchScope.allScope(project)`, so a sibling fixture file would silently bind a member to
 * the wrong file.
 */
@RunWith(JUnit4::class)
class Type13DeclarationLookupTest : IndexedBasePlatformTestCase() {
    private fun structuralMemberOf(
        graphType: LuaGraphType,
        name: String,
    ): VariableNode? =
        when (graphType) {
            is LuaGraphType.Table -> graphType.getMembers()[name]
            is LuaGraphType.Union -> graphType.types.firstNotNullOfOrNull { structuralMemberOf(it, name) }
            else -> null
        }

    /** The declaration a consumer would see if Phase 3's `putGraphMember` ran over [node] today. */
    private fun declarationFor(node: VariableNode?): PsiElement? {
        val declaringNode = node?.let(LuaMemberDeclarations::declaringNodeOf) ?: return null
        return LuaMemberDeclarations.declarationOf(
            LuaTypeMember("member", LuaPrimitiveType.UNKNOWN, sourceElement = declaringNode.element),
        )
    }

    private fun nameRefs(
        source: String,
        name: String,
    ): List<LuaNameRef> {
        val file = myFixture.configureByText("a.lua", source)
        return runReadAction {
            PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java).filter { it.text == name }
        }
    }

    private fun memberNodeAt(
        receiver: LuaNameRef,
        memberName: String,
    ): VariableNode? =
        runReadAction {
            val types = LuaTypesSnapshot.forFile(receiver.containingFile)
            structuralMemberOf(types.getValueType(receiver), memberName)
        }

    /** Asserts that [receiverName]'s [memberName] resolves to a member with no declaration. */
    private fun assertNoDeclaration(
        source: String,
        receiverName: String,
        memberName: String,
    ) {
        val receiver = nameRefs(source, receiverName).first()
        assertNull(declarationFor(memberNodeAt(receiver, memberName)))
    }

    // ---- Requirement TYPE-13-03 -------------------------------------------------------------

    /** Case 3: plain local table, colon method — `declarationOf` reaches the `LuaFuncDecl`. */
    @Test
    fun localColonMethodResolvesToFuncDecl() {
        val receiver =
            nameRefs(
                """
                local t = {}
                function t:m() end
                t:m()
                """.trimIndent(),
                "t",
            ).first()
        val declaration = declarationFor(memberNodeAt(receiver, "m"))
        assertNotNull(declaration)
        assertEquals("LuaFuncDeclImpl", declaration!!.javaClass.simpleName)
        assertEquals(13, declaration.textOffset)
    }

    /**
     * Case 4: global table, colon method, resolved from **both** handles (risks Gap 2.9): the
     * write-target `Obj` at offset 0 and the call receiver `Obj` at offset 30 must each yield the
     * same `LuaFuncDecl` at offset 9.
     */
    @Test
    fun globalColonMethodResolvesFromBothHandles() {
        val refs =
            nameRefs(
                """
                Obj = {}
                function Obj:m() end
                Obj:m()
                """.trimIndent(),
                "Obj",
            )
        val writeTarget = refs.first()
        val callReceiver = refs.last()
        assertEquals(0, writeTarget.textOffset)
        assertEquals(30, callReceiver.textOffset)

        val fromWriteTarget = declarationFor(memberNodeAt(writeTarget, "m"))
        val fromCallReceiver = declarationFor(memberNodeAt(callReceiver, "m"))

        assertNotNull(fromWriteTarget)
        assertEquals("LuaFuncDeclImpl", fromWriteTarget!!.javaClass.simpleName)
        assertEquals(9, fromWriteTarget.textOffset)

        assertNotNull(fromCallReceiver)
        assertEquals("LuaFuncDeclImpl", fromCallReceiver!!.javaClass.simpleName)
        assertEquals(9, fromCallReceiver.textOffset)
    }

    // ---- Requirement TYPE-13-05 -------------------------------------------------------------

    /** Case 5: `setmetatable` OO — the winning node is the call site, but the recovered declaration is not. */
    @Test
    fun setmetatableOoResolvesToDeclarationNotCallSite() {
        val receiver =
            nameRefs(
                """
                local Class = {}
                Class.__index = Class
                function Class:m() end
                local o = setmetatable({}, Class)
                o:m()
                """.trimIndent(),
                "o",
            ).first()
        val declaration = declarationFor(memberNodeAt(receiver, "m"))
        assertNotNull(declaration)
        assertEquals("LuaFuncDeclImpl", declaration!!.javaClass.simpleName)
        assertEquals(39, declaration.textOffset)
    }

    /** Case 13: a cyclic supertype graph still terminates and still finds the declaration. */
    @Test
    fun cyclicSupertypeGraphTerminatesAndResolves() {
        val receiver =
            nameRefs(
                """
                local A = {}
                local B = {}
                A.__index = B
                B.__index = A
                function B:m() end
                local o = setmetatable({}, A)
                o:m()
                """.trimIndent(),
                "o",
            ).first()
        val declaration = declarationFor(memberNodeAt(receiver, "m"))
        assertNotNull(declaration)
        assertEquals("LuaFuncDeclImpl", declaration!!.javaClass.simpleName)
        assertEquals(54, declaration.textOffset)
    }

    // ---- Requirement TYPE-13-04 -------------------------------------------------------------

    /** Case 6: nothing declares `m` on `t` — a member is still returned, but with no declaration. */
    @Test
    fun bareColonCallHasMemberButNoDeclaration() {
        val receiver =
            nameRefs(
                """
                local t = {}
                t:m()
                """.trimIndent(),
                "t",
            ).first()
        val node = memberNodeAt(receiver, "m")
        assertNotNull("resolveMember must still find the demand node", node)
        assertNull(declarationFor(node))
    }

    /**
     * Case 7: `t.a.m = f` mints a member `m` **on `t`** (bare-head anchoring), but the mint-site
     * predicate refuses it as a declaration. `t`'s member `a`, whose assignment IS a sole suffix,
     * still resolves correctly — proving the refusal is specific to the mis-anchored member, not to
     * `t`'s members generally.
     */
    @Test
    fun indexPrefixedAssignmentDoesNotDeclareOnlyTheMisAnchoredMember() {
        val receiver =
            nameRefs(
                """
                local t = {}
                t.a = {}
                t.a.m = function() end
                t.a.m()
                """.trimIndent(),
                "t",
            ).first()

        assertNull(declarationFor(memberNodeAt(receiver, "m")))

        val aDeclaration = declarationFor(memberNodeAt(receiver, "a"))
        assertNotNull(aDeclaration)
        assertEquals("LuaAssignmentStatementImpl", aDeclaration!!.javaClass.simpleName)
        assertEquals(13, aDeclaration.textOffset)
    }

    /**
     * Case 16: `t().m = f` mints a member `m` on `t` through a **call**-prefixed sole suffix; the
     * mint-site predicate must refuse the call step exactly as it refuses the index step in case 7.
     */
    @Test
    fun callPrefixedAssignmentOnLocalDoesNotDeclare() {
        assertNoDeclaration("local t = {}\nt().m = function() end", "t", "m")
    }

    /** Case 16: the same refusal on a global receiver. */
    @Test
    fun callPrefixedAssignmentOnGlobalDoesNotDeclare() {
        assertNoDeclaration("Cfg = {}\nCfg().m = function() end", "Cfg", "m")
    }

    /** Case 16: the same refusal when the call-prefixed receiver is a function parameter. */
    @Test
    fun callPrefixedAssignmentOnParameterDoesNotDeclare() {
        assertNoDeclaration(
            "function g(p)\n  p().m = function() end\n  return p.m\nend",
            "p",
            "m",
        )
    }

    /** Case 16: a double call `t()()` is refused the same way as a single call. */
    @Test
    fun doubleCallPrefixedAssignmentDoesNotDeclare() {
        assertNoDeclaration("local t = {}\nt()().m = function() end", "t", "m")
    }

    /** Case 16's control: no navigation step at all still declares, unmoved by the call-step clause. */
    @Test
    fun plainAssignmentControlStillDeclares() {
        val control = nameRefs("local t = {}\nt.m = function() end", "t").first()
        val controlDeclaration = declarationFor(memberNodeAt(control, "m"))
        assertNotNull(controlDeclaration)
        assertEquals("LuaAssignmentStatementImpl", controlDeclaration!!.javaClass.simpleName)
        assertEquals(13, controlDeclaration.textOffset)
    }

    // ---- Requirement TYPE-13-08 -------------------------------------------------------------

    /** Case 14: a `require`d, un-annotated module resolves the member but reports no declaration. */
    @Test
    fun crossFileRequireReportsNoDeclaration() {
        myFixture.addFileToProject(
            "mod.lua",
            """
            local M = {}
            function M:m() end
            return M
            """.trimIndent(),
        )
        val mainFile =
            myFixture.configureByText(
                "a.lua",
                """
                local M = require('mod')
                M:m()
                """.trimIndent(),
            )
        myFixture.configureByFiles("a.lua", "mod.lua")

        val receiver =
            runReadAction {
                // `.last`: the FIRST "M" is the local decl's own name — `local` binds through a
                // `LuaNameRef` too (there is no separate NameDef PSI class), and picking it would
                // still resolve the same scope-local variable, but the call-site receiver at
                // offset 25 is the handle `requirements.md`'s "M@25" measurement names.
                PsiTreeUtil.findChildrenOfType(mainFile, LuaNameRef::class.java).last { it.text == "M" }
            }
        assertEquals(25, receiver.textOffset)
        val node = memberNodeAt(receiver, "m")
        assertNotNull("resolveMember must still find the demand node", node)
        assertNull(declarationFor(node))
    }

    // ---- Requirement TYPE-13-07 -------------------------------------------------------------

    /**
     * Case 11: two non-declaring nodes wired into each other's `upSet` terminate rather than loop
     * forever — the de-duplication guard is what makes this return at all.
     */
    @Test
    fun mutualCycleOfNonDeclaringNodesTerminatesWithNoDeclaration() {
        val graph = LuaTypeGraph()
        val anchor = myFixture.addFileToProject("cycle11.lua", "")
        val a = graph.variable(anchor, declaresMember = false)
        val b = graph.variable(anchor, declaresMember = false)
        a.upSet.add(b)
        b.upSet.add(a)

        assertNull(LuaMemberDeclarations.declaringNodeOf(a))
    }

    /**
     * Case 12: a hand-built chain pins the node budget, not the walk's correctness. A 70-node
     * non-declaring chain exhausts `MAX_VISITED` (64) before reaching the declaring node at its end
     * and refuses; an 8-node control chain is well within the budget and finds it.
     */
    @Test
    fun chainLongerThanBudgetRefusesWhileShortChainResolves() {
        val graph = LuaTypeGraph()
        val anchor = myFixture.addFileToProject("cycle12.lua", "")

        val (longHead, _) = buildNonDeclaringChain(graph, anchor, nonDeclaringCount = 70)
        assertNull(LuaMemberDeclarations.declaringNodeOf(longHead))

        val (shortHead, shortDeclaration) = buildNonDeclaringChain(graph, anchor, nonDeclaringCount = 8)
        assertEquals(shortDeclaration, LuaMemberDeclarations.declaringNodeOf(shortHead))
    }

    /**
     * Builds `declaringNode <- node1 <- node2 <- ... <- nodeN` by writing directly into `upSet`
     * (bypassing `LuaTypeGraph.addEdge`'s transitive closure, which would otherwise put the
     * declaring node directly in every node's `upSet` and collapse the chain to depth 1). Returns
     * the head (farthest from the declaration) and the declaring node.
     */
    private fun buildNonDeclaringChain(
        graph: LuaTypeGraph,
        anchor: PsiElement,
        nonDeclaringCount: Int,
    ): Pair<VariableNode, VariableNode> {
        val declaringNode = graph.variable(anchor, declaresMember = true)
        var current: VariableNode = declaringNode
        repeat(nonDeclaringCount) {
            val next = graph.variable(anchor, declaresMember = false)
            next.upSet.add(current)
            current = next
        }
        return current to declaringNode
    }
}

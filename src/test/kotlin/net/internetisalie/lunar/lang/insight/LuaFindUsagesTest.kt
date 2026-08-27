package net.internetisalie.lunar.lang.insight

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usages.impl.rules.UsageType
import net.internetisalie.lunar.lang.psi.LuaAttName
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaVar
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for [LuaFindUsagesProvider] and [LuaReadWriteUsageTypeProvider].
 *
 * Covers:
 *   TC-NAV-02-01 — local variable usages (count + declaration excluded)
 *   TC-NAV-02-02 — cross-file global usages via stub index (see [LuaFindUsagesCrossFileTest])
 *   TC-NAV-02-03 — label usages (regression: LuaLabelName branch preserved)
 *   TC-NAV-02-04 — scope isolation (two locals with same name)
 *   TC-NAV-02-05 — read/write classification via [LuaReadWriteUsageTypeProvider]
 */
@RunWith(JUnit4::class)
class LuaFindUsagesTest : BasePlatformTestCase() {
    private val provider = LuaFindUsagesProvider()
    private val usageTypeProvider = LuaReadWriteUsageTypeProvider()

    // -------------------------------------------------------------------------
    // canFindUsagesFor — declaration-kind coverage
    // -------------------------------------------------------------------------

    @Test
    fun testCanFindUsagesForLocalVar() {
        val file = myFixture.configureByText("test.lua", "local x = 1")
        val attName = PsiTreeUtil.findChildOfType(file, LuaAttName::class.java)!!
        val identifier = attName.nameRef.identifier
        assertTrue("Should accept local var IDENTIFIER", provider.canFindUsagesFor(identifier))
        assertEquals("local variable", provider.getType(identifier))
    }

    @Test
    fun testCanFindUsagesForGlobalFunction() {
        val file = myFixture.configureByText("test.lua", "function greet() end")
        val funcDecl = PsiTreeUtil.findChildOfType(file, LuaFuncDecl::class.java)!!
        val identifier = funcDecl.funcName.nameRef.identifier
        assertTrue("Should accept global function IDENTIFIER", provider.canFindUsagesFor(identifier))
        assertEquals("global function", provider.getType(identifier))
    }

    @Test
    fun testCanFindUsagesForDottedFunction() {
        // REFACT-01-08 / TC-23: `funcName ::= nameRef funcNameProperty* funcNameMethod?`, so the
        // `run` leaf's grandparent is a LuaFuncNameProperty — a shape the old hand-written
        // predicate did not accept, leaving `function M.run()` neither findable nor safe-deletable.
        val file = myFixture.configureByText("test.lua", "M = {}\nfunction M.run() end\nM.run()\n")
        val funcDecl =
            requireNotNull(PsiTreeUtil.findChildOfType(file, LuaFuncDecl::class.java)) {
                "Expected a LuaFuncDecl in test.lua"
            }
        val runLeaf =
            funcDecl.funcName.funcNamePropertyList
                .last()
                .nameRef.identifier

        assertTrue("Should accept a dotted function-name IDENTIFIER", provider.canFindUsagesFor(runLeaf))
        assertEquals("global function", provider.getType(runLeaf))

        val refs = ReferencesSearch.search(runLeaf).findAll()
        assertEquals("Expected exactly 1 reference to M.run", 1, refs.size)
    }

    @Test
    fun testCanFindUsagesForLabel() {
        val file = myFixture.configureByText("test.lua", "::done:: goto done")
        val labelName = PsiTreeUtil.findChildOfType(file, LuaLabelName::class.java)!!
        assertTrue("Should accept LuaLabelName for labels", provider.canFindUsagesFor(labelName))
        assertEquals("label", provider.getType(labelName))
    }

    @Test
    fun testCannotFindUsagesForArbitraryIdentifier() {
        myFixture.configureByText("test.lua", "print(x)")
        val nameRefs = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaNameRef::class.java)
        val printRef = nameRefs.firstOrNull { it.identifier.text == "print" }
        assertNotNull("Expected a nameRef for 'print'", printRef)
        assertFalse(
            "Should NOT accept a usage-site identifier",
            provider.canFindUsagesFor(printRef!!.identifier),
        )
    }

    // -------------------------------------------------------------------------
    // TC-NAV-02-01: local variable usages — count + declaration excluded
    // -------------------------------------------------------------------------

    @Test
    fun testLocalVariableUsagesCount() {
        // local x = 1; print(x); x = 2   →   2 usages (not including declaration)
        val file = myFixture.configureByText("test.lua", "local x = 1\nprint(x)\nx = 2")
        val attName = PsiTreeUtil.findChildOfType(file, LuaAttName::class.java)!!
        val declIdentifier = attName.nameRef.identifier

        // Exercise the real Find Usages action: it drives ReferencesSearch over the
        // declaration leaf, which LuaNameReferenceSearcher now turns into a word scan.
        val usages = myFixture.findUsages(declIdentifier)
        assertEquals("Expected 2 usages of local 'x'", 2, usages.size)

        // Reverse search must also resolve directly (the searcher is what the action uses).
        val refs = ReferencesSearch.search(declIdentifier).findAll()
        assertEquals("Expected 2 references to local 'x'", 2, refs.size)
    }

    // -------------------------------------------------------------------------
    // TC-NAV-02-03: label usages — regression guard
    // -------------------------------------------------------------------------

    @Test
    fun testLabelUsagesCount() {
        // ::done:: goto done   →   1 usage
        val file = myFixture.configureByText("test.lua", "::done::\ngoto done")
        val labelName = PsiTreeUtil.findChildOfType(file, LuaLabelName::class.java)!!

        val usages = myFixture.findUsages(labelName)
        assertEquals("Expected 1 label usage", 1, usages.size)

        val refs = ReferencesSearch.search(labelName).findAll()
        assertEquals("Expected 1 label reference", 1, refs.size)
    }

    // -------------------------------------------------------------------------
    // TC-NAV-02-04: scope isolation — two locals with same name
    // -------------------------------------------------------------------------

    @Test
    fun testScopeIsolation() {
        val code =
            """
            local function f()
                local x = 1
                print(x)
            end
            local function g()
                local x = 2
                print(x)
            end
            """.trimIndent()
        val file = myFixture.configureByText("test.lua", code)

        // Find first attName (x inside f)
        val attNames = PsiTreeUtil.findChildrenOfType(file, LuaAttName::class.java)
        val firstX = attNames.first { it.nameRef.identifier.text == "x" }
        val usages = myFixture.findUsages(firstX.nameRef.identifier)

        // Only the x in f should be found, not x in g. The searcher scans every "x"
        // occurrence, but isReferenceTo (resolve() === f's leaf) rejects g's usage.
        assertEquals("Expected 1 usage of 'x' in f, not g's x", 1, usages.size)
    }

    // -------------------------------------------------------------------------
    // TC-NAV-02-05: read/write classification
    // -------------------------------------------------------------------------

    @Test
    fun testWriteClassification() {
        // x = 2  →  nameRef inside LuaVar with empty varSuffixList → WRITE
        val file = myFixture.configureByText("test.lua", "local x = 0\nx = 2")
        // Find the "x" nameRef on the assignment left-hand side
        val vars = PsiTreeUtil.findChildrenOfType(file, LuaVar::class.java)
        val assignedVar = vars.firstOrNull { v -> v.nameRef?.identifier?.text == "x" && v.varSuffixList.isEmpty() }
        assertNotNull("Expected a LuaVar for 'x' on lhs", assignedVar)

        val nameRef = assignedVar!!.nameRef!!
        val usageType = usageTypeProvider.getUsageType(nameRef.identifier)
        assertEquals("Assignment target should be WRITE", UsageType.WRITE, usageType)
    }

    @Test
    fun testReadClassification() {
        // print(x)  →  nameRef not in var-list lhs → READ
        val file = myFixture.configureByText("test.lua", "local x = 0\nprint(x)")
        // Find the "x" nameRef that's inside a function call argument
        val nameRefs = PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java)
        val readRef =
            nameRefs.firstOrNull { ref ->
                ref.identifier.text == "x" && ref.parent !is LuaVar
            }
        assertNotNull("Expected a read-site nameRef for 'x'", readRef)

        val usageType = usageTypeProvider.getUsageType(readRef!!.identifier)
        assertEquals("Read usage should be READ", UsageType.READ, usageType)
    }

    @Test
    fun testIndexBaseIsRead() {
        // t.k = 1  →  `t` is the base of a suffixed var; varSuffixList non-empty → READ
        val file = myFixture.configureByText("test.lua", "local t = {}\nt.k = 1")
        val vars = PsiTreeUtil.findChildrenOfType(file, LuaVar::class.java)
        // The var `t.k` has a non-empty varSuffixList; its base nameRef is `t`
        val suffixedVar = vars.firstOrNull { it.nameRef?.identifier?.text == "t" && it.varSuffixList.isNotEmpty() }
        assertNotNull("Expected a suffixed LuaVar for 't.k'", suffixedVar)

        val nameRef = suffixedVar!!.nameRef!!
        val usageType = usageTypeProvider.getUsageType(nameRef.identifier)
        assertEquals("Index base t in t.k=1 should be READ", UsageType.READ, usageType)
    }

    // -------------------------------------------------------------------------
    // isWriteTarget companion helper
    // -------------------------------------------------------------------------

    @Test
    fun testIsWriteTargetPredicate() {
        val file = myFixture.configureByText("test.lua", "local x = 0\nx = 99")
        val vars = PsiTreeUtil.findChildrenOfType(file, LuaVar::class.java)
        val assignedVar = vars.firstOrNull { it.nameRef?.identifier?.text == "x" && it.varSuffixList.isEmpty() }
        assertNotNull(assignedVar)
        val nameRef = assignedVar!!.nameRef as LuaNameRef
        assertTrue("isWriteTarget should be true for bare lhs", LuaReadWriteUsageTypeProvider.isWriteTarget(nameRef))
    }

    // -------------------------------------------------------------------------
    // TC-13 (REFACT-07-12): the PSI change alters no behaviour Lunar ships a feature for
    // -------------------------------------------------------------------------

    /**
     * TC-13 (`REFACT-07-12`) — REFACT-07 §3.1 grants `PsiNameIdentifierOwner` to every
     * `LuaNameRef`, and Find Usages is one of the platform behaviours design §4's consumer audit
     * requires to be observably unchanged by it. DR-03 measured this behaviour as byte-identical
     * across the base and treatment commits; this case is what keeps it that way in the suite,
     * because DR-03 also found that **no test in the 2851-name set asserted it**.
     *
     * **Mutation:** delete the `identifierLeafOf` normalisation from
     * `LuaNameReferenceSearcher.processQuery` and search `requested` directly
     * (`LuaNameReferenceSearcher.kt:57`) — the `kindOf` gate at `:58` then returns early and the
     * searcher yields nothing, so the "exactly the reads and writes are reported" half fails. All
     * three usages are in this fixture's own file, which is the scope the searcher covers. This
     * mutation is **this case's alone**: it is masked at the document layer, where
     * `MemberInplaceRenamer.collectRefs`'s second search on `getSubstituted()`
     * (`MemberInplaceRenamer.java:173-183`) passes the already-normalised leaf and so survives it.
     * TC-03 names a different mutation for that reason.
     *
     * **The "declaration is not among them" half is a guard, not a gate.** The identity check at
     * `LuaNameReference.kt:264` is masked by the very next line — `shadowsRatherThanUses(self)` at
     * `:265` is `kindOf(host.identifier)?.isFileLocal == true` (`:189-192`), true for the declaring
     * `LuaNameRef` of `local counter` — so deleting `:264` alone leaves the case green.
     *
     * **Phase 4 verdict: the mutation named above SURVIVED — this case stayed GREEN.** Executed
     * 2026-08-26; `risks-and-gaps.md` Risk 1.11 was read-not-run and is now confirmed by a run. Find
     * Usages passes the IDENTIFIER **leaf**, so with the normalisation deleted `target` is that same
     * leaf, `kindOf` is `LOCAL_VARIABLE` rather than null, the `:58` gate does not return early and the
     * search proceeds unchanged. The case cannot be re-pointed at the composite either:
     * `LuaFindUsagesProvider.canFindUsagesFor` is `kindOf(element) != null` (`:35`), false for a
     * `LuaNameRef`.
     *
     * **A reachable mutant does exist, so this requirement is not untestable and this case is not to be
     * deleted.** Replacing `reference.isReferenceTo(target)` with `false`
     * (`LuaNameReferenceSearcher.kt:76`) reddens the gating half `expected:<3> but was:<0>` — measured
     * in the same sweep. The cost of that correction is that the mutant is shared with TC-03 and
     * with every other case that needs the searcher to find a usage — a set that grows with each new
     * consumer — so it pins the searcher rather than the Find Usages path specifically; the
     * requirements row names `:76` and says so.
     */
    @Test
    fun testFindUsagesOnALocalDeclarationReportsItsReadsAndWritesAndNotItself() {
        val source = "local coun<caret>ter = 0\nprint(counter)\ncounter = counter + 1\n"
        val file = myFixture.configureByText("test.lua", source)
        val attName =
            requireNotNull(PsiTreeUtil.findChildOfType(file, LuaAttName::class.java)) {
                "expected a LuaAttName for the `counter` declaration"
            }
        val declaration = attName.nameRef.identifier

        val usages = myFixture.findUsages(declaration)

        assertEquals("Expected the read, the write and the read inside it: $usages", 3, usages.size)
        assertTrue(
            "the declaration itself must not be reported as a usage of itself: $usages",
            usages.none { it.element === declaration },
        )
    }
}

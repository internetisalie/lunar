package net.internetisalie.lunar.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.insight.LuaFindUsagesProvider
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaAttName
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaGlobalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaGlobalVarDecl
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [LuaSafeDeleteProcessor] and the [isSafeDeleteAvailable] hook in
 * [net.internetisalie.lunar.lang.insight.LuaRefactoringSupportProvider] (REFACT-03).
 *
 * TC-REFACT-03-01 — unused local deleted: drives [SafeDeleteHandler.invoke] in unit-test mode
 *   (no dialog) and asserts the full `local x = 1` statement is removed from the file.
 *
 * TC-REFACT-03-02 — used local → usages discovered: calls [LuaSafeDeleteProcessor.findUsages]
 *   directly on the IDENTIFIER leaf to avoid the interactive conflict dialog; asserts that at
 *   least one usage is returned for `print(x)`.
 *
 * TC-REFACT-03-03 — unavailable target: asserts [LuaFindUsagesProvider.canFindUsagesFor] and
 *   [LuaSafeDeleteProcessor.handlesElement] return false for a keyword / literal element.
 */
@RunWith(JUnit4::class)
class LuaSafeDeleteTest : BasePlatformTestCase() {
    private val processor = LuaSafeDeleteProcessor()
    private val findUsagesProvider = LuaFindUsagesProvider()

    // -------------------------------------------------------------------------
    // TC-REFACT-03-01: unused local is silently deleted (no prompt)
    // -------------------------------------------------------------------------

    @Test
    fun testUnusedLocalIsDeleted() {
        myFixture.configureByText("test.lua", "local x = 1\n")
        val file = myFixture.file

        // Locate the IDENTIFIER leaf for `x` inside the LuaAttName.
        val attName = PsiTreeUtil.findChildOfType(file, LuaAttName::class.java)!!
        val xLeaf = attName.nameRef.identifier

        // In unit-test mode SafeDeleteHandler skips the dialog and runs the
        // refactoring synchronously.  With checkDelegates=true, the handler
        // picks up LuaSafeDeleteProcessor, elevates the leaf to LuaLocalVarDecl,
        // finds 0 usages, and deletes the declaration without prompting.
        SafeDeleteHandler.invoke(project, arrayOf(xLeaf), true)

        // The whole `local x = 1` statement should now be gone.
        val remaining = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaLocalVarDecl::class.java)
        assertTrue(
            "Expected `local x = 1` to be deleted, but file is: ${myFixture.file.text}",
            remaining.isEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // TC-REFACT-03-02: used local → findUsages returns ≥1 usage
    //
    // Calling SafeDeleteHandler here would throw ConflictsInTestsException
    // (the platform's unit-test behaviour when unsafe usages are present).
    // Instead we test the processor directly: findUsages must report the
    // `print(x)` reference so the platform's conflict path is exercised.
    // -------------------------------------------------------------------------

    @Test
    fun testUsedLocalReturnsUsages() {
        myFixture.configureByText("test.lua", "local x = 1\nprint(x)\n")
        val file = myFixture.file

        val attName = PsiTreeUtil.findChildOfType(file, LuaAttName::class.java)!!
        val xLeaf = attName.nameRef.identifier

        val usages = mutableListOf<com.intellij.usageView.UsageInfo>()
        processor.findUsages(xLeaf, arrayOf(xLeaf), usages)

        assertTrue(
            "Expected ≥1 usage of `x` (the print(x) reference), got ${usages.size}",
            usages.isNotEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // TC-REFACT-03-03: Safe Delete not offered on keyword / literal targets
    // -------------------------------------------------------------------------

    @Test
    fun testUnavailableOnKeyword() {
        // `print` is a usage-site name ref, not a declaration leaf.
        myFixture.configureByText("test.lua", "print(x)")

        // Find the element at the caret-position for `print` (usage ref, not decl).
        val element =
            requireNotNull(myFixture.file.findElementAt(0)) {
                "Expected an element at offset 0"
            }

        assertFalse(
            "isSafeDeleteAvailable must be false for a usage-site identifier",
            findUsagesProvider.canFindUsagesFor(element),
        )
        assertFalse(
            "handlesElement must be false for a usage-site identifier",
            processor.handlesElement(element),
        )
    }

    @Test
    fun testHandlesElevatedDeclaration() {
        // getElementsToSearch elevates the caret leaf to its LuaLocalVarDecl, and the platform
        // re-dispatches handlesElement on that elevated node before calling findUsages. If the
        // delegate did not handle the elevated node, the platform would fall back to the default
        // delete and remove the declaration WITHOUT a usage search (silently orphaning references).
        myFixture.configureByText("test.lua", "local x = 1")
        val decl =
            requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, LuaLocalVarDecl::class.java)) {
                "Expected a LuaLocalVarDecl in test.lua"
            }
        assertTrue(
            "handlesElement must be true for the elevated LuaLocalVarDecl, or Safe Delete skips usage search",
            processor.handlesElement(decl),
        )
    }

    // -------------------------------------------------------------------------
    // TC-REFACT-03-03 (integration): Safe Delete of a USED local must NOT delete
    // silently — the platform must surface a conflict. In unit-test mode an
    // unresolved conflict throws ConflictsInTestsException. This is the test that
    // catches the regression where the elevated decl was not handlesElement-ed.
    // -------------------------------------------------------------------------

    @Test
    fun testUsedLocalRaisesConflict() {
        myFixture.configureByText("test.lua", "local x = 1\nprint(x)\n")
        val attName = PsiTreeUtil.findChildOfType(myFixture.file, LuaAttName::class.java)!!
        val xLeaf = attName.nameRef.identifier

        try {
            SafeDeleteHandler.invoke(project, arrayOf(xLeaf), true)
            fail("Safe Delete of a used local must raise a conflict, not delete silently: ${myFixture.file.text}")
        } catch (expected: BaseRefactoringProcessor.ConflictsInTestsException) {
            // expected: the print(x) usage is reported as a conflict
        }

        assertFalse(
            "The used local must survive when a conflict is raised: ${myFixture.file.text}",
            PsiTreeUtil.findChildrenOfType(myFixture.file, LuaLocalVarDecl::class.java).isEmpty(),
        )
    }

    @Test
    fun testLabelDeclarationIsAvailable() {
        myFixture.configureByText("test.lua", "::done::\ngoto done")
        val labelName =
            requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, LuaLabelName::class.java)) {
                "Expected a LuaLabelName in test.lua"
            }
        assertTrue(
            "isSafeDeleteAvailable must be true for a label declaration (LuaLabelName)",
            findUsagesProvider.canFindUsagesFor(labelName),
        )
        assertTrue(
            "handlesElement must be true for a label declaration",
            processor.handlesElement(labelName),
        )
    }

    // -------------------------------------------------------------------------
    // TC-32 (REFACT-01 Phase 1, Risk 1.6): Safe Delete of a USED global must raise a conflict.
    //
    // Phase 1 widened isSafeDeleteAvailable to every LuaDeclarationSite kind, which newly reaches
    // the three declaration nodes below. None of them is a PsiNamedElement, so if the elevation
    // predicate does not admit them the platform drops this delegate, runs no generic search
    // either, finds zero usages, raises no conflict and deletes the declaration outright.
    // -------------------------------------------------------------------------

    @Test
    fun testUsedGlobalRaisesConflict() {
        assertSafeDeleteRaisesConflict("config = {}\nprint(config)\n", "config", LuaAssignmentStatement::class.java)

        LuaProjectSettings.getInstance(project).state.languageLevel = LuaLanguageLevel.LUA55
        assertSafeDeleteRaisesConflict("global count = 0\nprint(count)\n", "count", LuaGlobalVarDecl::class.java)
        assertSafeDeleteRaisesConflict("global function f() end\nf()\n", "f", LuaGlobalFuncDecl::class.java)
    }

    // -------------------------------------------------------------------------
    // TC-33: every node declarationNodeOf can produce must round-trip back through
    // identifierLeafOf, and handlesElement must admit it. TC-32 proves the user-visible outcome;
    // this proves why.
    // -------------------------------------------------------------------------

    @Test
    fun testEveryElevatedDeclarationNodeRoundTrips() {
        assertElevationRoundTrips("cfg = {}\n", "cfg")
        assertElevationRoundTrips("M = {}\nfunction M.run() end\n", "run")
        // Pre-existing multi-name shape: the node is the LuaAttName, not the whole `local`.
        assertElevationRoundTrips("local a, b = 1, 2\n", "a")
        // Newly Safe-Deletable via design §3.5 row 14: the node is the LuaVar.
        assertElevationRoundTrips("a, b = 1, 2\n", "a")

        LuaProjectSettings.getInstance(project).state.languageLevel = LuaLanguageLevel.LUA55
        assertElevationRoundTrips("global x = 1\n", "x")
        assertElevationRoundTrips("global function f() end\n", "f")

        assertReadIsNotADeclaration()
        assertMultiTargetDeleteLeavesTheSeparator()
    }

    /**
     * The known granularity gap (`risks-and-gaps.md` Gap 2.6), pinned rather than assumed: nothing
     * removes the separating comma, so deleting one target of a multi-target assignment leaves it.
     * Comma-aware deletion is REFACT-03's business — REFACT-01 changes what Safe Delete *finds*,
     * not what it *removes*.
     */
    private fun assertMultiTargetDeleteLeavesTheSeparator() {
        myFixture.configureByText("test.lua", "a, b = 1, 2\n")
        SafeDeleteHandler.invoke(project, arrayOf(declarationLeaf("a")), true)
        assertEquals("multi-target deletion granularity", ", b = 1, 2\n", myFixture.file.text)
    }

    private fun assertReadIsNotADeclaration() {
        myFixture.configureByText("test.lua", "print(x)\n")
        val readLeaf = declarationLeaf("x")
        assertSame(
            "a read must not elevate: declarationNodeOf returns the leaf itself",
            readLeaf,
            LuaDeclarationSite.declarationNodeOf(readLeaf),
        )
        val enclosingVar =
            requireNotNull(
                PsiTreeUtil
                    .findChildrenOfType(myFixture.file, LuaVar::class.java)
                    .firstOrNull { it.nameRef?.text == "x" },
            ) { "Expected a LuaVar around the read `x`" }
        assertFalse(
            "handlesElement must be false for the LuaVar around a read",
            processor.handlesElement(enclosingVar),
        )
    }

    private fun assertElevationRoundTrips(
        text: String,
        declaredName: String,
    ) {
        myFixture.configureByText("test.lua", text)
        val leaf = declarationLeaf(declaredName)
        val node = LuaDeclarationSite.declarationNodeOf(leaf)
        assertSame(
            "declarationNodeOf/identifierLeafOf must round-trip for `$text`",
            leaf,
            LuaDeclarationSite.identifierLeafOf(node),
        )
        assertTrue(
            "handlesElement must be true for the elevated node of `$text`, or Safe Delete skips usage search",
            processor.handlesElement(node),
        )
    }

    private fun assertSafeDeleteRaisesConflict(
        text: String,
        declaredName: String,
        survivingType: Class<out PsiElement>,
    ) {
        myFixture.configureByText("test.lua", text)
        try {
            SafeDeleteHandler.invoke(project, arrayOf(declarationLeaf(declaredName)), true)
            fail("Safe Delete of a used declaration must raise a conflict, not delete: ${myFixture.file.text}")
        } catch (expected: BaseRefactoringProcessor.ConflictsInTestsException) {
            // expected: the usage is reported as a conflict
        }
        assertFalse(
            "The used declaration must survive when a conflict is raised: ${myFixture.file.text}",
            PsiTreeUtil.findChildrenOfType(myFixture.file, survivingType).isEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // SYNTAX-18 regression (REFACT-01 Phase 1): handlesElement is reached for whatever element the
    // platform offers, so it must survive a partially parsed declaration. `LuaLocalFuncDecl`'s
    // `getNameRef()` is declared @NotNull but returns null when a keyword sits in the name slot,
    // and the platform LOGS AN ERROR rather than returning null — which under BasePlatformTestCase
    // is a TestLoggerAssertionError and in production an internal-error balloon. Measured: with the
    // generated getter in `identifierLeafOf`, this fixture raised one where the pre-REFACT-01
    // predicate answered plainly.
    // -------------------------------------------------------------------------

    @Test
    fun testPartiallyParsedLocalFunctionIsHandledWithoutLoggingAnError() {
        myFixture.configureByText("test.lua", "local function repeat() end\n")
        val decl =
            requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, LuaLocalFuncDecl::class.java)) {
                "Expected a LuaLocalFuncDecl even though its name slot holds a keyword"
            }
        assertFalse(
            "A nameless declaration is not an elevated declaration — and asking must not log",
            processor.handlesElement(decl),
        )
        assertNull(
            "identifierLeafOf must answer null for a decl with no nameRef, not raise",
            LuaDeclarationSite.identifierLeafOf(decl),
        )
    }

    /**
     * The SECOND route into the same hazard, and the one the first test does not reach.
     * `funcDecl ::= FUNCTION funcName funcBody` is `pin = 1` (`lua.bnf:189-190`), so a plain
     * `function repeat() end` also produces a declaration node with no name child — and
     * `LuaFuncDecl.getFuncName()` is `@NotNull` (`LuaFuncDecl.java:20-21`). Measured at `c541aefb`
     * this raised `TestLoggerAssertionError` inside `PsiElementBase.notNullChild`, from evaluating
     * the ARGUMENT of `functionNameLeafOf(element.funcName)` — before that function was entered, so
     * no change inside it could have covered this.
     */
    @Test
    fun testPartiallyParsedFunctionDeclarationIsHandledWithoutLoggingAnError() {
        myFixture.configureByText("test.lua", "function repeat() end\n")
        val decl =
            requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, LuaFuncDecl::class.java)) {
                "Expected a LuaFuncDecl even though its name slot holds a keyword"
            }
        assertNull(
            "identifierLeafOf must answer null for a funcDecl with no funcName, not raise",
            LuaDeclarationSite.identifierLeafOf(decl),
        )
        assertFalse(
            "A nameless function declaration is not an elevated declaration — and asking must not log",
            processor.handlesElement(decl),
        )
    }

    /**
     * The generalisation of the two tests above, and the reason they are not the whole guard: two
     * routes were found one at a time, by two separate measurements, because each test named a
     * single PSI type. `handlesElement` is called with *whatever element the platform offers*, so
     * the property is "no element of a partially parsed file makes it raise" — asked of every
     * element, over the broken shape of each declaration form that carries a `pin`.
     *
     * A new `identifierLeafOf` row that dereferences a generated `@NotNull` getter fails here
     * without anyone remembering to add a fixture for it.
     */
    @Test
    fun testNoElementOfAPartiallyParsedDeclarationMakesTheProcessorLog() {
        val brokenFixtures =
            listOf(
                "local function repeat() end\n",
                "function repeat() end\n",
                "function M.repeat() end\n",
                "local repeat = 1\n",
                "repeat = 1\n",
                // globalFuncDecl carries pin = 2 (`lua.bnf:229`), so a GLOBAL_FUNC_DECL node
                // exists with no nameRef child. It was the one pinned broken-declaration form
                // with no fixture here, safe only because the generated getter happens to be
                // @Nullable — one generator annotation away from being uncaught.
                "global function repeat() end\n",
            )
        brokenFixtures.forEach { text ->
            myFixture.configureByText("test.lua", text)
            PsiTreeUtil.collectElements(myFixture.file) { true }.forEach { element ->
                // Each of these is a TestLoggerAssertionError, not a false answer, when a row
                // reaches a generated @NotNull getter on an absent child.
                LuaDeclarationSite.identifierLeafOf(element)
                LuaDeclarationSite.declarationNodeOf(element)
                processor.handlesElement(element)
            }
        }
    }

    // -------------------------------------------------------------------------
    // TC-14 (REFACT-07-12): the PSI change alters no behaviour Lunar ships a feature for
    // -------------------------------------------------------------------------

    /**
     * TC-14 (`REFACT-07-12`) — REFACT-07 §3.1 grants `PsiNameIdentifierOwner` to every
     * `LuaNameRef`, and Safe Delete is one of the platform behaviours design §4's consumer audit
     * requires to be observably unchanged by it. DR-03 measured it as byte-identical across the
     * base and treatment commits and found that **no test in the 2851-name set asserted the
     * resulting document text**, which is what this case adds.
     *
     * It asserts the *file*, not merely that no [LuaLocalVarDecl] survives, because the two differ
     * exactly where the mutation lands.
     *
     * **Mutation:** change [LuaSafeDeleteProcessor]'s elevation so the bare IDENTIFIER leaf is
     * deleted instead of its statement — the file is left as `local  = 0`, a parse error, with no
     * `LuaLocalVarDecl` in it either. The fixture's declaration has a statement-level container,
     * which is what the elevation targets.
     *
     * **Phase 4 verdict: RED**, executed 2026-08-26 — `junit.framework.ComparisonFailure: the whole
     * `local unused = 0` statement must go, leaf and container together`, `expected:<[]> but was:
     * <[local = 0]>`, the parse error this KDoc predicts. No other case in the run reddened.
     */
    @Test
    fun testSafeDeletingAnUnusedLocalRemovesTheWholeStatement() {
        myFixture.configureByText("test.lua", "local unu<caret>sed = 0\n")

        SafeDeleteHandler.invoke(project, arrayOf(declarationLeaf("unused")), true)

        assertEquals(
            "the whole `local unused = 0` statement must go, leaf and container together",
            "",
            myFixture.file.text.trim(),
        )
    }

    /** The FIRST IDENTIFIER leaf of that name — the declaration, in every fixture here. */
    private fun declarationLeaf(name: String): PsiElement =
        requireNotNull(
            PsiTreeUtil
                .collectElements(myFixture.file) { it.elementType == LuaElementTypes.IDENTIFIER && it.text == name }
                .firstOrNull(),
        ) { "No IDENTIFIER leaf '$name' in ${myFixture.file.text}" }
}

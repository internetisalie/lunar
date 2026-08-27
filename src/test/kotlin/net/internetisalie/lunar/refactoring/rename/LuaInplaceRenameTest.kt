package net.internetisalie.lunar.refactoring.rename

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import net.internetisalie.lunar.LuaBundle
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REFACT-07 — the in-place (inline) rename template, across the three layers design §6 requires.
 *
 * **No case in this class puts `CommonDataKeys.PSI_ELEMENT` into a data context.** Every context
 * comes from `DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)`, so
 * `PsiElementRenameHandler.getElement(context)` returns what `TargetElementUtil` computes rather
 * than what the case assumed. The suite this replaces injected the element in every case
 * (`SimpleDataContext…add(CommonDataKeys.PSI_ELEMENT, …)`), which is precisely why it could stay
 * green while a live editor blanked three usages: it measured the plan's assumption, not the
 * platform. DR-05 measured, over thirteen carets, that the two differ — a declaration caret
 * supplies the composite `LuaNameRefImpl`, while a usage caret and a parameter-declaration caret
 * both supply the IDENTIFIER **leaf**.
 *
 * **The three layers, and why no one of them can stand alone.**
 *
 * 1. **Registry** — [renameHandlersAtCaret] asks `RenameHandlerRegistry` what <kbd>Shift+F6</kbd>
 *    would select. This is the only layer that can see an availability predicate at all, and the
 *    only one that sees Ground 3's silent-selection hazard: with two in-place handlers available
 *    the registry *deletes* the `MemberInplaceRenameHandler`
 *    (`RenameHandlerRegistry.java:114-119`) and the user silently gets the other one.
 * 2. **Predicate** — [memberInplaceRenameOfferedAtCaret] drives
 *    `MemberInplaceRenameHandler().isAvailableOnDataContext`, which is `final` on
 *    `VariableInplaceRenameHandler` (`:33`) and dispatches to the overridden `isAvailable`, so
 *    constructing the member handler is enough to exercise the member predicate.
 * 3. **Document** — drive a handler to commit and assert the resulting text.
 *
 * **A document-layer case cannot see a predicate**, which is why layer 1 exists.
 * `CodeInsightTestUtil.tryInlineRename` calls `doRename` directly (`CodeInsightTestUtil.java:246`)
 * and [renameInPlaceViaHandler] calls `RenameHandler.invoke` directly; neither consults
 * `isInplaceRenameAvailable` or `isMemberInplaceRenameAvailable`, and both construct the handler
 * themselves, so a document-only suite would also stay green with the `renameHandler`
 * registration absent.
 *
 * **Every document-layer case asserts that a template started**, not only what the document says.
 * `tryInlineRename` returns `false` and asserts *nothing* when no template starts
 * (`CodeInsightTestUtil.java:250-256`), and [renameInPlaceViaHandler] returns `false` in the same
 * circumstance — so a case whose expected text is the *unchanged* file is otherwise satisfied by
 * the feature being absent. [testCancellingTheTemplateRestoresTheDocument] drives neither helper
 * and states the assertion directly.
 *
 * The one exemption is [testACapturingNewNameIsRefusedWithTheCaptureConflict], and it is earned by
 * measurement rather than argued from the shape of the case: its gating assertion is that the
 * driver *throws* `ConflictsInTestsException`, and with no template there is no commit, no
 * `RenameProcessor` and therefore no exception. DR-04 measured both halves on that exact fixture.
 *
 * **Which driver a case uses is part of the case**, and it follows from which element the data
 * context supplies at that case's caret, not from whether the caret is on a declaration. A caret
 * supplying the declaring `LuaNameRef` uses `CodeInsightTestUtil.tryInlineRename`; a caret
 * supplying an IDENTIFIER leaf uses [renameInPlaceViaHandler] on a [LuaInplaceRenameHandler],
 * which implements `RenameHandler` directly and therefore does not satisfy `tryInlineRename`'s
 * `VariableInplaceRenameHandler` parameter type (`CodeInsightTestUtil.java:236`). Using the wrong
 * driver measures the wrong route: on a leaf, `MemberInplaceRenameHandler.doRename` refuses at
 * `MemberInplaceRenameHandler.java:56` and falls through to `performDialogRename` at `:87`.
 *
 * `requirements.md`'s test-case table is the single list of which case is which; each case's KDoc
 * below names its own row and the mutation that row admits.
 */
@RunWith(JUnit4::class)
class LuaInplaceRenameTest : BasePlatformTestCase() {
    // -------------------------------------------------------------------------
    // Layer 1 — registry
    // -------------------------------------------------------------------------

    /**
     * TC-02 (`REFACT-07-02`) — exactly one handler claims a file-local declaration, and it is the
     * platform's member handler.
     *
     * **Mutation:** restore `isInplaceRenameAvailable` to the shipped predicate (design §3.2). Two
     * handlers then become available, the registry drops the `MemberInplaceRenameHandler`
     * (`RenameHandlerRegistry.java:114-119`) and the single survivor is a
     * `VariableInplaceRenameHandler` — a different renamer, on a different route, with no
     * `LuaRenameProcessor` commit. The fixture is a file-local declaration, the only input for
     * which the shipped predicate answers `true`.
     *
     * The `is MemberInplaceRenameHandler` test also distinguishes the platform's handler from
     * Lunar's: `LuaInplaceRenameHandler` implements `RenameHandler` directly and is not a
     * `MemberInplaceRenameHandler` (design §2.3, measured DR-02 probe 3), so this case confirms in
     * passing that Lunar's handler declines the `LuaNameRef` composite this caret supplies.
     *
     * **Phase 4 verdict: RED**, executed 2026-08-26 — `AssertionFailedError: the selected handler must
     * be the platform's member handler, not a `VariableInplaceRenameHandler`, which is what the
     * instance in the message was
     * which is the registry's removal loop leaving the variable handler behind. No other case in the
     * run reddened, so this mutant isolates the predicate.
     */
    @Test
    fun testExactlyOneHandlerClaimsAFileLocalDeclarationAndItIsTheMemberHandler() {
        myFixture.configureByText("test.lua", LOCAL_DECLARATION_FIXTURE)

        val claimants = renameHandlersAtCaret()

        assertEquals("Shift+F6 on a local declaration must select exactly one handler: $claimants", 1, claimants.size)
        assertTrue(
            "the selected handler must be the platform's member handler, not $claimants",
            claimants.first() is MemberInplaceRenameHandler,
        )
    }

    /**
     * TC-10 (`REFACT-07-10`) — a global takes the dialog, which is where its cross-file preview
     * pane lives.
     *
     * **Mutation:** delete the `isFileLocal` clause from `isMemberInplaceRenameAvailable` (design
     * §3.2); the returned handler becomes a `MemberInplaceRenameHandler` and both assertions fail.
     * The data context supplies the declaring `LuaNameRef` at this caret (DR-05 probe `e`, this
     * exact fixture, `LuaNameRefImpl` at `(0,6)`), so §3.2's predicate is the one on the path, and
     * the declaration classifies `GLOBAL_VARIABLE`, whose `isFileLocal` is `false` — the input that
     * clause refuses.
     *
     * The corresponding `isFileLocal` mutation in `LuaInplaceRenameHandler.declaringNameRefOf` is
     * **inert** here and must not be substituted: Lunar's handler never sees this caret's element,
     * because its first step requires an `IDENTIFIER` node type and this caret supplies a
     * composite.
     *
     * **Phase 4 verdict: RED**, executed 2026-08-26 — `AssertionFailedError: a global's usages are not
     * confined to this file, so no inline template may preview them:
     * [com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler@...]`. The predicate guard
     * `testMemberInplaceRenameIsWithheldFromAGlobalDeclaration` reddened alongside it, which is
     * expected: it asserts the same deleted clause one layer down.
     */
    @Test
    fun testNoInplaceHandlerClaimsAGlobalDeclaration() {
        myFixture.configureByText("test.lua", "con<caret>fig = {}\nprint(config)\n")

        val claimants = renameHandlersAtCaret()

        assertFalse(
            "a global's usages are not confined to this file, so no inline template may preview them: $claimants",
            claimants.any { it is VariableInplaceRenameHandler },
        )
        assertTrue(
            "a global must fall back to the platform's dialog handler, not $claimants",
            claimants.singleOrNull() is PsiElementRenameHandler,
        )
    }

    /**
     * TC-11 (`REFACT-07-13`) — REFACT-04's label rename keeps the handler it already had.
     *
     * **Mutation:** delete the `elementToRename is LuaLabelName` clause from
     * `isMemberInplaceRenameAvailable` (design §3.2). No in-place handler is then available for a
     * label, the registry falls back to `PsiElementRenameHandler`
     * (`RenameHandlerRegistry.java:121-123`) and the assertion fails.
     *
     * **This is a registry-layer case, and the document-layer half is elsewhere on purpose**:
     * `tryInlineRename` bypasses both predicates, so a document-layer label case could not see this
     * clause at all. `LuaLabelRenameTest` carries the document-layer half and must stay green and
     * unmodified.
     *
     * DR-05 probe `g` measured this exact fixture supplying `LuaLabelNameImpl`, with
     * `isAvailableOnDataContext` true and `getRenameHandlers` returning that handler alone, so the
     * expectation is today's measured behaviour and the mutation reaches the predicate. Lunar's own
     * handler declines a label composite — `declaringNameRefOf`'s first step requires an
     * `IDENTIFIER` node type and a `LuaLabelName` is a `LABEL_NAME` composite.
     *
     * **Phase 4 verdict: RED**, executed 2026-08-26 — `AssertionFailedError: REFACT-04's label rename
     * must keep the member handler, not [com.intellij.refactoring.rename.PsiElementRenameHandler@...]`,
     * the registry fallback at `RenameHandlerRegistry.java:121-123`. `LuaLabelRenameTest` stayed green
     * under the mutant, which is this row's claim that the document layer cannot see this clause.
     */
    @Test
    fun testExactlyOneHandlerClaimsALabelAndItIsTheMemberHandler() {
        myFixture.configureByText("test.lua", "::ret<caret>ry::\ngoto retry\n")

        val claimants = renameHandlersAtCaret()

        assertEquals("Shift+F6 on a label must select exactly one handler: $claimants", 1, claimants.size)
        assertTrue(
            "REFACT-04's label rename must keep the member handler, not $claimants",
            claimants.first() is MemberInplaceRenameHandler,
        )
    }

    /**
     * **Guard** — TC-12 (`REFACT-07-14`). A green run of this case is evidence that nothing
     * regressed, not evidence that the feature works, and it must not be counted as coverage.
     *
     * No mutation in code this repo can edit reddens it, and that is measured rather than argued:
     * at this caret the data context supplies **null** and `RenameHandlerRegistry` returns an
     * **empty** handler list (DR-05 probes `f`, `f2` and `f3` — all three caret placements, so it
     * is not a whitespace artifact). No Lunar predicate is on the path: the gate at
     * `MemberInplaceRenameHandler.java:46` is never reached with a null element, and
     * `LuaInplaceRenameHandler.declaringNameRefOf` returns at its first line for one. Widening
     * §3.2's first clause from `LuaNameRef` to `PsiNamedElement` does not bring it into reach
     * either, because `numericForStatement ::= FOR IDENTIFIER '='` (`lua.bnf:152`) produces no
     * `LuaNameRef` to anchor on. The only reddening mutation is on the grammar, which is outside
     * the routine sweep. `risks-and-gaps.md` Risk 1.7 carries the argument.
     *
     * **Phase 4: recorded as a GUARD, not as a passed mutation.** No mutation was executed against
     * this case, because none in code this repo can edit reaches it — the argument is the one above,
     * measured by DR-05 probes `f`/`f2`/`f3`: the data context supplies **null** at this caret and the
     * registry returns an empty handler list, so no Lunar predicate is on the path. Its green run in
     * every Phase 4 run is evidence that nothing regressed, not evidence that the feature works.
     */
    @Test
    fun testNoHandlerClaimsANumericForControlVariable() {
        myFixture.configureByText("test.lua", "for i<caret> = 1, 10 do\n  print(i)\nend\n")

        val claimants = renameHandlersAtCaret()

        assertTrue(
            "the platform supplies no element at a numeric-for control variable, so none may claim it: $claimants",
            claimants.isEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // Layer 2 — predicate
    // -------------------------------------------------------------------------

    /**
     * The predicate half of TC-02: a file-local declaration caret is offered the inline template by
     * `MemberInplaceRenameHandler.isAvailableOnDataContext`, which is `final` on
     * `VariableInplaceRenameHandler` (`:33`) and dispatches to
     * `LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable`.
     *
     * It is the **member** predicate this asserts, never `isInplaceRenameAvailable`, which design
     * §3.2 makes unconditionally `false`. The two are not interchangeable: asserting the wrong one
     * reports the template available at a caret the registry then hands to a different renamer,
     * which is Ground 3 and is what TC-02's own mutation produces.
     *
     * **Mutation:** delete the `LuaNameRef` + `isFileLocal` clause from
     * `isMemberInplaceRenameAvailable`.
     */
    @Test
    fun testMemberInplaceRenameIsOfferedForAFileLocalDeclaration() {
        myFixture.configureByText("test.lua", LOCAL_DECLARATION_FIXTURE)

        assertTrue(
            "Shift+F6 on a file-local declaration must reach the inline template",
            memberInplaceRenameOfferedAtCaret(),
        )
    }

    /**
     * **Guard** — the global exclusion at the predicate layer. It asserts something is *withheld*,
     * so it stayed green under the retargeting from `VariableInplaceRenameHandler` to
     * `MemberInplaceRenameHandler` and its green run is not evidence the retargeting happened;
     * TC-10 is the gate for this input. DR-01 confirmed that by measurement.
     */
    @Test
    fun testMemberInplaceRenameIsWithheldFromAGlobalDeclaration() {
        myFixture.configureByText("test.lua", "con<caret>fig = {}\nprint(config)\n")

        assertFalse(
            "a global's usages are not confined to this file, so the inline template cannot preview them",
            memberInplaceRenameOfferedAtCaret(),
        )
    }

    /**
     * **Guard** — the platform handler's leaf exclusion, and design §3.5's availability invariant:
     * at a usage caret the data context supplies the IDENTIFIER **leaf** (DR-05 probe `b`, this
     * exact fixture, `LeafPsiElement` at `(6,13)`), which fails the `instanceof
     * PsiNameIdentifierOwner` gate at `MemberInplaceRenameHandler.java:46`. That is what leaves
     * `LuaInplaceRenameHandler` alone in the registry for this caret rather than beside a second
     * claimant — TC-04 drives the same caret through the handler that does serve it.
     *
     * One case covers this caret because the editor supplies one element at it. A suite that
     * *injects* `CommonDataKeys.PSI_ELEMENT` can split it in two — one on a declaration leaf, one on
     * a usage's `LuaNameRef` — but the platform supplies the first at no caret and the second at
     * none at all, so both halves would measure the injection. The class KDoc states why no context
     * in this class is built that way.
     */
    @Test
    fun testMemberInplaceRenameIsWithheldFromAUsageCaret() {
        myFixture.configureByText("test.lua", "local counter = 0\nprint(coun<caret>ter)\n")

        assertFalse(
            "a usage caret supplies the IDENTIFIER leaf, which the platform's member handler refuses",
            memberInplaceRenameOfferedAtCaret(),
        )
    }

    // -------------------------------------------------------------------------
    // Layer 3 — document
    // -------------------------------------------------------------------------

    /**
     * TC-01 (`REFACT-07-01`, `-03`) — the template starts on a file-local declaration and commits.
     *
     * **Mutation:** change `LuaNameRefBaseImpl.getNameIdentifier()` (design §3.1) to `= null`.
     * `buildTemplateAndStart` then calls `getSelectedInEditorElement(null, refs, stringUsages,
     * offset)` (`InplaceRefactoring.java:347` → `:841-862`), whose first loop misses because no
     * collected reference contains a declaration caret, whose `nameIdentifier` branch at
     * `:851-854` is skipped, and whose `stringUsages` is empty — so control reaches `LOG.error` at
     * `:860`, which **throws** under the test logger (`TestLoggerFactory.java:550`, rethrown at
     * `:578-580`). The case therefore goes red *there*, before any template exists and before the
     * harness's own `assert range != null` (`CodeInsightTestUtil.java:257-258`) is reached.
     *
     * The override cannot be **deleted** instead: `PsiNameIdentifierOwner.getNameIdentifier()` has
     * no default (`PsiNameIdentifierOwner.java:14-15`) and no ancestor of `LuaNameRefBaseImpl`
     * supplies one, so a deletion does not compile and measures nothing.
     *
     * The caret is on a declaring `LuaNameRef` (DR-05 probe `a`, this exact fixture), so the
     * mutated accessor sits on the exact element the template anchors to, and the driver is
     * `CodeInsightTestUtil.tryInlineRename` on a `MemberInplaceRenameHandler`.
     *
     * **Phase 4 verdict: RED**, executed 2026-08-26 by the route named above —
     * `TestLoggerFactory$TestLoggerAssertionError: null by MemberInplaceRenamer`, raised from
     * `Logger.error` inside `InplaceRefactoring.getSelectedInEditorElement`, before any template
     * exists. Seven other document-layer cases redden with it. TC-04 does **not**, and the reason is
     * this KDoc's own: a usage caret sits inside a collected reference, so the first loop hits and
     * `LOG.error` is never reached.
     */
    @Test
    fun testTheTemplateCommitsANewNameOnADeclarationAndItsUsage() {
        myFixture.configureByText("test.lua", LOCAL_DECLARATION_FIXTURE)

        assertTrue(TEMPLATE_MUST_START, renameInPlaceViaMemberHandler("total"))

        myFixture.checkResult("local total = 0\nprint(total)\n")
    }

    /**
     * TC-16 (`REFACT-07-01`) — the template commits on a **generic-`for`** variable.
     *
     * `REFACT-07-01` names four declaration kinds. TC-01 drives one of them, the `local`, and its
     * mutation lands on a shared primitive — so a regression confined to this kind would leave the
     * suite green. That is the gap this case closes, and [testTheTemplateCommitsANewNameOnALocalFunctionName]
     * closes the other; the parameter kind is TC-09's and the `local` kind is TC-01's.
     *
     * **Driver:** `CodeInsightTestUtil.tryInlineRename` on a `MemberInplaceRenameHandler`, because
     * at this caret the data context supplies the declaring `LuaNameRef` **composite** — measured
     * on this exact fixture, DR-01 probe `b4b`, `LuaNameRefImpl` at `textRange (4,7)`, with
     * `MemberInplaceRenameHandler.isAvailableOnDataContext` true and Lunar's own handler false.
     * The composite is why this kind takes the platform handler and the `local function` kind does
     * not.
     *
     * **Mutation:** delete the generic-`for` arm from `LuaDeclarationSite.kindFromNameRefGrandParent`
     * — `grandParent is LuaNameList && grandParent.parent is LuaGenericForStatement ->
     * LuaDeclarationKind.GENERIC_FOR_VARIABLE` (`LuaDeclarationSite.kt:244-245`). It is the one
     * mutation that is specific to this declaration kind: `kindOf` then answers null for this
     * variable and for nothing else in the suite's other fixtures.
     *
     * **Verdict: RED**, executed 2026-08-26 over the **full** suite, no `--tests` filter. It reddens
     * on an **escaping exception**, not on a wrong document: with `kindOf` null for the composite,
     * `identifierLeafOf` yields nothing and the `resolvedDeclarationLeaf` fallback refuses, so
     * `CommonRefactoringUtil$RefactoringErrorHintException: Cannot perform refactoring. Cannot
     * determine which declaration this name refers to…` escapes from `LuaRenameProcessor.refuse`
     * (`:518`) ← `resolvedDeclarationLeaf` (`:379`) ← `substituteElementToRename` (`:107`).
     * `2867 tests completed, 3 failed` — this case, `LuaDeclarationSiteTest`'s shape enumeration,
     * and `LuaRenameTest.testRenameGenericForVariable`, the dialog-path sibling for this kind. The
     * mutation is kind-specific as claimed: nothing outside those three sees it.
     */
    @Test
    fun testTheTemplateCommitsANewNameOnAGenericForVariable() {
        myFixture.configureByText("test.lua", GENERIC_FOR_FIXTURE)

        assertTrue(TEMPLATE_MUST_START, renameInPlaceViaMemberHandler("entry"))

        myFixture.checkResult("for entry, value in pairs(t) do\n  print(entry, value)\nend\n")
    }

    /**
     * TC-17 (`REFACT-07-01`) — the template commits on a **`local function`** name.
     *
     * The fourth declaration kind, and the second of the two `REFACT-07-01` named without a
     * document-layer case. See [testTheTemplateCommitsANewNameOnAGenericForVariable] for why one
     * case per kind is required rather than TC-01 standing for all four.
     *
     * **Driver:** [renameInPlaceViaHandler] on a [LuaInplaceRenameHandler], because at this caret
     * the data context supplies the name's IDENTIFIER **leaf** — measured on this exact fixture,
     * DR-01 probe `b5`, `LeafPsiElement(IDENTIFIER)` at `textRange (15,21)`, with
     * `MemberInplaceRenameHandler.isAvailableOnDataContext` false and Lunar's handler true. This is
     * the third caret shape design §3.5 names, alongside the usage caret (TC-04) and the parameter
     * caret (TC-09), and it is the only one of the three with no case until now.
     *
     * **Mutation:** delete the `local function` arm from
     * `LuaDeclarationSite.kindFromNameRefGrandParent` — `grandParent is LuaLocalFuncDecl ->
     * LuaDeclarationKind.LOCAL_FUNCTION` (`LuaDeclarationSite.kt:239`). `declaringNameRefOf`'s
     * third step then refuses the leaf, `invoke` returns before starting a template, and
     * [renameInPlaceViaHandler] returns `false` — so the case reddens on [TEMPLATE_MUST_START],
     * which is the assertion that keeps it from being satisfied by the feature being absent.
     *
     * **Verdict: RED**, executed 2026-08-26 over the **full** suite, no `--tests` filter, by exactly
     * that route: `AssertionFailedError: no in-place template started — the document assertion below
     * is satisfied by the feature being absent`. `2867 tests completed, 3 failed` — this case,
     * `LuaDeclarationSiteTest`'s shape enumeration, and
     * `LuaRenameTest.testRenameLocalFunctionWithRecursiveCall`. Kind-specific as claimed.
     */
    @Test
    fun testTheTemplateCommitsANewNameOnALocalFunctionName() {
        myFixture.configureByText("test.lua", LOCAL_FUNCTION_FIXTURE)

        assertTrue(TEMPLATE_MUST_START, renameInPlaceViaHandler(LuaInplaceRenameHandler(), "compute"))

        myFixture.checkResult("local function compute() return 1 end\nprint(compute())\n")
    }

    /**
     * TC-03 (`REFACT-07-04`) — every usage in the file moves with the declaration.
     *
     * **Mutation:** make `LuaNameReferenceSearcher.processQuery` accept no candidate — replace
     * `reference.isReferenceTo(target)` with `false` at `LuaNameReferenceSearcher.kt:76`. The
     * commit's own `ReferencesSearch` then finds nothing and the file is left reading
     * `local total = 0` / `print(counter)` / `counter = counter + 1`. All three usages are in this
     * fixture's own file, which is the scope the searcher covers.
     *
     * **Not** the `identifierLeafOf` deletion at `:57` that TC-13 uses:
     * `MemberInplaceRenamer.collectRefs` searches **twice** (`MemberInplaceRenamer.java:173-183`),
     * once on the `LuaNameRef` composite and once on `getSubstituted()`, which for Lunar is the
     * already-normalised IDENTIFIER leaf — the second search survives that mutation and yields all
     * three usages, leaving the case GREEN. `risks-and-gaps.md` Risk 1.5 records the double search.
     *
     * **What this case does not cover:** `REFACT-07-04` says no usage is left on the old text
     * *while the template is live*, and the committed document cannot distinguish live mirroring
     * from the commit's own rename, because both run `LuaRenameProcessor`. The live half is
     * observed by DR-01 probes (d) and (e) and by `human-verification-checklists.md`.
     *
     * **Phase 4 verdict: RED**, executed 2026-08-26 — `FileComparisonFailedError` out of
     * `myFixture.checkResult`: the declaration moved to `total` while all three usages stayed on
     * `counter`, the document this KDoc predicts. The mutant is **broad, not isolating**: it
     * reddens every case in the suite that needs the searcher to find a usage, a set that grows with
     * each new consumer. TC-03 is not exempt from Phase 2's fail-first pass, so that breadth is
     * recorded rather than narrowed.
     */
    @Test
    fun testEveryUsageInTheFileMovesWithTheDeclaration() {
        myFixture.configureByText("test.lua", THREE_OCCURRENCE_FIXTURE)

        assertTrue(TEMPLATE_MUST_START, renameInPlaceViaMemberHandler("total"))

        myFixture.checkResult(THREE_OCCURRENCE_RENAMED)
    }

    /**
     * TC-05 (`REFACT-07-05`) — the template's commit produces the dialog path's exact result.
     *
     * **Mutation:** change `LuaRenameProcessor.renameElement` to rewrite the declaration and skip
     * the usage loop. **Both** executions change together, so the hard-coded expectation reddens
     * and the equality assertion stays green — which is why the hard-coded text is the load-bearing
     * half here. The equality is the guard against a route that stops using the processor at all.
     *
     * `myFixture.renameElementAtCaret` hard-codes `searchInComments=false` and
     * `searchTextOccurrences=false`, so it is a parity control and not a gate on those flags.
     *
     * **Phase 4 verdict: RED on the hard-coded text**, executed 2026-08-26 —
     * `junit.framework.ComparisonFailure: the template's commit must produce the specified text`,
     * `expected:<...print([total)...>` against `but was:<...print([counter)...>`. The equality
     * assertion stayed green, which is the prediction above measured rather than argued.
     */
    @Test
    fun testTheTemplateCommitMatchesTheDialogPathExactly() {
        myFixture.configureByText("test.lua", THREE_OCCURRENCE_FIXTURE)
        assertTrue(TEMPLATE_MUST_START, renameInPlaceViaMemberHandler("total"))
        val afterTemplate = myFixture.file.text

        myFixture.configureByText("test.lua", THREE_OCCURRENCE_FIXTURE)
        myFixture.renameElementAtCaret("total")
        val afterDialog = myFixture.file.text

        assertEquals("the template's commit must produce the specified text", THREE_OCCURRENCE_RENAMED, afterTemplate)
        assertEquals("the dialog's commit must produce the specified text", THREE_OCCURRENCE_RENAMED, afterDialog)
        assertEquals("the two rename paths must not diverge", afterDialog, afterTemplate)
    }

    /**
     * TC-06 (`REFACT-07-06`) — cancelling the template restores the document.
     *
     * **Mutation:** change `LuaNameRefBaseImpl.getNameIdentifier()` (design §3.1) to `= null` —
     * TC-01's mutation, on TC-01's fixture, and therefore TC-01's failure route: `LOG.error` at
     * `InplaceRefactoring.java:859` in GoLand 2026.1.3 — `:860` in the `intellij-community`
     * checkout the design cites, and `risks-and-gaps.md` Risk 1.3 records why both are given —
     * **throws** inside `doRename`, before the template-started assertion below ever executes.
     *
     * **This case is a gate, not a guard, and the template-started assertion is what makes it
     * one.** With only the document text asserted, the mutated tree leaves the file at exactly the
     * expected content and the case would pass with the feature absent.
     *
     * **What the mutation does not reach** is Esc-restore itself, which is entirely the platform's:
     * `InplaceRefactoring`'s own template listener reverts the document and this feature adds no
     * commit path, no document write and no template listener of its own. So this case gates "a
     * template started and the document came back unchanged"; that the *restoration* is what
     * returned it is evidenced live, by `human-verification-checklists.md`'s "Cancel restores" item
     * and DR-01 probe (e).
     *
     * **Phase 4 verdict: RED**, executed 2026-08-26 under TC-01's mutation and by TC-01's route —
     * `TestLoggerFactory$TestLoggerAssertionError: null by MemberInplaceRenamer`, thrown inside
     * `doRename` before the template-started assertion runs. The redness is a throw, not a failed
     * assertion, exactly as this KDoc states.
     */
    @Test
    fun testCancellingTheTemplateRestoresTheDocument() {
        myFixture.configureByText("test.lua", LOCAL_DECLARATION_FIXTURE)
        val disposable = Disposer.newDisposable()
        try {
            TemplateManagerImpl.setTemplateTesting(disposable)
            val context = contextAtCaret()
            val target = requireNotNull(PsiElementRenameHandler.getElement(context)) { "no element at caret" }
            MemberInplaceRenameHandler().doRename(target, myFixture.editor, context)
            val started = TemplateManagerImpl.getTemplateState(myFixture.editor)
            assertNotNull(TEMPLATE_MUST_START, started)
            val live = requireNotNull(started)
            val range = requireNotNull(live.currentVariableRange) { "template started with no current variable" }
            WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
                myFixture.editor.document.replaceString(range.startOffset, range.endOffset, "tot")
            }
            live.gotoEnd(true)
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        } finally {
            Disposer.dispose(disposable)
        }

        myFixture.checkResult(LOCAL_DECLARATION_FIXTURE)
    }

    /**
     * TC-07 (`REFACT-07-07`) — a reserved word typed into the template does not commit.
     *
     * **Mutation:** delete the reserved-word half of `LuaNamesValidator.isIdentifier`, leaving only
     * `IDENTIFIER_PATTERN.matches(name)` (`LuaNamesValidator.kt:21`). `end` matches the pattern,
     * `findProblem()` no longer cancels the template, and the commit proceeds into
     * `LuaRenameProcessor.renameElement`. **It reddens by exception, not by text:**
     * `LuaElementFactory.createIdentifier(project, "end")` returns null for a reserved word — its
     * own KDoc says why (`LuaElementFactory.kt:12-28`) — so `refuseRewrite` throws
     * `IncorrectOperationException` (`LuaRenameProcessor.kt:509-510`) before any edit. The document
     * stays exactly as asserted; the uncaught exception is what fails the case, which is why the
     * case asserts the text **and** lets any exception escape.
     *
     * `end` is a Lua keyword *and* matches the identifier pattern, so this fixture reaches the
     * deleted clause.
     *
     * **Phase 4 verdict: RED by exception**, executed 2026-08-26 — `java.lang.RuntimeException`
     * wrapping `IncorrectOperationException: Rename was not applied: 'end' cannot be written as a Lua
     * identifier, so the declaration and its usages cannot be rewritten together`, from
     * `refuseRewrite`. No other case in the rename suites reddened, so the mutant is this case's alone.
     */
    @Test
    fun testAReservedWordIsNotCommittedAsANewName() {
        myFixture.configureByText("test.lua", LOCAL_DECLARATION_FIXTURE)

        assertTrue(TEMPLATE_MUST_START, renameInPlaceViaMemberHandler("end"))

        myFixture.checkResult(LOCAL_DECLARATION_FIXTURE)
    }

    /**
     * TC-08 (`REFACT-07-08`) — a rename that would rebind is refused with the dialog path's rules,
     * and nothing is written.
     *
     * **This case is exempt from the class's template-started rule, by measurement**: the THROWS
     * assertion is that the driver raises `ConflictsInTestsException`, and with no template there
     * is no commit, no `RenameProcessor` and therefore no exception, so it cannot pass with the
     * feature absent. DR-04 measured both halves on this exact fixture — the throw, and a
     * non-colliding new name committing normally as the positive control that a template does start
     * here. It is **not** exempt from the fail-first pass.
     *
     * **The MESSAGE assertion is the gate, and it is a `contains`, never an equality.** DR-04
     * measured this fixture tripping **two** rules: the capture rule and the shadow rule, with
     * `getMessages()` holding both. An equality would couple this case to
     * `refactoring.rename.conflict.shadow`'s wording.
     *
     * **Mutation:** delete the capture rule from `LuaRenameConflictDetector.collisions`
     * (`LuaRenameConflictDetector.kt:120-131`, the `captures(target, usages)` term at `:125`).
     * Measured: the rename is still refused, still by `ConflictsInTestsException`, and the document
     * is still unchanged, because the shadow rule still fires. What changes is the message — so
     * THROWS and DOCUMENT stay green and MESSAGE is the only assertion that reddens. A TC-08
     * asserting only the exception type and the unchanged text would be green under its own named
     * mutation.
     *
     * **Class of mutant:** it deletes a `LuaRenameConflictDetector` rule that the dialog path
     * shares, so `LuaRenameConflictTest.testCaptureOfRenamedUsageIsReported` reddens under it too.
     * It is therefore not absence-detecting, and this case's absence evidence comes from the
     * fail-first pass instead.
     *
     * **Drain before reading anything** — the exception arrives on the asynchronous drain, not on
     * `gotoEnd`. In between, the document transiently holds two bindings spelled the same with
     * nothing thrown; a case reading it in that window reads an invalid intermediate.
     *
     * **Phase 4 verdict: RED on the MESSAGE assertion, and on that assertion alone**, executed
     * 2026-08-26 — `AssertionFailedError: expected the capture conflict among the messages the dialog
     * would have shown`, the surviving message being the shadow rule's. THROWS and DOCUMENT stayed
     * green under the mutant, which is why the message is the gate. `LuaRenameConflictTest`'s
     * `testCaptureOfRenamedUsageIsReported` reddened too, as predicted, and so did
     * `testCancellationIsCheckedPerUsageNotPerCollisionsCall`, whose per-usage checks live in the
     * deleted rule. Neither is a defect: this mutant is not absence-detecting and is not required to
     * be, because TC-08 took Phase 2's fail-first pass.
     */
    @Test
    fun testACapturingNewNameIsRefusedWithTheCaptureConflict() {
        myFixture.configureByText("test.lua", CONFLICTING_FIXTURE)

        val reported = conflictsFromRenamingInPlaceTo("total")

        assertTrue(
            "expected the capture conflict among the messages the dialog would have shown:\n  " +
                reported.joinToString("\n  "),
            LuaBundle.message("refactoring.rename.conflict.capture", "total", "counter") in reported,
        )
        myFixture.checkResult(CONFLICTING_FIXTURE)
    }

    /**
     * TC-15 (`REFACT-07-15`) — the whole document is asserted after a commit, which is the layer
     * every prior attempt lacked.
     *
     * **Mutation:** delete the final `LuaDeclarationSite.kindOf(element) != null` disjunct from
     * `LuaRenameProcessor.canProcessElement` (`LuaRenameProcessor.kt:90`), leaving only the
     * `element is LuaNameRef` test. The processor no longer claims the substituted IDENTIFIER
     * **leaf**, so `PsiElementRenameHandler.getRenameErrorMessage`'s first clause is satisfied —
     * `hasRenameProcessor` is false and the leaf is not a `PsiNamedElement`
     * (`PsiElementRenameHandler.java:150-158`) — and `canRename` returns a message.
     *
     * **The case reddens by exception, not by an unchanged document.** `canRename` calls
     * `CommonRefactoringUtil.showErrorHint(...)` at `PsiElementRenameHandler.java:139-140` *before*
     * its `return false` at `:141`, and `showErrorHint` throws `RefactoringErrorHintException` in
     * unit-test mode (`CommonRefactoringUtil.java:84-85`). The throw propagates out of
     * `RenamePsiElementProcessorBase.substituteElementToRename` — which would otherwise have
     * returned at its `canRename` gate, `:244` — through `MemberInplaceRenameHandler.doRename` and
     * out of `tryInlineRename`, which therefore never returns anything and never asserts the
     * document. Lunar's own code records this behaviour at `LuaRenameProcessor.kt:97-99`.
     *
     * **Not** the historical `getUseScope`-plus-no-`getNameIdentifier` mutation: that document
     * state was measured on Route A, which this design does not take, and half of it does not
     * compile (see [testTheTemplateCommitsANewNameOnADeclarationAndItsUsage]).
     *
     * **Phase 4 verdict: RED by an escaping exception**, executed 2026-08-26 —
     * `CommonRefactoringUtil$RefactoringErrorHintException: Cannot perform refactoring.` /
     * `Caret should be positioned at symbol to be renamed`, through the exact frames named above:
     * `CommonRefactoringUtil.showErrorHint(:85)` from `PsiElementRenameHandler.canRename(:139)` from
     * `RenamePsiElementProcessorBase.substituteElementToRename(:244)` from
     * `MemberInplaceRenameHandler.doRename(:64)`. `tryInlineRename` never returned, so no document was
     * asserted — which is why this case may not be recorded as an unchanged-document verdict.
     */
    @Test
    fun testTheWholeDocumentIsAssertedAfterTheCommit() {
        myFixture.configureByText("test.lua", THREE_OCCURRENCE_FIXTURE)

        assertTrue(TEMPLATE_MUST_START, renameInPlaceViaMemberHandler("total"))

        myFixture.checkResult(THREE_OCCURRENCE_RENAMED)
    }

    /**
     * TC-04 (`REFACT-07-11`) — a usage-site caret opens the template on the declaration.
     *
     * The data context supplies the IDENTIFIER **leaf** at this caret (DR-05 probe `b`, this exact
     * fixture), so the driver is [renameInPlaceViaHandler] on a [LuaInplaceRenameHandler] and
     * **not** `CodeInsightTestUtil.tryInlineRename`, whose `VariableInplaceRenameHandler` parameter
     * type that class does not satisfy.
     *
     * **Mutation:** delete the leaf→`LuaNameRef` normalisation from
     * [LuaInplaceRenameHandler].`invoke` (design §2.3), passing
     * `PsiElementRenameHandler.getElement(context)` straight to the delegated
     * `MemberInplaceRenameHandler().doRename` — the leaf fails the `instanceof
     * PsiNameIdentifierOwner` gate at `MemberInplaceRenameHandler.java:56`, control falls through
     * to `performDialogRename` at `:87`, no template starts, the driver returns `false` and the
     * document is still `local counter = 0`.
     *
     * **That mutant is what discharges this case's exemption from Phase 2's fail-first pass**: the
     * normalisation is the whole of `LuaInplaceRenameHandler`'s own contribution, so removing it is
     * removing the feature. It must be applied in `invoke`, not in `declaringNameRefOf`. Both
     * redden the case, but by different mechanisms — `invoke` reddens through the platform's
     * refusal of the leaf, the helper reddens through `invoke`'s own early return, which is the
     * availability gate's claim and not this case's.
     *
     * Reachable only from a caret whose context supplies the leaf: a caret supplying the declaring
     * `LuaNameRef` never reaches Lunar's handler at all (design §3.5's availability invariant).
     * Note that `LuaRenameProcessor.substituteElementToRename`'s `resolvedDeclarationLeaf` fallback
     * (`LuaRenameProcessor.kt:107`) is **not** on this path — §3.5 hands the handler the
     * *declaring* `LuaNameRef`, for which `identifierLeafOf` succeeds at `:106` — so mutating that
     * fallback would leave this case green.
     *
     * **Phase 4 verdict: RED, and absence-detecting**, executed 2026-08-26. The mutant reddened
     * exactly this case and TC-09 and nothing else in the rename suites, which is what discharges this
     * case's exemption from Phase 2's fail-first pass.
     *
     * **The mechanism is not the one predicted above, and the correction belongs to the row.** The
     * fall-through to `performDialogRename` is real and was observed in the stack
     * (`MemberInplaceRenameHandler.doRename` at `:88` in the shipped GoLand 2026.1.3, `:87` in the
     * `intellij-community` checkout design §1 names). But headlessly it runs a real `RenameProcessor`
     * with `initialName = null`, so `LuaRenameProcessor.refuseRewrite` throws
     * `IncorrectOperationException: Rename was not applied: '' cannot be written as a Lua identifier`
     * out of the driver. The case therefore never reaches either its return-value assertion or its
     * document assertion: the predicted branch is right, the predicted observable is not.
     */
    @Test
    fun testAUsageCaretOpensTheTemplateOnItsDeclaration() {
        myFixture.configureByText("test.lua", "local counter = 0\nprint(coun<caret>ter)\n")

        assertTrue(TEMPLATE_MUST_START, renameInPlaceViaHandler(LuaInplaceRenameHandler(), "total"))

        myFixture.checkResult("local total = 0\nprint(total)\n")
    }

    /**
     * TC-09 (`REFACT-07-09`) — renaming a parameter moves its `---@param` tag, exactly as the
     * dialog path does. This is Ground 1 of the route decision: under Route A the commit would be
     * `renameSynthetic`, an empty method, and the tag would not move.
     *
     * The fixture is the shape `LuaCatsParamRenameTest.testParamTagFollowsParameter`
     * (`LuaCatsParamRenameTest.kt:52-59`) already drives through the dialog path, so the two are a
     * matched pair. The data context supplies the parameter's IDENTIFIER **leaf** here — measured
     * on this exact fixture, DR-05 probe `d`, `textRange (36,37)` — which
     * `MemberInplaceRenameHandler` refuses at `MemberInplaceRenameHandler.java:56`; Lunar's handler
     * accepts it, because `kindOf` is `PARAMETER`, `isFileLocal` is `true` and the leaf's parent is
     * a `LuaNameRef`. Hence [renameInPlaceViaHandler], not `CodeInsightTestUtil.tryInlineRename`.
     *
     * **Three mutants, with different jobs; Phase 4 executes every one.**
     *
     * **M1 — absence-detecting, and the mutant that discharges this case's exemption from Phase 2's
     * fail-first pass:** TC-04's mutant, applied in `invoke` for the reason that row gives. No
     * template starts, the driver returns `false`, and the document still reads
     * `---@param a number` / `local function f(a) return a end`.
     *
     * **M2 — the requirement's substance, and NOT absence-detecting:** replace the `---@param`
     * hoist in `LuaRenameProcessor.renameElement` with `null` (delete the `PARAMETER` branch at
     * `LuaRenameProcessor.kt:262-267`). The code renames and the tag stays on `a`. That branch is
     * on the commit path the *dialog* shares, so `LuaCatsParamRenameTest.testParamTagFollowsParameter`
     * reddens under it too, with this feature entirely absent. What M2 proves is only that the
     * in-place commit reached `LuaRenameProcessor.renameElement` at all.
     *
     * **M3 — the second absence-detecting mutant:** revert design §3.6's normalisation so
     * `renameElement` classifies its raw argument again — `LuaRenameProcessor.kt:255-256`, where
     * `:255` is the normalisation and `:256` the classification the mutation edits to
     * `LuaDeclarationSite.kindOf(element)`. On the
     * in-place route `MemberInplaceRenamer.getSubstituted()` re-derives the target as a `LuaNameRef`
     * **composite** (`MemberInplaceRenamer.java:367-372`, measured by DR-01 at range `(36,37)`),
     * whose `kindOf` is null, so the `---@param` clause never builds: the file renames and keeps
     * `---@param a number` while reading `f(count)`. **M3 reddens this case and leaves
     * `LuaCatsParamRenameTest` green**, which is what M2 fails to do — the dialog path substitutes
     * to the leaf before `renameElement` (`:105-117`), and `kindOf` of a leaf is `PARAMETER` with or
     * without the normalisation. M3 isolates §3.6's contribution exactly as M1 isolates §2.3's.
     *
     * **Not** a mutation of `isMemberInplaceRenameAvailable`: neither driver consults an
     * availability predicate, so any predicate mutation leaves a document-layer case green.
     *
     * **Phase 4 verdicts, executed 2026-08-26 — RED under all three mutants, by three mechanisms.**
     *
     * **M1 — RED, and absence-detecting.** Reddened exactly this case and TC-04 and nothing else,
     * which discharges this case's exemption from Phase 2's fail-first pass. As for TC-04, the
     * observable is not the predicted one: `performDialogRename` runs a real `RenameProcessor` with
     * `initialName = null` and `refuseRewrite` throws `IncorrectOperationException: … '' cannot be
     * written as a Lua identifier` out of the driver, so no return value and no document are asserted.
     *
     * **M2 — RED, and NOT absence-detecting, as this KDoc states.** `FileComparisonFailedError`; the
     * same mutant reddened `LuaCatsParamRenameTest.testParamTagFollowsParameter` and two of its
     * siblings, with this feature entirely absent. What it proves is only that the in-place commit
     * reached `LuaRenameProcessor.renameElement`.
     *
     * **M3 — RED, and absence-detecting for §3.6.** `FileComparisonFailedError`, and this case was the
     * **only** failure in the run: `LuaCatsParamRenameTest` stayed green, which is what M2 cannot do
     * and what makes M3 the mutant that isolates §3.6's normalisation. **That "only" is over the
     * whole suite, and it was not always so.** Phase 4's sweep ran six test classes, which left
     * three of the seven suites that drive `renameElement` — `LuaRenameTest`,
     * `LuaRenameCrossFileTest`, `LuaRequireRenameTest` — unmutated, so the verdict was a universal
     * drawn from a partial run. Re-executed 2026-08-26 with **no `--tests` filter**:
     * `2867 tests completed, 1 failed, 1 skipped`, this case alone, across every class in the
     * repository. The mutation was applied at
     * `renameElement`'s call site, not to the shared `declarationLeafOf` helper, so `findReferences`
     * kept its own normalisation and the two sites stayed independent.
     */
    @Test
    fun testRenamingAParameterMovesItsParamTag() {
        myFixture.configureByText("test.lua", "---@param a number\nlocal function f(<caret>a) return a end\n")

        assertTrue(TEMPLATE_MUST_START, renameInPlaceViaHandler(LuaInplaceRenameHandler(), "count"))

        myFixture.checkResult("---@param count number\nlocal function f(count) return count end\n")
    }

    // -------------------------------------------------------------------------
    // Drivers and context
    // -------------------------------------------------------------------------

    /**
     * `CodeInsightTestUtil.tryInlineRename`'s body (`CodeInsightTestUtil.java:240-266`) with
     * `handler.doRename(...)` replaced by `handler.invoke(...)` — the entry point
     * `BaseRefactoringAction.performRefactoringAction` uses (`:172`) — and with the
     * `renamer.finish(false)` branch dropped, which loses nothing because
     * `MemberInplaceRenameHandler.doRename` returns `null` on every path (`:74`, `:88`) and
     * `tryInlineRename` already skips that branch for every case in this suite.
     *
     * It exists because `tryInlineRename`'s parameter type is `VariableInplaceRenameHandler`
     * (`CodeInsightTestUtil.java:236`) and [LuaInplaceRenameHandler] implements `RenameHandler`
     * directly (design §2.3), so the platform helper cannot take it. Its `false` return is the same
     * signal `tryInlineRename`'s is: **no template started**.
     */
    private fun renameInPlaceViaHandler(
        handler: RenameHandler,
        newName: String,
    ): Boolean {
        val disposable = Disposer.newDisposable()
        try {
            TemplateManagerImpl.setTemplateTesting(disposable)
            val context = contextAtCaret()
            handler.invoke(project, myFixture.editor, myFixture.file, context)
            val started = TemplateManagerImpl.getTemplateState(myFixture.editor) ?: return false
            val range = requireNotNull(started.currentVariableRange) { "template started with no current variable" }
            WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
                myFixture.editor.document.replaceString(range.startOffset, range.endOffset, newName)
            }
            requireNotNull(TemplateManagerImpl.getTemplateState(myFixture.editor)).gotoEnd(false)
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        } finally {
            Disposer.dispose(disposable)
        }
        return true
    }

    /**
     * The driver for every caret whose data context supplies the declaring `LuaNameRef`. It returns
     * `false` when no template started, which is the assertion every document-layer case above
     * makes before reading the document.
     */
    private fun renameInPlaceViaMemberHandler(newName: String): Boolean =
        CodeInsightTestUtil.tryInlineRename(MemberInplaceRenameHandler(), newName, myFixture.editor, leafAtCaret())

    /**
     * Drives the template to commit and returns the conflict messages the dialog would have shown.
     * A silent application is an `AssertionError` rather than an empty collection: a rename that
     * rebinds without warning is the defect `REFACT-07-08` exists against, and returning empty
     * would let the caller's `contains` assertion report the wrong failure.
     */
    private fun conflictsFromRenamingInPlaceTo(newName: String): Collection<String> {
        try {
            renameInPlaceViaMemberHandler(newName)
        } catch (conflicts: BaseRefactoringProcessor.ConflictsInTestsException) {
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
            return conflicts.messages
        }
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        throw AssertionError(
            "renaming in place to '$newName' applied silently; the file is now:\n${myFixture.file.text}",
        )
    }

    private fun renameHandlersAtCaret(): List<RenameHandler> =
        RenameHandlerRegistry.getInstance().getRenameHandlers(contextAtCaret())

    private fun memberInplaceRenameOfferedAtCaret(): Boolean =
        MemberInplaceRenameHandler().isAvailableOnDataContext(contextAtCaret())

    /**
     * The one source of a data context in this class. Nothing is injected into it — see the class
     * KDoc for why that is binding rather than stylistic.
     */
    private fun contextAtCaret(): DataContext =
        DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)

    /**
     * `tryInlineRename`'s `elementAtCaret` parameter is `@NotNull` but is only a fallback for when
     * the data context yields nothing (`CodeInsightTestUtil.java:246`), which never happens for the
     * fixtures above. The leaf is used rather than `myFixture.elementAtCaret` so that the fallback
     * cannot itself throw and disguise a context that stopped supplying an element.
     */
    private fun leafAtCaret(): PsiElement =
        requireNotNull(myFixture.file.findElementAt(myFixture.caretOffset)) {
            "no leaf at caret in ${myFixture.file.text}"
        }

    private companion object {
        const val TEMPLATE_MUST_START =
            "no in-place template started — the document assertion below is satisfied by the feature being absent"
        const val LOCAL_DECLARATION_FIXTURE = "local coun<caret>ter = 0\nprint(counter)\n"
        const val THREE_OCCURRENCE_FIXTURE = "local coun<caret>ter = 0\nprint(counter)\ncounter = counter + 1\n"
        const val THREE_OCCURRENCE_RENAMED = "local total = 0\nprint(total)\ntotal = total + 1\n"
        const val CONFLICTING_FIXTURE = "local coun<caret>ter = 0\nlocal total = 1\nprint(counter + total)\n"
        const val GENERIC_FOR_FIXTURE = "for ke<caret>y, value in pairs(t) do\n  print(key, value)\nend\n"
        const val LOCAL_FUNCTION_FIXTURE = "local function hel<caret>per() return 1 end\nprint(helper())\n"
    }
}

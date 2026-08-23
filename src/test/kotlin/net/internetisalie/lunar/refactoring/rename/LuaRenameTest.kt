package net.internetisalie.lunar.refactoring.rename

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFuncNameMethod
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaLabelRef
import net.internetisalie.lunar.refactoring.LuaNamesValidator
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REFACT-01 Phase 2 — rename rewrites the declaration **and every usage Lua's scoping rules bind
 * to it**, or refuses with a reason.
 *
 * The defect this class exists against is BUG-457, measured live: renaming an ordinary identifier
 * rewrote the declaration, left four usages behind and reported success. Every positive case here
 * therefore asserts the whole file text, not "the declaration changed" — the latter is green for
 * the defect as well as for the fix.
 *
 * Refusals assert the **message**, never a `null` return: `CommonRefactoringUtil.showErrorHint`
 * short-circuits headlessly and throws [CommonRefactoringUtil.RefactoringErrorHintException]
 * (`CommonRefactoringUtil.java:79-86`), so `substituteElementToRename` never reaches its own
 * `return`, and the message is the only thing that distinguishes which refusal branch fired.
 */
@RunWith(JUnit4::class)
class LuaRenameTest : BasePlatformTestCase() {
    /** TC-01 — REFACT-01-01: the core case, and the exact shape BUG-457 was measured on. */
    @Test
    fun testRenameLocalAndAllUsages() {
        myFixture.configureByText(
            "test.lua",
            "local coun<caret>ter = 0\ncounter = counter + 1\nprint(counter)\n",
        )

        myFixture.renameElementAtCaret("total")

        myFixture.checkResult("local total = 0\ntotal = total + 1\nprint(total)\n")
    }

    /** TC-02 — REFACT-01-02: a usage redirects to its declaration before the rename runs. */
    @Test
    fun testRenameFromUsageSite() {
        myFixture.configureByText("test.lua", "local counter = 0\nprint(coun<caret>ter)\n")

        myFixture.renameElementAtCaret("total")

        myFixture.checkResult("local total = 0\nprint(total)\n")
    }

    /**
     * TC-03 — REFACT-01-03: the Lua-specific correctness bar. An inner `local x` is a *different*
     * variable, so the block's two occurrences must not move.
     */
    @Test
    fun testShadowedLocalsStayIsolated() {
        myFixture.configureByText(
            "test.lua",
            "local <caret>x = 1\ndo\n  local x = 2\n  print(x)\nend\nprint(x)\n",
        )

        myFixture.renameElementAtCaret("y")

        myFixture.checkResult("local y = 1\ndo\n  local x = 2\n  print(x)\nend\nprint(y)\n")
    }

    /** TC-04 — REFACT-01-04. */
    @Test
    fun testRenameParameter() {
        myFixture.configureByText("test.lua", "local function f(<caret>a)\n  return a + 1\nend\n")

        myFixture.renameElementAtCaret("b")

        myFixture.checkResult("local function f(b)\n  return b + 1\nend\n")
    }

    /**
     * TC-05 — REFACT-01-05, numeric `for`. Driven from the USAGE, which is a deviation from the
     * plan's fixture and a measured one: see
     * [testNumericForDeclarationCaretHasNoRenameTargetAtAll]. The declaration leaf is still what is
     * renamed — the redirect through `substituteElementToRename` is what puts `idx` in the `for`
     * header — so this is the same code path the plan meant to exercise, entered from the only
     * caret position the platform can resolve.
     */
    @Test
    fun testRenameNumericForVariable() {
        myFixture.configureByText("test.lua", "for i = 1, 3 do print(<caret>i) end\n")

        myFixture.renameElementAtCaret("idx")

        myFixture.checkResult("for idx = 1, 3 do print(idx) end\n")
    }

    /**
     * The numeric-`for` variable is the one declaration kind with no `LuaNameRef`: `numericForStatement
     * ::= FOR IDENTIFIER '=' …` (`lua.bnf:152`) hangs the leaf directly off the statement. The leaf
     * carries no reference and `LuaNumericForStatement` is a plain `ASTWrapperPsiElement`, so
     * `TargetElementUtilBase.getNamedElement` finds no `PsiNamedElement` ancestor and
     * `findTargetElement` returns **null** — Shift+F6 on `for <caret>i` reports "cannot rename",
     * where every other declaration kind renames.
     *
     * Measured on the builder, not inferred. This test pins the limitation so that closing it (a
     * `TargetElementEvaluatorEx2` for Lua) goes red here and forces the gap record to be updated
     * rather than quietly diverging from it.
     */
    @Test
    fun testNumericForDeclarationCaretHasNoRenameTargetAtAll() {
        myFixture.configureByText("test.lua", "for <caret>i = 1, 3 do print(i) end\n")

        assertNull(
            "if this resolves, the numeric-for gap is closed and TC-05 should move to the declaration caret",
            TargetElementUtil.findTargetElement(myFixture.editor, CARET_TARGET_FLAGS),
        )
        assertTrue(
            "the declaration leaf itself is renameable — only the caret cannot reach it",
            LuaRenameProcessor().canProcessElement(leafAtCaret()),
        )
    }

    /** TC-06 — REFACT-01-05, generic `for`: a different PSI shape from the numeric form. */
    @Test
    fun testRenameGenericForVariable() {
        myFixture.configureByText("test.lua", "for <caret>k, v in pairs(t) do print(k, v) end\n")

        myFixture.renameElementAtCaret("key")

        myFixture.checkResult("for key, v in pairs(t) do print(key, v) end\n")
    }

    /**
     * TC-07 — REFACT-01-06. `local function f` puts `f` in scope inside its own body, so the
     * recursive call is a usage that must move; `local f = function() … end` would not be.
     */
    @Test
    fun testRenameLocalFunctionWithRecursiveCall() {
        myFixture.configureByText(
            "test.lua",
            "local function <caret>fact(n)\n  if n <= 1 then return 1 end\n  return n * fact(n - 1)\nend\nprint(fact(5))\n",
        )

        myFixture.renameElementAtCaret("factorial")

        myFixture.checkResult(
            "local function factorial(n)\n  if n <= 1 then return 1 end\n" +
                "  return n * factorial(n - 1)\nend\nprint(factorial(5))\n",
        )
    }

    /**
     * TC-13d — REFACT-01-15's Phase-2 gate. `RenameDialog` adds "Search in comments and strings"
     * unconditionally, so one click reaches `RenameUtil.getStringToReplace` with the renamed
     * **leaf**; without `getQualifiedNameAfterRename` the platform logs `"Unknown element type : "`
     * and feeds null into `document.replaceString`. Under a test logger that log IS the failure.
     */
    @Test
    fun testSearchInCommentsDoesNotLogAnUnknownElementType() {
        myFixture.configureByText(
            "test.lua",
            "local coun<caret>ter = 0\ncounter = counter + 1\nprint(counter)\n",
        )

        myFixture.renameElement(
            myFixture.elementAtCaret,
            "total",
            /* searchInComments = */ true,
            /* searchTextOccurrences = */ true,
        )

        myFixture.checkResult("local total = 0\ntotal = total + 1\nprint(total)\n")
    }

    /**
     * TC-19a — REFACT-01-19. Part (a) is the assertion that catches the design's original claim
     * that `self` resolves to the CLASS leaf: `LuaScopeProcessor` sets the result to
     * `funcName.funcNameMethod.nameRef.identifier`, i.e. the method name. Part (b) proves the
     * colon-method refusal is what stops the rename — there is no `self` guard and there must not
     * be one, because `function T.m(self, x)` is legal Lua whose `self` is an ordinary parameter.
     */
    @Test
    fun testSelfInsideAMethodIsRefusedAsTheMethod() {
        myFixture.configureByText("test.lua", "Obj = {}\nfunction Obj:m()\n  return se<caret>lf\nend\n")

        val target = TargetElementUtil.findTargetElement(myFixture.editor, CARET_TARGET_FLAGS)

        assertEquals("self resolves to the METHOD-NAME leaf, not the class", "m", target?.text)
        assertTrue(
            "and that leaf is the funcNameMethod's, not the funcName's receiver",
            target?.parent?.parent is LuaFuncNameMethod,
        )
        assertRefusedWith("function Obj:method()", target)
    }

    /**
     * TC-19b — REFACT-01-19. `...` binds no identifier, so nothing about it is renameable. The
     * leaf is taken from the file rather than from `elementAtCaret`, which would `fail()` on the
     * fixture: an ELLIPSIS has no reference and no `PsiNamedElement` ancestor.
     */
    @Test
    fun testVarargIsNotClaimed() {
        myFixture.configureByText("test.lua", "local function f(<caret>...) end\n")

        val leaf = leafAtCaret()

        assertEquals(LuaElementTypes.ELLIPSIS, leaf.elementType)
        assertFalse("... has no name to change", LuaRenameProcessor().canProcessElement(leaf))
    }

    /**
     * TC-19c — REFACT-01-19, and the case that keeps the `self` refusal honest. Lua makes `self`
     * implicit only for the COLON form; in `function Obj.m(self, x)` it is an ordinary parameter
     * with a real declaration site, so it must rename like any other. A `self` guard written
     * against the name TEXT — which design §3.1 records as having been removed for exactly this
     * reason — would refuse this and be wrong.
     */
    @Test
    fun testExplicitSelfParameterRenamesNormally() {
        myFixture.configureByText(
            "test.lua",
            "Obj = {}\nfunction Obj.m(<caret>self, x)\n  return self\nend\n",
        )

        myFixture.renameElementAtCaret("this")

        myFixture.checkResult("Obj = {}\nfunction Obj.m(this, x)\n  return this\nend\n")
    }

    /**
     * TC-11 — REFACT-01-10. The new-name gate is delegated to `LuaNamesValidator` (registered
     * `plugin.xml:393-395`), which the rename dialog consults before this processor ever runs. It
     * is pinned from here as well as from `LuaNamesValidatorTest` because it is the FIRST of the
     * two defences against an unbuildable name; the second is TC-36, which covers every
     * programmatic caller that does not consult a dialog.
     */
    @Test
    fun testKeywordIsNotAValidNewName() {
        val validator = LuaNamesValidator()

        assertFalse("a reserved word cannot be a new name", validator.isIdentifier("end", project))
        assertFalse("nor can a digit-initial string", validator.isIdentifier("2x", project))
        assertTrue("an ordinary identifier can", validator.isIdentifier("total", project))
    }

    /**
     * TC-13b — REFACT-01-13. The preview pane is platform-supplied and costs no code, but only if
     * the processor keeps offering the button; overriding `showRenamePreviewButton` to `false`
     * would remove the one place a user can inspect a best-effort Lua rename before it is applied
     * (REFACT-01-20 records why that matters here more than in a statically sound language).
     */
    @Test
    fun testPreviewButtonIsOffered() {
        myFixture.configureByText("test.lua", "local <caret>x = 1\n")

        assertTrue(
            "the preview pane is the user's only check on a best-effort rename",
            LuaRenameProcessor().showRenamePreviewButton(leafAtCaret()),
        )
    }

    /**
     * TC-25 — REFACT-01-17 / REFACT-04. The direct unit guard on design §3.0 rule 1.
     * `LuaDeclarationSite.kindOf(LuaLabelName)` is `LABEL`, not null, so folding the exclusion into
     * the `kindOf` test would claim labels — and because `forPsiElement` returns the FIRST matching
     * extension, that silently takes over the one refactoring the plugin ships today.
     */
    @Test
    fun testLabelsAreNotClaimed() {
        myFixture.configureByText("test.lua", "::ret<caret>ry::\ngoto retry\n")
        val processor = LuaRenameProcessor()
        val labelName = PsiTreeUtil.findChildOfType(myFixture.file, LuaLabelName::class.java)
        val labelRef = PsiTreeUtil.findChildOfType(myFixture.file, LuaLabelRef::class.java)

        val declaration = requireNotNull(labelName) { "fixture should contain a label declaration" }
        val gotoReference = requireNotNull(labelRef) { "fixture should contain a goto reference" }
        assertFalse("the declaration belongs to the platform default", processor.canProcessElement(declaration))
        assertFalse("and so does its IDENTIFIER child", processor.canProcessElement(declaration.identifier))
        assertFalse("and so does the goto side of the pair", processor.canProcessElement(gotoReference))
    }

    /**
     * TC-26 — REFACT-01-01. The guard on design §3.0 rule 3's deliberate over-claiming: if only
     * declaration leaves were claimed, this usage would reach the platform default, whose
     * `doRenameGenericNamedElement` renames the `LuaNameRef` in place and collects no usages — the
     * BUG-457 shape under a different code path.
     */
    @Test
    fun testUnresolvableUsageIsRefusedNotHalfApplied() {
        myFixture.configureByText("test.lua", "print(unde<caret>fined_name)\n")

        assertTrue(
            "an unresolvable usage must be CLAIMED, or the platform default renames it in place",
            RenamePsiElementProcessor.forElement(myFixture.elementAtCaret) is LuaRenameProcessor,
        )
        val refusal =
            try {
                myFixture.renameElementAtCaret("renamed")
                null
            } catch (thrown: CommonRefactoringUtil.RefactoringErrorHintException) {
                thrown
            }

        assertNotNull("driven end to end, the rename must refuse rather than half-apply", refusal)
        assertTrue(
            "and name its reason: " + refusal?.message,
            refusal?.message.orEmpty().contains("Cannot determine which declaration"),
        )
        myFixture.checkResult("print(undefined_name)\n")
    }

    /**
     * TC-36 — REFACT-01-01, added by the Phase-2 review. The ATOMICITY invariant of design §3.3:
     * the usage rewrites and the declaration rewrite succeed together or neither runs.
     *
     * `renameElement` edits two independent places, and both go through
     * [LuaElementFactory.createIdentifier] with the same name and project — so a name that cannot
     * be built is a failure of BOTH halves, discovered once. Discovering it after the usage loop
     * has run leaves every usage on the new name and the declaration on the old one: **BUG-457
     * inverted**, inside the function written to eliminate BUG-457. Resolving the replacement
     * before the first edit is what makes that unrepresentable.
     *
     * `end` is such a name: `gotoStatement ::= GOTO labelRef` is unpinned (`lua.bnf:125`), so the
     * factory's synthetic `goto end` parses to nothing and yields no identifier PSI
     * (`LuaElementFactoryTest.testCreateIdentifierIsNullForANameThatCannotBeAnIdentifier`).
     * `LuaNamesValidator` (TC-11) stops a user reaching this from the dialog; this covers the
     * programmatic callers that do not consult it, and pins the invariant rather than the caller.
     *
     * Mutation (executed): restore the reviewed ordering — usage loop first, then
     * `element.parent ?: return` and `createIdentifier(…) ?: return` — and this goes RED, because
     * the rename then returns normally having written nothing and reports success.
     */
    @Test
    fun testUnbuildableNewNameRefusesBeforeAnythingIsRewritten() {
        val source = "local coun<caret>ter = 0\ncounter = counter + 1\nprint(counter)\n"
        myFixture.configureByText("test.lua", source)

        val failure = renameFailure("end")

        assertNotNull("a rename that cannot be applied must fail loudly, not report success", failure)
        assertTrue(
            "and name why, so no caller mistakes it for a no-op: " + causeMessages(failure),
            causeMessages(failure).contains("cannot be written as a Lua identifier"),
        )
        myFixture.checkResult(source.replace("<caret>", ""))
    }

    /**
     * TC-34a — REFACT-01-08. `function M.run() end` classifies its RECEIVER `M` as a declaration
     * site, so without design §3.1 step 4a rename lands here and half-applies: `M` resolves to
     * nothing, so every `M.run()` call site is left on the old name. The fixture deliberately omits
     * `M = {}` — with it, the caret redirects to that declaration and the rename is correct.
     */
    @Test
    fun testFunctionNameReceiverIsRefused() {
        myFixture.configureByText("test.lua", "function <caret>M.run() end\nM.run()\nM.run()\n")

        assertRefusedWith("receiver part of a function name", myFixture.elementAtCaret)
        myFixture.checkResult("function M.run() end\nM.run()\nM.run()\n")
    }

    /**
     * TC-34b — REFACT-01-08. Proves step 4a is a round trip against
     * `LuaDeclarationSite.functionNameLeafOf` and not a "grandparent is a `LuaFuncName`"
     * enumeration: both leading segments of `function A.B.run()` are refused while `run` — the
     * segment the declaration actually names — substitutes normally.
     */
    @Test
    fun testIntermediateFunctionNameSegmentIsRefused() {
        val source = "function A.B.run() end\n"

        myFixture.configureByText("test.lua", source.replace("A.B", "A.<caret>B"))
        assertRefusedWith("receiver part of a function name", myFixture.elementAtCaret)

        myFixture.configureByText("test.lua", source.replace("function A", "function <caret>A"))
        assertRefusedWith("receiver part of a function name", myFixture.elementAtCaret)

        myFixture.configureByText("test.lua", source.replace("B.run", "B.<caret>run"))
        val substituted = LuaRenameProcessor().substituteElementToRename(myFixture.elementAtCaret, null)
        assertEquals("the last segment is the function's own name and renames normally", "run", substituted?.text)
    }

    /**
     * Design §3.0 rule 4 in its protective direction, measured. With `M = {}` present,
     * `TargetElementUtil` hands the caret on `M` the whole enclosing `LuaFuncDecl` — not, as design
     * §6 claims, the `M` of `M = {}` "with the funcName's `M` collected as an ordinary usage".
     * `canProcessElement` rejects a declaration NODE, so the platform reports "cannot be renamed".
     *
     * That refusal is load-bearing rather than incidental: `LuaDeclarationSite.identifierLeafOf` is
     * total over declaration nodes (Safe Delete needs it that way) and maps a `LuaFuncDecl` to its
     * LAST name segment, so a `canProcessElement` widened to admit declaration nodes would answer
     * a rename of `M` by renaming **`run`**. The refusal is the only thing between those two.
     */
    @Test
    fun testCaretOnAGlobalShadowedByADottedDeclarationIsRefusedNotMisdirected() {
        myFixture.configureByText("test.lua", "<caret>M = {}\nfunction M.run() end\nM.run()\n")

        val target = myFixture.elementAtCaret

        assertFalse(
            "a declaration NODE must not be claimed: substituting it yields the 'run' leaf",
            LuaRenameProcessor().canProcessElement(target),
        )
        assertEquals(
            "and that is exactly the leaf a widened predicate would have renamed",
            "run",
            LuaRenameProcessor().substituteElementToRename(target, null)?.text,
        )
    }

    /**
     * Registration, not construction. Every assertion in this class that instantiates
     * [LuaRenameProcessor] directly would stay green with the `plugin.xml` line pointing anywhere
     * — this is the one that fails if it does.
     */
    @Test
    fun testIsTheProcessorThePlatformSelects() {
        myFixture.configureByText("test.lua", "local counter = 0\nprint(coun<caret>ter)\n")

        assertTrue(
            "the registered processor must be this one, or none of the behaviour above ships",
            RenamePsiElementProcessor.forElement(myFixture.elementAtCaret) is LuaRenameProcessor,
        )
    }

    /**
     * `RenamePsiElementProcessorBase.forPsiElement` skips a processor that is not usable in the
     * current context (`:156`) while `RenameElementAction` is a `DumbAwareAction`
     * (`RenameElementAction.java:35`), so without this marker rename during indexing falls through
     * to the platform default — BUG-457 again, in a window nobody would think to test. Inherited
     * from `LuaUnsupportedRenameProcessor`, which carried it for the same reason.
     */
    @Test
    fun testSurvivesDumbMode() {
        myFixture.configureByText("test.lua", "local counter = 0\nprint(coun<caret>ter)\n")
        val target = myFixture.elementAtCaret

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            assertTrue(
                "without DumbAware the processor evaporates while indexing and the platform " +
                    "default half-renames — BUG-457 in a window the user cannot see",
                RenamePsiElementProcessor.forElement(target) is LuaRenameProcessor,
            )
        }
    }

    /**
     * Drives the registered rename end to end and returns whatever it threw, or `null` if it
     * completed. `RenameProcessor.performRefactoring` catches an [IncorrectOperationException] and
     * hands it to `RenameUtil.showErrorMessage`, which rethrows it wrapped under a test
     * application (`RenameUtil.java:264-268`) instead of painting a dialog — so the refusal
     * arrives as a [RuntimeException] whose CAUSE carries the message.
     */
    private fun renameFailure(newName: String): Throwable? =
        try {
            myFixture.renameElementAtCaret(newName)
            null
        } catch (thrown: RuntimeException) {
            thrown
        }

    private fun causeMessages(failure: Throwable?): String =
        generateSequence(failure) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" | ")

    private fun leafAtCaret(): PsiElement =
        requireNotNull(myFixture.file.findElementAt(myFixture.caretOffset)) {
            "no leaf at caret in ${myFixture.file.text}"
        }

    private fun assertRefusedWith(
        expectedFragment: String,
        target: PsiElement?,
    ) {
        val refusal =
            try {
                LuaRenameProcessor().substituteElementToRename(requireNotNull(target), null)
                null
            } catch (thrown: CommonRefactoringUtil.RefactoringErrorHintException) {
                thrown
            }

        assertNotNull("rename must be refused, not silently allowed or half-applied", refusal)
        assertTrue(
            "the refusal must name its own reason, not merely abort: " + refusal?.message,
            refusal?.message.orEmpty().contains(expectedFragment),
        )
    }

    private companion object {
        val CARET_TARGET_FLAGS =
            TargetElementUtil.ELEMENT_NAME_ACCEPTED or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
    }
}

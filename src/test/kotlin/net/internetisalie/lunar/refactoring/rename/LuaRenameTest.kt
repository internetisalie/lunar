package net.internetisalie.lunar.refactoring.rename

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.NonCodeSearchDescriptionLocation
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFuncNameMethod
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaLabelRef
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.refactoring.LuaNamesValidator
import net.internetisalie.lunar.settings.LuaRefactoringSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
     * TC-13c — REFACT-01-15's unit gate on design §2.9's `getElementToSearchInStringsAndComments`.
     *
     * Part (a) is the assertion that fails when the override is dropped: the platform default
     * returns the renamed element unchanged (`RenamePsiElementProcessorBase.java:264-266`), that
     * element is an IDENTIFIER **leaf**, a leaf is not a `PsiNamedElement`, so every description
     * provider declines and `ElementDescriptionUtil` falls through to `return element.toString()`
     * (`ElementDescriptionUtil.java:26`). The comment search would then look for a `LeafPsiElement`
     * debug string and match nothing — a checkbox that appears to work and does nothing.
     *
     * Part (b) pins the deliberate `null`: a numeric-`for` variable's leaf hangs directly off
     * `LuaNumericForStatement` (`lua.bnf:152`) and has no `LuaNameRef` parent.
     * `RenameUtil.processUsages` guards both non-code branches with `searchForInComments != null`
     * (`RenameUtil.java:147, 157`), so null disables non-code search for that kind rather than
     * searching for garbage.
     */
    @Test
    fun testNonCodeSearchTargetsTheNamedComposite() {
        myFixture.configureByText("test.lua", "local <caret>counter = 0\n")
        val searchTarget = LuaRenameProcessor().getElementToSearchInStringsAndComments(leafAtCaret())

        assertTrue("the searched element must be the named composite, not the leaf", searchTarget is LuaNameRef)
        assertEquals(
            "the searched string must be the identifier, not a LeafPsiElement debug string",
            "counter",
            ElementDescriptionUtil.getElementDescription(
                requireNotNull(searchTarget),
                NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS,
            ),
        )

        myFixture.configureByText("numeric.lua", "for <caret>i = 1, 3 do end\n")

        assertNull(
            "a numeric-for variable has no LuaNameRef parent, so non-code search is disabled for it",
            LuaRenameProcessor().getElementToSearchInStringsAndComments(leafAtCaret()),
        )
    }

    /**
     * TC-13c's companion — the four persisted accessors of design §2.9, which no other case
     * reaches.
     *
     * `myFixture.renameElement`'s four-argument form passes its flags straight into
     * `RenameProcessor` (`CodeInsightTestFixtureImpl.java:1098-1107`), so TC-13e never consults
     * `isToSearchInComments`; only `RenameDialog` does (`RenameDialog.java:93-94, 405`), and no
     * unit fixture opens it. Without this case a swap between the two backing fields — the most
     * plausible defect in a block of four near-identical delegations — would be invisible, and the
     * user's ticked checkbox would come back ticked on the wrong row.
     */
    @Test
    fun testNonCodeSearchChoicesArePersistedIndependently() {
        myFixture.configureByText("test.lua", "local <caret>x = 1\n")
        val processor = LuaRenameProcessor()
        val leaf = leafAtCaret()
        val settings = LuaRefactoringSettings.instance
        val restoreComments = settings.renameSearchInComments
        val restoreText = settings.renameSearchForText

        try {
            processor.setToSearchInComments(leaf, true)
            processor.setToSearchForTextOccurrences(leaf, false)

            assertTrue("the comments choice must round-trip", processor.isToSearchInComments(leaf))
            assertFalse(
                "and must not leak into the text-occurrences choice",
                processor.isToSearchForTextOccurrences(leaf),
            )

            processor.setToSearchInComments(leaf, false)
            processor.setToSearchForTextOccurrences(leaf, true)

            assertFalse("the comments choice must round-trip in both directions", processor.isToSearchInComments(leaf))
            assertTrue("and the text-occurrences choice independently", processor.isToSearchForTextOccurrences(leaf))
        } finally {
            settings.renameSearchInComments = restoreComments
            settings.renameSearchForText = restoreText
        }
    }

    /**
     * TC-13e — REFACT-01-15's end-to-end gate, and the only case that drives
     * `RenameUtil.processUsages`' non-code branch to completion.
     *
     * **`renameElementAtCaret` cannot be used here**: it delegates to the two-argument
     * `renameElement`, which hard-codes `searchInComments = false`
     * (`CodeInsightTestFixtureImpl.java:1092-1096`), so a test written on it never enters the
     * branch at all. The four-argument form is the only fixture entry point that propagates the
     * flag (`CodeInsightTestFixtureImpl.java:1098-1107`), and it still runs
     * `substituteElementToRename` and `RenameProcessor.run()`.
     *
     * The case is red in two independent ways: without
     * `getElementToSearchInStringsAndComments` the searched string is the leaf's `toString()` and
     * the comment is left alone; without `getQualifiedNameAfterRename` it throws before the
     * comment is reached.
     */
    @Test
    fun testSearchInCommentsRewritesTheComment() {
        myFixture.configureByText(
            "test.lua",
            "-- counter tracks the total\nlocal coun<caret>ter = 0\nprint(counter)\n",
        )

        myFixture.renameElement(
            myFixture.elementAtCaret,
            "total",
            /* searchInComments = */ true,
            /* searchTextOccurrences = */ false,
        )

        myFixture.checkResult("-- total tracks the total\nlocal total = 0\nprint(total)\n")
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
     * TC-09 — REFACT-01-08, the dotted form. `function M.run()` classifies its LAST segment
     * `DOTTED_FUNCTION`, which design §3.1 step 4 lets through: `run` is the segment the
     * declaration actually names, so `functionNameLeafOf`'s round trip admits it and every
     * `M.run()` call site is found.
     *
     * **The whole file is asserted, and `M.run()` is what makes the assertion worth writing.**
     * A test checking only that the declaration became `M.start()` is green for the exact defect
     * this case exists against: reverting `LuaNameReference.declarationIdentifier`'s `LuaFuncDecl`
     * branch to the bare `decl.funcName.nameRef.identifier` it used before REFACT-01 makes
     * `isReferenceTo` false for every call site, and the rename half-applies — declaration on the
     * new name, call sites on the old one, success reported. Measured, not predicted: that mutant
     * produces `function M.start() end` / `M.run()` and reddens this case at the `checkResult`.
     *
     * `M = {}` is present because it is the ordinary shape; the receiver being declared or not
     * changes nothing here, which was measured too — the caret is on `run`, so §3.1 step 4a is
     * inert either way and the call site resolves through the `"M.run"` stub key.
     */
    @Test
    fun testRenameDottedFunctionDeclaration() {
        myFixture.configureByText("test.lua", "M = {}\nfunction M.ru<caret>n() end\nM.run()\n")

        myFixture.renameElementAtCaret("start")

        myFixture.checkResult("M = {}\nfunction M.start() end\nM.start()\n")
    }

    /**
     * TC-10 — REFACT-01-08, the colon form, refused by design §3.1 step 4b.
     *
     * Two assertions, because either alone is weak. The first names **which** refusal fired: with
     * the caret on the declaration `m`, `functionNameLeafOf` returns that very leaf, so step 4a is
     * inert and only the `METHOD_FUNCTION` branch can decline — a test asserting merely "something
     * was refused" would stay green if step 4a fired instead, which is the failure shape this
     * feature has produced twelve times.
     *
     * The second drives the registered rename end to end and asserts the file is **byte-for-byte
     * unchanged**, which is the assertion that makes the refusal worth having: `findReferences` on
     * this fixture returns **zero** references — measured on the builder, the mechanism being that
     * `o:m()` puts the name under a `LuaMethodExpr`, for which `LuaNameReference.getQualifiedName`
     * returns null, while the declaration is stub-keyed `"Obj:m"` — so deleting
     * the branch does not merely allow the rename, it half-applies one — `function Obj:renamed()`
     * with `o:m()` left behind. That mutant reddens both assertions.
     *
     * TC-19a covers the same branch from the caret-on-`self` direction and asserts no file text;
     * this covers the declaration caret and does.
     */
    @Test
    fun testColonMethodDeclarationIsRefused() {
        val source = "Obj = {}\nfunction Obj:m() end\nlocal o = Obj\no:m()\n"
        myFixture.configureByText("test.lua", source.replace("Obj:m", "Obj:<caret>m"))

        assertRefusedWith("function Obj:method()", myFixture.elementAtCaret)

        assertNotNull("the end-to-end rename must decline too, not only the substitution", renameFailure("renamed"))
        myFixture.checkResult(source)
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
     * TC-43 — REFACT-01-01, and the gate on BUG-468. Cancelling mid-rename must leave the file
     * exactly as it was, not part-renamed.
     *
     * **The whole file is asserted, deliberately.** "The declaration is unchanged" is green on the
     * defect — the defect rewrote usages first and left the declaration alone — so an assertion
     * scoped to the declaration proves nothing here.
     *
     * **Nothing is asserted about what was thrown**, also deliberately:
     * `PotemkinProgress.runInSwingThread` swallows the `ProcessCanceledException`
     * (`PotemkinProgress.java:151-162`), so the throwable is `null` both before and after the fix
     * and any assertion on it would be a test that cannot fail (BUG-468 §6).
     *
     * **`SECOND` is load-bearing.** Measured on the parent commit: cancelling at the FIRST check
     * leaves the file untouched on the defect too, because the parse inside `setName` throws before
     * usage 1 is written — so a first-check variant of this case cannot fail.
     *
     * Mutation (executed): restore the Phase-2 apply loop
     * (`usages.forEach { checkCanceled(); RenameUtil.rename(usage, newName) }`). RED, on a
     * half-applied file — one of the three occurrences carries `total` while the declaration carries
     * `counter`. WHICH occurrence moves is not fixed: `findReferences` returns a `Query` with no
     * specified ordering and the usage array preserves it, so a run that moves a different line is a
     * correct reproduction. This case is unaffected by that, because it asserts the INPUT.
     */
    @Test
    fun testCancellingMidRenameLeavesTheFileUntouched() {
        val source = "local coun<caret>ter = 0\ncounter = counter + 1\nprint(counter)\n"
        myFixture.configureByText("cancel_mid_rename.lua", source)

        renameUnderHook(cancellingHookAt(2), "total")

        myFixture.checkResult(source.replace("<caret>", ""))
    }

    /**
     * TC-44 — REFACT-01-01. The cancellation point is inside the preparation loop, once per usage,
     * rather than once per rename — engineering-contract §2's CANCELLATION EXHAUSTIVENESS, which
     * TC-43 alone does not force.
     *
     * **A GUARD, not a gate**: it is green on the parent commit, where the check is already per
     * usage. Its executed mutant is hoisting `ProgressManager.checkCanceled()` out of
     * `preparedUsageRewrites`' lambda to a single pre-loop call — the delta collapses to 0 and this
     * goes red. That mutant is the only reason the case exists.
     *
     * `renameElementAtCaret` drives it rather than a direct call, so the count includes exactly the
     * checks a real rename makes. Counting is done through `ProgressManagerImpl`'s check-canceled
     * hook because it is the only observation point that does not require a cancelled indicator: with
     * none on the thread, `CoreProgressManager.doCheckCanceled` takes its `ONLY_HOOKS` branch
     * (`:868-876`) and a counting `ProgressIndicator` is never consulted at all.
     */
    @Test
    fun testCancellationIsCheckedPerUsageNotPerRename() {
        val checksForThree = processorChecksWhileRenamingWith(3)
        val checksForEight = processorChecksWhileRenamingWith(8)

        assertTrue(
            "the cancellation point must scale with the usage count, not fire once per rename: " +
                "$checksForThree checks for 3 usages, $checksForEight for 8",
            checksForEight - checksForThree >= 5,
        )
    }

    /**
     * TC-45 — REFACT-01-01, and the gate on design §3.3 step 4's `executeNonCancelableSection`. A
     * Cancel that arrives after the first edit must NOT split the file: the rename runs to
     * completion.
     *
     * That ignored Cancel is the accepted residual (`risks-and-gaps.md` Gap 2.18) — an ignored
     * Cancel with a correct file beats an honoured one with a broken file.
     *
     * **The indicator is captured OUTSIDE the listener.** Inside `documentChanged` the thread
     * indicator is `PomModelImpl`'s `NonCancelableIndicator` (`PomModelImpl.java:112`), on which
     * `cancel()` is a no-op — a measured way to write a test that cannot fail.
     *
     * Mutation (executed): drop the section and apply the prepared rewrites directly. RED — the
     * file splits. **Nothing is thrown at the test boundary**, measured: the
     * `ProcessCanceledException` dies inside `PotemkinProgress.runInSwingThread`
     * (`PotemkinProgress.java:151-162`), so the throwable is `null` on the mutant and on the fix
     * alike. The split is the whole observation. The assertion is the completed text,
     * which is a property rather than a pinned half-state: which occurrence a split would leave
     * behind is order-dependent (see TC-43), so only the fully-renamed form is assertable.
     *
     * **Nothing is asserted about what was thrown**, for TC-43's reason: the throwable is `null`
     * both before and after the fix, so any assertion on it would be a test that cannot fail
     * (BUG-468 §6). The completed text is the only gate.
     */
    @Test
    fun testCancellingAfterTheFirstEditStillAppliesEveryEdit() {
        myFixture.configureByText(
            "cancel_after_first_edit.lua",
            "local coun<caret>ter = 0\ncounter = counter + 1\nprint(counter)\n",
        )
        val liveIndicator = AtomicReference<ProgressIndicator?>()
        cancelOnFirstDocumentChange(liveIndicator)

        renameUnderHook(capturingHook(liveIndicator), "total")

        myFixture.checkResult("local total = 0\ntotal = total + 1\nprint(total)\n")
    }

    /**
     * BUG-472 T1 — the caret's own declaration is the one renamed, not the one it shadows.
     *
     * Before `LuaTargetElementEvaluator`, `TargetElementUtilBase`'s reference branch won with the
     * EARLIER `local`'s leaf and the file became `local renamed = 1` / `local config = 2` /
     * `print(renamed)`: still valid Lua, still reported as a success, and the program printed `2`
     * before and `1` after.
     */
    @Test
    fun testRenamingALocalThatShadowsAnEarlierLocalRenamesTheOneUnderTheCaret() {
        myFixture.configureByText(
            "shadowing_local.lua",
            "local config = 1\nlocal con<caret>fig = 2\nprint(config)\n",
        )

        myFixture.renameElementAtCaret("renamed")

        myFixture.checkResult("local config = 1\nlocal renamed = 2\nprint(renamed)\n")
    }

    /**
     * BUG-472 T2 — a usage binds to the NEAREST preceding declaration, not the earliest.
     *
     * Same document as T1 and the same assertion, against the other half of the defect: with
     * forward iteration in `LuaBlock.processDeclarations`, `print(config)` resolves to line 1, is
     * never collected as a usage of line 2, and is left behind reading the wrong binding.
     */
    @Test
    fun testAUsageBindsToTheNearestPrecedingDeclarationNotTheEarliest() {
        myFixture.configureByText(
            "nearest_declaration_wins.lua",
            "local config = 1\nlocal con<caret>fig = 2\nprint(config)\n",
        )

        myFixture.renameElementAtCaret("renamed")

        myFixture.checkResult("local config = 1\nlocal renamed = 2\nprint(renamed)\n")
    }

    /**
     * BUG-470 — a `local` shadowing a GLOBAL of the same name renames, where it used to refuse.
     *
     * The data context supplied the global's leaf, which classifies as no declaration kind at all
     * (`isGlobalAssignmentTarget` excludes a name that is also a file-scope local), so no processor
     * claimed it and `PsiElementRenameHandler` threw "Cannot perform refactoring".
     */
    @Test
    fun testRenamingALocalThatShadowsAGlobalRenamesTheLocal() {
        myFixture.configureByText(
            "shadowing_global.lua",
            "config = 1\nlocal con<caret>fig = 2\nprint(config)\n",
        )

        myFixture.renameElementAtCaret("renamed")

        myFixture.checkResult("config = 1\nlocal renamed = 2\nprint(renamed)\n")
    }

    /**
     * The over-correction guard. `local x = x` binds its right-hand `x` to the OUTER declaration
     * (Lua §3.3.3), which is what excluding the declaring statement from its own scope buys. This
     * case is green before BUG-472 and must stay green: a "fix" that simply admits the declaring
     * statement turns T1–T3 green while silently breaking early binding here.
     *
     * `print(x)` reads the second declaration and must not move either.
     */
    @Test
    fun testSelfReferentialLocalInitialiserStillReadsTheOuterBinding() {
        myFixture.configureByText(
            "self_referential_initialiser.lua",
            "local <caret>x = 1\nlocal x = x\nprint(x)\n",
        )

        myFixture.renameElementAtCaret("outer")

        myFixture.checkResult("local outer = 1\nlocal x = outer\nprint(x)\n")
    }

    /**
     * The over-correction guard across a closure: an upvalue binds to the declaration visible where
     * the closure is WRITTEN, not to a later one of the same name. `return x` moves; the trailing
     * `print(f(), x)` reads `local x = 2` and must not.
     */
    @Test
    fun testAnUpvalueBindsToTheDeclarationVisibleWhereTheClosureIsWritten() {
        myFixture.configureByText(
            "upvalue_binding.lua",
            "local <caret>x = 1\nlocal function f()\n  return x\nend\nlocal x = 2\nprint(f(), x)\n",
        )

        myFixture.renameElementAtCaret("outer")

        myFixture.checkResult(
            "local outer = 1\nlocal function f()\n  return outer\nend\nlocal x = 2\nprint(f(), x)\n",
        )
    }

    /**
     * BUG-472 T6 — the same defect one layer down, where rename reads its target from. Asserting
     * the element directly is what distinguishes "the evaluator declined the shadowed declaration"
     * from "the rename happened to produce the right text".
     */
    @Test
    fun testAShadowingDeclarationCaretTargetsItsOwnDeclaration() {
        myFixture.configureByText(
            "shadowing_caret_target.lua",
            "local config = 1\nlocal con<caret>fig = 2\nprint(config)\n",
        )

        val target = TargetElementUtil.findTargetElement(myFixture.editor, CARET_TARGET_FLAGS)

        assertTrue(
            "a declaration caret must target a LuaNameRef, not the shadowed declaration's leaf: " +
                target?.javaClass?.simpleName + " at " + target?.textRange,
            target is LuaNameRef,
        )
        assertTrue(
            "the target must be the declaration under the caret, not the one it shadows",
            requireNotNull(target).textRange.contains(myFixture.caretOffset),
        )
    }

    /**
     * Runs the registered rename under [hook] and returns whatever escaped, or `null`.
     *
     * `ProgressManagerImpl.runWithHook` is void, so the verdict is carried out in a holder rather
     * than returned through it.
     */
    private fun renameUnderHook(
        hook: CoreProgressManager.CheckCanceledHook,
        newName: String,
    ): Throwable? {
        val escaped = AtomicReference<Throwable?>()
        (ProgressManager.getInstance() as ProgressManagerImpl).runWithHook(hook) {
            escaped.set(renameFailure(newName))
        }
        return escaped.get()
    }

    /**
     * A hook that cancels the live indicator on the [checkToCancelAt]th `checkCanceled` made by
     * [LuaRenameProcessor] itself.
     *
     * The check does not throw: `doCheckCanceled` discards `runCheckCanceledHooks`' return on its
     * `ONLY_HOOKS` branch (`CoreProgressManager.java:220-222`), so the throw comes from the next
     * cancellation point the rename reaches.
     */
    private fun cancellingHookAt(checkToCancelAt: Int): CoreProgressManager.CheckCanceledHook {
        val checks = AtomicInteger()
        return CoreProgressManager.CheckCanceledHook {
            if (calledDirectlyByTheProcessor() && checks.incrementAndGet() == checkToCancelAt) {
                ProgressIndicatorProvider.getGlobalProgressIndicator()?.cancel()
            }
            false
        }
    }

    /** A hook that records the live indicator at the processor's FIRST check and cancels nothing. */
    private fun capturingHook(
        liveIndicator: AtomicReference<ProgressIndicator?>,
    ): CoreProgressManager.CheckCanceledHook =
        CoreProgressManager.CheckCanceledHook {
            if (calledDirectlyByTheProcessor() && liveIndicator.get() == null) {
                liveIndicator.set(ProgressIndicatorProvider.getGlobalProgressIndicator())
            }
            false
        }

    /** Cancels [liveIndicator] as soon as the rename's first edit reaches the document. */
    private fun cancelOnFirstDocumentChange(liveIndicator: AtomicReference<ProgressIndicator?>) {
        myFixture.editor.document.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    liveIndicator.get()?.cancel()
                }
            },
            testRootDisposable,
        )
    }

    /** How many `checkCanceled` calls [LuaRenameProcessor] itself makes renaming a local with [usageCount] usages. */
    private fun processorChecksWhileRenamingWith(usageCount: Int): Int {
        myFixture.configureByText("usages_$usageCount.lua", "local <caret>x = 1\n" + "print(x)\n".repeat(usageCount))
        val checks = AtomicInteger()
        val hook =
            CoreProgressManager.CheckCanceledHook {
                if (calledDirectlyByTheProcessor()) checks.incrementAndGet()
                false
            }
        renameUnderHook(hook, "y")
        return checks.get()
    }

    /**
     * True when the innermost frame below this test's own hook and the platform's progress plumbing
     * is [LuaRenameProcessor] — i.e. the processor called `checkCanceled` itself, rather than some
     * platform routine running underneath it doing so.
     */
    private fun calledDirectlyByTheProcessor(): Boolean =
        StackWalker.getInstance().walk { frames ->
            frames
                .limit(FRAME_SEARCH_DEPTH)
                .filter { !it.className.startsWith(javaClass.name) && !it.className.startsWith(PROGRESS_PACKAGE) }
                .findFirst()
                .map { it.className == PROCESSOR }
                .orElse(false)
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
        const val PROCESSOR = "net.internetisalie.lunar.refactoring.rename.LuaRenameProcessor"
        const val PROGRESS_PACKAGE = "com.intellij.openapi.progress"
        const val FRAME_SEARCH_DEPTH = 20L
    }
}

package net.internetisalie.lunar.refactoring.rename

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REFACT-01 Phase 6 — renaming a parameter moves its `---@param` tag (REFACT-01-16, design §3.6).
 *
 * Every case asserts the **whole file**, never "the tag now says the new name". A tag-only
 * assertion is green under a mutant that rewrites the tag and drops the parameter, and a
 * declaration-only assertion is green under the mutant that produced BUG-457's shape — Phase 4's
 * TC-09 was measured walking into exactly that.
 *
 * **Which of these can actually fail, stated rather than implied.** Measured by unwiring
 * `LuaCatsParamRenamer` from `LuaRenameProcessor.renameElement` — **both halves of the wiring**,
 * design §3.3 step 3a's `LuaCatsParamRenamer.preparedRename` lookup and step 4's
 * `applyCatsTagRewrite?.invoke()`; dropping only the invoke leaves the lookup running and is a
 * different mutant — and running the class: TC-20a, TC-20e and TC-20f go RED and are the **gates**. TC-20b, TC-20c,
 * TC-20d and TC-20g stay green, because each asserts that something does **not** move and nothing
 * moves when the feature is absent. Those four are **guards**, not gates: a green run of any of
 * them is no evidence the feature exists, and they are kept only because each names a mutant of the
 * shipped code that was executed and did make it RED (see their KDoc). One such claim was wrong on
 * the first pass — TC-20e was asserted to catch a no-match fallback it cannot see — which is what
 * TC-20g exists to cover.
 */
@RunWith(JUnit4::class)
class LuaCatsParamRenameTest : BasePlatformTestCase() {
    /**
     * TC-20a — REFACT-01-16. The gate: the tag and the code move together, in one refactoring.
     *
     * The caret is on the **parameter**, which is the only position that drives this at all: a
     * `LuaCatsArgName` is neither a `LuaNameRef` nor a declaration leaf, so `canProcessElement` is
     * false for a caret inside the comment (design §3.0) and `renameElementAtCaret` selects no
     * processor.
     *
     * Mutation (executed): drop design §3.3 step 3a's `LuaCatsParamRenamer.preparedRename` call
     * from `LuaRenameProcessor.renameElement`, so `applyCatsTagRewrite` is null and step 4 applies
     * nothing for the tag. RED — the tag keeps `a` while the parameter and its use become `count`.
     *
     * Also RED (executed) when step 3a's argument is changed from `element` to `replacement` — the
     * plausible wrong reading of the pre-hoist code. At step 3a `replacement` is still free-floating
     * from `LuaElementFactory.createIdentifier`, so `PsiTreeUtil.getParentOfType` finds no
     * `LuaCatsCommentOwner`, `preparedRename` returns null and the tag never moves. That is the only
     * reachable mutant for the hoist: moving the *lookup* back inside the non-cancelable section is
     * not observable from any test, because inside that section its parse cannot throw, so the hoist
     * is justified by design §3.6's argument and this case pins only the argument choice.
     */
    @Test
    fun testParamTagFollowsParameter() {
        myFixture.configureByText(
            "cats_param_follows.lua",
            "---@param a number\nlocal function f(<caret>a) return a end\n",
        )

        myFixture.renameElementAtCaret("count")

        myFixture.checkResult("---@param count number\nlocal function f(count) return count end\n")
    }

    /**
     * TC-20b — REFACT-01-16. `paramTag ::= '@param' ((<<ArgName NAME>> …) | <<ArgSymbol ('...')>>) …`
     * (`luacats.bnf:143`), so the variadic form has a **null** `argName` and names no parameter that
     * can be renamed.
     *
     * Mutant this alone catches (compiles, reachable from this fixture, EXECUTED): read "the tag for
     * this parameter" positionally and take whichever slot the tag has —
     * `paramTagList.getOrNull(LuaPsiImplUtil.getParameters(parList).indexOf(newName))`, then
     * `(tag.argName ?: tag.argSymbol)`. Renaming `x` at position 0 then rewrites this file's only
     * tag through its `...` symbol, producing `---@param first any`. Every other fixture here is
     * green under it: their tag positions and names agree.
     *
     * Green on the parent commit; a guard, not a gate.
     */
    @Test
    fun testVariadicParamTagIsUntouched() {
        myFixture.configureByText(
            "cats_param_variadic.lua",
            "---@param ... any\nlocal function f(<caret>x, ...) return x end\n",
        )

        myFixture.renameElementAtCaret("first")

        myFixture.checkResult("---@param ... any\nlocal function f(first, ...) return first end\n")
    }

    /**
     * TC-20c — REFACT-01-16. A comment with no `@param` tag at all is a no-op, and — the half that
     * matters — the rename still applies. The propagation must never be able to abort a rename it
     * has nothing to do in (design §3.3 step 5's atomicity, `risks-and-gaps.md` Gap 2.13).
     *
     * Mutant this alone catches (compiles, reachable from this fixture): make the empty-tag exit an
     * error instead of a no-op — `?: error("no @param tag for $oldName")` on the selection. The
     * rename then throws after the declaration and its usages are already rewritten, which is the
     * visible half-apply Gap 2.13 predicts for this phase. EXECUTED: RED here and in TC-20b — the
     * two fixtures with no tag to move — green everywhere else.
     *
     * Green on the parent commit; a guard, not a gate.
     */
    @Test
    fun testMissingTagIsANoOp() {
        myFixture.configureByText(
            "cats_param_missing.lua",
            "---@return number\nlocal function f(<caret>a) return a end\n",
        )

        myFixture.renameElementAtCaret("b")

        myFixture.checkResult("---@return number\nlocal function f(b) return b end\n")
    }

    /**
     * TC-20d — REFACT-01-16 with REFACT-01-01. The direction TC-20c cannot reach: a rename that is
     * **refused** must not move the tag either.
     *
     * `end` cannot be written as a Lua identifier, so design §3.3 step 2 throws before the first
     * edit (TC-36 pins that for the code halves). This adds the third rewrite path — comment text,
     * which does not funnel through `LuaElementFactory` — to the same invariant: nothing at all is
     * written, so the file, tag included, is byte-identical afterwards.
     *
     * Mutation (executed): relocate design §3.3 step 3a **above** step 2's refusal *and* invoke its
     * closure there — `LuaCatsParamRenamer.preparedRename(element, oldName, newName)?.invoke()`
     * ahead of `preparedDeclarationRewrite`'s `IncorrectOperationException`. RED — the comment
     * becomes `---@param end number` while the parameter stays `a` and the refactoring reports
     * failure: a half-apply, visible, in the file.
     *
     * **It needs BOTH halves, which is the near miss worth recording.** Invoking the closure at
     * step 3a *alone* does not redden this case, because step 3a already runs after step 2 and a
     * refused rename never reaches it. Since the split of `rename` into a step-3a lookup and a
     * step-4 edit, that one-line form is a different defect — an edit escaping the non-cancelable
     * section rather than escaping the refusal — and no case in this suite pins it; see
     * `risks-and-gaps.md` Gap 2.18.
     */
    @Test
    fun testRefusedRenameDoesNotMoveTheTag() {
        val source = "---@param a number\nlocal function f(<caret>a) return a end\n"
        myFixture.configureByText("cats_param_refused.lua", source)

        val failure = renameFailure("end")

        assertNotNull("a rename that cannot be applied must fail loudly, not report success", failure)
        myFixture.checkResult(source.replace("<caret>", ""))
    }

    /**
     * TC-20e — REFACT-01-16. The second gate, and the falsifiable form of TC-20c: a `@param` tag
     * that names a **different** parameter is left alone while the renamed one is not.
     *
     * Mutant this catches (compiles, reachable from this fixture, EXECUTED): drop the name predicate —
     * `.firstOrNull()` — so the FIRST `@param` tag moves whatever was renamed. `a`'s annotation
     * then becomes `label` and `b`'s is left behind. TC-20a, TC-20c and TC-20f each have at most
     * one named tag and stay green throughout; only a fixture with two can see it.
     *
     * Also RED with `LuaCatsParamRenamer` unwired — **both halves**, design §3.3 step 3a's
     * `preparedRename` lookup and step 4's `applyCatsTagRewrite?.invoke()` — because `b`'s tag must
     * move when `b` is the one renamed, which with TC-20a and TC-20f makes this one of the three
     * gates.
     *
     * A mutant it does **not** catch, recorded because it was executed and assumed otherwise: a
     * no-match FALLBACK to the first tag (`… ?: taggedNames.firstOrNull()`). Every tag this fixture
     * needs is present, so the fallback never fires here. TC-20g is that mutant's gate.
     */
    @Test
    fun testOnlyTheMatchingParamTagMoves() {
        myFixture.configureByText(
            "cats_param_two.lua",
            "---@param a number\n---@param b string\nlocal function f(a, <caret>b) return a, b end\n",
        )

        myFixture.renameElementAtCaret("label")

        myFixture.checkResult(
            "---@param a number\n---@param label string\nlocal function f(a, label) return a, label end\n",
        )
    }

    /**
     * TC-20f — REFACT-01-16. The comment owner of a `function` **expression** is the enclosing
     * `local` declaration, not the function (`LuaLocalVarDecl` is a `LuaCommentOwner`), so the
     * parameter's nearest `LuaCatsCommentOwner` ancestor is two containers further out than in
     * TC-20a. Design §3.6 asserts this shape works; measured here rather than trusted.
     *
     * Mutant this alone catches (compiles, reachable from this fixture, EXECUTED): replace the walk
     * with a fixed-depth ancestor — `parameterIdentifier.parent?.parent?.parent?.parent as?
     * LuaCatsCommentOwner` — which lands on TC-20a's `LuaLocalFuncDecl` and on this fixture's
     * `LuaFuncDef`, whose comment is one container further out. RED here alone.
     */
    @Test
    fun testTagOnAFunctionExpressionAssignedToALocal() {
        myFixture.configureByText(
            "cats_param_funcdef.lua",
            "---@param a number\nlocal h = function(<caret>a) return a end\n",
        )

        myFixture.renameElementAtCaret("count")

        myFixture.checkResult("---@param count number\nlocal h = function(count) return count end\n")
    }

    /**
     * TC-20g — REFACT-01-16. A `@param` tag that exists but names a **different** parameter than the
     * one being renamed. Design §3.6 lists "a mismatched tag name" among its no-ops; nothing else
     * here pins it, because every other fixture either has a matching tag or has none at all.
     *
     * Mutant this alone catches (compiles, reachable from this fixture, and EXECUTED — it is the
     * one that survived the first pass of these proofs): treat a no-match as "annotate the first
     * tag anyway" —
     * `taggedNames.firstOrNull { it.textMatches(oldName) } ?: taggedNames.firstOrNull()`. `a`'s
     * annotation then silently becomes `label` although `a` was not renamed. TC-20e cannot see it:
     * its `b` tag matches, so the fallback never runs.
     *
     * Green on the parent commit; a guard, not a gate.
     */
    @Test
    fun testATagNamingAnotherParameterIsUntouched() {
        myFixture.configureByText(
            "cats_param_mismatched.lua",
            "---@param a number\nlocal function f(a, <caret>b) return a, b end\n",
        )

        myFixture.renameElementAtCaret("label")

        myFixture.checkResult("---@param a number\nlocal function f(a, label) return a, label end\n")
    }

    private fun renameFailure(newName: String): Throwable? =
        try {
            myFixture.renameElementAtCaret(newName)
            null
        } catch (thrown: RuntimeException) {
            thrown
        }
}

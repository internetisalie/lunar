package net.internetisalie.lunar.lang.types

import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * NAV-13-07's **single scoped exception**, and NAV-13-05's withdrawal observed as an inference
 * change rather than as a `resolve()` (`requirements.md` case 19).
 *
 * TYPE-10's expected-callback seeding does fire for a colon call today, and it fires *unsoundly*.
 * `LuaExpectedCallbackResolver.resolveMethodCalleeType` resolves the member name and then requires
 * `resolved.parent?.parent as? LuaFuncDecl`. Pre-feature, the stub-index phase answered a bare
 * member name with a whole `LuaFuncDecl` **node**, and a *nested* declaration's `parent.parent` is
 * the **enclosing** `LuaFuncDecl` — so the lambda passed to `t:m(...)` was seeded from `outer`'s
 * `---@param`, for a call `outer` does not make. Executed at `30052d62`: `z` inferred `string`
 * (`risks-and-gaps.md` DR-03).
 *
 * Under this feature the member name is answered by the colon branch, which returns null mid-build
 * (design §3.6's `isSnapshotUnderConstruction` guard), so the seeding is withdrawn and `z` is
 * `unknown` — the value the dot-call control gives on **both** sides of the change. The withdrawal
 * costs no *sound* seeding: with the genuine spelling `---@param cb fun(a: string)` on
 * `function t:m(cb)`, the member name resolved to nothing pre-feature too (a colon method's stub is
 * keyed `t:m`, not `m`), so `z` was `unknown` there already.
 *
 * **This is a fixture rather than a ratchet row because the ratchet structurally cannot see it**:
 * the pinned corpus carries 0 `---@` tags across all 734 files, so no expected-callback type exists
 * to seed from. `ExpectedCallbackResolverTest` cannot see it either — it carries no colon-call
 * fixture at all.
 *
 * Falsifier: delete the colon branch from `LuaNameReference.multiResolve` (case 1's mutation). It
 * must be observed reddening **this** test, at `z` inferring `string`; `resolve()` cannot see an
 * inference change at all, which is what this case adds over case 1.
 */
@RunWith(JUnit4::class)
class LuaColonCallInferenceWithdrawalTest : IndexedBasePlatformTestCase() {
    /**
     * The colon spelling: the lambda parameter must infer `unknown`, not the `string` it took from
     * the *enclosing* function's `---@param` before this feature.
     */
    @Test
    fun aColonCallDoesNotSeedItsLambdaFromTheEnclosingFunctionsParamTag() {
        myFixture.configureByText(
            "test.lua",
            "---@param cb fun(a: string)\n" +
                "function outer(cb)\n" +
                "  function m(q) end\n" +
                "end\n" +
                "local t = {}\n" +
                "t:m(function(z) return z end)\n",
        )
        assertEquals(
            "the lambda parameter must not be seeded from the enclosing function's ---@param",
            "unknown",
            lambdaParameterTypeName(),
        )
    }

    /**
     * The dot-call control, with the same enclosing shape. It gives `unknown` on **both** sides of
     * the change, which is what makes the colon method's `string` attributable to the colon
     * spelling rather than to the fixture's annotated enclosing function.
     */
    @Test
    fun theDotCallControlIsUnknownOnBothSidesOfTheChange() {
        myFixture.configureByText(
            "test.lua",
            "---@param cb fun(a: string)\n" +
                "function outer(cb)\n" +
                "  function m(q) end\n" +
                "end\n" +
                "local t = {}\n" +
                "t.m2(function(z2) return z2 end)\n",
        )
        assertEquals(
            "the dot spelling never took the enclosing function's ---@param",
            "unknown",
            lambdaParameterTypeName(),
        )
    }

    /**
     * The inferred type name of the sole lambda's sole parameter. The `LuaNameRef` comes from the
     * [LuaFuncDef]'s `parList.nameList.nameRefList` — `function outer(...)` and the nested
     * `function m(...)` are `LuaFuncDecl`s, so the only [LuaFuncDef] in either fixture is the lambda.
     */
    private fun lambdaParameterTypeName(): String {
        val file = myFixture.file
        val lambda =
            requireNotNull(PsiTreeUtil.findChildrenOfType(file, LuaFuncDef::class.java).singleOrNull()) {
                "expected exactly one lambda (LuaFuncDef) in the fixture"
            }
        val parameter =
            requireNotNull(
                lambda.parList
                    ?.nameList
                    ?.nameRefList
                    ?.singleOrNull(),
            ) { "expected the lambda to declare exactly one parameter" }
        val types = LuaTypesSnapshot.forFile(file)
        return types.graphTypeToLuaType(types.getValueType(parameter)).name
    }
}

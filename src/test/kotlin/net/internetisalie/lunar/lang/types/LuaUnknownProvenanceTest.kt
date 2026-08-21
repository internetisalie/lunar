package net.internetisalie.lunar.lang.types

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspection
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-441 — an unknown write must widen the reaching-definition set, not vanish from it.
 *
 * `local d = wx.thing` contributes **no node at all** (measured: `upSet.size=0`), so `d` is
 * byte-identical to a `d` that was never assigned the unknown. The engine then reports its *other*
 * definition against a declared demand as though that were the whole story.
 *
 * [testTheSameCodeWithoutTheUnknownWriteStillErrors] is the control and carries the weight of the
 * whole file: a "fix" that silences the first test by suppressing more broadly has stopped checking
 * rather than represented the unknown, and every other assertion here still passes when it does.
 */
@RunWith(JUnit4::class)
class LuaUnknownProvenanceTest : BasePlatformTestCase() {
    /** Defect 1: the omitted write leaves a flow looking checkable when it is not. */
    @Test
    fun testAnUnknownWriteDefeatsCertainty() {
        val found =
            diagnosticsFor(
                """
                ---@param n number
                local function count(n) end
                local d = wx.thing
                if cond then d = "s" end
                count(d)
                """.trimIndent(),
            )
        assertEquals(
            "`d` may hold `wx.thing`, which the engine cannot model — reporting its OTHER " +
                "definition against a declared demand asserts more than the model knows. Got: $found",
            emptyList<String>(),
            found,
        )
    }

    /** **The control.** Identical minus the unknown write — this one is genuinely checkable. */
    @Test
    fun testTheSameCodeWithoutTheUnknownWriteStillErrors() {
        val found =
            diagnosticsFor(
                """
                ---@param n number
                local function count(n) end
                local d
                if cond then d = "s" end
                count(d)
                """.trimIndent(),
            )
        assertTrue(
            "with no unknown in `d`'s provenance the string write IS checkable against a declared " +
                "`@param n number`, and suppressing it would mean the fix stopped checking rather " +
                "than represented the unknown. Got: $found",
            found.any { it.contains("not assignable to number") },
        )
    }

    /** Defect 2: `wx.thing or "s"` must not be reported against a declared demand either. */
    @Test
    fun testAnUnknownOperandKeepsTheExpressionGradual() {
        val found =
            diagnosticsFor(
                """
                ---@param n number
                local function count(n) end
                local v = wx.thing or "s"
                count(v)
                """.trimIndent(),
            )
        assertEquals("an unknown operand makes the union gradual. Got: $found", emptyList<String>(), found)
    }

    /**
     * ERROR-tier only, deliberately. The fix **downgrades** an unaccountable conflict to
     * `HYPOTHESIS` rather than dropping it — BUG-416 requires that suppression never enable
     * anything, and the finding is still worth surfacing as "the model is incomplete here". So the
     * assertion has to name the tier: filtering on the message alone would go red on a correct fix.
     */

    /**
     * **The presentation half, which BUG-441 scoped out and no test reached.**
     *
     * `displayName()` is asserted in several places — `ArraySubscriptTypeTest`, `LambdaParam-
     * InferenceTest`, `StubGlobalSeedTypeTest` — but every one of them asserts on a
     * *sub-expression*: the subscript node, the lambda parameter, the `KEYS` reference itself.
     * RC-1 changes `collectRhsNodes`, which feeds the **variable on the left**, and nothing
     * asserted there. `StubGlobalSeedTypeTest` shows the shape exactly: its fixture is
     * `local x = KEYS` and it pins `KEYS`, never `x`.
     *
     * So this records what a user actually sees in an inlay for a variable bound to something the
     * engine cannot model. It is a **characterization** test, not a preference: the report leaves
     * the `any`-versus-`undefined` wording to a presentation-boundary projection (BUG-424's
     * precedent) if it proves noisy, and this is what would go red when that lands.
     */
    @Test
    fun testAVariableBoundToAnUnmodellableRhsDisplaysAsGradual() {
        myFixture.configureByText("test.lua", "local a = wx.thing\n")
        val shown =
            runReadAction {
                val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
                val aRef =
                    PsiTreeUtil
                        .findChildrenOfType(myFixture.file, LuaNameRef::class.java)
                        .first { it.text == "a" }
                snapshot.getValueType(aRef).displayName()
            }
        println("BUG-441 displayName of `a` in `local a = wx.thing` -> $shown")
        assertEquals(
            "an unmodellable RHS now contributes an explicit gradual node rather than no node at " +
                "all, so the variable reads `any` where it read `undefined` before BUG-441. Both " +
                "mean the engine does not know; this pins WHICH is shown, because the inlay is the " +
                "only surface where the difference is visible to a user",
            "any",
            shown,
        )
    }

    private fun diagnosticsFor(source: String): List<String> {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText("test.lua", source + "\n")
        val all = myFixture.doHighlighting()
        println("BUG-441 fixture ->")
        source.lines().forEach { println("    | $it") }
        all.forEach { println("    HIGHLIGHT sev=${it.severity} desc=${it.description}") }
        return all
            .filter { it.severity == HighlightSeverity.ERROR }
            .mapNotNull { it.description }
            .filter { it.contains("not assignable") }
    }
}

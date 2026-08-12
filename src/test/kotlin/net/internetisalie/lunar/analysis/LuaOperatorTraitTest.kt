package net.internetisalie.lunar.analysis

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-423 / BUG-424: what an operator POSITION admits.
 *
 * `visitBinOpExpr` demanded exactly `Number` at every arithmetic operator and exactly `String` at
 * `..`. Lua coerces between the two at both, and consults a metamethod when neither operand is a
 * number — so the engine rejected legal code, and did so 661 times on one corpus member alone.
 *
 * These drive the platform inspection rather than the snapshot, because the thing that must not
 * regress is what the user is shown.
 *
 * **Scope: metamethods recorded from a real `setmetatable` metatable.** The other half — a
 * `---@class` declaring `__add` as a field, which BUG-426 left as a Known limitation — is COMP-09-05
 * and lives in `MemberEnumerationMetamethodTest`. The two mechanisms record into the same
 * `LuaGraphType.Table.metamethods` and are consumed by the same `LuaTypeGraph.implementsOperator`.
 */
@RunWith(JUnit4::class)
class LuaOperatorTraitTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
    }

    private fun assignabilityProblems(text: String): List<String> {
        myFixture.configureByText("test.lua", text)
        return myFixture
            .doHighlighting()
            .mapNotNull { it.description }
            .filter { it.contains("not assignable") }
    }

    private fun assertNoProblem(
        why: String,
        text: String,
    ) {
        val problems = assignabilityProblems(text)
        assertTrue("$why — got: $problems", problems.isEmpty())
    }

    private fun assertProblem(
        why: String,
        text: String,
    ) {
        val problems = assignabilityProblems(text)
        assertTrue("$why — got no assignability problem", problems.isNotEmpty())
    }

    // --- Coercion: what Lua accepts at arithmetic and concatenation (BUG-423) ----------------

    /** `"10" + 5` is 15 on every supported level. Measured on 5.0.3, 5.4.7 and 5.5.0. */
    @Test
    fun testStringOperandAtArithmeticIsAccepted() =
        assertNoProblem(
            "Lua coerces a string operand at arithmetic",
            """local total = "10" + 5""",
        )

    /** `-"5"` is −5. Unary minus is the fourth `NUMBER` row in the design's operator table. */
    @Test
    fun testStringOperandAtUnaryMinusIsAccepted() =
        assertNoProblem(
            "Lua coerces a string operand at unary minus",
            """local negated = -"5"""",
        )

    /** `1 .. "x"` is `"1x"`. Concatenation coerces in the other direction. */
    @Test
    fun testNumberOperandAtConcatIsAccepted() =
        assertNoProblem(
            "Lua coerces a number operand at concatenation",
            """local joined = 1 .. "x"""",
        )

    /**
     * The variable hop. BUG-419 shipped a defect for exactly this shape — the checked pair is
     * frequently (value, *variable*), not (value, use-node) — so the direct form proves less than
     * it looks.
     */
    @Test
    fun testStringOperandReachingArithmeticThroughAVariableIsAccepted() =
        assertNoProblem(
            "a coercible operand must stay accepted through a variable",
            """
            local text = "10"
            local total = text + 5
            """.trimIndent(),
        )

    // --- …and what it still rejects ---------------------------------------------------------

    /** Widening must not become "anything goes": `true + 1` is a runtime error in every Lua. */
    @Test
    fun testBooleanOperandAtArithmeticIsStillRejected() =
        assertProblem(
            "a boolean operand at arithmetic is a genuine Lua error",
            """local total = true + 1""",
        )

    /** The `..` counterpart, and the shape `testBooleanConcatMismatchReported` already pins. */
    @Test
    fun testBooleanOperandAtConcatIsStillRejected() =
        assertProblem(
            "a boolean operand at concatenation is a genuine Lua error",
            """
            local flag = true
            local joined = flag .. "a"
            """.trimIndent(),
        )

    /**
     * The control for the whole trait mechanism: widening a *position* must not become "anything
     * goes at that position". A plain table has no arithmetic metamethod, so it is a genuine Lua
     * error and must still be reported.
     *
     * This is the fixture that keeps [LuaGraphType.Trait.admits] honest. Without it, adding
     * `Table` to `Numberable` would make every metamethod test pass while modelling nothing —
     * which is exactly the mistake BUG-424's own fix section warns about.
     */
    @Test
    fun testPlainTableAtArithmeticIsStillRejected() =
        assertProblem(
            "a table with no arithmetic metamethod is a genuine Lua error",
            """
            local plain = {}
            local total = plain + 2
            """.trimIndent(),
        )

    // --- Metamethods: a table that implements the operator (BUG-424) -------------------------

    /**
     * BUG-424's measured fixture, reduced. `V.__add` makes `instance + 2` legal Lua — 5.4.7 and
     * 5.5.0 both print `3`.
     *
     * These fixtures are only meaningful because BUG-426 landed first. Before it, `instance`
     * inferred `Undefined`, which absorbs every check — so this test passed with no metamethod arm
     * implemented at all, and would have "proved" a feature that did not exist.
     * [testPlainTableAtArithmeticIsStillRejected] is what keeps it honest now: the two differ only
     * in whether the metatable defines the operator.
     */
    @Test
    fun testTableWithArithmeticMetamethodIsAccepted() =
        assertNoProblem(
            "a table whose metatable defines __add implements arithmetic",
            """
            local V = {}
            V.__index = V
            V.__add = function(a, b) return 0 end
            local instance = setmetatable({}, V)
            local total = instance + 2
            """.trimIndent(),
        )

    /** `__concat` is the same arm at the other operator, and needs no `__index` to work. */
    @Test
    fun testTableWithConcatMetamethodIsAccepted() =
        assertNoProblem(
            "a table whose metatable defines __concat implements concatenation",
            """
            local V = {}
            V.__concat = function(a, b) return "V" end
            local instance = setmetatable({}, V)
            local joined = instance .. "s"
            """.trimIndent(),
        )

    /** `#` demanded an unnamed trait long before this; `__len` is the arm it was missing. */
    @Test
    fun testTableWithLenMetamethodIsAccepted() =
        assertNoProblem(
            "a table whose metatable defines __len implements length",
            """
            local V = {}
            V.__len = function(a) return 1 end
            local instance = setmetatable({}, V)
            local size = #instance
            """.trimIndent(),
        )

    /**
     * A metatable that implements `__add` does not thereby implement `__concat`. Without this, a
     * metamethod check that ignored *which* metamethod would pass all three tests above.
     */
    @Test
    fun testMetamethodDoesNotSatisfyAnUnrelatedOperator() =
        assertProblem(
            "__add must not make a table concatenable",
            """
            local V = {}
            V.__add = function(a, b) return 0 end
            local instance = setmetatable({}, V)
            local joined = instance .. "s"
            """.trimIndent(),
        )
}
